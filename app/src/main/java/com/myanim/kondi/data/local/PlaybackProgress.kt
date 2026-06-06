package com.myanim.kondi.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_progress")
data class PlaybackProgress(
    @PrimaryKey
    val url: String,
    val animeId: Int,
    val title: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val progressMillis: Long,
    val durationMillis: Long,
    val isCompleted: Boolean = false,
    val lastPlayedAt: Long = System.currentTimeMillis()
)
