package com.myanim.kondi.ui.animecix

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myanim.kondi.data.animecix.AnimecixAnime
import com.myanim.kondi.data.animecix.AnimecixRepository
import com.myanim.kondi.data.animecix.AnimecixVideo
import com.myanim.kondi.data.animecix.AnimecixVideoDetail
import com.myanim.kondi.data.download.VideoDownloadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import timber.log.Timber

sealed class AnimecixDetailState {
    object Loading : AnimecixDetailState()
    data class Success(val anime: AnimecixAnime) : AnimecixDetailState()
    data class Error(val message: String) : AnimecixDetailState()
}

class AnimecixDetailViewModel : ViewModel() {
    private val repository = AnimecixRepository()
    private var downloadManager: VideoDownloadManager? = null
    
    private val _anime = MutableStateFlow<AnimecixAnime?>(null)
    val anime: StateFlow<AnimecixAnime?> = _anime.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _sources = MutableStateFlow<List<com.myanim.kondi.data.animecix.AnimecixSource>>(emptyList())
    val sources: StateFlow<List<com.myanim.kondi.data.animecix.AnimecixSource>> = _sources.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isAscending = MutableStateFlow(true)
    val isAscending: StateFlow<Boolean> = _isAscending.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    fun init(context: Context) {
        if (downloadManager == null) {
            downloadManager = VideoDownloadManager.getInstance(context)
        }
    }

    fun loadAnimeDetails(context: Context, id: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            // First check if it's a favorite and has cached details
            try {
                val db = com.myanim.kondi.data.local.KondiDatabase.getDatabase(context)
                val favorite = db.favoriteDao().getFavoriteByUrl("animecix_$id")
                if (favorite != null && favorite.detailsJson != null) {
                    val cachedDetails = com.google.gson.Gson().fromJson(favorite.detailsJson, AnimecixAnime::class.java) as? AnimecixAnime
                    if (cachedDetails != null) {
                        _anime.value = cachedDetails
                        _isLoading.value = false
                        // Optionally fetch in background to update
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading cached favorite details")
            }
            
            try {
                val details = repository.getAnimeDetails(id)
                if (details != null) {
                     _anime.value = details
                     
                     // Update cache if it's a favorite
                     if (_isFavorite.value) {
                         val db = com.myanim.kondi.data.local.KondiDatabase.getDatabase(context)
                         val url = "animecix_$id"
                         val favorite = db.favoriteDao().getFavoriteByUrl(url)
                         if (favorite != null) {
                             val json = com.google.gson.Gson().toJson(details)
                             db.favoriteDao().insertFavorite(favorite.copy(detailsJson = json))
                         }
                     }
                } else if (_anime.value == null) {
                     _errorMessage.value = "Detaylar alınamadı"
                }
            } catch (e: Exception) {
                if (_anime.value == null) {
                    _errorMessage.value = e.message ?: "Bilinmeyen hata"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retry(context: Context, id: Int) {
        loadAnimeDetails(context, id)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSortOrder() {
        _isAscending.value = !_isAscending.value
    }

    fun checkFavoriteStatus(context: Context, animeId: Int) {
        viewModelScope.launch {
            val db = com.myanim.kondi.data.local.KondiDatabase.getDatabase(context)
            val isFav = db.favoriteDao().isFavorite("animecix_$animeId")
            _isFavorite.value = isFav
        }
    }

    fun toggleFavorite(context: Context, anime: AnimecixAnime) {
        viewModelScope.launch {
            val db = com.myanim.kondi.data.local.KondiDatabase.getDatabase(context)
            val url = "animecix_${anime.id}"
            if (_isFavorite.value) {
                db.favoriteDao().deleteFavoriteByUrl(url)
                _isFavorite.value = false
            } else {
                val json = com.google.gson.Gson().toJson(anime)
                db.favoriteDao().insertFavorite(
                    com.myanim.kondi.data.local.Favorite(
                        url = url,
                        title = anime.title,
                        posterUrl = anime.poster,
                        source = "ANIMECIX",
                        animeId = anime.id,
                        detailsJson = json
                    )
                )
                _isFavorite.value = true
            }
        }
    }

    fun loadSources(episodeId: Int, titleId: Int? = null, season: Int? = null, episode: Int? = null) {
        viewModelScope.launch {
            _sources.value = emptyList() // Clear previous
            try {
                // Fetch videos using available info
                val videos = repository.getVideoSources(episodeId, titleId, season, episode)
                _sources.value = videos
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    suspend fun resolveUrl(url: String): String? {
        return try {
            repository.resolveSource(url)
        } catch (e: Exception) {
            null
        }
    }

    fun downloadEpisode(episode: AnimecixVideo, animeName: String, context: Context) {
        init(context)
        viewModelScope.launch {
            downloadEpisodeSuspend(episode, animeName, context)
        }
    }

    private suspend fun downloadEpisodeSuspend(episode: AnimecixVideo, animeName: String, context: Context) {
        val mgr = downloadManager ?: return
        val currentAnime = _anime.value
        val epTitle = com.myanim.kondi.data.download.DownloadUtils.createAnimecixFileName(
            animeName,
            episode.seasonNumber ?: 1,
            episode.episodeNumber ?: 0
        )
        
        try {
            // Check if already downloaded
            val existing = mgr.downloadsFlow.value.find { it.title == epTitle }
            if (existing != null && existing.status == com.myanim.kondi.data.local.DownloadStatus.COMPLETED.name) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "${episode.episodeNumber}. Bölüm zaten inmiş", Toast.LENGTH_SHORT).show()
                }
                return
            }

            // Instead of resolving the actual source now, we queue a lazy resolution URL
            val lazyUrl = "animecix://resolve?episodeId=${episode.episodeId}&animeId=${currentAnime?.id}&season=${episode.seasonNumber}&episode=${episode.episodeNumber}"
            
            mgr.startDownload(
                title = epTitle,
                url = lazyUrl,
                source = "ANIMECIX",
                headers = mapOf("Referer" to "https://animecix.net"),
                forceDisplayName = true
            )
            
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "${episode.episodeNumber}. Bölüm kuyruğa eklendi", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error adding to download queue")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Hata (${episode.episodeNumber}. Bölüm): ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun downloadEpisodeDirectSuspend(
        episode: AnimecixVideo,
        animeName: String,
        context: Context,
        index: Int
    ): Boolean {
        val mgr = downloadManager ?: return false
        val currentAnime = _anime.value
        val epTitle = com.myanim.kondi.data.download.DownloadUtils.createAnimecixFileName(
            animeName,
            episode.seasonNumber ?: 1,
            episode.episodeNumber ?: 0
        )
        
        try {
            // Check if already downloaded
            val existing = mgr.downloadsFlow.value.find { it.title == epTitle }
            if (existing != null && existing.status == com.myanim.kondi.data.local.DownloadStatus.COMPLETED.name) {
                return false
            }

            // Fetch video sources
            val sources = repository.getVideoSources(
                episode.episodeId ?: 0,
                currentAnime?.id,
                episode.seasonNumber,
                episode.episodeNumber
            )
            
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
                
                val bestSource = prioritized.firstOrNull()
                if (bestSource != null) {
                    val rawUrl = bestSource.url
                    val resolvedUrl = repository.resolveSource(rawUrl)
                    if (resolvedUrl != null && resolvedUrl.startsWith("http")) {
                        val lowerRawUrl = rawUrl.lowercase()
                        val urlToDownload = if (
                            lowerRawUrl.contains("sibnet") || 
                            lowerRawUrl.contains("ok.ru") || 
                            lowerRawUrl.contains("odnoklassniki") || 
                            lowerRawUrl.contains("streamtape") || 
                            lowerRawUrl.contains("voe") || 
                            lowerRawUrl.contains("uqload") || 
                            lowerRawUrl.contains("dood")
                        ) {
                            rawUrl
                        } else {
                            resolvedUrl
                        }
                        
                        if (index > 0) {
                            kotlinx.coroutines.delay(2000L) // Stagger resolved direct start requests
                        }
                        
                        mgr.startDownload(
                            title = epTitle,
                            url = urlToDownload,
                            source = "ANIMECIX",
                            headers = mapOf("Referer" to "https://animecix.net"),
                            forceDisplayName = true
                        )
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error adding episode directly to download queue")
        }
        return false
    }

    fun downloadSeason(episodes: List<AnimecixVideo>, seasonNumber: Int, animeName: String, context: Context) {
        val seasonEpisodes = episodes.filter { it.seasonNumber == seasonNumber }.sortedBy { it.episodeNumber }
        if (seasonEpisodes.isEmpty()) {
            Toast.makeText(context, "Bu sezonda bölüm yok", Toast.LENGTH_SHORT).show()
            return
        }
        
        init(context)
        Toast.makeText(context, "${seasonEpisodes.size} bölüm çözümlenip kuyruğa ekleniyor...", Toast.LENGTH_SHORT).show()
        
        viewModelScope.launch {
            var addedCount = 0
            seasonEpisodes.forEach { episode ->
                val success = downloadEpisodeDirectSuspend(episode, animeName, context, addedCount)
                if (success) addedCount++
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "$addedCount bölüm başarıyla kuyruğa eklendi.", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    fun downloadAll(episodes: List<AnimecixVideo>, animeName: String, context: Context) {
        if (episodes.isEmpty()) return
        init(context)
        Toast.makeText(context, "Tüm bölümler (${episodes.size}) çözümlenip kuyruğa ekleniyor...", Toast.LENGTH_SHORT).show()
        
        viewModelScope.launch {
            var addedCount = 0
            episodes.sortedBy { it.episodeNumber }.forEach { episode ->
                val success = downloadEpisodeDirectSuspend(episode, animeName, context, addedCount)
                if (success) addedCount++
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "$addedCount bölüm başarıyla kuyruğa eklendi.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
