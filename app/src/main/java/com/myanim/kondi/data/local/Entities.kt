package com.myanim.kondi.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PAUSED
}

@Entity(tableName = "downloads")
data class Download(
    @PrimaryKey
    val id: String,
    val title: String,
    val url: String,
    val filePath: String,
    val status: String = "PENDING", // PENDING, DOWNLOADING, COMPLETED, FAILED, PAUSED
    val progress: Int = 0,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val source: String = "GENERAL",
    val errorMessage: String? = null,
    val queueOrder: Int = 0 // Used for IDM-style priority queuing
) : Serializable

@Entity(tableName = "watchlist")
data class Watchlist(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val sourceId: String,
    val contentId: String,
    val contentTitle: String,
    val contentType: String = "ANIME",
    val posterUrl: String? = null,
    val lastEpisodeWatched: Int = 0,
    val totalEpisodes: Int = 0,
    val watchProgress: Float = 0f,
    val addedAt: Long = System.currentTimeMillis(),
    val lastWatchedAt: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "favorites", indices = [androidx.room.Index(value = ["source"])])
data class Favorite(
    @PrimaryKey
    val url: String, // Unique identifier (anime detail page URL)
    val title: String,
    val posterUrl: String?,
    val source: String, 
    val animeId: Int? = null, // Added for Animecix detail navigation
    val timestamp: Long = System.currentTimeMillis(),
    val detailsJson: String? = null
)

@Entity(tableName = "watch_history", indices = [androidx.room.Index(value = ["source"])])
data class WatchHistory(
    @PrimaryKey
    val videoUrl: String, // Unique identifier for the specific episode/video URL
    val title: String, // Anime Title - Episode Name
    val animeUrl: String, // Parent anime URL to link back
    val posterUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String,
    val isBookmarked: Boolean = false,
    val bookmarkLabel: String? = null,
    val tags: String? = null, // Store as comma-separated or JSON
    val deviceId: String? = null
)
