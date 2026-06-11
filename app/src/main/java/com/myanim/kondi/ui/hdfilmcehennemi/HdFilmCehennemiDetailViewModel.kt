package com.myanim.kondi.ui.hdfilmcehennemi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myanim.kondi.data.hdfilmcehennemi.HdFilmCehennemiRepository
import com.myanim.kondi.data.hdfilmcehennemi.HdFilmCehennemiDetail
import com.myanim.kondi.data.hdfilmcehennemi.HdFilmCehennemiSource
import com.myanim.kondi.data.local.Favorite
import com.myanim.kondi.data.local.KondiDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HdFilmCehennemiDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HdFilmCehennemiRepository()
    private val database = KondiDatabase.getDatabase(application)

    private val _movieDetail = MutableStateFlow<HdFilmCehennemiDetail?>(null)
    val movieDetail: StateFlow<HdFilmCehennemiDetail?> = _movieDetail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _sources = MutableStateFlow<List<HdFilmCehennemiSource>>(emptyList())
    val sources: StateFlow<List<HdFilmCehennemiSource>> = _sources.asStateFlow()

    private val _isSourcesLoading = MutableStateFlow(false)
    val isSourcesLoading: StateFlow<Boolean> = _isSourcesLoading.asStateFlow()

    fun loadMovieDetails(id: String) {
        _isLoading.value = true
        _errorMessage.value = null
        _movieDetail.value = null
        
        viewModelScope.launch {
            try {
                val detail = repository.getMovieDetail(id)
                if (detail != null) {
                    _movieDetail.value = detail
                    checkFavoriteStatus(id)
                } else {
                    _errorMessage.value = "Film detayları yüklenemedi."
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Beklenmeyen bir hata oluştu."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadVideoSources(episodeUrl: String) {
        _isSourcesLoading.value = true
        _sources.value = emptyList()
        viewModelScope.launch {
            try {
                val resolved = repository.getStreamUrls(episodeUrl)
                _sources.value = resolved
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Video kaynakları yüklenemedi."
            } finally {
                _isSourcesLoading.value = false
            }
        }
    }

    suspend fun resolveSource(url: String): String? {
        return repository.resolveSource(url)
    }

    private fun checkFavoriteStatus(id: String) {
        viewModelScope.launch {
            database.favoriteDao().getFavoritesBySource("HDFILMCEHENNEMI").collectLatest { list ->
                _isFavorite.value = list.any { it.url == id }
            }
        }
    }

    fun toggleFavorite(detail: HdFilmCehennemiDetail) {
        viewModelScope.launch {
            val exists = database.favoriteDao().getFavoriteByUrl(detail.id)
            if (exists != null) {
                database.favoriteDao().deleteFavorite(exists)
                _isFavorite.value = false
            } else {
                database.favoriteDao().insertFavorite(
                    Favorite(
                        url = detail.id,
                        title = detail.title,
                        posterUrl = detail.poster,
                        source = "HDFILMCEHENNEMI"
                    )
                )
                _isFavorite.value = true
            }
        }
    }
}
