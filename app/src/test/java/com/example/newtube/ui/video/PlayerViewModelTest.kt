package com.example.newtube.ui.video

import com.example.newtube.repository.VideoRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.test.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val repository: VideoRepository = mockk()
    private lateinit var viewModel: PlayerViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = PlayerViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadStream updates state streamUrl`() = runTest {
        val videoId = "video123"
        val expectedUrl = "https://example.com/stream/video123"

        coEvery { repository.getStreamUrl(videoId) } returns expectedUrl

        viewModel.loadStream(videoId)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(expectedUrl, viewModel.state.value.streamUrl)
    }
}
