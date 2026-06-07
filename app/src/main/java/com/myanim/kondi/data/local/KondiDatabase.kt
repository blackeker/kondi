package com.myanim.kondi.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Download::class, Watchlist::class, Favorite::class, WatchHistory::class, WatchedAnime::class, DownloadedAnime::class, PlaybackProgress::class],
    version = 10,
    exportSchema = false
)
abstract class KondiDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun watchedAnimeDao(): WatchedAnimeDao
    abstract fun downloadedAnimeDao(): DownloadedAnimeDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    companion object {
        @Volatile
        private var INSTANCE: KondiDatabase? = null

        // Example migration path from 7 to 8
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `watched_anime` (`animeId` INTEGER NOT NULL, `title` TEXT NOT NULL, `lastWatchedEpisode` INTEGER NOT NULL, `watchedDate` INTEGER NOT NULL, `rating` REAL NOT NULL, `posterUrl` TEXT, PRIMARY KEY(`animeId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `downloaded_anime` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `animeId` INTEGER NOT NULL, `title` TEXT NOT NULL, `episode` INTEGER NOT NULL, `filePath` TEXT NOT NULL, `downloadDate` INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `playback_progress` (`url` TEXT NOT NULL, `animeId` INTEGER NOT NULL, `title` TEXT NOT NULL, `seasonNumber` INTEGER NOT NULL, `episodeNumber` INTEGER NOT NULL, `progressMillis` INTEGER NOT NULL, `durationMillis` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, PRIMARY KEY(`url`))")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE favorites ADD COLUMN detailsJson TEXT")
            }
        }

        fun getDatabase(context: Context): KondiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KondiDatabase::class.java,
                    "kondi_database"
                )
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
