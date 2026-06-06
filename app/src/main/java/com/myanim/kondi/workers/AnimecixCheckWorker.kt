package com.myanim.kondi.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.myanim.kondi.MainActivity
import com.myanim.kondi.R
import com.myanim.kondi.data.animecix.AnimecixRepository
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class AnimecixCheckWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID = "animecix_updates"
        const val PREFS_NAME = "animecix_prefs"
        const val KEY_LAST_EPISODE_URL = "last_episode_url"
    }

    override suspend fun doWork(): Result {
        return try {
            val repository = AnimecixRepository()
            val episodes = repository.getLatestEpisodes()
            
            if (episodes.isNotEmpty()) {
                val latestEpisode = episodes.first()
                val url = latestEpisode.url
                val sharedPrefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastUrl = sharedPrefs.getString(KEY_LAST_EPISODE_URL, "")
                
                if (url != null && url != lastUrl) {
                    // New episode found!
                    sendNotification(
                        "Yeni Bölüm Geldi!",
                        "${latestEpisode.name ?: "Bilinmeyen Anime"} yayınlandı.",
                        url,
                        latestEpisode.name ?: "Anime"
                    )
                    
                    // Save new last url
                    sharedPrefs.edit().putString(KEY_LAST_EPISODE_URL, url).apply()
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun sendNotification(title: String, message: String, url: String, animeTitle: String) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create Channel if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Animecix Güncellemeleri",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Yeni anime bölümleri için bildirimler"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create Intent
        // We want to open Player directly
        val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
        val encodedTitle = URLEncoder.encode(animeTitle, StandardCharsets.UTF_8.toString())
        // But MainActivity handles routing. 
        // We can pass data to MainActivity and handle it in onCreate/onNewIntent
        // Or deep link?
        // Let's launch MainActivity
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // We can put extra to signal navigation
             putExtra("navigate_to", "player/$encodedUrl/$encodedTitle/ANIMECIX")
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Using app icon as requested
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
