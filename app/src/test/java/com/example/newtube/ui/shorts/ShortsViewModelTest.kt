package com.example.newtube.ui.shorts

import com.example.newtube.model.Video
import com.example.newtube.repository.VideoRepository
import com.example.newtube.ui.shorts.ShortsViewModel
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.any
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.test.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class ShortsViewModelTest {
    private val repository: VideoRepository = mockk()
    private lateinit var viewModel: ShortsViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ShortsViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `shorts updates videos`() = runTest {
        val mockVideos = listOf(
            Video(
                id = "1",
                title = "Short Video 1",
                channelName = "Channel",
                thumbnailUrl = "https://example.com/thumb1.jpg",
                duration = 10L,
                views = 100L,
                uploadedAt = "2023-01-01T00:00:00Z",
                isLive = false
            )
        )

        coEvery { repository.getShorts(any()) } returns mockVideos

        // Init will trigger loadTrending which loads shorts
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mockVideos, viewModel.videos.value)
    }
}
