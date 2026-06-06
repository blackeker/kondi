package com.myanim.kondi.ui.common

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myanim.kondi.data.local.KondiDatabase
import com.myanim.kondi.data.local.WatchHistory
import com.myanim.kondi.data.animecix.AnimecixRepository
import com.myanim.kondi.data.animecix.AnimecixVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val database = KondiDatabase.getDatabase(application)
    private val historyDao = database.watchHistoryDao()
    private val repository = AnimecixRepository()
    private val _episodes = MutableStateFlow<List<AnimecixVideo>>(emptyList())
    val episodes = _episodes.asStateFlow()

    private val _currentEpisodeIndex = MutableStateFlow(-1)
    val currentEpisodeIndex = _currentEpisodeIndex.asStateFlow()

    fun loadAnimeEpisodes(animeIdOrSlug: String, currentVideoUrl: String, source: String) {
        viewModelScope.launch {
            try {
                val allEpisodes = run {
                    val anime = repository.getAnimeDetails(animeIdOrSlug.toIntOrNull() ?: 0)
                    anime?.videos?.filter { it.episodeNumber != null }
                        ?.sortedWith(compareBy({ it.seasonNumber ?: 1 }, { it.episodeNumber ?: 0 }))
                        ?: emptyList()
                }
                
                _episodes.value = allEpisodes
                val index = allEpisodes.indexOfFirst { it.url == currentVideoUrl || it.episodeId?.toString() == currentVideoUrl }
                _currentEpisodeIndex.value = index
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error loading episodes", e)
            }
        }
    }

    fun getNextEpisode(): AnimecixVideo? {
        val nextIndex = _currentEpisodeIndex.value + 1
        return if (nextIndex in _episodes.value.indices) {
            _episodes.value[nextIndex]
        } else null
    }

    fun getPrevEpisode(): AnimecixVideo? {
        val prevIndex = _currentEpisodeIndex.value - 1
        return if (prevIndex in _episodes.value.indices) {
            _episodes.value[prevIndex]
        } else null
    }

    fun saveProgress(url: String, title: String, position: Long, duration: Long, source: String, posterUrl: String? = null, animeUrl: String = "") {
        if (duration < 1000) return
        
        viewModelScope.launch(Dispatchers.IO) {
            val history = WatchHistory(
                videoUrl = url,
                title = title,
                animeUrl = animeUrl,
                posterUrl = posterUrl,
                positionMs = position,
                durationMs = duration,
                source = source,
                timestamp = System.currentTimeMillis()
            )
            historyDao.insertHistory(history)
        }
    }

    suspend fun getPlaybackPosition(url: String): Long {
        return withContext(Dispatchers.IO) {
            historyDao.getHistoryItem(url)?.positionMs ?: 0L
        }
    }

    fun toggleBookmark(url: String, title: String, position: Long, duration: Long, source: String, posterUrl: String? = null, animeUrl: String = "", label: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = historyDao.getHistoryItem(url)
            if (existing?.isBookmarked == true) {
                historyDao.removeBookmark(url)
            } else {
                val history = WatchHistory(
                    videoUrl = url,
                    title = title,
                    animeUrl = animeUrl,
                    posterUrl = posterUrl,
                    positionMs = position,
                    durationMs = duration,
                    source = source,
                    timestamp = System.currentTimeMillis(),
                    isBookmarked = true,
                    bookmarkLabel = label ?: "Mevcut Konum"
                )
                historyDao.insertHistory(history)
            }
        }
    }

    fun getWatchHistory(url: String) = flow {
        while(true) {
            emit(historyDao.getHistoryItem(url))
            delay(2000)
        }
    }
}
