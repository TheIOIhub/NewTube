package com.example.newtube.repository

import com.example.newtube.database.ChannelDao
import com.example.newtube.database.ChannelEntity
import com.example.newtube.database.VideoDao
import com.example.newtube.database.VideoEntity
import com.example.newtube.implementation.FeedCacheRepository
import com.example.newtube.implementation.VideoRepositoryImpl
import com.example.newtube.innertube.YouTubeContentClient
import com.example.newtube.model.AccountState
import com.example.newtube.model.AppSettings
import com.example.newtube.model.HomeCategory
import com.example.newtube.model.PlaybackSource
import com.example.newtube.model.WatchHistoryEntry
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

private const val HOME_SEARCH_PARAMS = "EgIYAw=="

class VideoRepositoryImplTest {
    private lateinit var repository: VideoRepositoryImpl
    private val contentClient: YouTubeContentClient = mockk()
    private val dao: VideoDao = mockk()
    private val channelDao: ChannelDao = mockk()
    private val shortsCache: ShortsCacheRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val historyRepository: HistoryRepository = mockk(relaxed = true)
    private val accountRepository: AccountRepository = mockk(relaxed = true)
    private val feedCache: FeedCacheRepository = mockk(relaxed = true)
    private val settingsFlow = MutableStateFlow(AppSettings())

    @Before
    fun setUp() {
        repository =
            VideoRepositoryImpl(
                contentClient,
                dao,
                channelDao,
                shortsCache,
                settingsRepository,
                historyRepository,
                accountRepository,
                feedCache,
            )
        settingsFlow.value = AppSettings()
        coEvery { settingsRepository.settings } returns settingsFlow
        coEvery { historyRepository.observeLocal(any()) } returns flowOf(emptyList())
        coEvery { accountRepository.state } returns MutableStateFlow(AccountState.SignedOut)
        coEvery { shortsCache.knownChannelIds() } returns emptySet()
        coJustRun { shortsCache.rememberChannelIds(any()) }
        coJustRun { shortsCache.clearKnownChannelIds() }
        coJustRun { shortsCache.clearFeedCache() }
        coJustRun { dao.insertAll(any()) }
        coEvery { channelDao.getChannelById(any()) } returns null
        coJustRun { channelDao.insert(any()) }
        coEvery { contentClient.browse(any(), any()) } returns feedJson()
        coEvery { contentClient.search(any(), any(), any()) } returns feedJson()
        coEvery { contentClient.player(any()) } returns playerJson()
        coEvery { contentClient.next(any()) } returns commentsJson()
        coEvery { contentClient.suggestions(any()) } returns emptyList()
    }

    @Test
    fun `getHomeFeed ALL uses a search feed because signed-out home browse is empty`() =
        runBlocking {
            coEvery { contentClient.search("популярні відео", HOME_SEARCH_PARAMS, null) } returns
                feedJson(FeedItem("1"), FeedItem("2"), FeedItem("2"))

            val feed = repository.getHomeFeed(HomeCategory.ALL)

            assertEquals(listOf("1", "2"), feed.map { it.id })
            coVerify(exactly = 1) { contentClient.search("популярні відео", HOME_SEARCH_PARAMS, null) }
            coVerify(exactly = 0) { contentClient.browse(any(), any()) }
        }

    @Test
    fun `getHomeFeed drops short clips so Shorts stay in their own shelf and tab`() =
        runBlocking {
            coEvery { contentClient.search("популярні відео", HOME_SEARCH_PARAMS, null) } returns
                feedJson(
                    FeedItem("short", seconds = 30),
                    FeedItem("reel", seconds = 0),
                    FeedItem("long", seconds = 300),
                    FeedItem("live", seconds = 30, live = true),
                )

            val feed = repository.getHomeFeed(HomeCategory.ALL)

            assertEquals(setOf("long", "live"), feed.map { it.id }.toSet())
        }

    @Test
    fun `getTrending maps feed metadata and writes it through to Room`() =
        runBlocking {
            coEvery { contentClient.search(any(), any(), any()) } returns
                feedJson(FeedItem("1", title = "Test Video", seconds = 300))

            val result = repository.getTrending()

            assertEquals(1, result.size)
            assertEquals("1", result[0].id)
            assertEquals("Test Video", result[0].title)
            assertEquals("UC1", result[0].channelId)
            assertEquals("https://example.invalid/1.jpg", result[0].thumbnailUrl)
            assertEquals(300L, result[0].duration)
            coVerify { dao.insertAll(any()) }
        }

    @Test
    fun `getShorts uses one shorts search and never an obsolete shelf browse`() =
        runBlocking {
            coEvery { contentClient.search("#shorts", null, null) } returns
                feedJson(
                    FeedItem("short", seconds = 30),
                    FeedItem("reel", seconds = 0),
                    FeedItem("long", seconds = 181),
                    FeedItem("live", seconds = 30, live = true),
                )

            val shorts = repository.getShorts(1)

            assertEquals(setOf("short", "reel"), shorts.map { it.id }.toSet())
            coVerify(exactly = 1) { contentClient.search("#shorts", null, null) }
            coVerify(exactly = 0) { contentClient.browse("FEshorts", any()) }
        }

    @Test
    fun `getShortsShelf keeps short reels and drops long or live clips`() =
        runBlocking {
            coEvery { contentClient.search("#shorts", null, null) } returns
                feedJson(
                    FeedItem("short", seconds = 30),
                    FeedItem("duplicate", seconds = 30),
                    FeedItem("duplicate", seconds = 30),
                    FeedItem("long", seconds = 61),
                    FeedItem("zero", seconds = 0),
                    FeedItem("live", seconds = 30, live = true),
                )

            val shelf = repository.getShortsShelf(limit = 5)

            assertEquals(setOf("short", "duplicate", "zero"), shelf.map { it.id }.toSet())
            coVerify(exactly = 1) { contentClient.search("#shorts", null, null) }
        }

    @Test
    fun `clearFeedCaches clears the local shorts state`() =
        runBlocking {
            repository.clearFeedCaches()

            coVerify { shortsCache.clearFeedCache() }
            coVerify { shortsCache.clearKnownChannelIds() }
            coVerify { feedCache.clearAll() }
        }

    @Test
    fun `observeFeed parses cached JSON and emits empty when absent`() =
        runBlocking {
            every { feedCache.observe("home:ALL") } returns flowOf(cachedFeedJson(FeedItem("cached")))
            every { feedCache.observe("shorts") } returns flowOf(null)

            assertEquals(listOf("cached"), repository.observeFeed(FeedKey.Home(HomeCategory.ALL)).first().map { it.id })
            assertTrue(repository.observeFeed(FeedKey.Shorts).first().isEmpty())
        }

    @Test
    fun `refreshFeed stores the raw response and returns parsed videos`() =
        runBlocking {
            val json = feedJson(FeedItem("fresh"))
            coEvery { contentClient.search("популярні відео", HOME_SEARCH_PARAMS, null) } returns json
            coJustRun { feedCache.put(any(), any()) }

            val result = repository.refreshFeed(FeedKey.Home(HomeCategory.ALL))

            assertEquals(listOf("fresh"), result.getOrThrow().map { it.id })
            coVerify(exactly = 1) { feedCache.put(any(), any()) }
            coVerify(exactly = 0) { feedCache.put("home:ALL", json) }
        }

    @Test
    fun `refreshFeed with no search continuation keeps the cached page`() =
        runBlocking {
            coEvery { contentClient.search("q", null, null) } returns feedJson(FeedItem("page1"))

            repository.refreshFeed(FeedKey.Search("q", 1))
            assertTrue(repository.refreshFeed(FeedKey.Search("q", 2)).getOrThrow().isEmpty())

            coVerify(exactly = 1) { feedCache.put("search:q:1", any()) }
            coVerify(exactly = 0) { feedCache.put("search:q:2", any()) }
        }

    @Test
    fun `refreshFeed failure keeps the cached feed untouched`() =
        runBlocking {
            coEvery { contentClient.search("#shorts", null, null) } throws IOException("offline")

            assertTrue(repository.refreshFeed(FeedKey.Shorts).isFailure)
            coVerify(exactly = 0) { feedCache.put(any(), any()) }
        }

    @Test
    fun `refreshFeed with an unusable response keeps the cached feed untouched`() =
        runBlocking {
            coEvery { contentClient.search("популярні відео", HOME_SEARCH_PARAMS, null) } returns "<html>bot check</html>"

            assertTrue(repository.refreshFeed(FeedKey.Home(HomeCategory.ALL)).getOrThrow().isEmpty())
            coVerify(exactly = 0) { feedCache.put(any(), any()) }
        }

    @Test
    fun `search cursors are isolated per query`() =
        runBlocking {
            coEvery { contentClient.search("first", null, null) } returns
                feedJson(FeedItem("a1"), continuation = "first-token")
            coEvery { contentClient.search("other", null, null) } returns
                feedJson(FeedItem("b1"), continuation = "other-token")
            coEvery { contentClient.search("first", null, "first-token") } returns
                feedJson(FeedItem("a2"))
            coEvery { contentClient.search("other", null, "other-token") } returns
                feedJson(FeedItem("b2"))

            assertEquals(listOf("a1"), repository.search("first", 1).map { it.id })
            assertEquals(listOf("b1"), repository.search("other", 1).map { it.id })
            assertEquals(listOf("a2"), repository.search("first", 2).map { it.id })
            assertEquals(listOf("b2"), repository.search("other", 2).map { it.id })

            coVerify { contentClient.search("first", null, "first-token") }
            coVerify { contentClient.search("other", null, "other-token") }
        }

    @Test
    fun `getForYouFeed ranks a watched Ukrainian channel first`() =
        runBlocking {
            coEvery { contentClient.search(any(), any(), any()) } returns forYouJson()
            coEvery { historyRepository.observeLocal(500) } returns flowOf(listOf(watchedUkrainian()))

            assertEquals("ua", repository.getForYouFeed().first().id)
        }

    @Test
    fun `refreshFeed Home FOR_YOU keeps the same ranked order as getForYouFeed`() =
        runBlocking {
            coEvery { contentClient.search(any(), any(), any()) } returns forYouJson()
            coEvery { historyRepository.observeLocal(500) } returns flowOf(listOf(watchedUkrainian()))

            val refreshed = repository.refreshFeed(FeedKey.Home(HomeCategory.FOR_YOU)).getOrThrow()

            assertEquals("ua", refreshed.first().id)
            coVerify { dao.insertAll(any()) }
        }

    @Test
    fun `observeFeed Home FOR_YOU ranks cached videos too`() =
        runBlocking {
            every { feedCache.observe("home:FOR_YOU") } returns
                flowOf(
                    cachedFeedJson(
                        FeedItem("us", title = "Breaking news", channelId = "UCus"),
                        FeedItem("ua", title = "Новини України", channelId = "UCua"),
                    ),
                )
            coEvery { historyRepository.observeLocal(500) } returns flowOf(listOf(watchedUkrainian()))

            val cached = repository.observeFeed(FeedKey.Home(HomeCategory.FOR_YOU)).first()

            assertEquals("ua", cached.first().id)
            coVerify { dao.insertAll(any()) }
        }

    @Test
    fun `refreshFeed and observeFeed apply content filtering and the feed limit`() =
        runBlocking {
            val json =
                feedJson(
                    *((1..70).map { FeedItem("$it") } + FeedItem("blocked", title = "adult content")).toTypedArray(),
                )
            coEvery { contentClient.search(any(), any(), any()) } returns json
            every { feedCache.observe("home:ALL") } returns
                flowOf(cachedFeedJson(*((1..70).map { FeedItem("$it") } + FeedItem("blocked", title = "adult content")).toTypedArray()))

            val refreshed = repository.refreshFeed(FeedKey.Home(HomeCategory.ALL)).getOrThrow()
            val cached = repository.observeFeed(FeedKey.Home(HomeCategory.ALL)).first()

            assertEquals(60, refreshed.size)
            assertEquals(60, cached.size)
            assertTrue(refreshed.none { it.id == "blocked" })
            assertTrue(cached.none { it.id == "blocked" })
            coVerify { dao.insertAll(any()) }
        }

    @Test
    fun `search maps one InnerTube search response`() =
        runBlocking {
            coEvery { contentClient.search("query", null, null) } returns
                feedJson(FeedItem("1"), FeedItem("2"))

            val result = repository.search("query")

            assertEquals(listOf("1", "2"), result.map { it.id })
            coVerify(exactly = 1) { contentClient.search("query", null, null) }
        }

    @Test
    fun `search page 2 uses the continuation and a missing token ends pagination`() =
        runBlocking {
            coEvery { contentClient.search("query", null, null) } returns
                feedJson(FeedItem("page1"), continuation = "search-token")
            coEvery { contentClient.search("query", null, "search-token") } returns
                feedJson(FeedItem("page2"))

            assertEquals(listOf("page1"), repository.search("query", 1).map { it.id })
            assertEquals(listOf("page2"), repository.search("query", 2).map { it.id })
            coVerify { contentClient.search("query", null, "search-token") }

            // Page 2 had no continuation, so the mediator must see an empty page and stop.
            assertTrue(repository.search("query", 3).isEmpty())
            coVerify(exactly = 2) { contentClient.search("query", null, any()) }
        }

    @Test
    fun `searchSuggestions uses suggestions and returns empty list on failure`() =
        runBlocking {
            coEvery { contentClient.suggestions("query") } returns listOf("first", "second")
            coEvery { contentClient.suggestions("broken") } throws IOException("boom")

            assertEquals(listOf("first", "second"), repository.searchSuggestions("query"))
            assertEquals(emptyList<String>(), repository.searchSuggestions("broken"))
            assertEquals(emptyList<String>(), repository.searchSuggestions("  "))
            coVerify { contentClient.suggestions("query") }
            coVerify { contentClient.suggestions("broken") }
        }

    @Test
    fun `playbackUsesFreshPlayerResponse`() =
        runBlocking {
            coEvery { dao.getVideoById("v1") } returns cachedEntity("v1")
            coEvery { contentClient.player("v1") } returns playerJson("v1")

            assertEquals("Cached", repository.getVideoById("v1").title)
            repository.getVideoDetails("v1", AppSettings.DEFAULT_QUALITY)
            repository.getVideoDetails("v1", AppSettings.DEFAULT_QUALITY)

            coVerify(exactly = 2) { contentClient.player("v1") }
        }

    @Test
    fun `getVideoDetails maps description and related videos`() =
        runBlocking {
            coEvery { contentClient.player("v1") } returns
                playerJson("v1", description = "About the video", related = listOf(FeedItem("2")))

            val details = repository.getVideoDetails("v1", AppSettings.DEFAULT_QUALITY)

            assertEquals("About the video", details.description)
            assertEquals(listOf("2"), details.related.map { it.id })
            assertEquals(PlaybackSource.Progressive("https://example.invalid/progressive.mp4"), details.playback)
        }

    @Test
    fun `getVideoDetails prefers muxed progressive streams that accept open ranges`() =
        runBlocking {
            coEvery { contentClient.player("v1") } returns
                playerJson(
                    "v1",
                    formats =
                        listOf(
                            format("https://example.invalid/progressive.mp4", "video/mp4; codecs=avc1.4D401E, mp4a.40.2", 360),
                        ),
                    adaptive =
                        listOf(
                            format("https://example.invalid/720.mp4", "video/mp4; codecs=avc1.64001F", 720),
                            format("https://example.invalid/audio.m4a", "audio/mp4; codecs=mp4a.40.2", bitrate = "130668"),
                        ),
                )

            val details = repository.getVideoDetails("v1", maxHeight = 720)

            assertEquals(PlaybackSource.Progressive("https://example.invalid/progressive.mp4"), details.playback)
        }

    @Test
    fun `getVideoDetails applies the maxHeight cap before choosing playback`() =
        runBlocking {
            coEvery { contentClient.player("v1") } returns
                playerJson(
                    "v1",
                    formats = emptyList(),
                    adaptive =
                        listOf(
                            format("https://example.invalid/360.mp4", "video/mp4; codecs=avc1.4D401E", 360),
                            format("https://example.invalid/480.mp4", "video/mp4; codecs=avc1.4D401F", 480),
                            format("https://example.invalid/720.mp4", "video/mp4; codecs=avc1.64001F", 720),
                            format("https://example.invalid/audio.m4a", "audio/mp4; codecs=mp4a.40.2", bitrate = "130668"),
                        ),
                )

            val capped = repository.getVideoDetails("v1", maxHeight = 480)
            val uncapped = repository.getVideoDetails("v1", maxHeight = 1080)

            assertEquals(
                PlaybackSource.Merged(
                    videoUrl = "https://example.invalid/480.mp4",
                    audioUrl = "https://example.invalid/audio.m4a",
                ),
                capped.playback,
            )
            assertEquals(
                PlaybackSource.Merged(
                    videoUrl = "https://example.invalid/720.mp4",
                    audioUrl = "https://example.invalid/audio.m4a",
                ),
                uncapped.playback,
            )
        }

    @Test
    fun `getVideoDetails keeps HLS for live streams`() =
        runBlocking {
            coEvery { contentClient.player("live1") } returns
                playerJson(
                    "live1",
                    formats = emptyList(),
                    adaptive =
                        listOf(
                            format("https://example.invalid/720.mp4", "video/mp4; codecs=avc1.64001F", 720),
                            format("https://example.invalid/audio.m4a", "audio/mp4; codecs=mp4a.40.2"),
                        ),
                    hls = "https://example.invalid/live.m3u8",
                    live = true,
                )

            val details = repository.getVideoDetails("live1", AppSettings.DEFAULT_QUALITY)

            assertTrue(details.video.isLive)
            assertEquals(PlaybackSource.Hls("https://example.invalid/live.m3u8"), details.playback)
        }

    @Test
    fun `getVideoDetails falls back to progressive then HLS for VOD`() =
        runBlocking {
            coEvery { contentClient.player("v1") } returns
                playerJson(
                    "v1",
                    adaptive =
                        listOf(
                            format("https://example.invalid/720.mp4", "video/mp4; codecs=avc1.64001F", 720),
                        ),
                    hls = "https://example.invalid/manifest.m3u8",
                )

            val progressive = repository.getVideoDetails("v1", AppSettings.DEFAULT_QUALITY)
            assertEquals(PlaybackSource.Progressive("https://example.invalid/progressive.mp4"), progressive.playback)

            coEvery { contentClient.player("v2") } returns
                playerJson(
                    "v2",
                    formats = emptyList(),
                    adaptive =
                        listOf(
                            format("https://example.invalid/720.mp4", "video/mp4; codecs=avc1.64001F", 720),
                        ),
                    hls = "https://example.invalid/manifest.m3u8",
                )
            val hls = repository.getVideoDetails("v2", AppSettings.DEFAULT_QUALITY)
            assertEquals(PlaybackSource.Hls("https://example.invalid/manifest.m3u8"), hls.playback)
        }

    @Test
    fun `getChannelVideos passes the continuation through to browse`() =
        runBlocking {
            coEvery { contentClient.browse("UC123", "token") } returns
                feedJson(FeedItem("5"), continuation = "next")

            val page = repository.getChannelVideos("UC123", "token")

            assertEquals(listOf("5"), page.videos.map { it.id })
            assertEquals("next", page.continuation)
            coVerify { contentClient.browse("UC123", "token") }
        }

    @Test
    fun `getChannelDetails uses Room when present and InnerTube otherwise`() =
        runBlocking {
            coEvery { channelDao.getChannelById("UCcached") } returns
                ChannelEntity("UCcached", "Cached", "", 1L, 2, "")
            assertEquals("Cached", repository.getChannelDetails("UCcached").name)

            coEvery { channelDao.getChannelById("UC1") } returns null
            coEvery { contentClient.browse("UC1", null) } returns channelJson("UC1", "Fresh")
            assertEquals("Fresh", repository.getChannelDetails("UC1").name)
            coVerify { channelDao.insert(any()) }
        }

    @Test
    fun `getComments passes the continuation to next`() =
        runBlocking {
            coEvery { contentClient.next("comments-token") } returns
                commentsJson("next-comments", "c1" to "Nice")

            val page = repository.getComments("v1", "comments-token")

            assertEquals("next-comments", page.continuation)
            assertEquals(42L, page.totalCount)
            assertEquals("c1", page.comments.single().id)
            assertEquals("Nice", page.comments.single().content)
            coVerify { contentClient.next("comments-token") }
            coVerify(exactly = 0) { contentClient.player(any()) }
        }

    @Test
    fun `getComments finds the comment continuation from the player response`() =
        runBlocking {
            coEvery { contentClient.player("v1") } returns
                playerJson("v1", commentContinuation = "from-player")
            coEvery { contentClient.next("from-player") } returns commentsJson()

            repository.getComments("v1")

            coVerify { contentClient.player("v1") }
            coVerify { contentClient.next("from-player") }
        }

    private fun forYouJson() =
        feedJson(
            FeedItem("us", title = "Breaking news", channelId = "UCus"),
            FeedItem("ua", title = "Новини України", channelId = "UCua"),
        )

    private fun watchedUkrainian() =
        WatchHistoryEntry(
            videoId = "old",
            title = "Watched",
            channelName = "UA",
            channelId = "UCua",
            authorAvatarUrl = "",
            thumbnailUrl = "",
            durationMs = 1000L,
            watchedAt = System.currentTimeMillis(),
        )

    private data class FeedItem(
        val id: String,
        val title: String = "Video $id",
        val channelId: String = "UC$id",
        val seconds: Long = 300,
        val live: Boolean = false,
    )

    private fun durationText(seconds: Long): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

    private fun renderer(
        item: FeedItem,
        key: String = "videoRenderer",
    ): String {
        val badge =
            if (item.live) {
                """"badges":[{"metadataBadgeRenderer":{"style":"LIVE"}}],"""
            } else {
                ""
            }
        return """{"$key":{"videoId":"${item.id}","title":{"simpleText":"${item.title}"},"ownerText":{"runs":[{"text":"Author","navigationEndpoint":{"browseEndpoint":{"browseId":"${item.channelId}"}}}]},$badge"lengthText":{"simpleText":"${durationText(item.seconds)}"},"viewCountText":{"simpleText":"100 views"},"publishedTimeText":{"simpleText":"1 day ago"},"thumbnail":{"thumbnails":[{"url":"https://example.invalid/${item.id}.jpg","width":480,"height":360}]}}}"""
    }

    private fun feedJson(
        vararg items: FeedItem,
        continuation: String? = null,
    ): String {
        val token =
            if (continuation == null) {
                ""
            } else {
                """,{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"$continuation"}}}}"""
            }
        return """{"contents":[${items.joinToString(",") { renderer(it) }}$token]}"""
    }

    /** Compact cache payload shape that must fit into a Room CursorWindow row. */
    private fun cachedFeedJson(vararg items: FeedItem): String =
        items.joinToString(",", "[", "]") { item ->
            """{"id":"${item.id}","title":"${item.title}","channelName":"Author","channelId":"${item.channelId}","thumbnailUrl":"https://example.invalid/${item.id}.jpg","duration":${item.seconds},"views":100,"uploadedAt":"1 day ago","isLive":${item.live},"authorAvatarUrl":""}"""
        }

    private fun format(
        url: String,
        mime: String,
        height: Int? = null,
        bitrate: String = "",
    ): String {
        val parts = mutableListOf("\"url\":\"$url\"", "\"mimeType\":\"$mime\"")
        height?.let { parts += "\"height\":$it" }
        if (bitrate.isNotBlank()) parts += "\"bitrate\":\"$bitrate\""
        return parts.joinToString(",", "{", "}")
    }

    private fun playerJson(
        videoId: String = "v1",
        description: String = "About the video",
        formats: List<String> =
            listOf(
                format("https://example.invalid/progressive.mp4", "video/mp4; codecs=avc1.4D401E, mp4a.40.2", 360),
            ),
        adaptive: List<String> = emptyList(),
        hls: String? = null,
        live: Boolean = false,
        related: List<FeedItem> = emptyList(),
        commentContinuation: String? = null,
    ): String {
        val hlsField = hls?.let { ""","hlsManifestUrl":"$it"""" } ?: ""
        val panels =
            if (commentContinuation == null) {
                ""
            } else {
                ""","engagementPanels":[{"engagementPanelSectionListRenderer":{"content":{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"$commentContinuation"}}}}}}]"""
            }
        val relatedJson = related.joinToString(",") { renderer(it, "compactVideoRenderer") }
        return """{"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"$videoId","title":"Video $videoId","author":"Author","channelId":"UC$videoId","lengthSeconds":"120","viewCount":"1000","shortDescription":"$description","isLiveContent":$live,"thumbnail":{"thumbnails":[{"url":"https://example.invalid/$videoId.jpg","width":480}]}},"streamingData":{"formats":[${formats.joinToString(",")}],"adaptiveFormats":[${adaptive.joinToString(",")}]$hlsField},"contents":{"twoColumnWatchNextResults":{"secondaryResults":{"secondaryResults":{"results":[$relatedJson]}}}}$panels}"""
    }

    private fun commentsJson(
        continuation: String? = null,
        vararg entries: Pair<String, String>,
    ): String {
        val renderers =
            entries.joinToString(",") { (id, text) ->
                """{"commentRenderer":{"commentId":"$id","authorText":{"runs":[{"text":"Alice","navigationEndpoint":{"browseEndpoint":{"browseId":"UC$id"}}}]},"authorThumbnail":{"thumbnails":[{"url":"https://example.invalid/$id.jpg","width":48}]},"contentText":{"simpleText":"$text"},"likeCountText":{"simpleText":"12 likes"},"publishedTimeText":{"simpleText":"2 days ago"},"isPinned":true}}"""
            }
        val token =
            if (continuation == null) {
                ""
            } else {
                """,{"continuationItemRenderer":{"continuationCommand":{"token":"$continuation"}}}"""
            }
        return """{"contents":[$renderers$token],"commentCount":"42"}"""
    }

    private fun channelJson(
        id: String,
        name: String,
        continuation: String? = null,
    ): String {
        val token =
            if (continuation == null) {
                ""
            } else {
                """{"continuationItemRenderer":{"continuationCommand":{"token":"$continuation"}}}"""
            }
        return """{"header":{"browseHeaderRenderer":{"title":"$name","avatar":{"thumbnails":[{"url":"https://example.invalid/avatar.jpg","width":176}]},"subscriberCountText":{"simpleText":"1.2K subscribers"},"videoCountText":{"simpleText":"42 videos"},"description":{"simpleText":"Channel description"},"navigationEndpoint":{"browseEndpoint":{"browseId":"$id"}}}},"contents":[$token]}"""
    }

    private fun cachedEntity(id: String) =
        VideoEntity(
            id = id,
            title = "Cached",
            channelName = "Author",
            channelId = "UC$id",
            thumbnailUrl = "https://example.invalid/$id.jpg",
            duration = 120L,
            views = 100L,
            uploadedAt = "",
            isLive = false,
        )
}
