package com.example.newtube.implementation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.example.newtube.database.ShortsFeedDao
import com.example.newtube.database.ShortsFeedEntity
import com.example.newtube.model.Video
import com.example.newtube.preferences.KNOWN_CHANNELS_KEY
import io.mockk.coEvery
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

class ShortsCacheRepositoryImplTest {
    private val dao: ShortsFeedDao = mockk(relaxed = true)
    private val dataStore: DataStore<Preferences> = mockk(relaxed = true)
    private val repository = ShortsCacheRepositoryImpl(dao, dataStore)

    @Test fun `replaceFeed preserves order and cachedAt`() =
        runTest {
            coJustRun { dao.replaceAll(any()) }
            val before = System.currentTimeMillis()
            val videos = listOf(video("a"), video("b"))

            repository.replaceFeed(videos)

            val captured = slot<List<ShortsFeedEntity>>()
            coVerify { dao.replaceAll(capture(captured)) }
            val entities = captured.captured
            assertEquals(listOf("a", "b"), entities.map { it.videoId })
            assertEquals(listOf(0, 1), entities.map { it.position })
            assertTrue(entities.all { it.cachedAt in before..System.currentTimeMillis() })
        }

    @Test fun `empty DataStore yields empty channel set`() =
        runTest {
            every { dataStore.data } returns flowOf(emptyPreferences())

            assertEquals(emptySet<String>(), repository.knownChannelIds())
        }

    @Test fun `rememberChannelIds writes the set`() =
        runTest {
            // DataStore.edit applies its transform via updateData; capture that
            // transform and run it against empty preferences to inspect the write.
            val transformSlot = slot<suspend (Preferences) -> Preferences>()
            coEvery { dataStore.updateData(capture(transformSlot)) } returns emptyPreferences()

            repository.rememberChannelIds(setOf("UC1", "UC2"))

            val updated = transformSlot.captured(emptyPreferences())
            assertEquals(setOf("UC1", "UC2"), updated[KNOWN_CHANNELS_KEY])
        }

    @Test fun `observeFeed maps cached rows to Video in position order`() =
        runTest {
            every { dao.observeAll() } returns
                flowOf(
                    listOf(
                        ShortsFeedEntity("a", "T", "C", "UC1", "thumb", 1L, 2L, "now", false, "avatar", 0, 10L),
                        ShortsFeedEntity("b", "T2", "C2", "UC2", "thumb2", 3L, 4L, "1d", true, "avatar2", 1, 10L),
                    ),
                )

            val feed = repository.observeFeed().first()

            assertEquals(listOf("a", "b"), feed.map { it.id })
            assertEquals(Video("a", "T", "C", "UC1", "thumb", 1L, 2L, "now", false, "avatar"), feed[0])
        }

    private fun video(id: String) =
        Video(
            id = id,
            title = "Video $id",
            channelName = "Channel",
            channelId = "UC1",
            thumbnailUrl = "https://example.com/thumb.jpg",
            duration = 30,
            views = 10,
            uploadedAt = "1 day ago",
            isLive = false,
            authorAvatarUrl = "https://example.com/avatar.jpg",
        )
}
