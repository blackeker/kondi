package com.myanim.kondi.data.download

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.coroutines.coroutineContext

class PausedException : Exception("PAUSED")

class DirectDownloadStrategy(private val context: android.content.Context, private val client: OkHttpClient) : DownloadStrategy {

    companion object {
        private const val TAG = "DirectDownloadStrategy"
        private const val BUFFER_SIZE = 262144 // 256 KB
    }

    // Lock for multi-chunk progress calculations
    private val progressLock = Any()

    // Download client wrapper with dedicated pool, HTTP/1.1 protocols, and timeouts
    private val downloadClient: OkHttpClient by lazy {
        client.newBuilder()
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .connectionPool(okhttp3.ConnectionPool(20, 5, java.util.concurrent.TimeUnit.MINUTES))
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    override suspend fun download(
        id: String,
        title: String,
        url: String,
        file: File,
        headers: Map<String, String>,
        onProgress: (progress: Int, downloaded: Long, total: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "[$title] Starting download probe: $url")
        var totalSize = -1L
        var supportsRange = false
        
        val probe = Request.Builder().url(url)
            .header("Range", "bytes=0-0")
            .apply { headers.forEach { (k, v) -> if (k != "Range") header(k, v) } }
            .build()
            
        val call = client.newCall(probe)
        
        try {
            // Bind OkHttp probe execution to Coroutine Cancellation
            coroutineScope {
                val execution = async(Dispatchers.IO) {
                    call.execute()
                }
                
                // If the coroutine is cancelled before execution completes, cancel OkHttp call
                try {
                    execution.await().use { r ->
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
                } catch (e: CancellationException) {
                    call.cancel()
                    throw e
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "[$title] Probe connection failed: ${e.message}")
        }

        val prefManager = com.myanim.kondi.data.prefs.UserPreferencesManager.getInstance(context)
        val multiChunkEnabled = prefManager.enableMultiChunk
        val numChunks = prefManager.downloadThreads.coerceAtMost(4)

        // Ensure we only use multi-chunk strategy if threads/chunks count is greater than 1
        val useMultiChunk = supportsRange && totalSize > 0 && multiChunkEnabled && numChunks > 1

        Log.i(TAG, "[$title] Probe result: supportsRange=$supportsRange, totalSize=$totalSize bytes, multiChunkEnabled=$multiChunkEnabled. Selected Strategy: ${if (useMultiChunk) "Multi-Chunk ($numChunks Chunks)" else "Single-Thread"}")

        if (useMultiChunk) {
            downloadMultiChunk(title, url, file, headers, totalSize, numChunks, onProgress)
            true  // downloadMultiChunk throws on failure, so reaching here means success
        } else {
            downloadSingleThread(title, url, file, headers, supportsRange, onProgress)
        }
    }

    private suspend fun downloadMultiChunk(
        title: String, url: String, file: File,
        headers: Map<String, String>, totalSize: Long,
        numChunks: Int,
        onProgress: (Int, Long, Long) -> Unit
    ) = coroutineScope {
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
        
        val lastCallbackTime = java.util.concurrent.atomic.AtomicLong(0L)
        val lastCallbackPct = java.util.concurrent.atomic.AtomicInteger(-1)

        Log.i(TAG, "[$title] Starting Multi-Chunk download: totalSize=$totalSize, chunks=$numChunks, initialDownloaded=$initialDownloaded")
        
        val tempMerge = File(file.parent, "${file.name}.merging")
        
        try {
            val jobs = (0 until numChunks).map { i ->
                val startByte = i.toLong() * chunkSize
                val endByte = if (i == numChunks - 1) totalSize - 1 else startByte + chunkSize - 1

                async(Dispatchers.IO) {
                    var completed = false
                    var attempt = 0
                    val maxAttempts = 10
                    
                    while (!completed && attempt < maxAttempts && isActive) {
                        try {
                            val cf = chunkFiles[i]
                            val existing = if (cf.exists()) cf.length() else 0L
                            val chunkTargetSize = endByte - startByte + 1
                            if (existing >= chunkTargetSize) {
                                Log.d(TAG, "[$title] Chunk $i already completed with $existing bytes. Skipping.")
                                completed = true
                                break
                            }
                            
                            val actualStart = startByte + existing
                            Log.d(TAG, "[$title] Chunk $i connecting to range: $actualStart-$endByte (attempt ${attempt + 1})")
                            val req = Request.Builder().url(url)
                                .apply { headers.forEach { (k, v) -> header(k, v) } }
                                .header("Range", "bytes=$actualStart-$endByte")
                                .build()

                            downloadClient.newCall(req).execute().use { resp ->
                                if (!resp.isSuccessful && resp.code != 206) {
                                    Log.e(TAG, "[$title] Chunk $i request failed: HTTP ${resp.code}")
                                    throw Exception("Chunk$i HTTP ${resp.code}")
                                }
                                val body = resp.body ?: throw Exception("Chunk$i null body")
                                Log.d(TAG, "[$title] Chunk $i connected: HTTP ${resp.code}, expected content length: ${body.contentLength()}")
                                val buf = ByteArray(BUFFER_SIZE)
                                var n = 0
                                
                                java.io.FileOutputStream(cf, true).use { fos ->
                                    java.io.BufferedInputStream(body.byteStream(), BUFFER_SIZE).use { inp ->
                                        while (isActive && inp.read(buf).also { n = it } != -1) {
                                            yield()
                                            fos.write(buf, 0, n)
                                            val currentTotal = totalDownloaded.addAndGet(n.toLong())
                                            
                                            val pct = ((currentTotal * 100) / totalSize).toInt().coerceIn(0, 99)
                                            val now = System.currentTimeMillis()
                                            var shouldCallback = false
                                            synchronized(progressLock) {
                                                if (pct != lastCallbackPct.get() || now - lastCallbackTime.get() >= 2000L) {
                                                    lastCallbackPct.set(pct)
                                                    lastCallbackTime.set(now)
                                                    shouldCallback = true
                                                }
                                            }
                                            if (shouldCallback) {
                                                Log.d(TAG, "[$title] Multi-Chunk progress: $pct% ($currentTotal/$totalSize bytes)")
                                                onProgress(pct, currentTotal, totalSize)
                                            }
                                        }
                                    }
                                }
                            }
                            Log.d(TAG, "[$title] Chunk $i completed downloading.")
                            completed = true
                        } catch (e: CancellationException) {
                            Log.i(TAG, "[$title] Chunk $i cancelled.")
                            throw e
                        } catch (e: Exception) {
                            if (e is PausedException) {
                                Log.i(TAG, "[$title] Chunk $i paused by user.")
                                throw e
                            }
                            attempt++
                            Log.w(TAG, "[$title] Chunk $i failed on attempt $attempt: ${e.message}. Retrying...")
                            if (attempt >= maxAttempts) {
                                Log.e(TAG, "[$title] Chunk $i exceeded maximum retry attempts ($maxAttempts). Failing download.")
                                throw e
                            }
                            
                            val delayMs = 1500L + kotlin.random.Random.nextLong(0, 500)
                            delay(delayMs)
                        }
                    }
                }
            }
            jobs.awaitAll()

            Log.i(TAG, "[$title] All chunks downloaded. Merging into target file: ${file.absolutePath}")
            
            if (tempMerge.exists()) tempMerge.delete()
            
            java.io.FileOutputStream(tempMerge, false).use { out ->
                chunkFiles.forEachIndexed { index, cf ->
                    Log.d(TAG, "[$title] Merging chunk $index (${cf.length()} bytes)...")
                    cf.inputStream().use { it.copyTo(out, 65536) }
                }
            }
            
            if (file.exists()) file.delete()
            val success = tempMerge.renameTo(file)
            if (success) {
                Log.i(TAG, "[$title] Merged successfully and renamed target file. Size: ${file.length()} bytes.")
            } else {
                throw Exception("Failed to rename merging temp file to target path")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$title] Error during Multi-Chunk download: ${e.message}", e)
            throw e
        } finally {
            if (tempMerge.exists()) tempMerge.delete()
            // Robust cleanup check: Only delete chunks if the final file exists AND matches the expected totalSize
            if (file.exists() && file.length() == totalSize) {
                chunkFiles.forEach { if (it.exists()) it.delete() }
            }
        }
    }

    private suspend fun downloadSingleThread(
        title: String, url: String, file: File,
        headers: Map<String, String>,
        supportsRange: Boolean,
        onProgress: (Int, Long, Long) -> Unit
    ): Boolean {
        var completed = false
        var attempt = 0
        val maxAttempts = 10
        
        var dynamicSupportsRange = supportsRange
        
        while (!completed && attempt < maxAttempts && coroutineContext.isActive) {
            try {
                val existingBytes = if (file.exists()) file.length() else 0L
                Log.i(TAG, "[$title] Starting Single-Thread download. Existing bytes: $existingBytes, supportsRange=$dynamicSupportsRange (attempt ${attempt + 1})")
                
                val reqBuilder = Request.Builder().url(url)
                    .apply { headers.forEach { (k, v) -> header(k, v) } }
                
                if (existingBytes > 0 && dynamicSupportsRange) {
                    reqBuilder.header("Range", "bytes=$existingBytes-")
                } else if (existingBytes > 0 && !dynamicSupportsRange) {
                    if (file.exists()) file.delete()
                }

                downloadClient.newCall(reqBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206 && response.code != 416 && response.code != 200) {
                        Log.e(TAG, "[$title] Single-Thread request failed: HTTP ${response.code}")
                        throw Exception("HTTP ${response.code}: ${response.message}")
                    }

                    if (response.code == 416) {
                        Log.i(TAG, "[$title] Already fully downloaded (HTTP 416).")
                        onProgress(100, existingBytes, existingBytes)
                        completed = true
                        return@use
                    }

                    val responseCode = response.code
                    val appendMode = (responseCode == 206 && existingBytes > 0 && dynamicSupportsRange)
                    
                    if (responseCode == 200 && existingBytes > 0) {
                        Log.i(TAG, "[$title] Range not supported by server (HTTP 200 returned). Deleting existing file and restarting from scratch.")
                        if (file.exists()) file.delete()
                        dynamicSupportsRange = false
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
                    } else if (responseCode == 206) {
                        if (contentSize > 0) contentSize + existingBytes else -1L
                    } else {
                        contentSize
                    }

                    Log.i(TAG, "[$title] Single-Thread connected: HTTP $responseCode, appendMode=$appendMode, totalExpectedSize=$totalExpectedSize bytes")

                    val startBytes = if (appendMode) existingBytes else 0L
                    onProgress(if (totalExpectedSize > 0) ((startBytes * 100) / totalExpectedSize).toInt() else 0, startBytes, totalExpectedSize)

                    val buf = ByteArray(BUFFER_SIZE)
                    var n = 0
                    var totalRead = startBytes

                    var lastCallbackTime = 0L
                    var lastCallbackPct = -1
                    var yieldCounter = 0

                    java.io.FileOutputStream(file, appendMode).use { fos ->
                        java.io.BufferedInputStream(body.byteStream(), BUFFER_SIZE).use { inp ->
                            while (coroutineContext.isActive && inp.read(buf).also { n = it } != -1) {
                                fos.write(buf, 0, n)
                                totalRead += n
                                
                                if (++yieldCounter % 4 == 0) {
                                    yield()
                                }
                                
                                val pct = if (totalExpectedSize > 0) ((totalRead * 100) / totalExpectedSize).toInt() else 0
                                val now = System.currentTimeMillis()
                                var shouldCallback = false
                                
                                if (pct != lastCallbackPct || now - lastCallbackTime >= 2000L) {
                                    lastCallbackPct = pct
                                    lastCallbackTime = now
                                    shouldCallback = true
                                }
                                
                                if (shouldCallback) {
                                    Log.d(TAG, "[$title] Single-Thread progress: $pct% ($totalRead/$totalExpectedSize bytes)")
                                    onProgress(pct, totalRead, totalExpectedSize)
                                }
                            }
                        }
                    }
                    Log.i(TAG, "[$title] Single-Thread download completed successfully. Total size: $totalRead bytes")
                    completed = true
                }
            } catch (e: CancellationException) {
                Log.i(TAG, "[$title] Single-Thread download cancelled.")
                throw e
            } catch (e: Exception) {
                if (e is PausedException) {
                    Log.i(TAG, "[$title] Single-Thread download paused by user.")
                    throw e
                }
                attempt++
                Log.w(TAG, "[$title] Single-Thread failed on attempt $attempt: ${e.message}. Retrying...")
                if (attempt >= maxAttempts) {
                    Log.e(TAG, "[$title] Single-Thread exceeded maximum retry attempts ($maxAttempts). Failing download.")
                    throw e
                }
                
                val delayMs = minOf(1000L * (1L shl minOf(attempt, 20)), 30000L) + kotlin.random.Random.nextLong(0, 1000)
                delay(delayMs)
            }
        }
        return completed
    }
}
