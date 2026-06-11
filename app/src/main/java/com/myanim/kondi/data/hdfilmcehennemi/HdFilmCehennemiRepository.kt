package com.myanim.kondi.data.hdfilmcehennemi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class HdFilmCehennemiRepository {
    private val TAG = "HdFilmCehennemiRepository"
    private val scraper = HdFilmCehennemiScraper()

    fun getCategories() = scraper.categories

    suspend fun getCategoryItems(categoryPath: String, page: Int = 1): List<HdFilmCehennemiTitle> = withContext(Dispatchers.IO) {
        scraper.getCategoryItems(categoryPath, page)
    }

    suspend fun search(query: String, page: Int = 1): List<HdFilmCehennemiTitle> = withContext(Dispatchers.IO) {
        scraper.search(query, page)
    }

    suspend fun getMovieDetail(id: String): HdFilmCehennemiDetail? = withContext(Dispatchers.IO) {
        scraper.getMovieDetail(id)
    }

    suspend fun getStreamUrls(detailUrl: String): List<HdFilmCehennemiSource> = withContext(Dispatchers.IO) {
        scraper.getStreamUrls(detailUrl)
    }

    suspend fun resolveSource(url: String): String? = withContext(Dispatchers.IO) {
        Timber.d("resolveSource: $url")
        scraper.resolveSource(url)
    }
}
