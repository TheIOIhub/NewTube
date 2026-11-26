package com.example.newtube.implementation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.paging.PagingSource
import androidx.paging.PagingState
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
import java.io.IOException

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

    @Test fun `appendFeed continues after the current maximum position`() =
        runTest {
            every { dao.observeAll() } returns
                flowOf(listOf(entity("a", 0), entity("b", 1)))
            val captured = slot<List<ShortsFeedEntity>>()
            coJustRun { dao.insertAll(capture(captured)) }

            repository.appendFeed(listOf(video("c"), video("d")))

            assertEquals(listOf("c", "d"), captured.captured.map { it.videoId })
            assertEquals(listOf(2, 3), captured.captured.map { it.position })
        }

    @Test fun `appendFeed ignores an empty page`() =
        runTest {
            repository.appendFeed(emptyList())
            coVerify(exactly = 0) { dao.insertAll(any()) }
        }

    @Test fun `pagingSource maps cached rows to videos`() =
        runTest {
            val delegate =
                object : PagingSource<Int, ShortsFeedEntity>() {
                    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ShortsFeedEntity> =
                        if (params.key == 42) {
                            LoadResult.Error(IOException("boom"))
                        } else {
                            LoadResult.Page(listOf(entity("a", 0)), null, 1)
                        }

                    override fun getRefreshKey(state: PagingState<Int, ShortsFeedEntity>): Int? = null
                }
            every { dao.pagingSource() } returns delegate

            val page =
                repository.pagingSource().load(
                    PagingSource.LoadParams.Refresh(
                        key = null,
                        loadSize = 10,
                        placeholdersEnabled = false,
                    ),
                )

            assertTrue(page is PagingSource.LoadResult.Page<Int, Video>)
            assertEquals("a", (page as PagingSource.LoadResult.Page<Int, Video>).data.single().id)

            val error =
                repository.pagingSource().load(
                    PagingSource.LoadParams.Refresh(
                        key = 42,
                        loadSize = 10,
                        placeholdersEnabled = false,
                    ),
                )
            assertTrue(error is PagingSource.LoadResult.Error)
        }

    private fun entity(id: String, position: Int) =
        ShortsFeedEntity(
            videoId = id,
            title = "Title $id",
            channelName = "Channel",
            channelId = "UC1",
            thumbnailUrl = "https://example.com/$id.jpg",
            duration = 30L,
            views = 10L,
            uploadedAt = "1 day ago",
            isLive = false,
            authorAvatarUrl = "https://example.com/avatar.jpg",
            position = position,
            cachedAt = 10L,
        )

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
