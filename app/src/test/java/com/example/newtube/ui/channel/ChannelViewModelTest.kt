package com.example.newtube.ui.channel

import com.example.newtube.model.Channel
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

        val mockChannel = Channel(
            id = "1",
            name = "Test Channel",
            avatarUrl = "https://example.com/avatar.png",
            subscriberCount = 1000L,
            totalVideos = 10,
            description = "Test description"
        )

        coEvery { repository.getChannelDetails(channelId) } returns mockChannel

        viewModel.loadChannelDetails(channelId)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mockChannel, viewModel.channelState.value)
    }
}
