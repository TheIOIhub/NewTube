package com.example.newtube.ui.shorts

import com.example.newtube.model.PlaybackSource
import com.example.newtube.model.Video
import com.example.newtube.model.VideoDetails
import com.example.newtube.repository.VideoRepository
import com.example.newtube.ui.common.UiState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShortsViewModelTest {
    private val repository: VideoRepository = mockk()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `short videos are collected across pages`() =
        runTest {
            val first = video("1")
            val second = video("2")
            coEvery { repository.getShorts(1) } returns listOf(first)
            coEvery { repository.getShorts(2) } returns listOf(second)
            coEvery { repository.getShorts(3) } returns emptyList()

            val viewModel = ShortsViewModel(repository)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(listOf(first, second)), viewModel.uiState.value)
        }

    @Test
    fun `failure is exposed as an error`() =
        runTest {
            coEvery { repository.getShorts(any()) } throws IllegalStateException("boom")

            val viewModel = ShortsViewModel(repository)
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value is UiState.Error)
        }

    @Test
    fun `playback source is resolved once per short`() =
        runTest {
            val short = video("1")
            coEvery { repository.getShorts(1) } returns listOf(short)
            coEvery { repository.getShorts(2) } returns emptyList()
            coEvery { repository.getVideoDetails("1") } returns
                VideoDetails(
                    video = short,
                    description = "",
                    related = emptyList(),
                    playback = PlaybackSource.Hls("https://example.com/manifest"),
                )

            val viewModel = ShortsViewModel(repository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.ensurePlayback("1")
            viewModel.ensurePlayback("1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                mapOf("1" to PlaybackSource.Hls("https://example.com/manifest")),
                viewModel.playbackSources.value,
            )
        }

    @Test
    fun `a reload that yields no clips is an error with retry`() =
        runTest {
            coEvery { repository.getShorts(any()) } returns emptyList()

            val viewModel = ShortsViewModel(repository)
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value is UiState.Error)
        }

    @Test
    fun `loadMore keeps the feed alive when a page only repeats known ids`() =
        runTest {
            val initial = (1..11).map { video("$it") }
            coEvery { repository.getShorts(1) } returns initial.take(4)
            coEvery { repository.getShorts(2) } returns initial.drop(4).take(4)
            coEvery { repository.getShorts(3) } returns initial.drop(8)
            // reload() stops after 3 pages / >= 10 clips, so loadMore starts at page 4
            coEvery { repository.getShorts(4) } returns listOf(initial[0]) // duplicate only
            coEvery { repository.getShorts(5) } returns listOf(video("12"))

            val viewModel = ShortsViewModel(repository)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.loadMore()
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value as UiState.Success
            assertEquals(12, state.data.size)
            assertEquals("12", state.data.last().id)
        }

    private fun video(id: String) =
        Video(
            id = id,
            title = "Short $id",
            channelName = "Channel",
            channelId = "UC$id",
            thumbnailUrl = "https://example.com/$id.jpg",
            duration = 30L,
            views = 100L,
            uploadedAt = "1 day ago",
            isLive = false,
        )
}
