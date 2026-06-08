package com.myanim.kondi.data.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.util.Log
import android.net.NetworkCapabilities
import android.provider.MediaStore
import android.os.Build
import android.content.ContentValues
import android.os.Environment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.myanim.kondi.data.local.Download
import com.myanim.kondi.data.local.DownloadStatus
import com.myanim.kondi.data.local.KondiDatabase
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

class VideoDownloadManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: VideoDownloadManager? = null
        private const val PREFS_NAME = "kondi_prefs"
        private const val KEY_MAX_DOWNLOADS = "max_concurrent_downloads"

        fun getInstance(context: Context): VideoDownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VideoDownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val database = KondiDatabase.getDatabase(context)
    private val dao = database.downloadDao()
    private val notificationManager = DownloadNotificationManager.getInstance(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        })
        .build()
    
    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val pausedDownloads = ConcurrentHashMap<String, Boolean>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val ytDlpDownloader = YtDlpDownloader()
    
    var maxConcurrentDownloads: Int
        get() = sharedPrefs.getInt(KEY_MAX_DOWNLOADS, 1)
        set(value) {
            sharedPrefs.edit().putInt(KEY_MAX_DOWNLOADS, value).apply()
            triggerQueueProcessing()
        }

    private val _downloadsFlow = MutableStateFlow<List<Download>>(emptyList())
    val downloadsFlow: StateFlow<List<Download>> = _downloadsFlow.asStateFlow()

    private var queueWorkerJob: Job? = null

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getStringExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID) ?: return
            when (intent.action) {
                DownloadActionReceiver.ACTION_DOWNLOAD_CANCEL -> cancelDownload(id)
                DownloadActionReceiver.ACTION_DOWNLOAD_PAUSE -> pauseDownload(id)
                DownloadActionReceiver.ACTION_DOWNLOAD_RESUME -> resumeDownload(id)
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(DownloadActionReceiver.ACTION_DOWNLOAD_CANCEL)
            addAction(DownloadActionReceiver.ACTION_DOWNLOAD_PAUSE)
            addAction(DownloadActionReceiver.ACTION_DOWNLOAD_RESUME)
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(broadcastReceiver, filter)
        
        scope.launch {
            dao.getAllDownloads().collect { downloads ->
                _downloadsFlow.value = downloads
            }
        }
        
        triggerQueueProcessing()
        
        scope.launch {
            resumeInterruptedDownloads()
        }
    }

    private suspend fun resumeInterruptedDownloads() {
        val allDownloads = withContext(Dispatchers.IO) { _downloadsFlow.value.ifEmpty { dao.getAllDownloads().firstOrNull() ?: emptyList() } }
        val interrupted = allDownloads.filter { 
            it.status == DownloadStatus.DOWNLOADING.name || it.status == DownloadStatus.PENDING.name 
        }
        
        for (download in interrupted) {
            if (pausedDownloads[download.id] == true) continue
            updateDownloadInDb(download.id) { it.copy(status = DownloadStatus.PENDING.name) }
        }
        triggerQueueProcessing()
    }

    private fun triggerQueueProcessing() {
        synchronized(this) {
            if (queueWorkerJob?.isActive == true) return

            queueWorkerJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val activeCount = downloadJobs.size
                if (activeCount < maxConcurrentDownloads) {
                    val allDownloads = _downloadsFlow.value.ifEmpty { dao.getAllDownloads().firstOrNull() ?: emptyList() }
                    
                    val pendingToStart = allDownloads
                        .filter { it.status == DownloadStatus.PENDING.name && pausedDownloads[it.id] != true && !downloadJobs.containsKey(it.id) }
                        .sortedBy { it.queueOrder }
                        .take(maxConcurrentDownloads - activeCount)
                        
                    for (download in pendingToStart) {
                        val file = File(download.filePath)
                        val job = launch {
                            executeDownload(download.id, download.title, download.url, file, emptyMap())
                        }
                        downloadJobs[download.id] = job
                        updateServiceState()
                        
                        job.invokeOnCompletion {
                            downloadJobs.remove(download.id)
                            updateServiceState()
                            // Launch a delayed trigger so the server has time to cool down before we make a new request
                            scope.launch {
                                delay(6000L)
                                triggerQueueProcessing()
                            }
                        }
                    }
                }
                delay(1000)
            }
            }
        }
    }

    private fun updateServiceState() {
        val intent = Intent(context, VideoDownloadService::class.java)
        if (downloadJobs.isNotEmpty()) {
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e("VideoDownloadManager", "Failed to start foreground service", e)
            }
        } else {
            context.stopService(intent)
        }
    }

    fun startDownload(title: String, url: String, wifiOnly: Boolean = false, cookies: String = "", headers: Map<String, String> = emptyMap(), source: String = "GENERAL", forceDisplayName: Boolean = false, queueOrder: Int? = null): String {
        val id = UUID.randomUUID().toString()
        val extension = ".mp4"
        
        val directory = context.getExternalFilesDir("Kondi_Temp") ?: context.filesDir
        directory.mkdirs()
        
        scope.launch {
            Log.i("VideoDownloadManager", "[$title] startDownload request received: url=$url, wifiOnly=$wifiOnly, source=$source")
            val fileName = (if (forceDisplayName) sanitizeFileName(title) else title.take(20)) + extension
            val file = File(directory, fileName)
        
            val duplicate = _downloadsFlow.value.find { it.url == url }
            if (duplicate != null) {
                if (duplicate.status == DownloadStatus.COMPLETED.name && File(duplicate.filePath).exists()) {
                    Log.i("VideoDownloadManager", "[$title] Already downloaded duplicate found. Skipping download request.")
                    return@launch
                } else if (duplicate.status != DownloadStatus.COMPLETED.name) {
                    Log.i("VideoDownloadManager", "[$title] Incomplete duplicate found (ID: ${duplicate.id}). Resuming existing download.")
                    resumeDownload(duplicate.id)
                    return@launch
                }
            }
            
            if (wifiOnly && !isWifiConnected()) {
                Log.w("VideoDownloadManager", "[$title] Wifi-only is active but WiFi is not connected. Failing immediately.")
                val download = Download(id = id, title = title, url = url, filePath = file.absolutePath, status = DownloadStatus.FAILED.name, progress = 0, totalBytes = 0, downloadedBytes = 0, createdAt = System.currentTimeMillis(), errorMessage = "WiFi gerekli", source = source)
                dao.insertDownload(download)
                notificationManager.showFailedNotification(id, title, "WiFi gerekli")
                return@launch
            }
            
            Log.i("VideoDownloadManager", "[$title] Queueing new download to database. ID: $id")
            val order = queueOrder ?: ((System.currentTimeMillis() / 1000) % Int.MAX_VALUE).toInt()
            val download = Download(id = id, title = title, url = url, filePath = file.absolutePath, status = DownloadStatus.PENDING.name, progress = 0, totalBytes = 0, downloadedBytes = 0, createdAt = System.currentTimeMillis(), source = source, queueOrder = order)
            dao.insertDownload(download)
            triggerQueueProcessing()
        }
        return id
    }

    private suspend fun executeDownload(id: String, title: String, url: String, file: File, passedHeaders: Map<String, String>) = withContext(Dispatchers.IO) {
        Log.i("VideoDownloadManager", "[$title] executeDownload task started. ID=$id, File=${file.absolutePath}, URL=$url")
        try {
            updateDownloadInDb(id) { it.copy(status = DownloadStatus.DOWNLOADING.name) }
            val startTime = System.currentTimeMillis()

            var realUrl = url
            var realHeaders = passedHeaders

            if (url.startsWith("animecix://resolve")) {
                Log.d("VideoDownloadManager", "[$title] Resolving Animecix lazy URL...")
                val uri = try { java.net.URI(url) } catch (e: Exception) { null }
                if (uri != null) {
                    val query = uri.query
                    if (query != null) {
                        val androidUri = android.net.Uri.parse(url)
                        val episodeId = androidUri.getQueryParameter("episodeId")?.toIntOrNull()
                        val titleId = androidUri.getQueryParameter("animeId")?.toIntOrNull()
                        val season = androidUri.getQueryParameter("season")?.toIntOrNull()
                        val episodeParam = androidUri.getQueryParameter("episode")?.toIntOrNull()
                        if (episodeId != null || (titleId != null && season != null && episodeParam != null)) {
                            val api = com.myanim.kondi.data.animecix.AnimecixRepository()
                            
                            var sources: List<com.myanim.kondi.data.animecix.AnimecixSource> = emptyList()
                            var resolvedFinalUrl: String? = null
                            var resolvedRawUrl: String? = null
                            var resolveAttempt = 0
                            val maxResolveAttempts = 3

                            while (resolveAttempt < maxResolveAttempts && resolvedFinalUrl == null) {
                                resolveAttempt++
                                try {
                                    Log.d("VideoDownloadManager", "[$title] Resolving attempt $resolveAttempt/$maxResolveAttempts...")
                                    sources = api.getVideoSources(episodeId ?: 0, titleId, season, episodeParam)
                                    Log.d("VideoDownloadManager", "[$title] Found ${sources.size} raw sources on attempt $resolveAttempt")
                                    if (sources.isNotEmpty()) {
                                        val prefManager = com.myanim.kondi.data.prefs.UserPreferencesManager.getInstance(context)
                                        val preferredHost = prefManager.preferredSource
                                        
                                        val prioritized = sources.sortedByDescending {
                                            val u = it.url.lowercase()
                                            var score = 0
                                            when {
                                                u.contains("tau-video") || u.contains("tau") -> score = 3
                                                u.contains(".m3u8") -> score = 2
                                                u.contains(".mp4") -> score = 1
                                            }
                                            if (preferredHost != null && u.contains(preferredHost)) {
                                                score = 10
                                            }
                                            score
                                        }
                                        Log.d("VideoDownloadManager", "[$title] Prioritized sources count: ${prioritized.size}")
                                        
                                        for (source in prioritized) {
                                            val rawSourceUrl = source.url
                                            if (rawSourceUrl.isBlank()) continue
                                            try {
                                                Log.d("VideoDownloadManager", "[$title] Attempting to resolve source: $rawSourceUrl")
                                                val resolved = api.resolveSource(rawSourceUrl)
                                                if (resolved != null && resolved.startsWith("http")) {
                                                    resolvedFinalUrl = resolved
                                                    resolvedRawUrl = rawSourceUrl
                                                    Log.i("VideoDownloadManager", "[$title] Successfully resolved source: raw=$rawSourceUrl, final=$resolvedFinalUrl")
                                                    break
                                                }
                                            } catch (e: Exception) {
                                                Log.w("VideoDownloadManager", "[$title] Failed to resolve source $rawSourceUrl: ${e.message}")
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("VideoDownloadManager", "[$title] Error resolving source on attempt $resolveAttempt", e)
                                }
                                
                                if (resolvedFinalUrl == null && resolveAttempt < maxResolveAttempts) {
                                    val retryDelay = 3000L * resolveAttempt
                                    Log.d("VideoDownloadManager", "[$title] Source resolution failed on attempt $resolveAttempt. Retrying in ${retryDelay}ms...")
                                    delay(retryDelay) // Exponential backoff (3s, 6s)
                                }
                            }
                            
                            if (resolvedFinalUrl != null && resolvedRawUrl != null) {
                                val lowerRaw = resolvedRawUrl.lowercase()
                                realUrl = if (
                                    lowerRaw.contains("sibnet") || 
                                    lowerRaw.contains("ok.ru") || 
                                    lowerRaw.contains("odnoklassniki") || 
                                    lowerRaw.contains("streamtape") || 
                                    lowerRaw.contains("voe") || 
                                    lowerRaw.contains("uqload") || 
                                    lowerRaw.contains("dood")
                                ) {
                                    resolvedRawUrl
                                } else {
                                    resolvedFinalUrl
                                }
                                
                                val mutableHeaders = passedHeaders.toMutableMap()
                                mutableHeaders["Referer"] = "https://animecix.net"
                                realHeaders = mutableHeaders
                                Log.i("VideoDownloadManager", "[$title] Final resolved URL for download: $realUrl")
                            } else {
                                if (sources.isEmpty()) {
                                    Log.e("VideoDownloadManager", "[$title] Resolution failed: Bölüm kaynağı bulunamadı!")
                                    throw java.lang.Exception("Bölüm kaynağı bulunamadı!")
                                } else {
                                    Log.e("VideoDownloadManager", "[$title] Resolution failed: Bölüm için hiçbir kaynak çözülemedi!")
                                    throw java.lang.Exception("Bölüm için hiçbir kaynak çözülemedi! (3 deneme başarısız)")
                                }
                            }
                        }
                    }
                }
            }

            val strategy = when {
                realUrl.contains("uqload", ignoreCase = true) || 
                realUrl.contains("dood", ignoreCase = true) ||
                realUrl.contains("sibnet", ignoreCase = true) ||
                realUrl.contains("ok.ru", ignoreCase = true) ||
                realUrl.contains("odnoklassniki", ignoreCase = true) ||
                realUrl.contains("streamtape", ignoreCase = true) ||
                realUrl.contains("voe", ignoreCase = true) -> YtDlpDownloadStrategy(ytDlpDownloader)
                realUrl.contains(".m3u8", ignoreCase = true) -> HlsDownloadStrategy(context, client)
                else -> DirectDownloadStrategy(context, client)
            }
            Log.i("VideoDownloadManager", "[$title] Selected strategy: ${strategy.javaClass.simpleName}")

            val headers = getHeadersForUrl(realUrl).toMutableMap().apply { putAll(realHeaders) }

            var lastUpdateProgress = -1
            var lastUpdateTime = 0L
            val success = strategy.download(id, title, realUrl, file, headers) { progress, downloaded, total ->
                if (strategy !is YtDlpDownloadStrategy && pausedDownloads[id] == true) {
                    Log.i("VideoDownloadManager", "[$title] Download paused by user.")
                    throw PausedException()
                }
                val currentTime = System.currentTimeMillis()
                if (progress != lastUpdateProgress || currentTime - lastUpdateTime > 1000L) {
                    lastUpdateProgress = progress
                    lastUpdateTime = currentTime
                    scope.launch {
                        val dbTotal = dao.getDownloadById(id)?.totalBytes ?: 0L
                        val finalTotal = if (total <= 0) {
                            if (dbTotal > 0) dbTotal else total
                        } else {
                            total
                        }
                        val finalProgress = if (finalTotal > 0) {
                            ((downloaded * 100) / finalTotal).toInt().coerceIn(0, 100)
                        } else {
                            progress
                        }
                        dao.updateDownloadProgress(id, DownloadStatus.DOWNLOADING.name, finalProgress, downloaded, finalTotal, currentTime)
                        notificationManager.showProgressNotification(id, title, finalProgress, finalTotal, downloaded, startTime)
                    }
                }
            }

            if (success) {
                val finalSize = file.length()
                Log.i("VideoDownloadManager", "[$title] Download completed successfully. Size: $finalSize bytes.")
                updateDownloadInDb(id) { it.copy(status = DownloadStatus.COMPLETED.name, progress = 100, totalBytes = finalSize, downloadedBytes = finalSize, errorMessage = null) }
                notificationManager.showCompletedNotification(id, title, file.absolutePath)
                dao.getDownloadById(id)?.let { moveToPublicDownloads(it) }
            } else {
                if (pausedDownloads[id] == true) {
                    Log.i("VideoDownloadManager", "[$title] Download is paused in DB.")
                    val currentDownload = dao.getDownloadById(id)
                    updateDownloadInDb(id) { it.copy(status = DownloadStatus.PAUSED.name, progress = currentDownload?.progress ?: 0, downloadedBytes = currentDownload?.downloadedBytes ?: 0, totalBytes = currentDownload?.totalBytes ?: 0, errorMessage = null) }
                } else {
                    Log.e("VideoDownloadManager", "[$title] Download failed (Strategy returned false).")
                    updateDownloadInDb(id) { it.copy(status = DownloadStatus.FAILED.name, errorMessage = "İndirme başarısız oldu (Strateji hatası)") }
                    notificationManager.showFailedNotification(id, title, "İndirme başarısız oldu")
                }
            }
        } catch (e: CancellationException) {
            Log.i("VideoDownloadManager", "[$title] Download cancelled (coroutine cancellation).")
            val currentDownload = dao.getDownloadById(id)
            updateDownloadInDb(id) { it.copy(status = DownloadStatus.PAUSED.name, progress = currentDownload?.progress ?: 0, downloadedBytes = currentDownload?.downloadedBytes ?: 0, totalBytes = currentDownload?.totalBytes ?: 0, errorMessage = null) }
            throw e  // Must rethrow CancellationException for structured concurrency
        } catch (e: Exception) {
            val isPaused = pausedDownloads[id] == true || e is PausedException || e.message == "PAUSED"
            if (!isPaused) {
                Log.e("VideoDownloadManager", "[$title] Execution exception occurred: ${e.message}", e)
                updateDownloadInDb(id) { it.copy(status = DownloadStatus.FAILED.name, errorMessage = e.message ?: "Bilinmeyen hata") }
                notificationManager.showFailedNotification(id, title, e.message ?: "Bilinmeyen hata")
            } else {
                Log.i("VideoDownloadManager", "[$title] Download successfully paused.")
                val currentDownload = dao.getDownloadById(id)
                updateDownloadInDb(id) { it.copy(status = DownloadStatus.PAUSED.name, progress = currentDownload?.progress ?: 0, downloadedBytes = currentDownload?.downloadedBytes ?: 0, totalBytes = currentDownload?.totalBytes ?: 0, errorMessage = null) }
            }
        } finally {
            downloadJobs.remove(id)
        }
    }

    private fun getHeadersForUrl(url: String): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
        headers["X-Requested-With"] = "com.kraptor.AnimeciX"
        
        val uri = try { java.net.URI(url) } catch (_: Exception) { null }
        val host = uri?.host ?: ""
        
        val referer = when {
            url.contains("tau-video") || url.contains("tau") -> "https://animecix.net/"
            url.contains("doodstream") || url.contains("dood.") -> "https://doodstream.com/"
            url.contains("uqload") -> "https://uqload.io/"
            url.contains("cloud.mail.ru") -> "https://cloud.mail.ru/"
            url.contains("sibnet") -> "https://video.sibnet.ru/"
            url.contains("ok.ru") || url.contains("odnoklassniki") -> "https://ok.ru/"
            host.contains("animecix") -> "https://animecix.net/"
            else -> "https://animecix.net/"
        }
        headers["Referer"] = referer
        return headers
    }

    private suspend fun updateDownloadInDb(id: String, update: (Download) -> Download) {
        val download = dao.getDownloadById(id)
        if (download != null) dao.updateDownload(update(download))
    }

    fun moveToPublicDownloads(download: Download): Boolean {
        Log.i("VideoDownloadManager", "[${download.title}] Starting migration to public Downloads folder.")
        try {
            val src = File(download.filePath)
            if (!src.exists()) {
                Log.e("VideoDownloadManager", "[${download.title}] Migration failed: Source temp file does not exist at ${download.filePath}")
                return false
            }
            
            var displayName = src.name
            val mimeType = "video/mp4"
            
            // Clean up double extensions or sanitized extension anomalies (e.g., _mp4.mp4 -> .mp4)
            if (displayName.endsWith("_mp4.mp4", ignoreCase = true)) {
                displayName = displayName.substringBeforeLast("_mp4.mp4") + ".mp4"
            } else if (displayName.endsWith(".mp4.mp4", ignoreCase = true)) {
                displayName = displayName.substringBeforeLast(".mp4.mp4") + ".mp4"
            }
            
            // Extract AnimeName and Season using relaxed regex
            val regex = Regex("(.*)_s(\\d+)_e(\\d+).*")
            val matchResult = regex.matchEntire(src.name)
            val subPath = if (matchResult != null) {
                val animeName = matchResult.groupValues[1]
                    .replace("_mp4", "")
                    .replace(".mp4", "")
                    .trim('_', ' ')
                "Kondi/$animeName"
            } else {
                "Kondi/Diger"
            }
            val relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + subPath
            Log.d("VideoDownloadManager", "[$displayName] Migration destination relative path: $relativePath")
 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = context.contentResolver.insert(collection, values) ?: return false
                val output = context.contentResolver.openOutputStream(uri)
                if (output == null) {
                    Log.e("VideoDownloadManager", "[${download.title}] Migration failed: Could not open output stream for MediaStore URI")
                    return false
                }
                output.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                if (src.delete()) {
                    scope.launch { updateDownloadInDb(download.id) { it.copy(filePath = uri.toString()) } }
                    Log.i("VideoDownloadManager", "[$displayName] Successfully migrated to public folder via MediaStore. Uri: $uri")
                    return true
                }
            } else {
                val destDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), subPath)
                destDir.mkdirs()
                val dest = File(destDir, displayName)
                src.copyTo(dest, overwrite = true)
                if (src.delete()) {
                    scope.launch { updateDownloadInDb(download.id) { it.copy(filePath = dest.absolutePath) } }
                    Log.i("VideoDownloadManager", "[$displayName] Successfully migrated to legacy public path: ${dest.absolutePath}")
                    return true
                }
            }
            Log.e("VideoDownloadManager", "[$displayName] Migration failed at final clean up stage.")
            return false
        } catch (e: Exception) {
            Log.e("VideoDownloadManager", "[${download.title}] Error migrating to public folder: ${e.message}", e)
            return false
        }
    }
 
    fun pauseDownload(id: String) {
        Log.i("VideoDownloadManager", "pauseDownload requested for ID: $id")
        pausedDownloads[id] = true
        downloadJobs[id]?.cancel()
        try {
            com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById(id)
            Log.d("VideoDownloadManager", "YoutubeDL process destroyed for ID: $id")
        } catch (e: Exception) {}
        scope.launch {
            val download = dao.getDownloadById(id) ?: return@launch
            dao.updateDownloadProgress(id, DownloadStatus.PAUSED.name, download.progress, download.downloadedBytes, download.totalBytes, System.currentTimeMillis())
            notificationManager.showPausedNotification(id, download.title, download.downloadedBytes, download.totalBytes)
            Log.i("VideoDownloadManager", "[${download.title}] Download successfully set to PAUSED state in database.")
        }
    }
     
    fun resumeDownload(id: String) {
        Log.i("VideoDownloadManager", "resumeDownload requested for ID: $id")
        pausedDownloads.remove(id)
        scope.launch {
            updateDownloadInDb(id) { it.copy(status = DownloadStatus.PENDING.name) }
            Log.i("VideoDownloadManager", "Download ID: $id status set to PENDING. Triggering queue processing.")
            triggerQueueProcessing()
        }
    }
 
    fun cancelDownload(id: String) {
        Log.i("VideoDownloadManager", "cancelDownload requested for ID: $id")
        downloadJobs[id]?.cancel()
        downloadJobs.remove(id)
        pausedDownloads.remove(id)
        try {
            com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById(id)
            Log.d("VideoDownloadManager", "YoutubeDL process destroyed for ID: $id")
        } catch (e: Exception) {}
        scope.launch {
            val download = dao.getDownloadById(id)
            val title = download?.title ?: "Unknown"
            updateDownloadInDb(id) { it.copy(status = DownloadStatus.FAILED.name, errorMessage = "İptal edildi") }
            notificationManager.cancelNotification(id)
            Log.i("VideoDownloadManager", "[$title] Download successfully cancelled and set to FAILED (Cancelled) state.")
        }
    }
     
    fun deleteDownload(id: String) {
        Log.i("VideoDownloadManager", "deleteDownload requested for ID: $id")
        scope.launch {
            val download = dao.getDownloadById(id)
            if (download != null) {
                val file = File(download.filePath)
                val isDeleted = file.delete()
                Log.d("VideoDownloadManager", "[${download.title}] Temp file deletion result: $isDeleted (path=${download.filePath})")
                
                // Clean up any associated multi-chunk temporary files
                try {
                    val parent = file.parentFile
                    if (parent != null && parent.exists()) {
                        val prefix = file.name + ".chunk"
                        parent.listFiles()?.forEach { f ->
                            if (f.name.startsWith(prefix)) {
                                val chunkDeleted = f.delete()
                                Log.d("VideoDownloadManager", "[${download.title}] Deleted chunk file: ${f.name} (result=$chunkDeleted)")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("VideoDownloadManager", "Error deleting chunk files for ${file.name}", e)
                }
 
                dao.deleteDownload(download)
                notificationManager.cancelNotification(id)
                Log.i("VideoDownloadManager", "[${download.title}] Download successfully purged from database and memory.")
            } else {
                Log.w("VideoDownloadManager", "deleteDownload failed: Download ID $id not found in DB.")
            }
        }
    }

    fun cleanupDeletedFiles() {
        Log.i("VideoDownloadManager", "cleanupDeletedFiles task started.")
        scope.launch {
            val allDownloads = withContext(Dispatchers.IO) { _downloadsFlow.value.ifEmpty { dao.getAllDownloads().firstOrNull() ?: emptyList() } }
            val completed = allDownloads.filter { it.status == DownloadStatus.COMPLETED.name }
            var deletedCount = 0
            for (download in completed) {
                val exists = if (download.filePath.startsWith("content://")) {
                    try {
                        context.contentResolver.query(android.net.Uri.parse(download.filePath), null, null, null, null)?.use { cursor ->
                            cursor.moveToFirst()
                        } ?: false
                    } catch (e: Exception) { false }
                } else {
                    File(download.filePath).exists()
                }
                
                if (!exists) {
                    Log.i("VideoDownloadManager", "[${download.title}] Completed file no longer exists at ${download.filePath}. Cleaning up database record.")
                    dao.deleteDownload(download)
                    deletedCount++
                }
            }
            
            // Clean up any stray chunk files left over in Kondi_Temp directory
            var chunkDeletedCount = 0
            try {
                val directory = context.getExternalFilesDir("Kondi_Temp") ?: context.filesDir
                if (directory.exists()) {
                    directory.listFiles()?.forEach { file ->
                        if (file.name.contains(".chunk")) {
                            if (file.delete()) {
                                chunkDeletedCount++
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("VideoDownloadManager", "Error cleaning up stray chunk files", e)
            }
            Log.i("VideoDownloadManager", "cleanupDeletedFiles completed. Purged $deletedCount missing records and $chunkDeletedCount stray chunk files.")
        }
    }

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    suspend fun getFileSize(url: String, headers: Map<String, String> = emptyMap()): Long = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).head()
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.headers["Content-Length"]?.toLongOrNull() ?: -1L
                } else -1L
            }
        } catch (e: Exception) {
            -1L
        }
    }
    
    fun retryDownload(id: String) {
        scope.launch {
            updateDownloadInDb(id) { it.copy(status = DownloadStatus.PENDING.name, errorMessage = null) }
            triggerQueueProcessing()
        }
    }

    fun updateQueueOrder(id: String, newOrder: Int) {
        scope.launch {
            updateDownloadInDb(id) { it.copy(queueOrder = newOrder) }
            triggerQueueProcessing()
        }
    }

    fun pauseAllDownloads() {
        scope.launch {
            val allDownloads = _downloadsFlow.value.ifEmpty { dao.getAllDownloads().firstOrNull() ?: emptyList() }
            val activeOrPending = allDownloads.filter { 
                it.status == DownloadStatus.DOWNLOADING.name || it.status == DownloadStatus.PENDING.name 
            }
            for (download in activeOrPending) {
                pausedDownloads[download.id] = true
                downloadJobs[download.id]?.cancel()
                try {
                    com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById(download.id)
                } catch (e: Exception) {}
                dao.updateDownloadProgress(
                    download.id, 
                    DownloadStatus.PAUSED.name, 
                    download.progress, 
                    download.downloadedBytes, 
                    download.totalBytes, 
                    System.currentTimeMillis()
                )
                notificationManager.cancelNotification(download.id)
            }
            updateServiceState()
        }
    }

    fun resumeAllDownloads() {
        scope.launch {
            val allDownloads = _downloadsFlow.value.ifEmpty { dao.getAllDownloads().firstOrNull() ?: emptyList() }
            val pausableOrFailed = allDownloads.filter { 
                it.status == DownloadStatus.PAUSED.name || it.status == DownloadStatus.FAILED.name 
            }
            for (download in pausableOrFailed) {
                pausedDownloads.remove(download.id)
                dao.updateDownload(download.copy(status = DownloadStatus.PENDING.name, errorMessage = null))
            }
            triggerQueueProcessing()
        }
    }

    fun moveQueueItemUp(targetId: String) {
        scope.launch(Dispatchers.IO) {
            val allDownloads = _downloadsFlow.value
            val pending = allDownloads.filter { it.status == DownloadStatus.PENDING.name }.sortedBy { it.queueOrder }
            val target = pending.find { it.id == targetId } ?: return@launch
            val index = pending.indexOf(target)
            if (index > 0) {
                val prev = pending[index - 1]
                val targetOrder = target.queueOrder
                val prevOrder = prev.queueOrder
                
                dao.updateDownload(target.copy(queueOrder = prevOrder))
                dao.updateDownload(prev.copy(queueOrder = targetOrder))
                triggerQueueProcessing()
            } else if (index == 0 && pending.isNotEmpty()) {
                val minOrder = pending.minOf { it.queueOrder }
                dao.updateDownload(target.copy(queueOrder = minOrder - 1))
                triggerQueueProcessing()
            }
        }
    }

    fun getImageCacheSize(): Long {
        return getFolderSize(context.cacheDir.resolve("image_cache"))
    }
    
    fun getStrayChunksSize(): Long {
        val directory = context.getExternalFilesDir("Kondi_Temp") ?: context.filesDir
        var size = 0L
        if (directory.exists()) {
            directory.listFiles()?.forEach { file ->
                if (file.name.contains(".chunk")) {
                    size += file.length()
                }
            }
        }
        return size
    }
    
    fun getLogsSize(): Long {
        val logDir = context.getExternalFilesDir(null)?.resolve("logcat")
        val crashDir = context.getExternalFilesDir(null)?.resolve("crash_logs")
        var size = 0L
        if (logDir != null && logDir.exists()) size += getFolderSize(logDir)
        if (crashDir != null && crashDir.exists()) size += getFolderSize(crashDir)
        return size
    }
    
    fun clearImageCache(): Boolean {
        return deleteFolderContents(context.cacheDir.resolve("image_cache"))
    }
    
    fun clearStrayChunks(): Int {
        val directory = context.getExternalFilesDir("Kondi_Temp") ?: context.filesDir
        var deletedCount = 0
        if (directory.exists()) {
            directory.listFiles()?.forEach { file ->
                if (file.name.contains(".chunk")) {
                    if (file.delete()) {
                        deletedCount++
                    }
                }
            }
        }
        return deletedCount
    }
    
    fun clearLogs(): Boolean {
        val logDir = context.getExternalFilesDir(null)?.resolve("logcat")
        val crashDir = context.getExternalFilesDir(null)?.resolve("crash_logs")
        var success = true
        if (logDir != null && logDir.exists()) success = success && deleteFolderContents(logDir)
        if (crashDir != null && crashDir.exists()) success = success && deleteFolderContents(crashDir)
        return success
    }
    
    private fun getFolderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        file.listFiles()?.forEach { size += getFolderSize(it) }
        return size
    }
    
    private fun deleteFolderContents(file: File): Boolean {
        if (!file.exists()) return true
        var success = true
        if (file.isDirectory) {
            file.listFiles()?.forEach { success = success && deleteFolder(it) }
        }
        return success
    }
    
    private fun deleteFolder(file: File): Boolean {
        if (!file.exists()) return true
        var success = true
        if (file.isDirectory) {
            file.listFiles()?.forEach { success = success && deleteFolder(it) }
        }
        return success && file.delete()
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9]"), "_")
    }
}
