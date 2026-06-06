package com.myanim.kondi.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watched_anime")
data class WatchedAnime(
    @PrimaryKey
    val animeId: Int,
    val title: String,
    val lastWatchedEpisode: Int,
    val watchedDate: Long = System.currentTimeMillis(),
    val rating: Float = 0f,
    val posterUrl: String? = null
)
