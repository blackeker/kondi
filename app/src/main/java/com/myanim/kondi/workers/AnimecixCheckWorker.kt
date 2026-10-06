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
        const val KEY_NOTIFIED_EPISODES = "notified_episodes_set"
        const val KEY_LAST_EPISODE_URL = "last_episode_url"
    }

    override suspend fun doWork(): Result {
        return try {
            val repository = AnimecixRepository()
            val episodes = repository.getLatestEpisodes(page = 1)
            if (episodes.isEmpty()) return Result.success()

            val sharedPrefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val notifiedSet = sharedPrefs.getStringSet(KEY_NOTIFIED_EPISODES, emptySet())?.toMutableSet() ?: mutableSetOf()
            
            val db = com.myanim.kondi.data.local.KondiDatabase.getDatabase(applicationContext)
            val favorites = db.favoriteDao().getAllFavoritesSync()

            var notifiedAny = false

            if (favorites.isNotEmpty()) {
                // Sadece kullanıcının takip ettiği (favorilere eklediği) animeler için kontrol yap
                for (ep in episodes) {
                    val epAnimeId = ep.animeId
                    val epName = (ep.name ?: "").trim().lowercase()
                    
                    val matchedFav = favorites.find { fav ->
                        (epAnimeId != null && fav.animeId != null && epAnimeId == fav.animeId) ||
                        (epName.isNotEmpty() && fav.title.trim().lowercase().contains(epName)) ||
                        (epName.isNotEmpty() && epName.contains(fav.title.trim().lowercase()))
                    }

                    if (matchedFav != null) {
                        val episodeKey = "${matchedFav.title}_s${ep.seasonNumber ?: 1}_e${ep.episodeNumber ?: 1}"
                        if (!notifiedSet.contains(episodeKey)) {
                            notifiedSet.add(episodeKey)
                            notifiedAny = true

                            val seasonText = if ((ep.seasonNumber ?: 1) > 1) "${ep.seasonNumber}. Sezon " else ""
                            val episodeText = "${ep.episodeNumber ?: 1}. Bölüm"
                            val notifTitle = "Takip Ettiğin Anime: ${matchedFav.title}"
                            val notifMessage = "$seasonText$episodeText yayınlandı! İzlemek için tıkla."

                            sendNotification(
                                title = notifTitle,
                                message = notifMessage,
                                animeId = matchedFav.animeId ?: ep.animeId,
                                url = ep.directUrl ?: ""
                            )
                        }
                    }
                }
            } else {
                // Kullanıcının henüz favorisi yoksa en son çıkan 1 genel bölümü takip et
                val latest = episodes.firstOrNull()
                val url = latest?.directUrl ?: latest?.name ?: ""
                val lastUrl = sharedPrefs.getString(KEY_LAST_EPISODE_URL, "")
                if (url.isNotEmpty() && url != lastUrl) {
                    sendNotification(
                        title = "Yeni Bölüm Eklendi",
                        message = "${latest?.name ?: "Yeni anime"} yayında!",
                        animeId = latest?.animeId,
                        url = latest?.directUrl ?: ""
                    )
                    sharedPrefs.edit().putString(KEY_LAST_EPISODE_URL, url).apply()
                }
            }

            // Temizlik: max 100 anahtar sakla
            if (notifiedSet.size > 150) {
                val trimmed = notifiedSet.toList().takeLast(100).toSet()
                sharedPrefs.edit().putStringSet(KEY_NOTIFIED_EPISODES, trimmed).apply()
            } else if (notifiedAny) {
                sharedPrefs.edit().putStringSet(KEY_NOTIFIED_EPISODES, notifiedSet).apply()
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

    private fun sendNotification(title: String, message: String, animeId: Int?, url: String) {
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

        // Create Intent to open MainActivity and navigate to the anime detail or player
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (animeId != null) {
                putExtra("navigate_to", "anime_detail/$animeId")
            } else if (url.isNotEmpty()) {
                val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                putExtra("navigate_to", "player/$encodedUrl/$encodedTitle/ANIMECIX")
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            (animeId ?: System.currentTimeMillis().toInt()),
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
