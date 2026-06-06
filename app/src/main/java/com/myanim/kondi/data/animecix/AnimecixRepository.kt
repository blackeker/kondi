package com.myanim.kondi.data.animecix

import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnimecixRepository {
    private val TAG = "AnimecixRepository"
    private val scraper = AnimecixScraper()

    suspend fun getLatestEpisodes(page: Int = 1): List<AnimecixVideo> = withContext(Dispatchers.IO) {
        scraper.getLatestEpisodes(page)
    }
    
    suspend fun getAnimeDetails(id: Int): AnimecixAnime? = withContext(Dispatchers.IO) {
        scraper.getAnimeDetails(id)
    }

    suspend fun getVideoSources(
        episodeId: Int,
        titleId: Int? = null,
        season: Int? = null,
        episode: Int? = null
    ): List<AnimecixSource> = withContext(Dispatchers.IO) {
        Timber.d("getVideoSources: episodeId=$episodeId, titleId=$titleId, season=$season, episode=$episode")
        scraper.getVideoSources(episodeId, titleId, season, episode)
    }

    suspend fun getVideos(
        episodeId: Int?,
        titleId: Int? = null,
        season: Int? = null,
        episode: Int? = null
    ): List<AnimecixSource> {
        return if (episodeId != null) {
            getVideoSources(episodeId, titleId, season, episode)
        } else {
            emptyList()
        }
    }

    suspend fun resolveSource(url: String): String? = withContext(Dispatchers.IO) {
        Timber.d("resolveSource: $url")
        scraper.resolveSource(url)
    }

    suspend fun search(query: String, page: Int = 1): List<AnimecixTitle> = withContext(Dispatchers.IO) {
        scraper.search(query, page)
    }

    suspend fun getCategoryItems(categoryPath: String, page: Int = 1): List<AnimecixTitle> = withContext(Dispatchers.IO) {
        scraper.getCategoryItems(categoryPath, page)
    }

    fun getCategories() = scraper.categories
}

