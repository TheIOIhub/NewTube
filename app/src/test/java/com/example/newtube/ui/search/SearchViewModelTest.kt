package com.example.newtube.ui.search

import com.example.newtube.model.AppSettings
import com.example.newtube.model.Video
import com.example.newtube.repository.SettingsRepository
import com.example.newtube.repository.VideoRepository
import com.example.newtube.ui.common.UiState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val repository: VideoRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        io.mockk.every { settingsRepository.settings } returns flowOf(AppSettings())
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

            val viewModel = SearchViewModel(repository, settingsRepository)
            viewModel.onQueryChange("kotlin")
            viewModel.onQueryChange("kotlin")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(videos), viewModel.uiState.value)
        }

    @Test
    fun `blank query clears the results without a request`() =
        runTest {
            val viewModel = SearchViewModel(repository, settingsRepository)

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

            val viewModel = SearchViewModel(repository, settingsRepository)
            viewModel.onQueryChange("kotlin")
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.loadMore()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(listOf(first, second)), viewModel.uiState.value)
        }

    @Test
    fun `suggestions are mapped and cleared on blank query`() =
        runTest {
            coEvery { repository.searchSuggestions("kot") } returns listOf("kotlin", "kotlin coroutines")
            coEvery { repository.search(any(), any()) } returns emptyList()
            val viewModel = SearchViewModel(repository, settingsRepository)

            viewModel.onQueryChange("kot")
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf("kotlin", "kotlin coroutines"), viewModel.suggestions.value)

            viewModel.onQueryChange("   ")
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(emptyList<String>(), viewModel.suggestions.value)
        }

    @Test
    fun `suggestion endpoint failure leaves results intact`() =
        runTest {
            coEvery { repository.searchSuggestions(any()) } throws IOException("boom")
            coEvery { repository.search(any(), any()) } returns listOf(video("1"))
            val viewModel = SearchViewModel(repository, settingsRepository)

            viewModel.onQueryChange("kot")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(emptyList<String>(), viewModel.suggestions.value)
            assertEquals(UiState.Success(listOf(video("1"))), viewModel.uiState.value)
        }

    @Test
    fun `acceptSuggestion searches immediately`() =
        runTest {
            coEvery { repository.search("kotlin", 1) } returns listOf(video("1"))
            val viewModel = SearchViewModel(repository, settingsRepository)

            viewModel.acceptSuggestion("kotlin")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(listOf(video("1"))), viewModel.uiState.value)
            assertEquals(emptyList<String>(), viewModel.suggestions.value)
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
