package com.example.newtube.ui.home

import com.example.newtube.model.AppSettings
import com.example.newtube.model.HomeCategory
import com.example.newtube.model.Video
import com.example.newtube.repository.SettingsRepository
import com.example.newtube.repository.VideoRepository
import com.example.newtube.ui.common.UiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
class HomeViewModelTest {
    private val repository: VideoRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { settingsRepository.settings } returns flowOf(AppSettings())
        coEvery { repository.getShortsShelf() } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts on All and loads the aggregated feed`() =
        runTest {
            val videos = listOf(video("1"))
            coEvery { repository.getHomeFeed(HomeCategory.ALL) } returns videos

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(HomeCategory.ALL, viewModel.selectedCategory.value)
            assertEquals(UiState.Success(videos), viewModel.uiState.value)
        }

    @Test
    fun `network failure is exposed as an error with a retry message`() =
        runTest {
            coEvery { repository.getHomeFeed(HomeCategory.ALL) } throws IOException("offline")

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is UiState.Error)
            assertEquals("Network error. Check your connection", (state as UiState.Error).message)
        }

    @Test
    fun `retry reloads the feed`() =
        runTest {
            coEvery { repository.getHomeFeed(HomeCategory.ALL) } throws IOException("offline")

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value is UiState.Error)

            val videos = listOf(video("2"))
            coEvery { repository.getHomeFeed(HomeCategory.ALL) } returns videos
            viewModel.load()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(videos), viewModel.uiState.value)
        }

    @Test
    fun `selecting a category loads it and caches the result`() =
        runTest {
            coEvery { repository.getHomeFeed(HomeCategory.ALL) } returns listOf(video("all"))
            coEvery { repository.getHomeFeed(HomeCategory.GAMING) } returns listOf(video("game"))

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.selectCategory(HomeCategory.GAMING)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(UiState.Success(listOf(video("game"))), viewModel.uiState.value)

            viewModel.selectCategory(HomeCategory.ALL)
            viewModel.selectCategory(HomeCategory.GAMING)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { repository.getHomeFeed(HomeCategory.GAMING) }
        }

    @Test
    fun `refresh re-fetches the selected category`() =
        runTest {
            coEvery { repository.getHomeFeed(HomeCategory.ALL) } returns listOf(video("all"))
            coEvery { repository.getHomeFeed(HomeCategory.NEWS) } returns listOf(video("news"))

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.selectCategory(HomeCategory.NEWS)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.refresh()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 2) { repository.getHomeFeed(HomeCategory.NEWS) }
        }

    @Test
    fun `a late response from a previous category is ignored`() =
        runTest {
            val never = CompletableDeferred<List<Video>>()
            coEvery { repository.getHomeFeed(HomeCategory.ALL) } coAnswers { never.await() }
            coEvery { repository.getHomeFeed(HomeCategory.SPORTS) } returns listOf(video("sport"))

            val viewModel = HomeViewModel(repository, settingsRepository)
            viewModel.selectCategory(HomeCategory.SPORTS)
            testDispatcher.scheduler.advanceUntilIdle()
            never.complete(listOf(video("stale")))
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(listOf(video("sport"))), viewModel.uiState.value)
        }

    @Test
    fun `shorts shelf loads without blocking the feed`() =
        runTest {
            val shelf = CompletableDeferred<List<Video>>()
            coEvery { repository.getHomeFeed(HomeCategory.ALL) } returns listOf(video("1"))
            coEvery { repository.getShortsShelf() } coAnswers { shelf.await() }

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(listOf(video("1"))), viewModel.uiState.value)
            assertTrue(viewModel.shortsShelf.value.isEmpty())

            shelf.complete(listOf(video("s1")))
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf("s1"), viewModel.shortsShelf.value.map { it.id })
        }

    @Test
    fun `a shelf failure is silent and keeps an empty shelf`() =
        runTest {
            coEvery { repository.getHomeFeed(HomeCategory.ALL) } returns listOf(video("1"))
            coEvery { repository.getShortsShelf() } throws IOException("offline")

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(listOf(video("1"))), viewModel.uiState.value)
            assertTrue(viewModel.shortsShelf.value.isEmpty())
        }

    @Test
    fun `refresh reloads the shelf too`() =
        runTest {
            coEvery { repository.getHomeFeed(HomeCategory.ALL) } returns listOf(video("1"))
            coEvery { repository.getShortsShelf() } returnsMany
                listOf(listOf(video("s1")), listOf(video("s2")))

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf("s1"), viewModel.shortsShelf.value.map { it.id })

            viewModel.refresh()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf("s2"), viewModel.shortsShelf.value.map { it.id })
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
