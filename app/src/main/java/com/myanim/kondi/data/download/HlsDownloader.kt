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
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class HlsDownloader(private val context: android.content.Context, private val client: OkHttpClient) {
    
    private val batchSize: Int
        get() = com.myanim.kondi.data.prefs.UserPreferencesManager.getInstance(context).downloadThreads

    companion object {
        private const val TAG = "HlsDownloader"
        private const val BUFFER_SIZE = 262144    // 256 KB I/O buffer (Hız için artırıldı)
        private const val MAX_RETRIES = 5
        private const val RETRY_DELAY_MS = 500L  // Hızlı retry
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
            
            Log.d(TAG, "HLS download completed successfully: ${outputFile.absolutePath} (${outputFile.length() / 1024 / 1024} MB)")
            true
            
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
            // Batch halinde paralel indir
            val currentBatchSize = batchSize
            for ((batchIndex, batch) in segments.chunked(currentBatchSize).withIndex()) {
                coroutineContext.ensureActive() // İptal kontrolü
                
                val batchResults = batch.mapIndexed { indexInBatch, segmentUrl ->
                    async(Dispatchers.IO) {
                        val globalIndex = batchIndex * currentBatchSize + indexInBatch
                        val tempFile = File(
                            outputFile.parent,
                            "${outputFile.nameWithoutExtension}_seg_${globalIndex.toString().padStart(5, '0')}.ts"
                        )
                        
                        // Resume: Check if segment already exists and has content
                        if (tempFile.exists() && tempFile.length() > 0) {
                            Log.v(TAG, "Segment $globalIndex already exists, skipping")
                            return@async Pair(tempFile, tempFile.length())
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
                        
                        Pair(tempFile, size)
                    }
                }
                
                // Batch'i bekle
                val results = batchResults.awaitAll()
                
                results.forEach { (file, size) ->
                    tempFiles.add(file)
                    totalDownloaded += size
                }
                
                Log.v(TAG, "Batch completed: ${tempFiles.size}/$totalSegments")
                
                // İlerleme güncelle
                val progress = (tempFiles.size * 100 / totalSegments).toInt()
                val avgSegmentSize = if (tempFiles.isNotEmpty()) totalDownloaded / tempFiles.size else 0L
                val estimatedTotal = avgSegmentSize * totalSegments
                
                progressCallback(progress, totalDownloaded, estimatedTotal)
                
                if (batchIndex % 5 == 0) {
                    Log.d(TAG, "Progress: ${tempFiles.size}/$totalSegments segments, ${totalDownloaded / 1024 / 1024} MB downloaded")
                }
            }
            
            Pair(tempFiles.toList(), true)
            
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
        
        Log.v("HlsDownloader", "Downloading segment: $url")
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e("HlsDownloader", "Segment download failed: HTTP ${response.code} for $url")
                throw Exception("Segment download failed: HTTP ${response.code}")
            }
            
            Log.v("HlsDownloader", "Successfully opened segment stream: $url")
            val inputStream = response.body?.byteStream() 
                ?: throw Exception("Empty segment response")
            
            var totalBytes = 0L
            BufferedOutputStream(FileOutputStream(outputFile), BUFFER_SIZE).use { outputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
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
        val tempMerge = File(outputFile.parent, "${outputFile.name}.merging")
        
        // 256 KB merge buffer - daha hızlı disk write
        val mergeBuffer = BUFFER_SIZE * 2
        BufferedOutputStream(FileOutputStream(tempMerge), mergeBuffer).use { output ->
            val buffer = ByteArray(mergeBuffer)

            sortedSegments.forEach { segment ->
                segment.inputStream().buffered(BUFFER_SIZE).use { input ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
                // Segment'leri merge sırasında silme - finally bloğunda silinecek
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
