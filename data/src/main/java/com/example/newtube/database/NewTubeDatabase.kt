package com.example.newtube.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [VideoEntity::class, ChannelEntity::class],
    version = NewTubeDatabase.VERSION,
    exportSchema = true,
)
abstract class NewTubeDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao

    abstract fun channelDao(): ChannelDao

    companion object {
        const val VERSION = 2

        /**
         * v2 adds `VideoEntity.channelId`, which is what the channel screen
         * needs: Invidious only resolves channels by their `UC…` id.
         */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        "ALTER TABLE videos ADD COLUMN channelId TEXT NOT NULL DEFAULT ''",
                    )
                }
            }
    }
}
