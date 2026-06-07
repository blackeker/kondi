package com.myanim.kondi.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: Download)

    @Update
    suspend fun updateDownload(download: Download)

    @Delete
    suspend fun deleteDownload(download: Download)

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: String): Download?

    @Query("SELECT * FROM downloads ORDER BY queueOrder ASC, createdAt ASC")
    fun getAllDownloads(): Flow<List<Download>>
 
    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY queueOrder ASC, createdAt ASC")
    fun getDownloadsByStatus(status: String): Flow<List<Download>>
 
    @Query("SELECT * FROM downloads WHERE source = :source ORDER BY queueOrder ASC, createdAt ASC")
    fun getDownloadsBySource(source: String): Flow<List<Download>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY createdAt DESC")
    fun getCompletedDownloads(): Flow<List<Download>>

    @Query("UPDATE downloads SET status = :status, progress = :progress, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDownloadProgress(id: String, status: String, progress: Int, downloadedBytes: Long, totalBytes: Long, updatedAt: Long)

    @Query("UPDATE downloads SET queueOrder = :queueOrder WHERE id = :id")
    suspend fun updateDownloadQueueOrder(id: String, queueOrder: Int)

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED'")
    suspend fun deleteCompletedDownloads()
}

@Dao
interface WatchlistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchlist(watchlist: Watchlist)

    @Update
    suspend fun updateWatchlist(watchlist: Watchlist)

    @Delete
    suspend fun deleteWatchlist(watchlist: Watchlist)

    @Query("SELECT * FROM watchlist WHERE id = :id")
    suspend fun getWatchlistById(id: Int): Watchlist?

    @Query("SELECT * FROM watchlist ORDER BY lastWatchedAt DESC")
    fun getAllWatchlist(): Flow<List<Watchlist>>

    @Query("SELECT * FROM watchlist WHERE sourceId = :sourceId ORDER BY lastWatchedAt DESC")
    fun getWatchlistBySource(sourceId: String): Flow<List<Watchlist>>

    @Query("SELECT * FROM watchlist WHERE contentId = :contentId AND sourceId = :sourceId LIMIT 1")
    suspend fun getWatchlistItem(contentId: String, sourceId: String): Watchlist?

    @Query("UPDATE watchlist SET watchProgress = :progress, lastEpisodeWatched = :lastEpisode, lastWatchedAt = :updatedAt WHERE id = :id")
    suspend fun updateWatchProgress(id: Int, progress: Float, lastEpisode: Int, updatedAt: Long)

    @Query("DELETE FROM watchlist WHERE id = :id")
    suspend fun removeFromWatchlist(id: Int)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY timestamp DESC")
    fun getAllFavorites(): Flow<List<Favorite>>

    @Query("SELECT * FROM favorites WHERE source = :source ORDER BY timestamp DESC")
    fun getFavoritesBySource(source: String): Flow<List<Favorite>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE url = :url)")
    suspend fun isFavorite(url: String): Boolean

    @Query("SELECT * FROM favorites WHERE url = :url LIMIT 1")
    suspend fun getFavoriteByUrl(url: String): Favorite?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: Favorite)

    @Delete
    suspend fun deleteFavorite(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE url = :url")
    suspend fun deleteFavoriteByUrl(url: String)
}

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<WatchHistory>>

    @Query("SELECT * FROM watch_history WHERE source = :source ORDER BY timestamp DESC")
    fun getHistoryBySource(source: String): Flow<List<WatchHistory>>

    @Query("SELECT * FROM watch_history WHERE videoUrl = :videoUrl")
    suspend fun getHistoryItem(videoUrl: String): WatchHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: WatchHistory)

    @Delete
    suspend fun deleteHistory(history: WatchHistory)
    
    @Query("DELETE FROM watch_history")
    suspend fun clearHistory()

    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int): Flow<List<WatchHistory>>

    @Query("SELECT * FROM watch_history WHERE source = :source ORDER BY timestamp DESC LIMIT :limit")
    fun getHistoryBySource(source: String, limit: Int): Flow<List<WatchHistory>>

    @Query("SELECT * FROM watch_history WHERE isBookmarked = 1 ORDER BY timestamp DESC")
    fun getBookmarkedVideos(): Flow<List<WatchHistory>>
    
    @Query("UPDATE watch_history SET isBookmarked = 1, bookmarkLabel = :label WHERE videoUrl = :videoUrl")
    suspend fun bookmarkVideo(videoUrl: String, label: String)

    @Query("UPDATE watch_history SET isBookmarked = 0 WHERE videoUrl = :videoUrl")
    suspend fun removeBookmark(videoUrl: String)
    
    @Query("SELECT * FROM watch_history WHERE tags LIKE '%' || :tag || '%'")
    fun searchByTag(tag: String): Flow<List<WatchHistory>>

    @Query("DELETE FROM watch_history WHERE timestamp < :timestamp")
    suspend fun deleteOldHistory(timestamp: Long)
}

@Dao
interface WatchedAnimeDao {
    @Query("SELECT * FROM watched_anime ORDER BY watchedDate DESC")
    fun getAllWatchedAnime(): Flow<List<WatchedAnime>>

    @Query("SELECT * FROM watched_anime WHERE animeId = :animeId")
    suspend fun getWatchedAnimeById(animeId: Int): WatchedAnime?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchedAnime(anime: WatchedAnime)

    @Delete
    suspend fun deleteWatchedAnime(anime: WatchedAnime)
}

@Dao
interface DownloadedAnimeDao {
    @Query("SELECT * FROM downloaded_anime ORDER BY downloadDate DESC")
    fun getAllDownloadedAnime(): Flow<List<DownloadedAnime>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadedAnime(anime: DownloadedAnime)

    @Delete
    suspend fun deleteDownloadedAnime(anime: DownloadedAnime)

    @Query("SELECT * FROM downloaded_anime WHERE animeId = :animeId AND episode = :episode")
    suspend fun getDownloadedAnime(animeId: Int, episode: Int): DownloadedAnime?
}
