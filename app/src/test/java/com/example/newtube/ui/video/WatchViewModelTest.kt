package com.example.newtube.ui.video

import com.example.newtube.model.PlaybackSource
import com.example.newtube.model.Video
import com.example.newtube.model.VideoDetails
import com.example.newtube.repository.PlaybackPositionRepository
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class WatchViewModelTest {
    private val repository: VideoRepository = mockk()
    private val playbackPositions: PlaybackPositionRepository = mockk()
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
    fun `details and the saved position are exposed`() =
        runTest {
            val details = details()
            coEvery { repository.getVideoDetails("v1") } returns details
            coEvery { playbackPositions.positionFor("v1") } returns 124_000L

            val viewModel = WatchViewModel(repository, playbackPositions)
            viewModel.load("v1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(WatchUiState(details, 124_000L)), viewModel.uiState.value)
        }

    @Test
    fun `failure is exposed as an error`() =
        runTest {
            coEvery { repository.getVideoDetails("v1") } throws IOException("offline")

            val viewModel = WatchViewModel(repository, playbackPositions)
            viewModel.load("v1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value is UiState.Error)
        }

    @Test
    fun `position is persisted for the current video`() =
        runTest {
            coEvery { repository.getVideoDetails("v1") } returns details()
            coEvery { playbackPositions.positionFor("v1") } returns 0L
            coEvery { playbackPositions.savePosition("v1", 42_000L) } returns Unit

            val viewModel = WatchViewModel(repository, playbackPositions)
            viewModel.load("v1")
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.savePosition(42_000L)
            testDispatcher.scheduler.advanceUntilIdle()

            io.mockk.coVerify { playbackPositions.savePosition("v1", 42_000L) }
        }

    private fun details() =
        VideoDetails(
            video =
                Video(
                    id = "v1",
                    title = "Video",
                    channelName = "Channel",
                    channelId = "UC1",
                    thumbnailUrl = "https://example.com/thumb.jpg",
                    duration = 300L,
                    views = 100L,
                    uploadedAt = "1 day ago",
                    isLive = false,
                ),
            description = "Description",
            related = emptyList(),
            playback = PlaybackSource.Hls("https://example.com/manifest"),
        )
}
