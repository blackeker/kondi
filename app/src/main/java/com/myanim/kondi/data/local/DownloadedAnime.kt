package com.myanim.kondi.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_anime")
data class DownloadedAnime(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val animeId: Int,
    val title: String,
    val episode: Int,
    val filePath: String,
    val downloadDate: Long = System.currentTimeMillis()
)
