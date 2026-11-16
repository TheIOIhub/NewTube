package com.example.newtube.implementation

import com.example.newtube.database.WatchHistoryDao
import com.example.newtube.database.WatchHistoryEntity
import com.example.newtube.model.Video
import com.example.newtube.model.WatchHistoryEntry
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRepositoryImplTest {
    private val dao: WatchHistoryDao = mockk(relaxed = true)
    private val repository = HistoryRepositoryImpl(dao)

    @Test
    fun `observeLocal maps rows to domain entries`() =
        runTest {
            every { dao.observeRecent(500) } returns flowOf(listOf(entity("v1", watchedAt = 42)))

            assertEquals(
                listOf(
                    WatchHistoryEntry(
                        videoId = "v1",
                        title = "Video v1",
                        channelName = "Channel",
                        channelId = "UC1",
                        authorAvatarUrl = "https://example.com/avatar.jpg",
                        thumbnailUrl = "https://example.com/thumb.jpg",
                        durationMs = 30_000,
                        watchedAt = 42,
                    ),
                ),
                repository.observeLocal(500).first(),
            )
        }

    @Test
    fun `record converts seconds to millis and trims to 500 rows`() =
        runTest {
            val before = System.currentTimeMillis()
            val captured = slot<WatchHistoryEntity>()
            coJustRun { dao.upsert(capture(captured)) }
            coJustRun { dao.trim(any()) }

            repository.record(video("v1", duration = 120))

            val entity = captured.captured
            assertEquals("v1", entity.videoId)
            assertEquals(120_000L, entity.durationMs)
            assertTrue(entity.watchedAt in before..System.currentTimeMillis())
            coVerify { dao.trim(500) }
        }

    @Test
    fun `clearLocal deletes every row`() =
        runTest {
            coJustRun { dao.clear() }

            repository.clearLocal()

            coVerify { dao.clear() }
        }

    private fun entity(
        id: String,
        watchedAt: Long,
    ) = WatchHistoryEntity(
        videoId = id,
        title = "Video $id",
        channelName = "Channel",
        channelId = "UC1",
        authorAvatarUrl = "https://example.com/avatar.jpg",
        thumbnailUrl = "https://example.com/thumb.jpg",
        durationMs = 30_000,
        watchedAt = watchedAt,
    )

    private fun video(
        id: String,
        duration: Long,
    ) = Video(
        id = id,
        title = "Video $id",
        channelName = "Channel",
        channelId = "UC1",
        thumbnailUrl = "https://example.com/thumb.jpg",
        duration = duration,
        views = 10,
        uploadedAt = "1 day ago",
        isLive = false,
        authorAvatarUrl = "https://example.com/avatar.jpg",
    )
}
