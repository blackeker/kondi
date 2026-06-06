package com.myanim.kondi.ui.animecix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myanim.kondi.data.animecix.AnimecixRepository
import com.myanim.kondi.data.animecix.AnimecixVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AnimecixViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val repository = AnimecixRepository()
    private val database = com.myanim.kondi.data.local.KondiDatabase.getDatabase(application)
    private val favoriteDao = database.favoriteDao()
    private val watchHistoryDao = database.watchHistoryDao()
    private val playbackProgressDao = database.playbackProgressDao()
    
    val categories = repository.getCategories()

    private val _latestEpisodes = MutableStateFlow<List<AnimecixVideo>>(emptyList())
    val latestEpisodes: StateFlow<List<AnimecixVideo>> = _latestEpisodes.asStateFlow()
    
    private val _categoryItems = MutableStateFlow<List<com.myanim.kondi.data.animecix.AnimecixTitle>>(emptyList())
    val categoryItems: StateFlow<List<com.myanim.kondi.data.animecix.AnimecixTitle>> = _categoryItems.asStateFlow()

    private val _selectedCategory = MutableStateFlow<Pair<String, String>?>(null)
    val selectedCategory: StateFlow<Pair<String, String>?> = _selectedCategory.asStateFlow()

    private var currentPage = 1
    private var isLastPage = false

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val favorites = favoriteDao.getFavoritesBySource("ANIMECIX").stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val watchHistory = watchHistoryDao.getHistoryBySource("ANIMECIX", 10).stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val playbackProgress = playbackProgressDao.getAllProgressFlow().stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private var latestCurrentPage = 1
    private var isLatestLastPage = false

    init {
        fetchLatestEpisodes()
    }

    private fun fetchLatestEpisodes() {
        if (_isLoading.value || isLatestLastPage) return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val newEpisodes = repository.getLatestEpisodes(latestCurrentPage)
                if (newEpisodes.isEmpty()) {
                    isLatestLastPage = true
                } else {
                    _latestEpisodes.value += newEpisodes
                    latestCurrentPage++
                }
            } catch (e: Exception) {
                _errorMessage.value = "Hata oluştu: Son bölümler yüklenemedi."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMoreLatest() = fetchLatestEpisodes()

    fun retryLatestEpisodes() {
        latestCurrentPage = 1
        isLatestLastPage = false
        _latestEpisodes.value = emptyList()
        fetchLatestEpisodes()
    }

    fun selectCategory(category: Pair<String, String>) {
        _selectedCategory.value = category
        _categoryItems.value = emptyList()
        currentPage = 1
        isLastPage = false
        loadCategoryItems()
    }

    fun loadCategoryItems() {
        if (_isLoading.value || isLastPage) return
        val category = _selectedCategory.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val newItems = repository.getCategoryItems(category.second, currentPage)
                if (newItems.isEmpty()) {
                    isLastPage = true
                } else {
                    _categoryItems.value += newItems
                    currentPage++
                }
            } catch (e: Exception) {
                _errorMessage.value = "Hata oluştu: Kategoriler yüklenemedi."
            } finally {
                _isLoading.value = false
            }
        }
    }

    private val _searchResults = MutableStateFlow<List<com.myanim.kondi.data.animecix.AnimecixTitle>>(emptyList())
    val searchResults: StateFlow<List<com.myanim.kondi.data.animecix.AnimecixTitle>> = _searchResults.asStateFlow()

    private val _isSearchLoading = MutableStateFlow(false)
    val isSearchLoading: StateFlow<Boolean> = _isSearchLoading.asStateFlow()

    private var searchCurrentPage = 1
    private var isSearchLastPage = false
    private var lastSearchQuery = ""

    fun search(query: String) {
        if (query.isBlank()) return
        
        android.util.Log.d("AnimecixViewModel", "Search initiated with query: $query")
        lastSearchQuery = query
        searchCurrentPage = 1
        isSearchLastPage = false
        _searchResults.value = emptyList()
        _selectedCategory.value = null
        _errorMessage.value = null
        
        executeSearch()
    }

    private fun executeSearch() {
        if (lastSearchQuery.isBlank()) return
        if (_isSearchLoading.value || isSearchLastPage) return

        viewModelScope.launch {
            _isSearchLoading.value = true
            _errorMessage.value = null
            try {
                val newItems = repository.search(lastSearchQuery, searchCurrentPage)
                android.util.Log.d("AnimecixViewModel", "Search repository returned ${newItems.size} items for query '$lastSearchQuery' page $searchCurrentPage")
                if (newItems.isEmpty()) {
                    isSearchLastPage = true
                } else {
                    val updatedResults = _searchResults.value + newItems
                    _searchResults.value = updatedResults
                    android.util.Log.d("AnimecixViewModel", "Updated search results state, total results: ${updatedResults.size}")
                    searchCurrentPage++
                }
            } catch (e: Exception) {
                _errorMessage.value = "Arama sırasında bir hata oluştu."
            } finally {
                _isSearchLoading.value = false
            }
        }
    }

    fun loadMoreSearch() = executeSearch()

    fun clearSearch() {
        _searchResults.value = emptyList()
        _selectedCategory.value = null
        lastSearchQuery = ""
        searchCurrentPage = 1
        isSearchLastPage = false
    }

    fun toggleFavorite(video: AnimecixVideo) {
        val url = video.url ?: return
        viewModelScope.launch {
            if (favoriteDao.isFavorite(url)) {
                favoriteDao.deleteFavoriteByUrl(url)
            } else {
                favoriteDao.insertFavorite(
                    com.myanim.kondi.data.local.Favorite(
                        url = url,
                        title = video.name ?: "Bilinmeyen Anime",
                        posterUrl = video.poster,
                        source = "ANIMECIX",
                        animeId = video.animeId // Store the ID for navigation
                    )
                )
            }
        }
    }
}

