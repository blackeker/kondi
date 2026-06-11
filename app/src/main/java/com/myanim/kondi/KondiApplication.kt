package com.myanim.kondi

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.myanim.kondi.workers.AnimecixCheckWorker
import com.myanim.kondi.data.local.KondiDatabase
import java.util.concurrent.TimeUnit
import timber.log.Timber
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.myanim.kondi.util.NetworkUtils
import okhttp3.OkHttpClient

class KondiApplication : Application(), ImageLoaderFactory {
    
    override fun newImageLoader(): ImageLoader {
        val okHttpClient = NetworkUtils.getUnsafeOkHttpClientBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                val newRequestBuilder = request.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
                
                chain.proceed(newRequestBuilder.build())
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // Increased to 100MB
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }
    
    @Volatile private var logcatProcess: Process? = null
    
    companion object {
        private var instance: KondiApplication? = null
        fun getContext(): android.content.Context {
            return instance!!.applicationContext
        }
    }

    override fun onCreate() {
        instance = this
        super.onCreate()
        if (com.myanim.kondi.BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        setupLogcatCapture()
        setupCrashReporter()
        initializeDatabase()
        setupBackgroundWork()
    }

    private fun setupLogcatCapture() {
        try {
            val logDir = getExternalFilesDir(null)?.resolve("logcat") ?: return
            logDir.mkdirs()
            
            // Clean up old logs (keep only last 5)
            val oldLogs = logDir.listFiles()?.sortedBy { it.lastModified() }
            if (oldLogs != null && oldLogs.size > 5) {
                oldLogs.take(oldLogs.size - 5).forEach { it.delete() }
            }

            val timeFormat = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
            val timestamp = timeFormat.format(java.util.Date())
            val logFile = java.io.File(logDir, "logcat_$timestamp.txt")
            
            // Run logcat operations on a background thread to avoid blocking main thread
            Thread {
                try {
                    val clearProcess = Runtime.getRuntime().exec("logcat -c")
                    clearProcess.waitFor()
                    clearProcess.destroy()
                    logcatProcess = Runtime.getRuntime().exec(arrayOf("logcat", "-f", logFile.absolutePath))
                } catch (e: Exception) {
                    Timber.e(e, "Failed to start logcat capture (background)")
                }
            }.start()
        } catch (e: Exception) {
            Timber.e(e, "Failed to start logcat capture")
        }
    }

    private fun setupCrashReporter() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            writeCrashLog("FATAL CRASH on thread ${thread.name}", exception)
            defaultHandler?.uncaughtException(thread, exception)
        }
        
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                if (priority == android.util.Log.ERROR) {
                    writeCrashLog("ERROR ($tag): $message", t)
                }
            }
        })
    }

    private fun writeCrashLog(title: String, exception: Throwable?) {
        try {
            val crashDir = getExternalFilesDir(null)?.resolve("crash_logs") ?: return
            crashDir.mkdirs()
            val timeFormat = java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS", java.util.Locale.getDefault())
            val timestamp = timeFormat.format(java.util.Date())
            val crashFile = java.io.File(crashDir, "error_$timestamp.txt")
            
            val log = buildString {
                append("--- $title ---\n")
                append("Time: $timestamp\n")
                append("Android Version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
                append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
                if (exception != null) {
                    val writer = java.io.StringWriter()
                    exception.printStackTrace(java.io.PrintWriter(writer))
                    append("\n--- Stack Trace ---\n")
                    append(writer.toString())
                }
            }
            crashFile.writeText(log)
        } catch (e: Exception) {
            // Log yazılamazsa görmezden gel
        }
    }

    private fun initializeDatabase() {
        // Initialize Room database eagerly
        KondiDatabase.getDatabase(this)
        
        // Initialize YoutubeDL
        try {
            com.yausername.youtubedl_android.YoutubeDL.getInstance().init(this)
            android.util.Log.d("KondiApplication", "YoutubeDL initialized successfully")
        } catch (e: Throwable) {
            android.util.Log.e("KondiApplication", "Failed to initialize YoutubeDL: ${e.message}", e)
        }

        // Initialize DownloadManager eagerly to resume tasks
        com.myanim.kondi.data.download.VideoDownloadManager.getInstance(this)
    }

    private fun setupBackgroundWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<AnimecixCheckWorker>(
            3, TimeUnit.HOURS // Check every 3 hours
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "AnimecixUpdateCheck",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
