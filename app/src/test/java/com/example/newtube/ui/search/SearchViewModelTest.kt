package com.example.newtube.ui.search

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
class SearchViewModelTest {

    private val repository: VideoRepository = mockk()
    private lateinit var viewModel: SearchViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SearchViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `search updates results`() = runTest {
        val query = "kotlin"

        val mockVideos = listOf(
            Video(
                id = "1",
                title = "Kotlin for Beginners",
                channelName = "DevChannel",
                thumbnailUrl = "https://example.com/thumb1.jpg",
                duration = 300L,
                views = 1000L,
                uploadedAt = "2023-01-01T00:00:00Z",
                isLive = false
            )
        )

        coEvery { repository.search(query, any()) } returns mockVideos

        viewModel.search(query)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mockVideos, viewModel.results.value)
    }
}
