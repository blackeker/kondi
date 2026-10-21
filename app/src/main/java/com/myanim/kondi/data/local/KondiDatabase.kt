package com.myanim.kondi.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
        entities = [Download::class, Watchlist::class, Favorite::class, WatchHistory::class, WatchedAnime::class, DownloadedAnime::class, PlaybackProgress::class],
        version = 11,
        exportSchema = true
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
                // Entity tanımlarıyla birebir aynı index'ler oluşturulmalı;
                // aksi halde Room şema doğrulaması migration'ı reddeder.
                createPerformanceIndices(db)
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

        /**
         * v10 -> v11: watched_anime ve downloaded_anime tablolarında sorgu
         * performansı için eksik olan index'ler ekleniyor.
         * (Entity sınıflarındaki @Index tanımlarıyla aynı adlar kullanılmalıdır.)
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createPerformanceIndices(db)
            }
        }

        private fun createPerformanceIndices(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_watched_anime_watchedDate` ON `watched_anime` (`watchedDate`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloaded_anime_animeId_episode` ON `downloaded_anime` (`animeId`, `episode`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloaded_anime_downloadDate` ON `downloaded_anime` (`downloadDate`)")
        }

        fun getDatabase(context: Context): KondiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KondiDatabase::class.java,
                    "kondi_database"
                )
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    // 1-6 sürümleri bu deponun geçmişinde bulunmuyor; şemaları bilinmediği için
                    // güvenli migration yazılamaz. Bu durumda Room veritabanını sıfırlar
                    // (çökme yerine veri kaybı tercih edilir).
                    .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
