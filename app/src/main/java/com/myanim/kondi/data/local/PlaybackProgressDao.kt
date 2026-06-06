package com.myanim.kondi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackProgressDao {
    @Query("SELECT * FROM playback_progress WHERE url = :url LIMIT 1")
    suspend fun getProgress(url: String): PlaybackProgress?

    @Query("SELECT * FROM playback_progress WHERE url = :url LIMIT 1")
    fun getProgressFlow(url: String): Flow<PlaybackProgress?>

    @Query("SELECT * FROM playback_progress ORDER BY lastPlayedAt DESC")
    fun getAllProgressFlow(): Flow<List<PlaybackProgress>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(progress: PlaybackProgress)

    @Query("DELETE FROM playback_progress WHERE url = :url")
    suspend fun deleteProgress(url: String)
}
