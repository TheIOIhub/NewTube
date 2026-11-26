package com.example.newtube.ui.shorts

import com.example.newtube.model.AppSettings
import com.example.newtube.model.PlaybackSource
import com.example.newtube.model.Video
import com.example.newtube.model.VideoDetails
import com.example.newtube.repository.HistoryRepository
import com.example.newtube.repository.SettingsRepository
import com.example.newtube.repository.ShortsCacheRepository
import com.example.newtube.repository.VideoRepository
import com.example.newtube.ui.common.UiState
import io.mockk.coEvery
import io.mockk.coJustRun
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShortsViewModelTest {
    private val repository: VideoRepository = mockk()
    private val history: HistoryRepository = mockk(relaxed = true)
    private val settings: SettingsRepository = mockk()
    private val shortsCache: ShortsCacheRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { settings.settings } returns flowOf(AppSettings())
        every { shortsCache.observeFeed() } returns flowOf(emptyList())
        coJustRun { history.record(any()) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `short videos are collected across pages`() =
        runTest {
            val first = video("1")
            val second = video("2")
            coEvery { repository.getShorts(1) } returns listOf(first)
            coEvery { repository.getShorts(2) } returns listOf(second)
            coEvery { repository.getShorts(3) } returns emptyList()

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(listOf(first, second)), viewModel.uiState.value)
        }

    @Test
    fun `failure is exposed as an error`() =
        runTest {
            coEvery { repository.getShorts(any()) } throws IllegalStateException("boom")

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value is UiState.Error)
        }

    @Test
    fun `cached feed is published before network responds`() =
        runTest {
            val cached = video("cached")
            val firstPage = CompletableDeferred<List<Video>>()
            every { shortsCache.observeFeed() } returns flowOf(listOf(cached))
            coEvery { repository.getShorts(1) } coAnswers { firstPage.await() }

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(listOf(cached)), viewModel.uiState.value)
            assertTrue(viewModel.isRefreshing.value)

            firstPage.complete(emptyList())
            testDispatcher.scheduler.advanceUntilIdle()
        }

    @Test
    fun cachedFeedSurvivesFailedRefresh() =
        runTest {
            val cached = video("cached")
            every { shortsCache.observeFeed() } returns flowOf(listOf(cached))
            coEvery { repository.getShorts(any()) } throws IllegalStateException("boom")

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(cached), (viewModel.uiState.value as UiState.Success).data)
            assertFalse(viewModel.isRefreshing.value)
            assertNotNull(viewModel.refreshError.value)
        }

    @Test
    fun `successful refresh replaces cached feed`() =
        runTest {
            val cached = video("cached")
            val fresh = video("fresh")
            every { shortsCache.observeFeed() } returns flowOf(listOf(cached))
            coEvery { repository.getShorts(1) } returns listOf(fresh)
            coEvery { repository.getShorts(2) } returns emptyList()

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(listOf(fresh)), viewModel.uiState.value)
            assertFalse(viewModel.isRefreshing.value)
            assertNull(viewModel.refreshError.value)
            coVerify { shortsCache.replaceFeed(listOf(fresh)) }
        }

    @Test
    fun `refresh keeps current clip index when id survives`() =
        runTest {
            val a = video("a")
            val b = video("b")
            val c = video("c")
            val x = video("x")
            val y = video("y")
            every { shortsCache.observeFeed() } returns flowOf(listOf(a, b, c))
            coEvery { repository.getShorts(1) } returns listOf(x, b, y)
            coEvery { repository.getShorts(2) } returns emptyList()

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.onPageSettled(b)
            viewModel.reload()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(listOf(x, b, y)), viewModel.uiState.value)
            assertEquals("b", viewModel.requestedStartId.value)
        }

    @Test
    fun `refresh drops the start request when the current clip is gone`() =
        runTest {
            val a = video("a")
            val b = video("b")
            val c = video("c")
            every { shortsCache.observeFeed() } returns flowOf(listOf(a, b, c))
            coEvery { repository.getShorts(1) } returns listOf(video("x"), video("y"))
            coEvery { repository.getShorts(2) } returns emptyList()

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.onPageSettled(b)
            viewModel.reload()
            testDispatcher.scheduler.advanceUntilIdle()

            assertNull(viewModel.requestedStartId.value)
        }

    @Test
    fun `manual refresh does not clear cache`() =
        runTest {
            val cached = video("cached")
            every { shortsCache.observeFeed() } returns flowOf(listOf(cached))
            coEvery { repository.getShorts(any()) } throws IllegalStateException("boom")

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.reload()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(cached), (viewModel.uiState.value as UiState.Success).data)
            coVerify(exactly = 0) { shortsCache.replaceFeed(emptyList()) }
        }

    @Test
    fun `playback source is resolved once per short`() =
        runTest {
            val short = video("1")
            coEvery { repository.getShorts(1) } returns listOf(short)
            coEvery { repository.getShorts(2) } returns emptyList()
            coEvery { repository.getVideoDetails("1", 720) } returns
                VideoDetails(
                    video = short,
                    description = "",
                    related = emptyList(),
                    playback = PlaybackSource.Hls("https://example.com/manifest"),
                )

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.ensurePlayback("1")
            viewModel.ensurePlayback("1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                mapOf("1" to PlaybackSource.Hls("https://example.com/manifest")),
                viewModel.playbackSources.value,
            )
        }

    @Test
    fun `shorts use the saved maximum quality`() =
        runTest {
            every { settings.settings } returns flowOf(AppSettings(defaultQuality = 480))
            val short = video("1")
            coEvery { repository.getShorts(1) } returns listOf(short)
            coEvery { repository.getShorts(2) } returns emptyList()
            coEvery { repository.getVideoDetails("1", 480) } returns
                VideoDetails(
                    video = short,
                    description = "",
                    related = emptyList(),
                    playback = PlaybackSource.Progressive("https://example.com/480.mp4"),
                )

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.ensurePlayback("1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                mapOf("1" to PlaybackSource.Progressive("https://example.com/480.mp4")),
                viewModel.playbackSources.value,
            )
        }

    @Test
    fun `a reload that yields no clips is an error with retry`() =
        runTest {
            coEvery { repository.getShorts(any()) } returns emptyList()

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value is UiState.Error)
        }

    @Test
    fun `loadMore keeps the feed alive when a page only repeats known ids`() =
        runTest {
            val initial = (1..11).map { video("$it") }
            coEvery { repository.getShorts(1) } returns initial.take(4)
            coEvery { repository.getShorts(2) } returns initial.drop(4).take(4)
            coEvery { repository.getShorts(3) } returns initial.drop(8)
            // reload() stops after 3 pages / >= 10 clips, so loadMore starts at page 4
            coEvery { repository.getShorts(4) } returns listOf(initial[0]) // duplicate only
            coEvery { repository.getShorts(5) } returns listOf(video("12"))

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.loadMore()
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value as UiState.Success
            assertEquals(12, state.data.size)
            assertEquals("12", state.data.last().id)
        }

    @Test
    fun `startFrom prepends a clip that is missing from the feed`() =
        runTest {
            val feed = video("feed1")
            val requested = video("requested")
            coEvery { repository.getShorts(1) } returns listOf(feed)
            coEvery { repository.getShorts(2) } returns emptyList()
            coEvery { repository.getVideoById("requested") } returns requested

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.startFrom("requested")
            testDispatcher.scheduler.advanceUntilIdle()

            val data = (viewModel.uiState.value as UiState.Success).data
            assertEquals(listOf("requested", "feed1"), data.map { it.id })
        }

    @Test
    fun `startFrom does not fetch a clip that is already in the feed`() =
        runTest {
            coEvery { repository.getShorts(1) } returns listOf(video("feed1"))
            coEvery { repository.getShorts(2) } returns emptyList()

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.startFrom("feed1")
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) { repository.getVideoById(any()) }
        }

    @Test
    fun `startFrom while the feed is loading is applied after reload`() =
        runTest {
            val firstPage = CompletableDeferred<List<Video>>()
            coEvery { repository.getShorts(1) } coAnswers { firstPage.await() }
            coEvery { repository.getShorts(2) } returns emptyList()
            coEvery { repository.getVideoById("requested") } returns video("requested")

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            viewModel.startFrom("requested")
            firstPage.complete(listOf(video("feed1")))
            testDispatcher.scheduler.advanceUntilIdle()

            val data = (viewModel.uiState.value as UiState.Success).data
            assertEquals("requested", data.first().id)
        }

    @Test
    fun `consumeStartRequest stops a restored tab from jumping again`() =
        runTest {
            coEvery { repository.getShorts(1) } returns listOf(video("feed1"))
            coEvery { repository.getShorts(2) } returns emptyList()
            coEvery { repository.getVideoById("requested") } returns video("requested")

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.startFrom("requested")
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.consumeStartRequest()

            assertNull(viewModel.requestedStartId.value)

            coVerify(exactly = 1) { repository.getVideoById("requested") }
            viewModel.startFrom("requested")
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify(exactly = 1) { repository.getVideoById("requested") }
        }

    @Test
    fun `a settled short is recorded after three seconds`() =
        runTest {
            val short = video("1")
            coEvery { repository.getShorts(1) } returns listOf(short)
            coEvery { repository.getShorts(2) } returns emptyList()

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onPageSettled(short)
            testDispatcher.scheduler.advanceTimeBy(2_999)
            coVerify(exactly = 0) { history.record(any()) }

            testDispatcher.scheduler.advanceTimeBy(2)
            coVerify(exactly = 1) { history.record(short) }
        }

    @Test
    fun `swiping before three seconds cancels the pending record`() =
        runTest {
            val first = video("1")
            val second = video("2")
            coEvery { repository.getShorts(1) } returns listOf(first, second)
            coEvery { repository.getShorts(2) } returns emptyList()

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onPageSettled(first)
            testDispatcher.scheduler.advanceTimeBy(2_000)
            viewModel.onPageSettled(second)
            testDispatcher.scheduler.advanceTimeBy(2_000)
            coVerify(exactly = 0) { history.record(any()) }

            testDispatcher.scheduler.advanceTimeBy(1_001)
            coVerify(exactly = 1) { history.record(second) }
        }

    @Test
    fun `cancelPendingRecord drops a clipped watch when the tab is left`() =
        runTest {
            val short = video("1")
            coEvery { repository.getShorts(1) } returns listOf(short)
            coEvery { repository.getShorts(2) } returns emptyList()

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onPageSettled(short)
            testDispatcher.scheduler.advanceTimeBy(1_000)
            viewModel.cancelPendingRecord()
            testDispatcher.scheduler.advanceTimeBy(5_000)

            coVerify(exactly = 0) { history.record(any()) }
        }

    private fun video(id: String) =
        Video(
            id = id,
            title = "Short $id",
            channelName = "Channel",
            channelId = "UC$id",
            thumbnailUrl = "https://example.com/$id.jpg",
            duration = 30L,
            views = 100L,
            uploadedAt = "1 day ago",
            isLive = false,
        )
}
