package com.example.newtube.ui.home

import com.example.newtube.innertube.ContentSourceUnavailableException
import com.example.newtube.model.AppSettings
import com.example.newtube.model.HomeCategory
import com.example.newtube.model.Video
import com.example.newtube.repository.FeedKey
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
class HomeViewModelTest {
    private val repository: VideoRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { settingsRepository.settings } returns flowOf(AppSettings())
        every { repository.observeFeed(any()) } returns flowOf(emptyList())
        coEvery { repository.refreshFeed(any()) } returns Result.success(emptyList())
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
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.ALL)) } returns Result.success(videos)

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(HomeCategory.ALL, viewModel.selectedCategory.value)
            assertEquals(UiState.Success(videos), viewModel.uiState.value)
        }

    @Test
    fun `network failure is exposed as an error with a retry message`() =
        runTest {
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.ALL)) } returns
                Result.failure(IOException("offline"))

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is UiState.Error)
            assertEquals("Network error. Check your connection", (state as UiState.Error).message)
        }

    @Test
    fun `retry reloads the feed`() =
        runTest {
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.ALL)) } returns
                Result.failure(IOException("offline"))

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value is UiState.Error)

            val videos = listOf(video("2"))
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.ALL)) } returns Result.success(videos)
            viewModel.load()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(videos), viewModel.uiState.value)
        }

    @Test
    fun `selecting a category loads it and caches the result`() =
        runTest {
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.ALL)) } returns
                Result.success(listOf(video("all")))
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.GAMING)) } returns
                Result.success(listOf(video("game")))

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.selectCategory(HomeCategory.GAMING)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(UiState.Success(listOf(video("game"))), viewModel.uiState.value)

            viewModel.selectCategory(HomeCategory.ALL)
            viewModel.selectCategory(HomeCategory.GAMING)
            testDispatcher.scheduler.advanceUntilIdle()

            // Every switch back to a cached chip revalidates that same key.
            coVerify(exactly = 2) { repository.refreshFeed(FeedKey.Home(HomeCategory.GAMING)) }
        }

    @Test
    fun `switching back to a cached category shows it immediately then revalidates`() =
        runTest {
            val cached = listOf(video("all-cached"))
            val refreshed = listOf(video("all-fresh"))
            every { repository.observeFeed(FeedKey.Home(HomeCategory.ALL)) } returns flowOf(cached)
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.ALL)) } returnsMany
                listOf(Result.success(cached), Result.success(refreshed))
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.GAMING)) } returns
                Result.success(listOf(video("game")))

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(UiState.Success(cached), viewModel.uiState.value)

            viewModel.selectCategory(HomeCategory.GAMING)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.selectCategory(HomeCategory.ALL)

            assertEquals(UiState.Success(cached), viewModel.uiState.value)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(UiState.Success(refreshed), viewModel.uiState.value)
            coVerify(exactly = 2) { repository.refreshFeed(FeedKey.Home(HomeCategory.ALL)) }
        }

    @Test
    fun `a late response from a cached category cannot publish under a new chip`() =
        runTest {
            val gameFeed = CompletableDeferred<Result<List<Video>>>()
            every { repository.observeFeed(FeedKey.Home(HomeCategory.GAMING)) } returns
                flowOf(listOf(video("game-cached")))
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.GAMING)) } coAnswers { gameFeed.await() }
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.ALL)) } returnsMany
                listOf(Result.success(listOf(video("all-1"))), Result.success(listOf(video("all-2"))))

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.selectCategory(HomeCategory.GAMING)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(UiState.Success(listOf(video("game-cached"))), viewModel.uiState.value)

            viewModel.selectCategory(HomeCategory.ALL)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(UiState.Success(listOf(video("all-2"))), viewModel.uiState.value)

            gameFeed.complete(Result.success(listOf(video("stale-game"))))
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(UiState.Success(listOf(video("all-2"))), viewModel.uiState.value)
        }

    @Test
    fun `a source failure on a cached category raises the banner and a retry clears it`() =
        runTest {
            val cached = listOf(video("cached"))
            every { repository.observeFeed(FeedKey.Home(HomeCategory.ALL)) } returns flowOf(cached)
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.ALL)) } returnsMany
                listOf(
                    Result.failure(ContentSourceUnavailableException("blocked")),
                    Result.success(listOf(video("fresh"))),
                )

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(UiState.Success(cached), viewModel.uiState.value)
            assertTrue(viewModel.sourceUnavailable.value)

            viewModel.refresh()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(UiState.Success(listOf(video("fresh"))), viewModel.uiState.value)
            assertFalse(viewModel.sourceUnavailable.value)
        }

    @Test
    fun `refresh re-fetches the selected category`() =
        runTest {
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.ALL)) } returns
                Result.success(listOf(video("all")))
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.NEWS)) } returns
                Result.success(listOf(video("news")))

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.selectCategory(HomeCategory.NEWS)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.refresh()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 2) { repository.refreshFeed(FeedKey.Home(HomeCategory.NEWS)) }
        }

    @Test
    fun `a late response from a previous category is ignored`() =
        runTest {
            val never = CompletableDeferred<Result<List<Video>>>()
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.ALL)) } coAnswers { never.await() }
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.SPORTS)) } returns
                Result.success(listOf(video("sport")))

            val viewModel = HomeViewModel(repository, settingsRepository)
            viewModel.selectCategory(HomeCategory.SPORTS)
            testDispatcher.scheduler.advanceUntilIdle()
            never.complete(Result.success(listOf(video("stale"))))
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(listOf(video("sport"))), viewModel.uiState.value)
        }

    @Test
    fun `shorts shelf loads without blocking the feed`() =
        runTest {
            val shelf = CompletableDeferred<List<Video>>()
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.ALL)) } returns
                Result.success(listOf(video("1")))
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
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.ALL)) } returns
                Result.success(listOf(video("1")))
            coEvery { repository.getShortsShelf() } throws IOException("offline")

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(listOf(video("1"))), viewModel.uiState.value)
            assertTrue(viewModel.shortsShelf.value.isEmpty())
        }

    @Test
    fun `refresh reloads the shelf too`() =
        runTest {
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.ALL)) } returns
                Result.success(listOf(video("1")))
            coEvery { repository.getShortsShelf() } returnsMany
                listOf(listOf(video("s1")), listOf(video("s2")))

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf("s1"), viewModel.shortsShelf.value.map { it.id })

            viewModel.refresh()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf("s2"), viewModel.shortsShelf.value.map { it.id })
        }

    @Test
    fun `a stale fetch cannot leave the refresh indicator stuck on`() =
        runTest {
            val gate = CompletableDeferred<Result<List<Video>>>()
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.ALL)) } coAnswers { gate.await() }
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.GAMING)) } returns
                Result.success(listOf(video("game")))

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(viewModel.isRefreshing.value)

            viewModel.selectCategory(HomeCategory.GAMING)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(UiState.Success(listOf(video("game"))), viewModel.uiState.value)

            gate.complete(Result.success(listOf(video("stale"))))
            testDispatcher.scheduler.advanceUntilIdle()
            assertFalse(viewModel.isRefreshing.value)
            assertEquals(UiState.Success(listOf(video("game"))), viewModel.uiState.value)
        }

    @Test
    fun refreshFailureKeepsCachedFeed() =
        runTest {
            val cached = listOf(video("cached"))
            val cachedFeed = MutableStateFlow(cached)
            every { repository.observeFeed(FeedKey.Home(HomeCategory.ALL)) } returns cachedFeed
            val refresh = CompletableDeferred<Result<List<Video>>>()
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.ALL)) } coAnswers { refresh.await() }

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(cached), viewModel.uiState.value)
            assertTrue(viewModel.isRefreshing.value)

            refresh.complete(Result.failure(IOException("offline")))
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(cached), viewModel.uiState.value)
            assertFalse(viewModel.isRefreshing.value)
        }

    @Test
    fun `switching to a category without a cache drops the previous feed on failure`() =
        runTest {
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.ALL)) } returns
                Result.success(listOf(video("all")))
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.GAMING)) } returns
                Result.failure(IOException("offline"))

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(UiState.Success(listOf(video("all"))), viewModel.uiState.value)

            viewModel.selectCategory(HomeCategory.GAMING)
            assertTrue(viewModel.uiState.value is UiState.Loading)
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value is UiState.Error)
            assertFalse(viewModel.isRefreshing.value)
        }

    @Test
    fun `a failing cache clear still reloads the feed`() =
        runTest {
            every { repository.observeFeed(FeedKey.Home(HomeCategory.ALL)) } returns
                flowOf(listOf(video("stale")))
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.ALL)) } returns
                Result.success(listOf(video("fresh")))
            coEvery { repository.clearFeedCaches() } throws IOException("offline")

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onContentPreferencesChanged()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(listOf(video("fresh"))), viewModel.uiState.value)
            assertFalse(viewModel.isRefreshing.value)
        }

    @Test
    fun `a pre-clear cached emission cannot repopulate the feed`() =
        runTest {
            val cachedFeed = MutableStateFlow(listOf(video("stale")))
            val refresh = CompletableDeferred<Result<List<Video>>>()
            every { repository.observeFeed(FeedKey.Home(HomeCategory.ALL)) } returns cachedFeed
            coEvery { repository.refreshFeed(FeedKey.Home(HomeCategory.ALL)) } coAnswers { refresh.await() }
            coEvery { repository.clearFeedCaches() } coAnswers { cachedFeed.value = emptyList() }

            val viewModel = HomeViewModel(repository, settingsRepository)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(UiState.Success(listOf(video("stale"))), viewModel.uiState.value)

            viewModel.onContentPreferencesChanged()
            refresh.complete(Result.success(listOf(video("fresh"))))
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(listOf(video("fresh"))), viewModel.uiState.value)
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
