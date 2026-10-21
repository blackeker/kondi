package com.myanim.kondi.data.download

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withPermit

class HlsDownloader(private val context: android.content.Context, private val client: OkHttpClient) {
    
    private val batchSize: Int
        get() = com.myanim.kondi.data.prefs.UserPreferencesManager.getInstance(context).downloadThreads.coerceIn(2, 8)

    companion object {
        private const val TAG = "HlsDownloader"
        private const val BUFFER_SIZE = 262144    // 256 KB I/O buffer (Hız için artırıldı)
        private const val MAX_RETRIES = 5
        private const val RETRY_DELAY_MS = 500L  // Hızlı retry
        /** Bozuk/yarım playlist ve segment patlamalarına karşı güvenlik üst sınırı. */
        const val MAX_SEGMENTS = 10_000
        /** Segment indirmesinin yarım kalmaması için kullanılan geçici uzantı. */
        private const val PART_SUFFIX = ".part"
    }
    
    data class DownloadProgress(
        val segmentsDownloaded: Int,
        val totalSegments: Int,
        val bytesDownloaded: Long,
        val estimatedTotalBytes: Long
    )
    
    data class VideoQuality(
        val label: String,
        val bandwidth: Int, // in bps
        val url: String
    )
    
    /**
     * Get available video qualities from master playlist
     * Returns list of available qualities by bandwidth
     */
    suspend fun getAvailableQualities(m3u8Url: String, headers: Map<String, String> = emptyMap()): List<VideoQuality> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching available qualities: $m3u8Url")
            val playlist = downloadPlaylistWithRetry(m3u8Url, headers)
            val lines = playlist.lines().map { it.trim() }
            
            // Check if this is a master playlist
            if (!lines.any { it.contains("#EXT-X-STREAM-INF") }) {
                Log.d(TAG, "Not a master playlist, treating as single quality")
                return@withContext listOf(VideoQuality("Orijinal", 0, m3u8Url))
            }
            
            val qualities = mutableListOf<VideoQuality>()
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                if (line.contains("#EXT-X-STREAM-INF")) {
                    val bandwidth = Regex("BANDWIDTH=(\\d+)")
                        .find(line)
                        ?.groupValues?.get(1)
                        ?.toIntOrNull() ?: 0
                    
                    val resolution = Regex("RESOLUTION=([\\dx]+)")
                        .find(line)
                        ?.groupValues?.get(1) ?: "Bilinmiyor"
                    
                    if (i + 1 < lines.size) {
                        val variantUrlRaw = lines[i + 1]
                        val variantUrl = when {
                            variantUrlRaw.startsWith("http://") || variantUrlRaw.startsWith("https://") -> variantUrlRaw
                            variantUrlRaw.startsWith("/") -> {
                                val protocol = m3u8Url.substringBefore("://")
                                val domain = m3u8Url.substringAfter("://").substringBefore("/")
                                "$protocol://$domain$variantUrlRaw"
                            }
                            else -> {
                                val basePath = m3u8Url.substringBeforeLast("/")
                                "$basePath/$variantUrlRaw"
                            }
                        }
                        
                        val label = when {
                            bandwidth >= 5000000 -> "4K (${bandwidth / 1000000}Mbps)"
                            bandwidth >= 3000000 -> "1080p (${bandwidth / 1000000}Mbps)"
                            bandwidth >= 1500000 -> "720p (${bandwidth / 1000000}Mbps)"
                            bandwidth >= 800000 -> "480p (${bandwidth / 1000}Kbps)"
                            else -> "Düşük (${bandwidth / 1000}Kbps)"
                        }
                        qualities.add(VideoQuality(label, bandwidth, variantUrl))
                    }
                }
                i++
            }
            
            // Sort by bandwidth descending
            qualities.sortByDescending { it.bandwidth }
            Log.d(TAG, "Found ${qualities.size} qualities")
            qualities
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching qualities: ${e.message}")
            emptyList()
        }
    }
    
    suspend fun downloadHls(
        m3u8Url: String,
        outputFile: File,
        headers: Map<String, String> = emptyMap(),
        progressCallback: (Int, Long, Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var tempFiles: List<File> = emptyList()
        var success = false
        
        try {
            Log.d(TAG, "Starting HLS download: $m3u8Url")
            
            // Çıktı dizininin var olduğundan emin ol
            outputFile.parentFile?.mkdirs()
            
            // Playlist'i indir ve ayrıştır
            val playlist = downloadPlaylistWithRetry(m3u8Url, headers)
            val segments = parseM3u8(playlist, m3u8Url, headers)
            
            if (segments.isEmpty()) {
                Log.e(TAG, "No segments found in playlist")
                return@withContext false
            }
            
            // Bozuk/istismar playlist'lerine karşı üst sınır: her segment ayrı dosya + koroutine demek.
            if (segments.size > MAX_SEGMENTS) {
                Log.e(TAG, "Playlist too large: ${segments.size} segments (max $MAX_SEGMENTS)")
                return@withContext false
            }
            
            Log.d(TAG, "Found ${segments.size} segments")
            
            // Segmentleri indir
            val downloadResult = downloadSegments(segments, outputFile, headers, progressCallback)
            tempFiles = downloadResult.first
            success = downloadResult.second
            
            if (!success) {
                Log.e(TAG, "Segment download failed")
                return@withContext false
            }
            
            // Segmentleri birleştir
            Log.d(TAG, "Merging ${tempFiles.size} segments...")
            mergeSegmentsOptimized(tempFiles, outputFile)
            
            // Birleştirme sonrası bütünlük kontrolü: segment dosyaları eksikse video bozuk olur.
            val expectedSize = tempFiles.sumOf { it.length() }
            val actualSize = outputFile.length()
            if (expectedSize <= 0L || actualSize != expectedSize) {
                Log.e(TAG, "HLS merge size mismatch: expected $expectedSize bytes, got $actualSize")
                // Bozuk dosya diskte bırakılmaz: aksi halde "indirilmiş" gibi görünür
                // ve kullanıcı bozuk videoyu oynatamaz.
                outputFile.delete()
                return@withContext false
            }
            
            Log.d(TAG, "HLS download completed successfully: ${outputFile.absolutePath} (${outputFile.length() / 1024 / 1024} MB)")
            true
            
        } catch (e: CancellationException) {
            // İptal (kullanıcı iptali / servis ölümü) bir hata değil; yeniden fırlatılmalı ki
            // üst katman coroutine'u doğru şekilde iptal edebilsin.
            Log.w(TAG, "HLS download cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "HLS download failed: ${e.message}", e)
            false
        } finally {
            // Geçici dosyaları SADECE başarı durumunda temizle (mergeSegmentsOptimized içinde veya sonrasında)
            // Eğer success false ise, segmentleri resume için sakla.
            if (success) {
                tempFiles.forEach { file ->
                    try {
                        if (file.exists()) file.delete()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete temp file: ${file.name}")
                    }
                }
            }
        }
    }
    
    private suspend fun downloadSegments(
        segments: List<String>,
        outputFile: File,
        headers: Map<String, String>,
        progressCallback: (Int, Long, Long) -> Unit
    ): Pair<List<File>, Boolean> = coroutineScope {
        val tempFiles = mutableListOf<File>()
        var totalDownloaded = 0L
        val totalSegments = segments.size.toLong()
        
        try {
            // Pipeline halinde paralel kesintisiz indir (Semaphore ile beklemesiz akış)
            val currentBatchSize = batchSize
            val semaphore = kotlinx.coroutines.sync.Semaphore(currentBatchSize)
            val downloadedCount = java.util.concurrent.atomic.AtomicInteger(0)
            val downloadedBytes = java.util.concurrent.atomic.AtomicLong(0L)
            val lastProgressCallbackTime = java.util.concurrent.atomic.AtomicLong(0L)
            val resultsArray = arrayOfNulls<Pair<File, Long>>(segments.size)

            val jobs = segments.mapIndexed { globalIndex, segmentUrl ->
                async<Unit>(Dispatchers.IO) {
                    semaphore.withPermit {
                        coroutineContext.ensureActive()
                        val tempFile = File(
                            outputFile.parentFile ?: outputFile,
                            "${outputFile.nameWithoutExtension}_seg_${globalIndex.toString().padStart(5, '0')}.ts"
                        )
                        
                        // Resume: Check if segment already exists and has content
                        if (tempFile.exists() && tempFile.length() > 0) {
                            val len = tempFile.length()
                            val count = downloadedCount.incrementAndGet()
                            val bytes = downloadedBytes.addAndGet(len)
                            val progress = (count * 100 / totalSegments).toInt().coerceIn(0, 99)
                            val now = System.currentTimeMillis()
                            if (now - lastProgressCallbackTime.get() >= 1000L || count == totalSegments.toInt()) {
                                lastProgressCallbackTime.set(now)
                                val avgSegmentSize = bytes / count
                                progressCallback(progress, bytes, avgSegmentSize * totalSegments)
                            }
                            resultsArray[globalIndex] = Pair(tempFile, len)
                            return@withPermit
                        }
                        
                        val size = DownloadUtils.retryWithBackoff(
                            maxRetries = MAX_RETRIES,
                            initialDelay = RETRY_DELAY_MS,
                            onRetry = { attempt, e ->
                                Log.w(TAG, "Segment $globalIndex retry $attempt: ${e.message}")
                            }
                        ) {
                            coroutineContext.ensureActive()
                            downloadSegment(segmentUrl, tempFile, headers)
                        }
                        
                        val count = downloadedCount.incrementAndGet()
                        val bytes = downloadedBytes.addAndGet(size)
                        val progress = (count * 100 / totalSegments).toInt().coerceIn(0, 99)
                        val now = System.currentTimeMillis()
                        if (now - lastProgressCallbackTime.get() >= 1000L || count == totalSegments.toInt()) {
                            lastProgressCallbackTime.set(now)
                            val avgSegmentSize = bytes / count
                            progressCallback(progress, bytes, avgSegmentSize * totalSegments)
                        }
                        resultsArray[globalIndex] = Pair(tempFile, size)
                    }
                }
            }
            
            jobs.awaitAll()

            resultsArray.filterNotNull().forEach { (file, _) ->
                tempFiles.add(file)
            }
            
            Pair(tempFiles.toList(), true)
            
        } catch (e: CancellationException) {
            Log.w(TAG, "Segment download cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Segment download failed: ${e.message}", e)
            Pair(tempFiles.toList(), false)
        }
    }
    
    private suspend fun downloadPlaylistWithRetry(
        url: String,
        headers: Map<String, String>,
        retryCount: Int = 0
    ): String = withContext(Dispatchers.IO) {
        try {
            downloadPlaylist(url, headers)
        } catch (e: CancellationException) {
            // İptal edilmiş bir indirme yeniden denenmemeli.
            throw e
        } catch (e: Exception) {
            if (retryCount < MAX_RETRIES) {
                Log.w(TAG, "Playlist download failed, retrying (${retryCount + 1}/$MAX_RETRIES)...")
                kotlinx.coroutines.delay(RETRY_DELAY_MS * (retryCount + 1))
                downloadPlaylistWithRetry(url, headers, retryCount + 1)
            } else {
                throw e
            }
        }
    }
    
    private fun downloadPlaylist(url: String, headers: Map<String, String>): String {
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
        
        headers.forEach { (key, value) -> 
            requestBuilder.addHeader(key, value) 
        }
        
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                // Özel hata durumları için retry mantığı
                if (response.code == 404 && url.contains("double_encode")) {
                    val fallbackUrl = url.substringBefore("?")
                    return downloadPlaylist(fallbackUrl, headers)
                }
                throw Exception("Playlist download failed: HTTP ${response.code}")
            }
            
            return response.body?.string() ?: throw Exception("Empty playlist response")
        }
    }
    
    private suspend fun parseM3u8(
        content: String,
        baseUrl: String,
        headers: Map<String, String>
    ): List<String> {
        val lines = content.lines().map { it.trim() }
        
        // Master playlist kontrolü
        if (lines.any { it.contains("#EXT-X-STREAM-INF") }) {
            Log.d(TAG, "Master playlist detected, selecting best quality")
            
            val variants = mutableListOf<Pair<Int, String>>()
            
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                if (line.contains("#EXT-X-STREAM-INF")) {
                    val bandwidth = Regex("BANDWIDTH=(\\d+)")
                        .find(line)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: 0
                    
                    if (i + 1 < lines.size) {
                        val variantUrl = resolveUrl(lines[i + 1], baseUrl)
                        variants.add(bandwidth to variantUrl)
                    }
                }
                i++
            }
            
            val bestVariant = variants.maxByOrNull { it.first }
                ?: throw Exception("No variants found in master playlist")
            
            Log.d(TAG, "Selected quality: ${bestVariant.first / 1000}kbps")
            
            val variantContent = downloadPlaylistWithRetry(bestVariant.second, headers)
            return parseM3u8(variantContent, bestVariant.second, headers)
        }
        
        // Media playlist - segment URL'lerini çıkar
        return lines
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { resolveUrl(it, baseUrl) }
    }
    
    private fun resolveUrl(url: String, baseUrl: String): String {
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("/") -> {
                val protocol = baseUrl.substringBefore("://")
                val domain = baseUrl.substringAfter("://").substringBefore("/")
                "$protocol://$domain$url"
            }
            else -> {
                val basePath = baseUrl.substringBeforeLast("/")
                "$basePath/$url"
            }
        }
    }
    
    private suspend fun downloadSegmentWithRetry(
        url: String,
        outputFile: File,
        headers: Map<String, String>,
        segmentIndex: Int,
        retryCount: Int = 0
    ): Long = withContext(Dispatchers.IO) {
        try {
            downloadSegment(url, outputFile, headers)
        } catch (e: CancellationException) {
            // İptal edilmiş indirme yeniden denenmemeli.
            throw e
        } catch (e: Exception) {
            if (retryCount < MAX_RETRIES) {
                Log.w(TAG, "Segment $segmentIndex failed, retrying (${retryCount + 1}/$MAX_RETRIES)...")
                kotlinx.coroutines.delay(RETRY_DELAY_MS)
                downloadSegmentWithRetry(url, outputFile, headers, segmentIndex, retryCount + 1)
            } else {
                Log.e(TAG, "Segment $segmentIndex failed after $MAX_RETRIES retries")
                throw e
            }
        }
    }
    
    private fun downloadSegment(
        url: String,
        outputFile: File,
        headers: Map<String, String>
    ): Long {
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
        
        headers.forEach { (key, value) -> 
            requestBuilder.addHeader(key, value) 
        }
        
        // Ensure Referer is set for segments if present in headers
        if (!headers.containsKey("Referer") && headers.containsKey("referer")) {
            headers["referer"]?.let { requestBuilder.addHeader("Referer", it) }
        }
        
        // Yarım (kısmi) segment dosyası "tamamlanmış" sayılmasın diye önce .part dosyasına yazılır.
        // Yazma bittikten ve boyut doğrulandıktan sonra atomik olarak hedef adıyla değiştirilir.
        val partFile = File(outputFile.parentFile ?: outputFile, outputFile.name + PART_SUFFIX)
        if (partFile.exists()) partFile.delete()

        Log.v("HlsDownloader", "Downloading segment: $url")
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e("HlsDownloader", "Segment download failed: HTTP ${response.code} for $url")
                throw IOException("Segment download failed: HTTP ${response.code}")
            }
            
            Log.v("HlsDownloader", "Successfully opened segment stream: $url")
            val inputStream = response.body?.byteStream() 
                ?: throw IOException("Empty segment response")
            
            // Sunucu boyutu bildiriyorsa indirilen veriyle karşılaştıracağız.
            val declaredLength = response.body?.contentLength()?.takeIf { it >= 0L }
            
            var totalBytes = 0L
            try {
                BufferedOutputStream(FileOutputStream(partFile), BUFFER_SIZE).use { outputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }
                }
            } catch (e: Throwable) {
                // Yarım dosya kalmasın: sonraki çalıştırmada "zaten indirilmiş" sanılmasın.
                partFile.delete()
                throw e
            }
            
            if (totalBytes <= 0L) {
                partFile.delete()
                throw IOException("Empty segment downloaded: $url")
            }
            
            if (declaredLength != null && declaredLength != totalBytes) {
                partFile.delete()
                throw IOException(
                    "Segment size mismatch for $url: expected $declaredLength bytes, got $totalBytes"
                )
            }
            
            // Atomik tamamlama: eski hedef varsa sil, .part -> hedef
            if (outputFile.exists() && !outputFile.delete()) {
                partFile.delete()
                throw IOException("Cannot replace existing segment file: ${outputFile.name}")
            }
            if (!partFile.renameTo(outputFile)) {
                // Farklı dosya sistemi durumunda yedek yol
                try {
                    partFile.copyTo(outputFile, overwrite = true)
                    partFile.delete()
                } catch (e: Throwable) {
                    partFile.delete()
                    throw IOException("Cannot finalize segment file: ${outputFile.name}", e)
                }
            }
            
            return totalBytes
        }
    }
    
    private fun mergeSegmentsOptimized(segments: List<File>, outputFile: File) {
        // Dosyaları index sırasına göre sırala
        val sortedSegments = segments.sortedBy { file ->
            file.nameWithoutExtension
                .substringAfterLast("_seg_")
                .toIntOrNull() ?: Int.MAX_VALUE
        }

        // Atomik birleştirme: önce geçici dosyaya yaz, sonra yeniden adlandır
        val tempMerge = File(outputFile.parentFile ?: outputFile, "${outputFile.name}.merging")
        
        // Zero-copy FileChannel ile yüksek hızlı segment birleştirme
        java.io.FileOutputStream(tempMerge, false).use { outFos ->
            val outChannel = outFos.channel
            sortedSegments.forEach { segment ->
                try {
                    java.io.FileInputStream(segment).use { inFis ->
                        val inChannel = inFis.channel
                        var transferred = 0L
                        val size = inChannel.size()
                        while (transferred < size) {
                            transferred += inChannel.transferTo(transferred, size - transferred, outChannel)
                        }
                    }
                } catch (_: Exception) {
                    segment.inputStream().use { it.copyTo(outFos, 262144) }
                }
            }
        }
        
        // Başarılı merge sonrası atomik rename
        if (outputFile.exists()) outputFile.delete()
        if (!tempMerge.renameTo(outputFile)) {
            // renameTo başarısız olabilir (farklı dosya sistemi gibi), fallback: kopyala
            tempMerge.copyTo(outputFile, overwrite = true)
            tempMerge.delete()
        }
    }
}
