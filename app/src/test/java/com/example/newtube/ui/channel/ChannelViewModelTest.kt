package com.example.newtube.ui.channel

import com.example.newtube.model.Video
import com.example.newtube.repository.VideoRepository
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
class ChannelViewModelTest {

    private val repository: VideoRepository = mockk()
    private lateinit var viewModel: ChannelViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ChannelViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `channel updates videos`() {
        val channelId = "channel1"

        val mockVideos = listOf(
            Video(
                id = "1",
                title = "Channel Video 1",
                channelName = "Channel",
                thumbnailUrl = "https://example.com/thumb1.jpg",
                duration = 120L,
                views = 200L,
                uploadedAt = "2023-01-01T00:00:00Z",
                isLive = false
            )
        )

        coEvery { repository.getChannelVideos(channelId, any()) } returns mockVideos

        viewModel.loadChannelVideos(channelId)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mockVideos, viewModel.channelVideos.value)
    }
}
