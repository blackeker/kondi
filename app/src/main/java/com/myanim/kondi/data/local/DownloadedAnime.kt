package com.myanim.kondi.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "downloaded_anime",
    indices = [
        // "WHERE animeId = ? AND episode = ?" sorgusunun ana erişim yolu
        Index(value = ["animeId", "episode"]),
        // "ORDER BY downloadDate DESC" sorgusu için
        Index(value = ["downloadDate"])
    ]
)
data class DownloadedAnime(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val animeId: Int,
    val title: String,
    val episode: Int,
    val filePath: String,
    val downloadDate: Long = System.currentTimeMillis()
)
