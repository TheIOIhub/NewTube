package com.example.newtube.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedCacheMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            NewTubeDatabase::class.java,
        )

    @Test
    fun migration4To5CreatesFeedCacheAndObservesOneRow() =
        runBlocking {
            helper.createDatabase(TEST_DB, 4).apply { close() }
            helper.runMigrationsAndValidate(TEST_DB, 5, true, NewTubeDatabase.MIGRATION_4_5).use { migrated ->
                migrated
                    .query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_feed_cache_cachedAt'")
                    .use { cursor -> assertTrue(cursor.moveToFirst()) }
            }

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val db =
                Room
                    .databaseBuilder(context, NewTubeDatabase::class.java, TEST_DB)
                    .addMigrations(NewTubeDatabase.MIGRATION_4_5)
                    .build()
            val dao = db.feedCacheDao()
            val row = FeedCacheEntity(key = "home:ALL", json = """{"contents":[]}""", cachedAt = 1234L)
            try {
                dao.insert(row)

                assertEquals(row.json, dao.observe(row.key).first())

                dao.clear()
                assertNull(dao.observe(row.key).first())
            } finally {
                db.close()
            }
        }

    private companion object {
        const val TEST_DB = "feed-cache-migration-test"
    }
}
