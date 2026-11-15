package com.example.newtube.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchHistoryDaoTest {
    private lateinit var db: NewTubeDatabase
    private lateinit var dao: WatchHistoryDao

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                NewTubeDatabase::class.java,
            ).allowMainThreadQueries().build()
        dao = db.watchHistoryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsertReplacesTheSameVideoAndObservesNewestFirst() = runBlocking {
        dao.upsert(entry("a", 1))
        dao.upsert(entry("b", 2))
        dao.upsert(entry("a", 3))

        val rows = dao.observeRecent(500).first()

        assertEquals(listOf("a", "b"), rows.map { it.videoId })
        assertEquals(3L, rows.first().watchedAt)
    }

    @Test
    fun trimKeepsOnlyTheNewestRows() = runBlocking {
        dao.upsert(entry("a", 1))
        dao.upsert(entry("b", 2))
        dao.upsert(entry("c", 3))

        dao.trim(2)

        assertEquals(listOf("c", "b"), dao.observeRecent(500).first().map { it.videoId })
    }

    @Test
    fun clearRemovesEverything() = runBlocking {
        dao.upsert(entry("a", 1))

        dao.clear()

        assertEquals(emptyList<WatchHistoryEntity>(), dao.observeRecent(500).first())
    }

    private fun entry(id: String, watchedAt: Long) =
        WatchHistoryEntity(
            videoId = id,
            title = "Video $id",
            channelName = "Channel",
            channelId = "UC$id",
            authorAvatarUrl = "",
            thumbnailUrl = "https://example.com/$id.jpg",
            durationMs = 30_000,
            watchedAt = watchedAt,
        )
}
