package com.myanim.kondi.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.myanim.kondi.R
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

/**
 * Geliştirilmiş Download Notification Manager
 * 
 * Yeni Özellikler:
 * - Otomatik bildirim güncellemesi (throttling ile)
 * - Daha iyi hata yönetimi
 * - FileProvider desteği
 * - Download state tracking
 * - Memory leak önleme
 * - Singleton pattern
 */
class DownloadNotificationManager private constructor(private val context: Context) {
    
    private val notificationManager = NotificationManagerCompat.from(context)
    private val activeDownloads = ConcurrentHashMap<String, DownloadState>()
    
    // Notification throttling - Performans için
    private val minUpdateIntervalMs = 500L
    
    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val CHANNEL_NAME = "İndirmeler"
        private const val CHANNEL_DESCRIPTION = "Video indirme durumu bildirimleri"
        
        // Action IDs
        const val ACTION_CANCEL = "com.myanim.kondi.ACTION_CANCEL_DOWNLOAD"
        const val ACTION_PAUSE = "com.myanim.kondi.ACTION_PAUSE_DOWNLOAD"
        const val ACTION_RESUME = "com.myanim.kondi.ACTION_RESUME_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "download_id"
        
        // Notification groups
        private const val GROUP_DOWNLOADS = "downloads_group"
        const val SUMMARY_ID = 1338  // Must not collide with foreground service ID (9999)
        
        @Volatile
        private var instance: DownloadNotificationManager? = null
        
        fun getInstance(context: Context): DownloadNotificationManager {
            return instance ?: synchronized(this) {
                instance ?: DownloadNotificationManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    // Cache the large icon bitmap (M-14 fix)
    private val largeIcon by lazy {
        android.graphics.BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
    }
    
    init {
        createNotificationChannel()
    }
    
    /**
     * Download state bilgilerini tutar
     */
    private data class DownloadState(
        val id: String,
        val title: String,
        var progress: Int = 0,
        var downloadedBytes: Long = 0,
        var totalBytes: Long = 0,
        var startTime: Long = System.currentTimeMillis(),
        var lastUpdateTime: Long = 0,
        var isPaused: Boolean = false
    )
    
    /**
     * Generate a unique notification ID from download ID, avoiding collision with reserved IDs.
     */
    private fun getNotificationId(downloadId: String): Int {
        val hash = downloadId.hashCode()
        // Ensure it doesn't collide with SUMMARY_ID (1338) or foreground service ID (9999)
        return if (hash == SUMMARY_ID || hash == 9999 || hash == 0) hash + 10000 else hash
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(true)
                enableLights(true)
                lightColor = context.getColor(R.color.purple_500)
                enableVibration(false)
            }
            
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    
    fun showProgressNotification(
        id: String,
        title: String,
        progress: Int,
        totalBytes: Long,
        downloadedBytes: Long,
        startTime: Long = System.currentTimeMillis(),
        isPaused: Boolean = false
    ) {
        val state = activeDownloads.getOrPut(id) {
            DownloadState(id, title, startTime = startTime)
        }.apply {
            this.progress = progress.coerceIn(0, 100)
            this.downloadedBytes = downloadedBytes
            this.totalBytes = totalBytes
            this.isPaused = isPaused
        }
        
        // Throttling: Çok sık güncelleme yapma
        val currentTime = System.currentTimeMillis()
        if (currentTime - state.lastUpdateTime < minUpdateIntervalMs && progress < 100) {
            return
        }
        state.lastUpdateTime = currentTime
        
        // İstatistikleri hesapla
        val stats = calculateDownloadStats(state)
        
        // Bildirim oluştur
        val notification = buildProgressNotification(
            id = id,
            title = title,
            progress = state.progress,
            stats = stats,
            isPaused = isPaused
        )
        
        try {
            notificationManager.notify(getNotificationId(id), notification)
            updateSummaryNotification()
        } catch (e: SecurityException) {
            handleNotificationError(id, e)
        }
    }
    
    fun showCompletedNotification(id: String, title: String, filePath: String) {
        activeDownloads.remove(id)
        
        val file = File(filePath)
        if (!file.exists()) {
            showFailedNotification(id, title, "Dosya bulunamadı")
            return
        }
        
        val fileUri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Uri.fromFile(file)
        }
        
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, getMimeType(filePath))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        
        val openPendingIntent = PendingIntent.getActivity(
            context,
            id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = getMimeType(filePath)
            putExtra(Intent.EXTRA_STREAM, fileUri)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val sharePendingIntent = PendingIntent.getActivity(
            context,
            (id.hashCode() + 1),
            Intent.createChooser(shareIntent, "Videoyu Paylaş"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("✓ İndirme tamamlandı")
            .setContentText(title)
            .setSubText(formatBytes(file.length()))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setColor(context.getColor(R.color.teal_200))
            .setContentIntent(openPendingIntent)
            .setGroup(GROUP_DOWNLOADS)
            .addAction(android.R.drawable.ic_menu_view, "Oynat", openPendingIntent)
            .addAction(android.R.drawable.ic_menu_share, "Paylaş", sharePendingIntent)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$title\n\n${formatBytes(file.length())} • Dosya kaydedildi ve izlemeye hazır!"))
            .build()
        
        try {
            notificationManager.notify(getNotificationId(id), notification)
            updateSummaryNotification()
        } catch (e: SecurityException) {
            handleNotificationError(id, e)
        }
    }
    
    fun showFailedNotification(id: String, title: String, error: String) {
        activeDownloads.remove(id)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("✗ İndirme başarısız")
            .setContentText(title)
            .setSubText(error)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setColor(context.getColor(R.color.red_error))
            .setGroup(GROUP_DOWNLOADS)
            .setContentIntent(getAppPendingIntent(id))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$title\n\nHata: $error"))
            .build()
        
        try {
            notificationManager.notify(getNotificationId(id), notification)
            updateSummaryNotification()
        } catch (e: SecurityException) {
            handleNotificationError(id, e)
        }
    }
    
    fun showPausedNotification(id: String, title: String, downloadedBytes: Long, totalBytes: Long) {
        val state = activeDownloads[id] ?: return
        state.isPaused = true
        
        val resumeIntent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = ACTION_RESUME
            putExtra(EXTRA_DOWNLOAD_ID, id)
        }
        val resumePendingIntent = PendingIntent.getBroadcast(
            context,
            id.hashCode() + 2,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val cancelIntent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_DOWNLOAD_ID, id)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            id.hashCode() + 3,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("⏸ İndirme duraklatıldı")
            .setContentText(title)
            .setSubText("${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}")
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(context.getColor(R.color.orange_warning))
            .setContentIntent(getAppPendingIntent(id))
            .addAction(android.R.drawable.ic_media_play, "Devam Et", resumePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "İptal", cancelPendingIntent)
            .build()
        
        try {
            notificationManager.notify(getNotificationId(id), notification)
            updateSummaryNotification()
        } catch (e: SecurityException) {
            handleNotificationError(id, e)
        }
    }
    
    fun cancelNotification(id: String) {
        notificationManager.cancel(getNotificationId(id))
        activeDownloads.remove(id)
        if (activeDownloads.isEmpty()) {
            notificationManager.cancel(SUMMARY_ID)
        } else {
            updateSummaryNotification()
        }
    }
    
    fun cancelAllNotifications() {
        activeDownloads.keys.forEach { id ->
            notificationManager.cancel(getNotificationId(id))
        }
        activeDownloads.clear()
        notificationManager.cancel(SUMMARY_ID)
    }
    
    private fun getAppPendingIntent(id: String): PendingIntent {
        val intent = Intent(context, com.myanim.kondi.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "animecix_downloads")
        }
        return PendingIntent.getActivity(
            context,
            id.hashCode() + 10,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun buildProgressNotification(
        id: String,
        title: String,
        progress: Int,
        stats: DownloadStats,
        isPaused: Boolean
    ): android.app.Notification {
        val cancelIntent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_DOWNLOAD_ID, id)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val pauseIntent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = ACTION_PAUSE
            putExtra(EXTRA_DOWNLOAD_ID, id)
        }
        val pausePendingIntent = PendingIntent.getBroadcast(
            context,
            id.hashCode() + 1,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("$progress% • ${stats.progressText}")
            .setSubText("${stats.speedText} • ${stats.etaText}")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setLargeIcon(largeIcon)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(context.getColor(R.color.purple_500))
            .setGroup(GROUP_DOWNLOADS)
            .setContentIntent(getAppPendingIntent(id))
            .addAction(android.R.drawable.ic_media_pause, "Duraklat", pausePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "İptal", cancelPendingIntent)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$progress% • ${stats.progressText}\n${stats.speedText} • ${stats.etaText}"))
        
        return builder.build()
    }
    
    private data class DownloadStats(
        val progressText: String,
        val speedText: String,
        val etaText: String
    )
    
    private fun calculateDownloadStats(state: DownloadState): DownloadStats {
        val elapsedTime = (System.currentTimeMillis() - state.startTime) / 1000.0
        val speed = if (elapsedTime > 0.1) state.downloadedBytes / elapsedTime else 0.0
        val remainingBytes = maxOf(0, state.totalBytes - state.downloadedBytes)
        val eta = if (speed > 0 && !state.isPaused) (remainingBytes / speed).toLong() else 0
        
        val progressText = "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}"
        val speedText = if (state.isPaused) "Duraklatıldı" else formatSpeed(speed)
        val etaText = if (state.isPaused) "" else formatETA(eta)
        
        return DownloadStats(progressText, speedText, etaText)
    }
    
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 0 -> "0 B"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
    
    private fun formatSpeed(bytesPerSecond: Double): String {
        return when {
            bytesPerSecond < 1024 -> "${bytesPerSecond.toInt()} B/s"
            bytesPerSecond < 1024 * 1024 -> String.format("%.1f KB/s", bytesPerSecond / 1024)
            else -> String.format("%.1f MB/s", bytesPerSecond / (1024 * 1024))
        }
    }
    
    private fun formatETA(seconds: Long): String {
        return when {
            seconds <= 0 -> ""
            seconds < 60 -> "${seconds}s kaldı"
            seconds < 3600 -> "${seconds / 60}dk ${seconds % 60}s kaldı"
            else -> "${seconds / 3600}sa ${(seconds % 3600) / 60}dk kaldı"
        }
    }
    
    private fun getMimeType(filePath: String): String {
        return when {
            filePath.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            filePath.endsWith(".mkv", ignoreCase = true) -> "video/x-matroska"
            filePath.endsWith(".avi", ignoreCase = true) -> "video/x-msvideo"
            filePath.endsWith(".mov", ignoreCase = true) -> "video/quicktime"
            filePath.endsWith(".webm", ignoreCase = true) -> "video/webm"
            else -> "video/*"
        }
    }
    
    private fun handleNotificationError(id: String, error: Exception) {
        error.printStackTrace()
    }
    
    private fun updateSummaryNotification() {
        if (activeDownloads.isEmpty()) return
        
        val inboxStyle = NotificationCompat.InboxStyle()
            .setSummaryText("${activeDownloads.size} dosya işlemi")
            
        activeDownloads.values.take(5).forEach { state ->
            val status = if (state.isPaused) "Duraklatıldı" else "%${state.progress}"
            inboxStyle.addLine("↓ $status - ${state.title}")
        }
        
        if (activeDownloads.size > 5) {
            inboxStyle.addLine("... ve ${activeDownloads.size - 5} dosya daha")
        }

        val firstActive = activeDownloads.values.firstOrNull()
        val contentText = if (firstActive != null) {
             val status = if (firstActive.isPaused) "Duraklatıldı" else "%${firstActive.progress}"
             "$status - ${firstActive.title}" + if (activeDownloads.size > 1) " (+${activeDownloads.size - 1})" else ""
        } else {
             "İndirmeler arka planda devam ediyor"
        }

        val summaryNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("İndirmeler (${activeDownloads.size})")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setStyle(inboxStyle)
            .setGroup(GROUP_DOWNLOADS)
            .setGroupSummary(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(context.getColor(R.color.purple_500))
            .setContentIntent(getAppPendingIntent("summary"))
            .build()
            
        try {
            notificationManager.notify(SUMMARY_ID, summaryNotification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
    
    fun cleanup() {
        activeDownloads.clear()
    }
}
