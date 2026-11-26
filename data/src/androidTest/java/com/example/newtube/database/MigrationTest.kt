package com.example.newtube.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            NewTubeDatabase::class.java,
        )

    @Test
    fun migrate2To3_addsAvatarAndWatchHistory() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO videos (id, title, channelName, channelId, thumbnailUrl, duration, views, uploadedAt, isLive) " +
                    "VALUES ('v1', 'Title', 'Channel', 'UC1', 'thumb', 10, 20, '1 day ago', 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, NewTubeDatabase.MIGRATION_2_3)

        db.query("SELECT authorAvatarUrl FROM videos WHERE id = 'v1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("", cursor.getString(0))
        }
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'watch_history'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        db.close()
    }

    @Test
    fun migration3To4CreatesShortsFeed() {
        helper.createDatabase(TEST_DB, 3).apply { close() }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, NewTubeDatabase.MIGRATION_3_4)

        db.execSQL(
            "INSERT INTO shorts_feed (" +
                "videoId, title, channelName, channelId, thumbnailUrl, duration, views, " +
                "uploadedAt, isLive, authorAvatarUrl, position, cachedAt) " +
                "VALUES ('v1', 'Title', 'Channel', 'UC1', 'thumb', 10, 20, '1 day ago', 0, 'avatar', 3, 1234)",
        )

        val columns =
            listOf(
                "videoId",
                "title",
                "channelName",
                "channelId",
                "thumbnailUrl",
                "duration",
                "views",
                "uploadedAt",
                "isLive",
                "authorAvatarUrl",
                "position",
                "cachedAt",
            )
        db.query("SELECT ${columns.joinToString(", ")} FROM shorts_feed WHERE videoId = 'v1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("v1", cursor.getString(cursor.getColumnIndexOrThrow("videoId")))
            assertEquals("Title", cursor.getString(cursor.getColumnIndexOrThrow("title")))
            assertEquals("Channel", cursor.getString(cursor.getColumnIndexOrThrow("channelName")))
            assertEquals("UC1", cursor.getString(cursor.getColumnIndexOrThrow("channelId")))
            assertEquals("thumb", cursor.getString(cursor.getColumnIndexOrThrow("thumbnailUrl")))
            assertEquals(10, cursor.getInt(cursor.getColumnIndexOrThrow("duration")))
            assertEquals(20, cursor.getInt(cursor.getColumnIndexOrThrow("views")))
            assertEquals("1 day ago", cursor.getString(cursor.getColumnIndexOrThrow("uploadedAt")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("isLive")))
            assertEquals("avatar", cursor.getString(cursor.getColumnIndexOrThrow("authorAvatarUrl")))
            assertEquals(3, cursor.getInt(cursor.getColumnIndexOrThrow("position")))
            assertEquals(1234, cursor.getInt(cursor.getColumnIndexOrThrow("cachedAt")))
        }

        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_shorts_feed_position'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
