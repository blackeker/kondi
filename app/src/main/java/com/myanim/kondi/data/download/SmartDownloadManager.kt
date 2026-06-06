package com.myanim.kondi.data.download

import android.content.Context
import android.widget.Toast
import com.myanim.kondi.data.animecix.AnimecixRepository
import com.myanim.kondi.data.local.KondiDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmartDownloadManager(private val context: Context) {
    
    suspend fun processSmartDownload(animeId: Int, currentSeason: Int, currentEpisode: Int) {
        withContext(Dispatchers.IO) {
            try {
                val db = KondiDatabase.getDatabase(context)
                val downloadManager = VideoDownloadManager.getInstance(context)
                
                // 1. Delete the current episode if it was downloaded
                val currentFileName = DownloadUtils.createAnimecixFileName("", currentSeason, currentEpisode)
                // We need the anime title to find the exact file. Let's fetch anime details first.
                val api = AnimecixRepository()
                val details = api.getAnimeDetails(animeId)
                
                if (details == null) return@withContext
                
                val title = details.title ?: "Anime"
                val exactCurrentFileName = DownloadUtils.createAnimecixFileName(title, currentSeason, currentEpisode)
                
                val currentDownload = downloadManager.downloadsFlow.value.find { it.title == exactCurrentFileName }
                if (currentDownload != null) {
                    downloadManager.deleteDownload(currentDownload.id)
                }

                // 2. Find the next episode
                val videos = details.videos ?: emptyList()
                // Sort to ensure order
                val sortedVideos = videos.sortedWith(compareBy({ it.seasonNumber ?: 1 }, { it.episodeNumber ?: 0 }))
                
                var foundCurrent = false
                var nextVideo: com.myanim.kondi.data.animecix.AnimecixVideo? = null
                
                for (v in sortedVideos) {
                    val s = v.seasonNumber ?: 1
                    val e = v.episodeNumber ?: 0
                    if (foundCurrent) {
                        nextVideo = v
                        break
                    }
                    if (s == currentSeason && e == currentEpisode) {
                        foundCurrent = true
                    }
                }
                
                if (nextVideo != null) {
                    val nextSeason = nextVideo.seasonNumber ?: 1
                    val nextEp = nextVideo.episodeNumber ?: 0
                    val nextFileName = DownloadUtils.createAnimecixFileName(title, nextSeason, nextEp)
                    
                    // Check if already downloaded
                    val existing = downloadManager.downloadsFlow.value.find { it.title == nextFileName }
                    if (existing == null) {
                        val episodeIdToFetch = nextVideo.episodeId ?: return@withContext
                        val lazyUrl = "animecix://resolve?episodeId=${episodeIdToFetch}&animeId=${animeId}&season=${nextSeason}&episode=${nextEp}"
                        downloadManager.startDownload(
                            title = nextFileName,
                            url = lazyUrl,
                            source = "ANIMECIX",
                            headers = mapOf("Referer" to "https://animecix.net"),
                            forceDisplayName = true
                        )
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Akıllı İndirme: Sonraki bölüm kuyruğa eklendi.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
