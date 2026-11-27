package com.example.newtube.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [VideoEntity::class, ChannelEntity::class, WatchHistoryEntity::class, ShortsFeedEntity::class],
    version = NewTubeDatabase.VERSION,
    exportSchema = true,
)
abstract class NewTubeDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao

    abstract fun channelDao(): ChannelDao

    abstract fun watchHistoryDao(): WatchHistoryDao

    abstract fun shortsFeedDao(): ShortsFeedDao

    companion object {
        const val VERSION = 4

        /**
         * v2 adds `VideoEntity.channelId`, which is what the channel screen
         * needs: channels are resolved by their `UC…` id.
         */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        "ALTER TABLE videos ADD COLUMN channelId TEXT NOT NULL DEFAULT ''",
                    )
                }
            }

        /**
         * v3 adds the channel avatar to cached videos and introduces the
         * local watch history table. Both land in one migration.
         */
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        "ALTER TABLE videos ADD COLUMN authorAvatarUrl TEXT NOT NULL DEFAULT ''",
                    )
                    database.execSQL(
                        "CREATE TABLE IF NOT EXISTS watch_history (" +
                            "videoId TEXT NOT NULL PRIMARY KEY, " +
                            "title TEXT NOT NULL, " +
                            "channelName TEXT NOT NULL, " +
                            "channelId TEXT NOT NULL, " +
                            "authorAvatarUrl TEXT NOT NULL, " +
                            "thumbnailUrl TEXT NOT NULL, " +
                            "durationMs INTEGER NOT NULL, " +
                            "watchedAt INTEGER NOT NULL)",
                    )
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_watch_history_watchedAt " +
                            "ON watch_history (watchedAt)",
                    )
                }
            }

        /**
         * v4 adds the Shorts feed cache. Metadata only; signed stream
         * URLs are never persisted.
         */
        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        "CREATE TABLE IF NOT EXISTS shorts_feed (" +
                            "videoId TEXT NOT NULL PRIMARY KEY, " +
                            "title TEXT NOT NULL, " +
                            "channelName TEXT NOT NULL, " +
                            "channelId TEXT NOT NULL, " +
                            "thumbnailUrl TEXT NOT NULL, " +
                            "duration INTEGER NOT NULL, " +
                            "views INTEGER NOT NULL, " +
                            "uploadedAt TEXT NOT NULL, " +
                            "isLive INTEGER NOT NULL, " +
                            "authorAvatarUrl TEXT NOT NULL, " +
                            "position INTEGER NOT NULL, " +
                            "cachedAt INTEGER NOT NULL)",
                    )
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_shorts_feed_position " +
                            "ON shorts_feed (position)",
                    )
                }
            }
    }
}
