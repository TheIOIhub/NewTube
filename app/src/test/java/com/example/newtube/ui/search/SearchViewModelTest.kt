package com.example.newtube.ui.search

import com.example.newtube.model.AppSettings
import com.example.newtube.model.Video
import com.example.newtube.repository.FeedKey
import com.example.newtube.repository.SettingsRepository
import com.example.newtube.repository.VideoRepository
import com.example.newtube.ui.common.UiState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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
class SearchViewModelTest {
    private val repository: VideoRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        io.mockk.every { settingsRepository.settings } returns flowOf(AppSettings())
        every { repository.observeFeed(any()) } returns flowOf(emptyList())
        coEvery { repository.refreshFeed(any()) } returns Result.success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `search debounces the query and exposes results`() =
        runTest {
            val videos = listOf(video("1"))
            coEvery { repository.refreshFeed(FeedKey.Search("kotlin", 1)) } returns Result.success(videos)

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
            coEvery { repository.refreshFeed(FeedKey.Search("kotlin", 1)) } returns
                Result.success(listOf(first))
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
            coEvery { repository.refreshFeed(any()) } returns Result.success(listOf(video("1")))
            val viewModel = SearchViewModel(repository, settingsRepository)

            viewModel.onQueryChange("kot")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(emptyList<String>(), viewModel.suggestions.value)
            assertEquals(UiState.Success(listOf(video("1"))), viewModel.uiState.value)
        }

    @Test
    fun `acceptSuggestion searches immediately`() =
        runTest {
            coEvery { repository.refreshFeed(FeedKey.Search("kotlin", 1)) } returns
                Result.success(listOf(video("1")))
            val viewModel = SearchViewModel(repository, settingsRepository)

            viewModel.acceptSuggestion("kotlin")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(listOf(video("1"))), viewModel.uiState.value)
            assertEquals(emptyList<String>(), viewModel.suggestions.value)
        }

    @Test
    fun `a query change shows the refresh indicator then clears it`() =
        runTest {
            val refresh = CompletableDeferred<Result<List<Video>>>()
            coEvery { repository.refreshFeed(FeedKey.Search("kotlin", 1)) } coAnswers { refresh.await() }
            val viewModel = SearchViewModel(repository, settingsRepository)
            val observed = mutableListOf<Boolean>()
            val collector = launch { viewModel.isRefreshing.take(3).toList(observed) }

            viewModel.onQueryChange("kotlin")
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(viewModel.isRefreshing.value)

            refresh.complete(Result.success(listOf(video("1"))))
            testDispatcher.scheduler.advanceUntilIdle()
            collector.join()

            assertEquals(listOf(false, true, false), observed)
        }

    @Test
    fun `cached results are emitted before the network result`() =
        runTest {
            val cached = listOf(video("cached"))
            val fresh = listOf(video("fresh"))
            every { repository.observeFeed(FeedKey.Search("kotlin", 1)) } returns flowOf(cached)
            val refresh = CompletableDeferred<Result<List<Video>>>()
            coEvery { repository.refreshFeed(FeedKey.Search("kotlin", 1)) } coAnswers { refresh.await() }

            val viewModel = SearchViewModel(repository, settingsRepository)
            viewModel.onQueryChange("kotlin")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(cached), viewModel.uiState.value)
            assertTrue(viewModel.isRefreshing.value)

            refresh.complete(Result.success(fresh))
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(fresh), viewModel.uiState.value)
            assertFalse(viewModel.isRefreshing.value)
        }

    @Test
    fun `clearing the query clears an in-flight refresh indicator`() =
        runTest {
            val refresh = CompletableDeferred<Result<List<Video>>>()
            coEvery { repository.refreshFeed(FeedKey.Search("kot", 1)) } coAnswers { refresh.await() }

            val viewModel = SearchViewModel(repository, settingsRepository)
            viewModel.onQueryChange("kot")
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(viewModel.isRefreshing.value)

            viewModel.onQueryChange(" ")
            testDispatcher.scheduler.advanceUntilIdle()
            assertFalse(viewModel.isRefreshing.value)

            refresh.complete(Result.success(listOf(video("stale"))))
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(UiState.Success(emptyList<Video>()), viewModel.uiState.value)
        }

    @Test
    fun `a failed refresh keeps the cached results`() =
        runTest {
            val cached = listOf(video("cached"))
            every { repository.observeFeed(FeedKey.Search("kotlin", 1)) } returns flowOf(cached)
            coEvery { repository.refreshFeed(FeedKey.Search("kotlin", 1)) } returns
                Result.failure(IOException("offline"))

            val viewModel = SearchViewModel(repository, settingsRepository)
            viewModel.onQueryChange("kotlin")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(cached), viewModel.uiState.value)
            assertFalse(viewModel.isRefreshing.value)
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
