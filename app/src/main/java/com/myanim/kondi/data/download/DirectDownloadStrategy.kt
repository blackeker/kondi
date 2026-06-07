package com.myanim.kondi.data.download

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile

class DirectDownloadStrategy(private val context: android.content.Context, private val client: OkHttpClient) : DownloadStrategy {
    override suspend fun download(
        id: String,
        title: String,
        url: String,
        file: File,
        headers: Map<String, String>,
        onProgress: (progress: Int, downloaded: Long, total: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        Log.i("DirectDownloadStrategy", "[$title] Starting download probe: $url")
        // Probe server for size and range support (GET request with Range: bytes=0-0)
        var totalSize = -1L
        var supportsRange = false
        try {
            val probe = Request.Builder().url(url)
                .header("Range", "bytes=0-0")
                .apply { headers.forEach { (k, v) -> if (k != "Range") header(k, v) } }
                .build()
            client.newCall(probe).execute().use { r ->
                if (r.isSuccessful) {
                    if (r.code == 206) {
                        supportsRange = true
                        val contentRange = r.header("Content-Range")
                        if (contentRange != null) {
                            totalSize = contentRange.substringAfter("/").toLongOrNull() ?: -1L
                        }
                    } else {
                        totalSize = r.headers["Content-Length"]?.toLongOrNull() ?: -1L
                        val acceptRanges = r.header("Accept-Ranges")
                        if (acceptRanges == "bytes") {
                            supportsRange = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("DirectDownloadStrategy", "[$title] Probe connection failed: ${e.message}")
        }

        val prefManager = com.myanim.kondi.data.prefs.UserPreferencesManager.getInstance(context)
        val multiChunkEnabled = prefManager.enableMultiChunk
        val numChunks = prefManager.downloadThreads

        val useMultiChunk = supportsRange && totalSize > 0 && multiChunkEnabled

        Log.i("DirectDownloadStrategy", "[$title] Probe result: supportsRange=$supportsRange, totalSize=$totalSize bytes, multiChunkEnabled=$multiChunkEnabled. Selected Strategy: ${if (useMultiChunk) "Multi-Chunk ($numChunks Chunks)" else "Single-Thread"}")

        if (useMultiChunk) {
            downloadMultiChunk(id, title, url, file, headers, totalSize, numChunks, onProgress)
        } else {
            downloadSingleThread(id, title, url, file, headers, supportsRange, onProgress)
        }
        true
    }

    private suspend fun downloadMultiChunk(
        id: String, title: String, url: String, file: File,
        headers: Map<String, String>, totalSize: Long,
        numChunks: Int,
        onProgress: (Int, Long, Long) -> Unit
    ) = coroutineScope {
        //Chunk ayarla
        val chunkSize = totalSize / numChunks
        val chunkFiles = (0 until numChunks).map { File(file.parent, "${file.name}.chunk$it") }
        
        val totalDownloaded = java.util.concurrent.atomic.AtomicLong(0L)
        
        var initialDownloaded = 0L
        for (i in 0 until numChunks) {
            val startByte = i.toLong() * chunkSize
            val endByte = if (i == numChunks - 1) totalSize - 1 else startByte + chunkSize - 1
            val cf = chunkFiles[i]
            val existing = if (cf.exists()) cf.length() else 0L
            val chunkTargetSize = endByte - startByte + 1
            val actualExisting = existing.coerceAtMost(chunkTargetSize)
            initialDownloaded += actualExisting
        }
        totalDownloaded.set(initialDownloaded)
        
        val BUF = 131072 // 128 KB buffer
 
        val lastCallbackTime = java.util.concurrent.atomic.AtomicLong(0L)
        val lastCallbackPct = java.util.concurrent.atomic.AtomicInteger(-1)
 
        Log.i("DirectDownloadStrategy", "[$title] Starting Multi-Chunk download: totalSize=$totalSize, chunks=$numChunks, initialDownloaded=$initialDownloaded")
        try {
            val jobs = (0 until numChunks).map { i ->
                val startByte = i.toLong() * chunkSize
                val endByte = if (i == numChunks - 1) totalSize - 1 else startByte + chunkSize - 1
 
                async(Dispatchers.IO) {
                    var completed = false
                    var attempt = 0
                    val maxAttempts = 10
                    
                    while (!completed && attempt < maxAttempts) {
                        try {
                            val cf = chunkFiles[i]
                            val existing = if (cf.exists()) cf.length() else 0L
                            val chunkTargetSize = endByte - startByte + 1
                            if (existing >= chunkTargetSize) {
                                Log.d("DirectDownloadStrategy", "[$title] Chunk $i already completed with $existing bytes. Skipping.")
                                completed = true
                                break
                            }
                            
                            val actualStart = startByte + existing
                            Log.d("DirectDownloadStrategy", "[$title] Chunk $i connecting to range: $actualStart-$endByte (attempt ${attempt + 1})")
                            val req = Request.Builder().url(url)
                                .apply { headers.forEach { (k, v) -> header(k, v) } }
                                .header("Range", "bytes=$actualStart-$endByte")
                                .build()
 
                            // Use a customized client with shorter timeouts for individual chunks to fail fast and retry
                            val chunkClient = client.newBuilder()
                                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                                .build()
 
                            chunkClient.newCall(req).execute().use { resp ->
                                if (!resp.isSuccessful && resp.code != 206) {
                                    Log.e("DirectDownloadStrategy", "[$title] Chunk $i request failed: HTTP ${resp.code}")
                                    throw Exception("Chunk$i HTTP ${resp.code}")
                                }
                                val body = resp.body ?: throw Exception("Chunk$i null body")
                                Log.d("DirectDownloadStrategy", "[$title] Chunk $i connected: HTTP ${resp.code}, expected content length: ${body.contentLength()}")
                                val buf = ByteArray(BUF)
                                var n = 0
                                
                                java.io.FileOutputStream(cf, true).use { fos ->
                                    java.io.BufferedInputStream(body.byteStream(), BUF).use { inp ->
                                        while (isActive && inp.read(buf).also { n = it } != -1) {
                                            yield()
                                            fos.write(buf, 0, n)
                                            val currentTotal = totalDownloaded.addAndGet(n.toLong())
                                            
                                            val pct = ((currentTotal * 100) / totalSize).toInt().coerceIn(0, 99)
                                            val now = System.currentTimeMillis()
                                            var shouldCallback = false
                                            synchronized(onProgress) {
                                                if (pct != lastCallbackPct.get() || now - lastCallbackTime.get() >= 500L) {
                                                    lastCallbackPct.set(pct)
                                                    lastCallbackTime.set(now)
                                                    shouldCallback = true
                                                }
                                            }
                                            if (shouldCallback) {
                                                Log.d("DirectDownloadStrategy", "[$title] Multi-Chunk progress: $pct% ($currentTotal/$totalSize bytes)")
                                                onProgress(pct, currentTotal, totalSize)
                                            }
                                        }
                                    }
                                }
                            }
                            Log.d("DirectDownloadStrategy", "[$title] Chunk $i completed downloading.")
                            completed = true
                        } catch (e: CancellationException) {
                            Log.i("DirectDownloadStrategy", "[$title] Chunk $i cancelled.")
                            throw e
                        } catch (e: Exception) {
                            if (e.message == "PAUSED") {
                                Log.i("DirectDownloadStrategy", "[$title] Chunk $i paused by user.")
                                throw e
                            }
                            attempt++
                            Log.w("DirectDownloadStrategy", "[$title] Chunk $i failed on attempt $attempt: ${e.message}. Retrying...")
                            if (attempt >= maxAttempts) {
                                Log.e("DirectDownloadStrategy", "[$title] Chunk $i exceeded maximum retry attempts ($maxAttempts). Failing download.")
                                throw e
                            }
                            delay(1000L * attempt + (0..1000).random())
                        }
                    }
                }
            }
            jobs.awaitAll()
 
            Log.i("DirectDownloadStrategy", "[$title] All chunks downloaded. Merging into target file: ${file.absolutePath}")
            // Merge all chunks
            file.delete()
            java.io.FileOutputStream(file, false).use { out ->
                chunkFiles.forEachIndexed { index, cf ->
                    Log.d("DirectDownloadStrategy", "[$title] Merging chunk $index (${cf.length()} bytes)...")
                    cf.inputStream().use { it.copyTo(out, 65536) }
                    cf.delete()
                }
            }
            Log.i("DirectDownloadStrategy", "[$title] Merged successfully. Target file size: ${file.length()} bytes.")
        } catch (e: Exception) {
            Log.e("DirectDownloadStrategy", "[$title] Error during Multi-Chunk download: ${e.message}", e)
            throw e
        }
    }

    private suspend fun downloadSingleThread(
        id: String, title: String, url: String, file: File,
        headers: Map<String, String>,
        supportsRange: Boolean,
        onProgress: (Int, Long, Long) -> Unit
    ) {
        var completed = false
        var attempt = 0
        val maxAttempts = 10
        
        while (!completed && attempt < maxAttempts) {
            try {
                if (!supportsRange && attempt > 0) {
                    if (file.exists()) file.delete()
                }
                
                val existingBytes = if (file.exists()) file.length() else 0L
                Log.i("DirectDownloadStrategy", "[$title] Starting Single-Thread download. Existing bytes: $existingBytes (attempt ${attempt + 1})")
                val reqBuilder = Request.Builder().url(url)
                    .apply { headers.forEach { (k, v) -> header(k, v) } }
                
                if (existingBytes > 0 && supportsRange) {
                    reqBuilder.header("Range", "bytes=$existingBytes-")
                } else if (existingBytes > 0 && !supportsRange) {
                    if (file.exists()) file.delete()
                }
                
                val singleClient = client.newBuilder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
 
                singleClient.newCall(reqBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206 && response.code != 416) {
                        Log.e("DirectDownloadStrategy", "[$title] Single-Thread request failed: HTTP ${response.code}")
                        throw Exception("HTTP ${response.code}: ${response.message}")
                    }
 
                    if (response.code == 416) {
                        Log.i("DirectDownloadStrategy", "[$title] Already fully downloaded (HTTP 416).")
                        onProgress(100, existingBytes, existingBytes)
                        completed = true
                        return
                    }
 
                    val body = response.body ?: throw Exception("Null body")
                    
                    val contentRange = response.header("Content-Range")
                    var parsedTotalSize = -1L
                    if (contentRange != null) {
                        parsedTotalSize = contentRange.substringAfter("/").toLongOrNull() ?: -1L
                    }
 
                    val contentSize = response.header("Content-Length")?.toLongOrNull() ?: body.contentLength()
                    val totalExpectedSize = if (parsedTotalSize > 0) {
                        parsedTotalSize
                    } else if (response.code == 206) {
                        if (contentSize > 0) contentSize + existingBytes else -1L
                    } else {
                        contentSize
                    }
 
                    Log.i("DirectDownloadStrategy", "[$title] Single-Thread connected: HTTP ${response.code}, totalExpectedSize=$totalExpectedSize bytes")
 
                    // Report initial size immediately
                    onProgress(if (totalExpectedSize > 0) ((existingBytes * 100) / totalExpectedSize).toInt() else 0, existingBytes, totalExpectedSize)
 
                    val appendMode = (response.code == 206 && existingBytes > 0 && supportsRange)
                    val BUF_SIZE = 262144 // 256 KB Buffer for faster chunk delivery
                    val buf = ByteArray(BUF_SIZE)
                    var n = 0
                    var totalRead = if (appendMode) existingBytes else 0L
 
                    var lastCallbackTime = 0L
                    var lastCallbackPct = -1
                    var yieldCounter = 0
 
                    java.io.FileOutputStream(file, appendMode).use { fos ->
                        java.io.BufferedInputStream(body.byteStream(), BUF_SIZE).use { inp ->
                            while (kotlin.coroutines.coroutineContext.isActive && inp.read(buf).also { n = it } != -1) {
                                fos.write(buf, 0, n)
                                totalRead += n
                                
                                // Yield every 4 cycles (~1MB of data) to balance thread responsiveness and loop speed
                                if (++yieldCounter % 4 == 0) {
                                    yield()
                                }
                                
                                val pct = if (totalExpectedSize > 0) ((totalRead * 100) / totalExpectedSize).toInt() else 0
                                val now = System.currentTimeMillis()
                                var shouldCallback = false
                                synchronized(onProgress) {
                                    // Throttle progress callback to 2 seconds to reduce disk writing overhead
                                    if (pct != lastCallbackPct || now - lastCallbackTime >= 2000L) {
                                        lastCallbackPct = pct
                                        lastCallbackTime = now
                                        shouldCallback = true
                                    }
                                }
                                if (shouldCallback) {
                                    Log.d("DirectDownloadStrategy", "[$title] Single-Thread progress: $pct% ($totalRead/$totalExpectedSize bytes)")
                                    onProgress(pct, totalRead, totalExpectedSize)
                                }
                            }
                        }
                    }
                    Log.i("DirectDownloadStrategy", "[$title] Single-Thread download completed successfully. Total size: $totalRead bytes")
                    completed = true
                }
            } catch (e: CancellationException) {
                Log.i("DirectDownloadStrategy", "[$title] Single-Thread download cancelled.")
                throw e
            } catch (e: Exception) {
                if (e.message == "PAUSED") {
                    Log.i("DirectDownloadStrategy", "[$title] Single-Thread download paused by user.")
                    throw e
                }
                attempt++
                Log.w("DirectDownloadStrategy", "[$title] Single-Thread failed on attempt $attempt: ${e.message}. Retrying...")
                if (attempt >= maxAttempts) {
                    Log.e("DirectDownloadStrategy", "[$title] Single-Thread exceeded maximum retry attempts ($maxAttempts). Failing download.")
                    throw e
                }
                delay(1000L * attempt + (0..1000).random())
            }
        }
    }
}
