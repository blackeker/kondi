package com.myanim.kondi.data.repository

import android.content.Context
import com.myanim.kondi.data.local.*
import kotlinx.coroutines.flow.Flow

class HomeRepository(context: Context) {
    private val database = KondiDatabase.getDatabase(context)
    private val downloadDao = database.downloadDao()
    private val watchlistDao = database.watchlistDao()
    private val favoriteDao = database.favoriteDao()
    private val watchHistoryDao = database.watchHistoryDao()

    // ============ DOWNLOAD OPERATIONS ============
    suspend fun addDownload(download: Download) {
        downloadDao.insertDownload(download)
    }

    suspend fun updateDownload(download: Download) {
        downloadDao.updateDownload(download)
    }

    suspend fun updateDownloadProgress(id: String, status: String, progress: Int, downloadedBytes: Long, totalBytes: Long) {
        downloadDao.updateDownloadProgress(id, status, progress, downloadedBytes, totalBytes, System.currentTimeMillis())
    }

    suspend fun deleteDownload(download: Download) {
        downloadDao.deleteDownload(download)
    }

    suspend fun getDownloadById(id: String): Download? {
        return downloadDao.getDownloadById(id)
    }

    fun getAllDownloads(): Flow<List<Download>> {
        return downloadDao.getAllDownloads()
    }

    fun getDownloadsByStatus(status: String): Flow<List<Download>> {
        return downloadDao.getDownloadsByStatus(status)
    }

    fun getDownloadsBySource(sourceId: String): Flow<List<Download>> {
        return downloadDao.getDownloadsBySource(sourceId)
    }

    fun getDownloadingVideos(): Flow<List<Download>> {
        return downloadDao.getDownloadsByStatus("DOWNLOADING")
    }

    fun getCompletedDownloads(): Flow<List<Download>> {
        return downloadDao.getCompletedDownloads()
    }

    suspend fun deleteCompletedDownloads() {
        downloadDao.deleteCompletedDownloads()
    }

    // ============ WATCHLIST OPERATIONS ============
    suspend fun addToWatchlist(watchlist: Watchlist) {
        watchlistDao.insertWatchlist(watchlist)
    }

    suspend fun updateWatchlist(watchlist: Watchlist) {
        watchlistDao.updateWatchlist(watchlist)
    }

    suspend fun updateWatchProgress(id: Int, progress: Float, lastEpisode: Int) {
        watchlistDao.updateWatchProgress(id, progress, lastEpisode, System.currentTimeMillis())
    }

    suspend fun removeFromWatchlist(id: Int) {
        watchlistDao.removeFromWatchlist(id)
    }

    suspend fun getWatchlistItem(contentId: String, sourceId: String): Watchlist? {
        return watchlistDao.getWatchlistItem(contentId, sourceId)
    }

    fun getAllWatchlist(): Flow<List<Watchlist>> {
        return watchlistDao.getAllWatchlist()
    }

    fun getWatchlistBySource(sourceId: String): Flow<List<Watchlist>> {
        return watchlistDao.getWatchlistBySource(sourceId)
    }

    // ============ FAVORITE OPERATIONS ============
    suspend fun addToFavorites(favorite: Favorite) {
        favoriteDao.insertFavorite(favorite)
    }

    suspend fun removeFromFavorites(favorite: Favorite) {
        favoriteDao.deleteFavorite(favorite)
    }

    suspend fun removeFavoriteByUrl(url: String) {
        favoriteDao.deleteFavoriteByUrl(url)
    }

    suspend fun isFavorite(url: String): Boolean {
        return favoriteDao.isFavorite(url)
    }

    fun getAllFavorites(): Flow<List<Favorite>> {
        return favoriteDao.getAllFavorites()
    }

    fun getFavoritesBySource(source: String): Flow<List<Favorite>> {
        return favoriteDao.getFavoritesBySource(source)
    }

    // ============ WATCH HISTORY OPERATIONS ============
    suspend fun addToHistory(history: WatchHistory) {
        watchHistoryDao.insertHistory(history)
    }

    suspend fun deleteHistoryItem(history: WatchHistory) {
        watchHistoryDao.deleteHistory(history)
    }

    fun getRecentHistory(): Flow<List<WatchHistory>> {
        return watchHistoryDao.getRecentHistory(20)
    }

    fun getAllHistory(): Flow<List<WatchHistory>> {
        return watchHistoryDao.getAllHistory()
    }

    fun getHistoryBySource(source: String): Flow<List<WatchHistory>> {
        return watchHistoryDao.getHistoryBySource(source, 20)
    }

    suspend fun clearAllHistory() {
        watchHistoryDao.clearHistory()
    }

    suspend fun deleteOldHistory(beforeTimestamp: Long) {
        watchHistoryDao.deleteOldHistory(beforeTimestamp)
    }
}
