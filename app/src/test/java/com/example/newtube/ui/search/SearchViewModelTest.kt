package com.example.newtube.ui.search

import com.example.newtube.model.Video
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
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
    fun `search debounces the query and exposes results`() =
        runTest {
            val videos = listOf(video("1"))
            coEvery { repository.search("kotlin", 1) } returns videos

            val viewModel = SearchViewModel(repository)
            viewModel.onQueryChange("kotlin")
            viewModel.onQueryChange("kotlin")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(videos), viewModel.uiState.value)
        }

    @Test
    fun `blank query clears the results without a request`() =
        runTest {
            val viewModel = SearchViewModel(repository)

            viewModel.onQueryChange("   ")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(emptyList<Video>()), viewModel.uiState.value)
        }

    @Test
    fun `load more appends the next page`() =
        runTest {
            val first = video("1")
            val second = video("2")
            coEvery { repository.search("kotlin", 1) } returns listOf(first)
            coEvery { repository.search("kotlin", 2) } returns listOf(second)

            val viewModel = SearchViewModel(repository)
            viewModel.onQueryChange("kotlin")
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.loadMore()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(listOf(first, second)), viewModel.uiState.value)
        }

    private fun video(id: String) =
        Video(
            id = id,
            title = "Video $id",
            channelName = "Channel",
            channelId = "UC$id",
            thumbnailUrl = "https://example.com/$id.jpg",
            duration = 120L,
            views = 100L,
            uploadedAt = "1 day ago",
            isLive = false,
        )
}
