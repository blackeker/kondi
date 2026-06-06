package com.myanim.kondi.data.download

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.content.pm.ServiceInfo
import android.os.Build
import com.myanim.kondi.R

class VideoDownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        return START_STICKY
    }

    private fun promoteToForeground() {
        // Ensure channel exists
        DownloadNotificationManager.getInstance(this)

        val notification = NotificationCompat.Builder(this, "downloads")
            .setContentTitle(getString(R.string.app_name))
            .setContentText("İndirmeler arka planda devam ediyor")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            try {
                // Try with DATA_SYNC type first (Android 10+)
                startForeground(1337, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } catch (e: Exception) {
                // Fallback for strict devices or if permission missing (though it shouldn't be)
                try {
                     startForeground(1337, notification)
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
        } else {
            startForeground(1337, notification)
        }
    }
}
