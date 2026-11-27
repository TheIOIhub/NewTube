package com.example.newtube.ui.shorts

import com.example.newtube.model.AppSettings
import com.example.newtube.model.PlaybackSource
import com.example.newtube.model.Video
import com.example.newtube.model.VideoDetails
import com.example.newtube.repository.FeedKey
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
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

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
        every { repository.observeFeed(any()) } returns flowOf(emptyList())
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
    fun `generic cached feed is used when the shorts cache is empty`() =
        runTest {
            val cached = video("generic")
            val firstPage = CompletableDeferred<List<Video>>()
            every { repository.observeFeed(FeedKey.Shorts) } returns flowOf(listOf(cached))
            coEvery { repository.getShorts(1) } coAnswers { firstPage.await() }

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiState.Success(listOf(cached)), viewModel.uiState.value)

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

    @Test
    fun prefetchWindowIsBounded() =
        runTest {
            val feed = (0..9).map { video("v$it") }
            coEvery { repository.getShorts(1) } returns feed
            coEvery { repository.getShorts(2) } returns emptyList()
            coEvery { repository.getVideoDetails(any(), any()) } returns details(video("resolved"))

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onPageChanged(4)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(setOf("v3", "v4", "v5", "v6"), viewModel.playbackSources.value.keys)
            coVerify(exactly = 4) { repository.getVideoDetails(any(), any()) }
        }

    @Test
    fun prefetchNeverExceedsTwoConcurrentRequests() =
        runTest {
            val feed = (0..9).map { video("v$it") }
            coEvery { repository.getShorts(1) } returns feed
            coEvery { repository.getShorts(2) } returns emptyList()
            val gate = CompletableDeferred<Unit>()
            val active = AtomicInteger(0)
            val peak = AtomicInteger(0)
            coEvery { repository.getVideoDetails(any(), any()) } coAnswers {
                val current = active.incrementAndGet()
                peak.updateAndGet { previous -> maxOf(previous, current) }
                gate.await()
                active.decrementAndGet()
                details(video("resolved"))
            }

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onPageChanged(4)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(2, peak.get())

            gate.complete(Unit)
            testDispatcher.scheduler.advanceUntilIdle()
        }

    @Test
    fun repeatedPageChangeDoesNotRefetch() =
        runTest {
            val feed = (0..9).map { video("v$it") }
            coEvery { repository.getShorts(1) } returns feed
            coEvery { repository.getShorts(2) } returns emptyList()
            coEvery { repository.getVideoDetails(any(), any()) } returns details(video("resolved"))

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onPageChanged(4)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.ensurePlayback("v4")
            viewModel.onPageChanged(4)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 4) { repository.getVideoDetails(any(), any()) }
        }

    @Test
    fun pageChangeTracksActiveClipForRefresh() =
        runTest {
            val cached = (0..9).map { video("v$it") }
            val fresh = (0..4).map { video("x$it") } + cached.drop(4)
            var shortsCalls = 0
            every { shortsCache.observeFeed() } returns flowOf(cached)
            // Initial load shows v0..v9; the manual refresh rearranges the feed.
            coEvery { repository.getShorts(1) } coAnswers {
                if (shortsCalls++ == 0) cached else fresh
            }
            coEvery { repository.getShorts(2) } returns emptyList()
            coEvery { repository.getVideoDetails(any(), any()) } returns details(video("resolved"))

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onPageSettled(cached[0])
            viewModel.onPageChanged(4)
            viewModel.reload()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("v4", viewModel.requestedStartId.value)
        }

    @Test
    fun refreshDoesNotOverwritePendingStartRequest() =
        runTest {
            val cached = listOf(video("v0"), video("v1"))
            every { shortsCache.observeFeed() } returns flowOf(cached)
            coEvery { repository.getShorts(1) } returns listOf(video("f0"), video("v1"))
            coEvery { repository.getShorts(2) } returns emptyList()
            coEvery { repository.getVideoById("deep") } returns video("deep")
            coEvery { repository.getVideoDetails(any(), any()) } returns details(video("resolved"))

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.startFrom("deep")
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.onPageSettled(video("v1"))
            viewModel.reload()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("deep", viewModel.requestedStartId.value)
        }

    @Test
    fun failedDeepLinkFetchDoesNotBlockFutureRefresh() =
        runTest {
            val cached = listOf(video("v0"), video("v1"))
            var shortsCalls = 0
            every { shortsCache.observeFeed() } returns flowOf(cached)
            coEvery { repository.getShorts(1) } coAnswers {
                if (shortsCalls++ == 0) cached else listOf(video("x0"), video("v0"))
            }
            coEvery { repository.getShorts(2) } returns emptyList()
            coEvery { repository.getVideoById("missing") } throws IOException("gone")
            coEvery { repository.getVideoDetails(any(), any()) } returns details(video("resolved"))

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.startFrom("missing")
            testDispatcher.scheduler.advanceUntilIdle()
            assertNull(viewModel.requestedStartId.value)

            viewModel.onPageSettled(video("v0"))
            viewModel.reload()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("v0", viewModel.requestedStartId.value)
        }

    @Test
    fun deepLinkUnresolvableDuringRefreshDoesNotBlockFutureRefresh() =
        runTest {
            val firstPage = CompletableDeferred<List<Video>>()
            var shortsCalls = 0
            every { shortsCache.observeFeed() } returns flowOf(emptyList())
            coEvery { repository.getShorts(1) } coAnswers {
                if (shortsCalls++ == 0) firstPage.await() else listOf(video("f0"), video("v0"))
            }
            coEvery { repository.getShorts(2) } returns emptyList()
            coEvery { repository.getVideoById("deep") } throws IOException("gone")
            coEvery { repository.getVideoDetails(any(), any()) } returns details(video("resolved"))

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            viewModel.startFrom("deep") // feed is still Loading: no prepend job is launched
            firstPage.complete(listOf(video("v0")))
            testDispatcher.scheduler.advanceUntilIdle()
            assertNull(viewModel.requestedStartId.value)

            viewModel.onPageSettled(video("v0"))
            viewModel.reload()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("v0", viewModel.requestedStartId.value)
        }

    @Test
    fun prefetchFailureIsVisibleAndRetryable() =
        runTest {
            val feed = (0..9).map { video("v$it") }
            coEvery { repository.getShorts(1) } returns feed
            coEvery { repository.getShorts(2) } returns emptyList()
            coEvery { repository.getVideoDetails(any(), any()) } returns details(video("resolved"))
            var v4Calls = 0
            coEvery { repository.getVideoDetails("v4", any()) } coAnswers {
                if (v4Calls++ == 0) throw IOException("boom") else details(video("v4"))
            }

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onPageChanged(4)
            testDispatcher.scheduler.advanceUntilIdle()

            assertNull(viewModel.playbackSources.value["v4"])
            assertNotNull(viewModel.playbackErrors.value["v4"])
            assertEquals(setOf("v3", "v5", "v6"), viewModel.playbackSources.value.keys)

            viewModel.ensurePlayback("v4", force = true)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(details(video("v4")).playback, viewModel.playbackSources.value["v4"])
            assertNull(viewModel.playbackErrors.value["v4"])
        }

    @Test
    fun successfulPageEventClearsStalePrefetchError() =
        runTest {
            val feed = (0..9).map { video("v$it") }
            coEvery { repository.getShorts(1) } returns feed
            coEvery { repository.getShorts(2) } returns emptyList()
            coEvery { repository.getVideoDetails(any(), any()) } returns details(video("resolved"))
            var v4Calls = 0
            coEvery { repository.getVideoDetails("v4", any()) } coAnswers {
                if (v4Calls++ == 0) throw IOException("boom") else details(video("v4"))
            }

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onPageChanged(4)
            testDispatcher.scheduler.advanceUntilIdle()
            assertNotNull(viewModel.playbackErrors.value["v4"])

            // The next window still includes v4; its failed resolve was removed
            // from requestedSources, so it is retried and the error must clear.
            viewModel.onPageChanged(5)
            testDispatcher.scheduler.advanceUntilIdle()

            assertNotNull(viewModel.playbackSources.value["v4"])
            assertNull(viewModel.playbackErrors.value["v4"])
        }

    @Test
    fun `loadMorePersistsAppendedFeed`() =
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

            val onlyNew = listOf(video("12"))
            // Persisted to the cache; NOT a no-op and NOT a replaceFeed wipe.
            coVerify { shortsCache.appendFeed(onlyNew) }
            coVerify(exactly = 0) { shortsCache.replaceFeed(onlyNew) }
        }

    @Test
    fun `loadMorePersistsNoAppendedFeedWhenNothingNew`() =
        runTest {
            val initial = (1..10).map { video("$it") }
            coEvery { repository.getShorts(1) } returns initial
            // reload() reaches MIN_SHORTS on page 1, so loadMore starts at page 2
            coEvery { repository.getShorts(2) } returns listOf(initial.first()) // duplicate only
            coEvery { repository.getShorts(3) } returns listOf(initial.first())

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()
            val nextPageBefore = viewModel.uiState.value
            viewModel.loadMore()
            testDispatcher.scheduler.advanceUntilIdle()

            // no only-new items -> appendFeed not called with content; repo no-ops on empty
            coVerify(exactly = 0) { shortsCache.appendFeed(match { it.isNotEmpty() }) }
            assertEquals(nextPageBefore, viewModel.uiState.value)
        }

    @Test
    fun `playerErrorIsReportedAsPlaybackError`() =
        runTest {
            val feed = (0..9).map { video("v$it") }
            coEvery { repository.getShorts(1) } returns feed
            coEvery { repository.getShorts(2) } returns emptyList()

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.reportPlayerError("v4", "HTTP 403")

            assertEquals("HTTP 403", viewModel.playbackErrors.value["v4"])
        }

    @Test
    fun `reportPlayerErrorFallsBackToActiveClip`() =
        runTest {
            val feed = (0..9).map { video("v$it") }
            coEvery { repository.getShorts(1) } returns feed
            coEvery { repository.getShorts(2) } returns emptyList()

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.onPageSettled(feed[4])

            viewModel.reportPlayerError(videoId = null, message = null)

            assertEquals("Playback failed", viewModel.playbackErrors.value["v4"])
        }

    @Test
    fun `reportPlayerErrorUsesBlankFallbackMessage`() =
        runTest {
            coEvery { repository.getShorts(1) } returns listOf(video("v0"))
            coEvery { repository.getShorts(2) } returns emptyList()

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.reportPlayerError("v0", "   ")

            assertEquals("Playback failed", viewModel.playbackErrors.value["v0"])
        }

    @Test
    fun `retryPlaybackRequestsRePrepare`() =
        runTest {
            val feed = (0..9).map { video("v$it") }
            coEvery { repository.getShorts(1) } returns feed
            coEvery { repository.getShorts(2) } returns emptyList()
            coEvery { repository.getVideoDetails(any(), any()) } returns details(video("resolved"))

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.onPageChanged(4)
            testDispatcher.scheduler.advanceUntilIdle()

            val sourceBeforeRetry = viewModel.playbackSources.value["v4"]
            assertNotNull(sourceBeforeRetry)
            viewModel.reportPlayerError("v4", "boom")
            val nonceBefore = viewModel.retryNonce.value

            viewModel.retryPlayback("v4")
            testDispatcher.scheduler.advanceUntilIdle()

            // error cleared; re-prepare token bumped (drives controller.play
            // even though the re-resolved source is equals-identical); source
            // restored; a fresh resolve was attempted. One prepare path intact.
            assertNull(viewModel.playbackErrors.value["v4"])
            assertEquals(nonceBefore + 1, viewModel.retryNonce.value)
            assertNotNull(viewModel.playbackSources.value["v4"])
            coVerify(atLeast = 1) { repository.getVideoDetails("v4", any()) }
        }

    @Test
    fun `retryResolveFailureRemovesStaleSource`() =
        runTest {
            val feed = (0..9).map { video("v$it") }
            coEvery { repository.getShorts(1) } returns feed
            coEvery { repository.getShorts(2) } returns emptyList()
            var v4Calls = 0
            coEvery { repository.getVideoDetails("v4", any()) } coAnswers {
                if (v4Calls++ == 0) details(video("v4")) else throw IOException("gone")
            }
            coEvery { repository.getVideoDetails(match { it != "v4" }, any()) } returns details(video("other"))

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.onPageChanged(4)
            testDispatcher.scheduler.advanceUntilIdle()
            assertNotNull(viewModel.playbackSources.value["v4"])

            viewModel.retryPlayback("v4")
            testDispatcher.scheduler.advanceUntilIdle()

            // stale `_playbackSources[v4]` removed on resolve failure; error shown
            assertNull(viewModel.playbackSources.value["v4"])
            assertNotNull(viewModel.playbackErrors.value["v4"])
        }

    @Test
    fun `scrollToHeadWhenCurrentClipGoneAfterRefresh`() =
        runTest {
            val a = video("a")
            val b = video("b")
            val c = video("c")
            every { shortsCache.observeFeed() } returns flowOf(listOf(a, b, c))
            coEvery { repository.getShorts(1) } returns listOf(video("x"), video("y"))
            coEvery { repository.getShorts(2) } returns emptyList()

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()
            val before = viewModel.scrollToHead.value
            viewModel.onPageSettled(b)
            viewModel.reload()
            testDispatcher.scheduler.advanceUntilIdle()

            // current clip no longer in the refreshed feed -> explicit go-to-head
            assertTrue(viewModel.scrollToHead.value > before)
            assertNull(viewModel.requestedStartId.value)
        }

    @Test
    fun `scrollToHeadNotEmittedWhenCurrentClipSurvives`() =
        runTest {
            val a = video("a")
            val b = video("b")
            val c = video("c")
            every { shortsCache.observeFeed() } returns flowOf(listOf(a, b, c))
            coEvery { repository.getShorts(1) } returns listOf(video("x"), b, video("y"))
            coEvery { repository.getShorts(2) } returns emptyList()

            val viewModel = ShortsViewModel(repository, settings, history, shortsCache)
            testDispatcher.scheduler.advanceUntilIdle()
            val before = viewModel.scrollToHead.value
            viewModel.onPageSettled(b)
            viewModel.reload()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(before, viewModel.scrollToHead.value)
            assertEquals("b", viewModel.requestedStartId.value)
        }

    private fun details(video: Video) =
        VideoDetails(
            video = video,
            description = "",
            related = emptyList(),
            playback = PlaybackSource.Hls("https://example.com/${video.id}.m3u8"),
        )

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
