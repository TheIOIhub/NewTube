package com.example.newtube.ui.channel

import com.example.newtube.innertube.ContentSourceUnavailableException
import com.example.newtube.model.Channel
import com.example.newtube.model.ChannelVideosPage
import com.example.newtube.model.Video
import com.example.newtube.repository.FeedKey
import com.example.newtube.repository.VideoRepository
import com.example.newtube.ui.common.UiState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelViewModelTest {
    private val repository: VideoRepository = mockk()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.observeFeed(any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `channel details and uploads are loaded`() =
        runTest {
            val channel = channel()
            val videos = listOf(video("1"))
            coEvery { repository.getChannelDetails("UC1") } returns channel
            coEvery { repository.refreshFeed(FeedKey.Channel("UC1")) } returns Result.success(videos)

            val viewModel = ChannelViewModel(repository)
            viewModel.loadChannelDetails("UC1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(channel), viewModel.uiState.value.channel)
            assertEquals(videos, viewModel.uiState.value.videos)
            assertFalse(viewModel.isRefreshing.value)
        }

    @Test
    fun `load more follows the continuation token`() =
        runTest {
            val first = video("1")
            val second = video("2")
            coEvery { repository.getChannelDetails("UC1") } returns channel()
            coEvery { repository.refreshFeed(FeedKey.Channel("UC1")) } returns Result.success(listOf(first))
            coEvery { repository.getChannelVideos("UC1", null) } returns
                ChannelVideosPage(listOf(first), continuation = "next")
            coEvery { repository.getChannelVideos("UC1", "next") } returns
                ChannelVideosPage(listOf(second), continuation = null)

            val viewModel = ChannelViewModel(repository)
            viewModel.loadChannelDetails("UC1")
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.loadMore()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(first, second), viewModel.uiState.value.videos)
        }

    @Test
    fun `a channel details failure clears the refresh indicator`() =
        runTest {
            coEvery { repository.getChannelDetails("UC1") } throws IOException("offline")

            val viewModel = ChannelViewModel(repository)
            viewModel.loadChannelDetails("UC1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.channel is UiState.Error)
            assertFalse(viewModel.isRefreshing.value)
        }

    @Test
    fun `a failed refresh keeps the cached uploads`() =
        runTest {
            val cached = listOf(video("cached"))
            every { repository.observeFeed(FeedKey.Channel("UC1")) } returns flowOf(cached)
            coEvery { repository.getChannelDetails("UC1") } returns channel()
            coEvery { repository.refreshFeed(FeedKey.Channel("UC1")) } returns
                Result.failure(IOException("offline"))

            val viewModel = ChannelViewModel(repository)
            viewModel.loadChannelDetails("UC1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(cached, viewModel.uiState.value.videos)
            assertFalse(viewModel.isRefreshing.value)
        }

    @Test
    fun `a source failure keeps cached uploads and raises the banner`() =
        runTest {
            val cached = listOf(video("cached"))
            every { repository.observeFeed(FeedKey.Channel("UC1")) } returns flowOf(cached)
            coEvery { repository.getChannelDetails("UC1") } returns channel()
            coEvery { repository.refreshFeed(FeedKey.Channel("UC1")) } returns
                Result.failure(ContentSourceUnavailableException("blocked"))

            val viewModel = ChannelViewModel(repository)
            viewModel.loadChannelDetails("UC1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(cached, viewModel.uiState.value.videos)
            assertTrue(viewModel.uiState.value.sourceUnavailable)
            assertEquals(null, viewModel.uiState.value.videosError)
        }

    @Test
    fun `retry re-publishes cached uploads when the refresh fails`() =
        runTest {
            val cached = listOf(video("cached"))
            every { repository.observeFeed(FeedKey.Channel("UC1")) } returns flowOf(cached)
            coEvery { repository.getChannelDetails("UC1") } returns channel()
            coEvery { repository.refreshFeed(FeedKey.Channel("UC1")) } returnsMany
                listOf(Result.success(cached), Result.failure(IOException("offline")))

            val viewModel = ChannelViewModel(repository)
            viewModel.loadChannelDetails("UC1")
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(cached, viewModel.uiState.value.videos)

            viewModel.retry()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(cached, viewModel.uiState.value.videos)
            assertFalse(viewModel.isRefreshing.value)
        }

    @Test
    fun `a page from a previous channel cannot mix videos into the active one`() =
        runTest {
            val first = video("first")
            val pending = CompletableDeferred<ChannelVideosPage>()
            coEvery { repository.getChannelDetails("UC1") } returns channel()
            coEvery { repository.getChannelDetails("UC2") } returns channel()
            coEvery { repository.refreshFeed(any()) } returns Result.success(listOf(first))
            coEvery { repository.getChannelVideos("UC1", null) } coAnswers { pending.await() }

            val viewModel = ChannelViewModel(repository)
            viewModel.loadChannelDetails("UC1")
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.loadMore()

            viewModel.loadChannelDetails("UC2")
            testDispatcher.scheduler.advanceUntilIdle()

            pending.complete(ChannelVideosPage(listOf(video("stale")), continuation = null))
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(first), viewModel.uiState.value.videos)
            assertFalse(viewModel.uiState.value.isLoadingMore)
        }

    private fun channel() =
        Channel(
            id = "UC1",
            name = "Test Channel",
            avatarUrl = "https://example.com/avatar.png",
            subscriberCount = 1000L,
            totalVideos = 10,
            description = "Test description",
        )

    private fun video(id: String) =
        Video(
            id = id,
            title = "Video $id",
            channelName = "Test Channel",
            channelId = "UC1",
            thumbnailUrl = "https://example.com/$id.jpg",
            duration = 120L,
            views = 100L,
            uploadedAt = "1 day ago",
            isLive = false,
        )
}
