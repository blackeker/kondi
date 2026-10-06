package com.myanim.kondi.data.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.myanim.kondi.R
import timber.log.Timber

class VideoDownloadService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        acquireLocks()
        return START_STICKY
    }

    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "kondi:VideoDownloadWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld != true) {
                // Safety timeout: 6 hours max
                wakeLock?.acquire(6 * 60 * 60 * 1000L)
                Timber.d("VideoDownloadService: Partial WakeLock acquired")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to acquire WakeLock")
        }

        try {
            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL
                }
                wifiLock = wifiManager?.createWifiLock(lockMode, "kondi:VideoDownloadWifiLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wifiLock?.isHeld != true) {
                wifiLock?.acquire()
                Timber.d("VideoDownloadService: High-Perf WifiLock acquired")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to acquire WifiLock")
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Timber.d("VideoDownloadService: WakeLock released")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to release WakeLock")
        }
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
                Timber.d("VideoDownloadService: WifiLock released")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to release WifiLock")
        }
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    private fun promoteToForeground() {
        // Ensure channel exists
        DownloadNotificationManager.getInstance(this)

        val notification = NotificationCompat.Builder(this, "downloads")
            .setContentTitle(getString(R.string.app_name))
            .setContentText("İndirmeler arka planda kesintisiz devam ediyor")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            try {
                // Try with DATA_SYNC type first (Android 10+)
                startForeground(9999, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } catch (e: Exception) {
                // Fallback for strict devices or if permission missing (though it shouldn't be)
                try {
                     startForeground(9999, notification)
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
        } else {
            startForeground(9999, notification)
        }
    }
}
