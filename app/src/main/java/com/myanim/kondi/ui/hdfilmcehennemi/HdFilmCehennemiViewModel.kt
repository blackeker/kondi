package com.myanim.kondi.ui.hdfilmcehennemi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myanim.kondi.data.hdfilmcehennemi.HdFilmCehennemiRepository
import com.myanim.kondi.data.hdfilmcehennemi.HdFilmCehennemiTitle
import com.myanim.kondi.data.hdfilmcehennemi.HdFilmCehennemiDetail
import com.myanim.kondi.data.hdfilmcehennemi.HdFilmCehennemiSource
import com.myanim.kondi.data.local.Favorite
import com.myanim.kondi.data.local.KondiDatabase
import com.myanim.kondi.data.local.WatchHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HdFilmCehennemiViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HdFilmCehennemiRepository()
    private val database = KondiDatabase.getDatabase(application)

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    fun selectTab(tab: Int) {
        _selectedTab.value = tab
    }

    private val _latestMovies = MutableStateFlow<List<HdFilmCehennemiTitle>>(emptyList())
    val latestMovies: StateFlow<List<HdFilmCehennemiTitle>> = _latestMovies.asStateFlow()

    private val _categoryItems = MutableStateFlow<List<HdFilmCehennemiTitle>>(emptyList())
    val categoryItems: StateFlow<List<HdFilmCehennemiTitle>> = _categoryItems.asStateFlow()

    private val _searchResults = MutableStateFlow<List<HdFilmCehennemiTitle>>(emptyList())
    val searchResults: StateFlow<List<HdFilmCehennemiTitle>> = _searchResults.asStateFlow()

    private val _favorites = MutableStateFlow<List<Favorite>>(emptyList())
    val favorites: StateFlow<List<Favorite>> = _favorites.asStateFlow()

    private val _watchHistory = MutableStateFlow<List<WatchHistory>>(emptyList())
    val watchHistory: StateFlow<List<WatchHistory>> = _watchHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSearchLoading = MutableStateFlow(false)
    val isSearchLoading: StateFlow<Boolean> = _isSearchLoading.asStateFlow()

    private val _selectedCategory = MutableStateFlow<Pair<String, String>?>(null)
    val selectedCategory: StateFlow<Pair<String, String>?> = _selectedCategory.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val categories = repository.getCategories()

    private var latestPage = 1
    private var isLatestHasMore = true
    private var latestLoading = false

    private var categoryPage = 1
    private var isCategoryHasMore = true
    private var categoryLoading = false

    init {
        loadLatestMovies()
        observeDatabase()
    }

    private fun observeDatabase() {
        viewModelScope.launch {
            database.favoriteDao().getFavoritesBySource("HDFILMCEHENNEMI").collectLatest { list ->
                _favorites.value = list
            }
        }
        viewModelScope.launch {
            database.watchHistoryDao().getHistoryBySource("HDFILMCEHENNEMI").collectLatest { list ->
                _watchHistory.value = list
            }
        }
    }

    fun loadLatestMovies() {
        if (latestLoading || !isLatestHasMore) return
        latestLoading = true
        _errorMessage.value = null
        viewModelScope.launch {
            _isLoading.value = latestPage == 1
            try {
                val results = repository.getCategoryItems("home", latestPage)
                if (results.isEmpty()) {
                    isLatestHasMore = false
                } else {
                    val current = _latestMovies.value.toMutableList()
                    val filtered = results.filter { r -> current.none { it.id == r.id } }
                    current.addAll(filtered)
                    _latestMovies.value = current
                    latestPage++
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Son filmler yüklenirken hata oluştu"
            } finally {
                _isLoading.value = false
                latestLoading = false
            }
        }
    }

    fun loadMoreLatest() {
        loadLatestMovies()
    }

    fun retryLatestMovies() {
        latestPage = 1
        isLatestHasMore = true
        _latestMovies.value = emptyList()
        loadLatestMovies()
    }

    fun selectCategory(category: Pair<String, String>) {
        _selectedCategory.value = category
        categoryPage = 1
        isCategoryHasMore = true
        _categoryItems.value = emptyList()
        loadCategoryItems()
    }

    fun loadCategoryItems() {
        val category = _selectedCategory.value ?: return
        if (categoryLoading || !isCategoryHasMore) return
        categoryLoading = true
        _errorMessage.value = null
        viewModelScope.launch {
            _isLoading.value = categoryPage == 1
            try {
                val results = repository.getCategoryItems(category.second, categoryPage)
                if (results.isEmpty()) {
                    isCategoryHasMore = false
                } else {
                    val current = _categoryItems.value.toMutableList()
                    val filtered = results.filter { r -> current.none { it.id == r.id } }
                    current.addAll(filtered)
                    _categoryItems.value = current
                    categoryPage++
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Kategori yüklenirken hata oluştu"
            } finally {
                _isLoading.value = false
                categoryLoading = false
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        if (_isSearchLoading.value) return
        _isSearchLoading.value = true
        _errorMessage.value = null
        _searchResults.value = emptyList()
        
        viewModelScope.launch {
            try {
                val results = repository.search(query)
                _searchResults.value = results
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Arama hatası oluştu"
            } finally {
                _isSearchLoading.value = false
            }
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
    }

    fun toggleFavorite(title: HdFilmCehennemiTitle) {
        viewModelScope.launch {
            val exists = database.favoriteDao().getFavoriteByUrl(title.id)
            if (exists != null) {
                database.favoriteDao().deleteFavorite(exists)
            } else {
                database.favoriteDao().insertFavorite(
                    Favorite(
                        url = title.id,
                        title = title.name,
                        posterUrl = title.poster,
                        source = "HDFILMCEHENNEMI"
                    )
                )
            }
        }
    }
}
