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

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
