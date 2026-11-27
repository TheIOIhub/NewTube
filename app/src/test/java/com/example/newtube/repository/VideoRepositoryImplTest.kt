package com.example.newtube.repository

import com.example.newtube.database.ChannelDao
import com.example.newtube.database.VideoDao
import com.example.newtube.implementation.VideoRepositoryImpl
import com.example.newtube.model.AccountState
import com.example.newtube.model.AppSettings
import com.example.newtube.model.HomeCategory
import com.example.newtube.model.PlaybackSource
import com.example.newtube.model.WatchHistoryEntry
import com.example.newtube.remote.ChannelSearchDto
import com.example.newtube.remote.ChannelVideosDto
import com.example.newtube.remote.CommentDto
import com.example.newtube.remote.CommentsPageDto
import com.example.newtube.remote.FormatStreamDto
import com.example.newtube.remote.InvidiousService
import com.example.newtube.remote.ThumbnailDto
import com.example.newtube.remote.VideoDto
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class VideoRepositoryImplTest {
    private lateinit var repository: VideoRepositoryImpl
    private val service: InvidiousService = mockk()
    private val dao: VideoDao = mockk()
    private val channelDao: ChannelDao = mockk()
    private val shortsCache: ShortsCacheRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val historyRepository: HistoryRepository = mockk(relaxed = true)
    private val accountRepository: AccountRepository = mockk(relaxed = true)
    private val settingsFlow = MutableStateFlow(AppSettings())

    @Before
    fun setUp() {
        repository =
            VideoRepositoryImpl(
                service,
                dao,
                channelDao,
                shortsCache,
                settingsRepository,
                historyRepository,
                accountRepository,
            )
        settingsFlow.value = AppSettings()
        coEvery { settingsRepository.settings } returns settingsFlow
        coEvery { historyRepository.observeLocal(any()) } returns flowOf(emptyList())
        coEvery { shortsCache.knownChannelIds() } returns emptySet()
        coJustRun { shortsCache.rememberChannelIds(any()) }
        coJustRun { shortsCache.clearFeedCache() }
        coJustRun { dao.insertAll(any()) }
    }

    @Test
    fun `getTrending should map the channel id and the largest thumbnail`() =
        runBlocking {
            val dto =
                VideoDto(
                    type = "video",
                    videoId = "1",
                    title = "Test Video",
                    author = "Test Author",
                    authorId = "UC123",
                    videoThumbnails =
                        listOf(
                            ThumbnailDto(url = "http://example.com/small.jpg", width = 120, height = 90),
                            ThumbnailDto(url = "http://example.com/thumb.jpg", width = 480, height = 360),
                        ),
                    publishedText = "1 day ago",
                    viewCount = 100L,
                    lengthSeconds = 120L,
                    liveNow = false,
                )
            coEvery { service.getTrending(any(), any(), any()) } returns listOf(dto)
            coJustRun { dao.insertAll(any()) }

            val result = repository.getTrending()

            assertEquals(1, result.size)
            val video = result[0]
            assertEquals("1", video.id)
            assertEquals("Test Video", video.title)
            assertEquals("Test Author", video.channelName)
            assertEquals("UC123", video.channelId)
            assertEquals("http://example.com/thumb.jpg", video.thumbnailUrl)
            assertEquals(100L, video.views)
            assertEquals("1 day ago", video.uploadedAt)
        }

    @Test
    fun `getTrending passes stored region and language to Invidious`() =
        runBlocking {
            settingsFlow.value = AppSettings(contentCountry = "US", contentLanguage = "en")
            coEvery { service.getTrending("US", "Default", "en") } returns emptyList()

            repository.getTrending()

            coVerify { service.getTrending("US", "Default", "en") }
        }

    @Test
    fun `getHomeFeed passes region and language to every source`() =
        runBlocking {
            settingsFlow.value = AppSettings(contentCountry = "US", contentLanguage = "en")
            coEvery { service.getTrending(any(), any(), any()) } returns emptyList()
            coEvery { service.getPopular() } returns emptyList()
            coEvery { service.searchVideos(any(), any(), any(), any(), any()) } returns emptyList()

            repository.getHomeFeed(HomeCategory.ALL)

            coVerify { service.getTrending("US", "Default", "en") }
            coVerify { service.searchVideos(any(), any(), any(), "US", "en") }
        }

    @Test
    fun `getShortsShelf passes region and language to the shorts search`() =
        runBlocking {
            settingsFlow.value = AppSettings(contentCountry = "US", contentLanguage = "en")
            coEvery { service.getShorts(any(), any(), any(), any(), any(), any()) } returns emptyList()

            repository.getShortsShelf()

            coVerify { service.getShorts(any(), any(), any(), any(), "US", "en") }
        }

    @Test
    fun `clearFeedCaches clears known shorts channels`() =
        runBlocking {
            coJustRun { shortsCache.clearKnownChannelIds() }

            repository.clearFeedCaches()

            coVerify { shortsCache.clearKnownChannelIds() }
        }

    @Test
    fun `shorts discovery uses a Ukrainian query when no channels are cached`() =
        runBlocking {
            coEvery { service.searchChannels(any(), any(), any(), any(), any()) } returns listOf(ChannelSearchDto(authorId = "UCua"))
            coEvery { service.getChannelShorts("UCua", any()) } returns
                ChannelVideosDto(videos = listOf(videoDto("short-ua", length = 30)), continuation = null)
            coJustRun { dao.insertAll(any()) }

            repository.getShorts(1)

            coVerify {
                service.searchChannels(
                    withArg { assertTrue(it.any { char -> char in '\u0400'..'\u04FF' }) },
                    any(),
                    any(),
                    any(),
                    any(),
                )
            }
        }

    @Test
    fun `shorts discovery passes region and language to channel search`() =
        runBlocking {
            settingsFlow.value = AppSettings(contentCountry = "US", contentLanguage = "en")
            coEvery { service.searchChannels(any(), any(), any(), any(), any()) } returns
                listOf(ChannelSearchDto(authorId = "UCua"))
            coEvery { service.getChannelShorts("UCua", any()) } returns
                ChannelVideosDto(videos = listOf(videoDto("ua-short", length = 30)), continuation = null)

            repository.getShorts(1)

            coVerify { service.searchChannels(any(), any(), any(), "US", "en") }
        }

    @Test
    fun `clearFeedCaches clears repository channel rotation too`() =
        runBlocking {
            coJustRun { shortsCache.clearKnownChannelIds() }
            coJustRun { shortsCache.clearFeedCache() }

            repository.clearFeedCaches()

            coVerify { shortsCache.clearFeedCache() }
        }

    @Test
    fun `getForYouFeed ranks a watched Ukrainian channel first`() =
        runBlocking {
            coEvery { service.getTrending(any(), any(), any()) } returns
                listOf(
                    videoDto("us").copy(title = "Breaking news", authorId = "UCus"),
                    videoDto("ua").copy(title = "Новини України", authorId = "UCua"),
                )
            coEvery { service.getPopular() } returns emptyList()
            coEvery { service.searchVideos(any(), any(), any(), any(), any()) } returns emptyList()
            coEvery { service.getShorts(any(), any(), any(), any(), any(), any()) } returns emptyList()
            coEvery { accountRepository.state } returns MutableStateFlow(AccountState.SignedOut)
            coEvery { historyRepository.observeLocal(500) } returns
                flowOf(
                    listOf(
                        WatchHistoryEntry(
                            videoId = "old",
                            title = "Watched",
                            channelName = "UA",
                            channelId = "UCua",
                            authorAvatarUrl = "",
                            thumbnailUrl = "",
                            durationMs = 1000L,
                            watchedAt = System.currentTimeMillis(),
                        ),
                    ),
                )

            assertEquals("ua", repository.getForYouFeed().first().id)
        }

    @Test
    fun `getVideoDetails should prefer a progressive stream over the HLS manifest`() =
        runBlocking {
            val dto =
                videoDto(id = "1").copy(
                    hlsUrl = "https://example.com/manifest",
                    formatStreams = listOf(FormatStreamDto(url = "https://example.com/progressive.mp4", type = "video/mp4")),
                )
            coEvery { service.getVideoDetails("1", any(), any()) } returns dto
            coJustRun { dao.insertAll(any()) }

            val details = repository.getVideoDetails("1", AppSettings.DEFAULT_QUALITY)

            assertEquals(PlaybackSource.Progressive("https://example.com/progressive.mp4"), details.playback)
        }

    @Test
    fun `getVideoDetails should merge adaptive tracks instead of using HLS for VOD`() =
        runBlocking {
            val dto =
                videoDto(id = "1").copy(
                    hlsUrl = "https://example.com/broken-manifest",
                    adaptiveFormats =
                        listOf(
                            adaptiveVideo("https://example.com/360.mp4", "360p", "avc1.4D401E"),
                            adaptiveVideo("https://example.com/720.mp4", "720p", "avc1.4D401F"),
                            adaptiveAudio("https://example.com/audio.m4a", "130668", "mp4a.40.2"),
                        ),
                )
            coEvery { service.getVideoDetails("1", any(), any()) } returns dto
            coJustRun { dao.insertAll(any()) }

            val details = repository.getVideoDetails("1", AppSettings.DEFAULT_QUALITY)

            assertEquals(
                PlaybackSource.Merged(
                    videoUrl = "https://example.com/720.mp4",
                    audioUrl = "https://example.com/audio.m4a",
                ),
                details.playback,
            )
        }

    @Test
    fun `getVideoDetails should keep HLS for livestreams`() =
        runBlocking {
            val dto =
                videoDto(id = "live1").copy(
                    type = "livestream",
                    liveNow = true,
                    hlsUrl = "https://example.com/live.m3u8",
                    adaptiveFormats =
                        listOf(
                            adaptiveVideo("https://example.com/720.mp4", "720p", "avc1.4D401F"),
                            adaptiveAudio("https://example.com/audio.m4a", "130668", "mp4a.40.2"),
                        ),
                )
            coEvery { service.getVideoDetails("live1", any(), any()) } returns dto
            coJustRun { dao.insertAll(any()) }

            val details = repository.getVideoDetails("live1", AppSettings.DEFAULT_QUALITY)

            assertEquals(PlaybackSource.Hls("https://example.com/live.m3u8"), details.playback)
        }

    @Test
    fun `getVideoDetails should fall back to HLS when the adaptive pair is incomplete`() =
        runBlocking {
            val dto =
                videoDto(id = "1").copy(
                    hlsUrl = "https://example.com/manifest",
                    adaptiveFormats = listOf(adaptiveVideo("https://example.com/720.mp4", "720p", "avc1.4D401F")),
                )
            coEvery { service.getVideoDetails("1", any(), any()) } returns dto
            coJustRun { dao.insertAll(any()) }

            val details = repository.getVideoDetails("1", AppSettings.DEFAULT_QUALITY)

            assertEquals(PlaybackSource.Hls("https://example.com/manifest"), details.playback)
        }

    @Test
    fun `getVideoDetails should merge separate DASH video and audio tracks`() =
        runBlocking {
            val dto =
                videoDto(id = "1").copy(
                    adaptiveFormats =
                        listOf(
                            FormatStreamDto(url = "https://example.com/audio.m4a", type = "audio/mp4"),
                            FormatStreamDto(url = "https://example.com/video.mp4", type = "video/mp4"),
                        ),
                )
            coEvery { service.getVideoDetails("1", any(), any()) } returns dto
            coJustRun { dao.insertAll(any()) }

            val details = repository.getVideoDetails("1", AppSettings.DEFAULT_QUALITY)

            assertEquals(
                PlaybackSource.Merged(
                    videoUrl = "https://example.com/video.mp4",
                    audioUrl = "https://example.com/audio.m4a",
                ),
                details.playback,
            )
        }

    @Test
    fun `getVideoDetails should map recommended videos`() =
        runBlocking {
            val dto =
                videoDto(id = "1").copy(
                    hlsUrl = "https://example.com/manifest",
                    description = "About the video",
                    recommendedVideos = listOf(videoDto(id = "2").copy(type = null)),
                )
            coEvery { service.getVideoDetails("1", any(), any()) } returns dto
            coJustRun { dao.insertAll(any()) }

            val details = repository.getVideoDetails("1", AppSettings.DEFAULT_QUALITY)

            assertEquals("About the video", details.description)
            assertEquals(1, details.related.size)
            assertEquals("2", details.related[0].id)
        }

    @Test
    fun `getChannelVideos should pass the continuation token through`() =
        runBlocking {
            coEvery { service.getChannelVideos("UC123", "token") } returns
                ChannelVideosDto(videos = listOf(videoDto(id = "5")), continuation = "next")
            coJustRun { dao.insertAll(any()) }

            val page = repository.getChannelVideos("UC123", "token")

            assertEquals(1, page.videos.size)
            assertEquals("next", page.continuation)
        }

    @Test
    fun `getComments should map comments and the total count`() =
        runBlocking {
            coEvery { service.getComments("v1", null) } returns
                CommentsPageDto(
                    comments =
                        listOf(
                            CommentDto(
                                commentId = "c1",
                                author = "Alice",
                                authorId = "UC1",
                                authorThumbnail = "https://example.com/a.jpg",
                                content = "Nice",
                                likeCount = 12,
                                publishedText = "2 days ago",
                                isPinned = true,
                            ),
                        ),
                    continuation = "nextComments",
                    commentCount = 42,
                )

            val page = repository.getComments("v1")

            assertEquals(42, page.totalCount)
            assertEquals("nextComments", page.continuation)
            assertEquals(1, page.comments.size)
            val comment = page.comments[0]
            assertEquals("c1", comment.id)
            assertEquals("Alice", comment.author)
            assertEquals("UC1", comment.authorChannelId)
            assertEquals(12L, comment.likeCount)
            assertTrue(comment.isPinned)
        }

    @Test
    fun `getVideoDetails should map a livestream payload`() =
        runBlocking {
            val dto =
                videoDto(id = "live1").copy(
                    type = "livestream",
                    liveNow = true,
                    lengthSeconds = 0,
                    hlsUrl = "https://example.com/live.m3u8",
                )
            coEvery { service.getVideoDetails("live1", any(), any()) } returns dto
            coJustRun { dao.insertAll(any()) }

            val details = repository.getVideoDetails("live1", AppSettings.DEFAULT_QUALITY)

            assertEquals("live1", details.video.id)
            assertTrue(details.video.isLive)
            assertEquals(PlaybackSource.Hls("https://example.com/live.m3u8"), details.playback)
        }

    @Test
    fun `mixed search results keep videos and livestreams but drop channels`() =
        runBlocking {
            val channel = VideoDto(type = "channel", videoId = null, title = "Some channel")
            val playlist = VideoDto(type = "playlist", videoId = null, title = "Some playlist")
            val regular = videoDto(id = "1")
            val live = videoDto(id = "2").copy(type = "livestream", liveNow = true)
            coEvery { service.searchVideos(any(), any(), any(), any(), any()) } returns listOf(channel, playlist, regular, live)
            coJustRun { dao.insertAll(any()) }

            val result = repository.search("q")

            assertEquals(listOf("1", "2"), result.map { it.id })
        }

    @Test
    fun `getHomeFeed ALL merges every source and removes duplicates`() =
        runBlocking {
            coEvery { service.getTrending("UA", "Default", any()) } returns listOf(videoDto("1"), videoDto("2"))
            coEvery { service.getTrending("UA", "Gaming", any()) } returns listOf(videoDto("2"), videoDto("3"))
            coEvery { service.getPopular() } returns listOf(videoDto("3"), videoDto("4"))
            coEvery { service.searchVideos(any(), any(), any(), any(), any()) } returns listOf(videoDto("4"), videoDto("5"))
            coJustRun { dao.insertAll(any()) }

            val feed = repository.getHomeFeed(HomeCategory.ALL)

            assertEquals(setOf("1", "2", "3", "4", "5"), feed.map { it.id }.toSet())
            assertEquals(feed.size, feed.map { it.id }.distinct().size)
        }

    @Test
    fun `getHomeFeed keeps the feed when one source fails`() =
        runBlocking {
            coEvery { service.getTrending("UA", "Default", any()) } throws IOException("boom")
            coEvery { service.getTrending("UA", "Gaming", any()) } returns listOf(videoDto("2"))
            coEvery { service.getPopular() } returns listOf(videoDto("3"))
            coEvery { service.searchVideos(any(), any(), any(), any(), any()) } throws IOException("boom")
            coJustRun { dao.insertAll(any()) }

            val feed = repository.getHomeFeed(HomeCategory.ALL)

            assertEquals(setOf("2", "3"), feed.map { it.id }.toSet())
        }

    @Test
    fun `getHomeFeed fails only when every source fails`() =
        runBlocking {
            coEvery { service.getTrending(any(), any(), any()) } throws IOException("down")
            coEvery { service.getPopular() } throws IOException("down")
            coEvery { service.searchVideos(any(), any(), any(), any(), any()) } throws IOException("down")

            val error = runCatching { repository.getHomeFeed(HomeCategory.MUSIC) }.exceptionOrNull()

            assertTrue(error is IOException)
        }

    @Test
    fun `getHomeFeed topic category includes its anchor and searches`() =
        runBlocking {
            coEvery { service.getTrending("UA", "Gaming", any()) } returns listOf(videoDto("g1"))
            coEvery { service.searchVideos(any(), any(), any(), any(), any()) } returns listOf(videoDto("m1"))
            coJustRun { dao.insertAll(any()) }

            val feed = repository.getHomeFeed(HomeCategory.GAMING)

            assertEquals(setOf("g1", "m1"), feed.map { it.id }.toSet())
            coVerify { service.getTrending("UA", "Gaming", any()) }
        }

    @Test
    fun `getShorts uses discovered channels and keeps 1 to 180 seconds`() =
        runBlocking {
            coEvery { service.searchChannels(any(), any(), any(), any(), any()) } returns
                listOf(ChannelSearchDto(authorId = "UCa"), ChannelSearchDto(authorId = "UCb"))
            coEvery { service.getChannelShorts("UCa", null) } returns
                ChannelVideosDto(
                    videos =
                        listOf(
                            videoDto("short1", length = 59),
                            videoDto("long", length = 181),
                            videoDto("live").copy(liveNow = true),
                        ),
                )
            coEvery { service.getChannelShorts("UCb", null) } returns
                ChannelVideosDto(
                    videos = listOf(videoDto("short2", length = 90), videoDto("short1", length = 59)),
                )
            coJustRun { dao.insertAll(any()) }

            val shorts = repository.getShorts(1)

            assertEquals(setOf("short1", "short2"), shorts.map { it.id }.toSet())
        }

    @Test
    fun `getShorts skips a channel that fails`() =
        runBlocking {
            coEvery { service.searchChannels(any(), any(), any(), any(), any()) } returns
                listOf(ChannelSearchDto(authorId = "UCbad"), ChannelSearchDto(authorId = "UCgood"))
            coEvery { service.getChannelShorts("UCbad", null) } throws IOException("500")
            coEvery { service.getChannelShorts("UCgood", null) } returns
                ChannelVideosDto(videos = listOf(videoDto("short1", length = 30)))
            coJustRun { dao.insertAll(any()) }

            assertEquals(listOf("short1"), repository.getShorts(1).map { it.id })
        }

    @Test
    fun `getShorts falls back to curated channels when discovery is empty`() =
        runBlocking {
            coEvery { service.searchChannels(any(), any(), any(), any(), any()) } returns emptyList()
            coEvery { service.getChannelShorts(any(), null) } returns
                ChannelVideosDto(videos = listOf(videoDto("fallback", length = 30)))
            coJustRun { dao.insertAll(any()) }

            assertEquals(listOf("fallback"), repository.getShorts(1).map { it.id })
            coVerify { service.getChannelShorts(any(), null) }
        }

    @Test
    fun `getShorts throws when every channel request fails`() =
        runBlocking {
            coEvery { service.searchChannels(any(), any(), any(), any(), any()) } returns
                listOf(
                    ChannelSearchDto(authorId = "UCa"),
                    ChannelSearchDto(authorId = "UCb"),
                    ChannelSearchDto(authorId = "UCc"),
                )
            coEvery { service.getChannelShorts(any(), null) } throws IOException("500")

            val error = runCatching { repository.getShorts(1) }.exceptionOrNull()

            assertTrue(error is IOException)
        }

    @Test
    fun `getShorts caps per channel contribution and mixes many channels`() =
        runBlocking {
            coEvery { service.searchChannels(any(), any(), any(), any(), any()) } returns
                (1..8).map { ChannelSearchDto(authorId = "UC$it") }
            coEvery { service.getChannelShorts(any(), null) } answers {
                val channel = firstArg<String>()
                ChannelVideosDto(
                    videos =
                        (1..20).map { i ->
                            videoDto("${channel}_$i", length = 30).copy(authorId = channel)
                        },
                )
            }
            coJustRun { dao.insertAll(any()) }

            val shorts = repository.getShorts(1)

            assertEquals(28, shorts.size)
            val perChannel = shorts.groupBy { it.channelId }
            assertTrue(perChannel.values.all { it.size <= 7 })
            assertTrue(perChannel.size >= 4)
        }

    @Test
    fun `getShorts tops up from fallback when discovery pool is small`() =
        runBlocking {
            coEvery { service.searchChannels(any(), any(), any(), any(), any()) } answers {
                listOf(ChannelSearchDto(authorId = "UC-a"), ChannelSearchDto(authorId = "UC-b"))
            }
            coEvery { service.getChannelShorts(any(), null) } answers {
                val channel = firstArg<String>()
                ChannelVideosDto(videos = listOf(videoDto("s-$channel", length = 30).copy(authorId = channel)))
            }
            coJustRun { dao.insertAll(any()) }

            val shorts = repository.getShorts(1)

            assertTrue(shorts.size >= 4)
        }

    @Test
    fun `getShorts caps per channel when authorId is blank`() =
        runBlocking {
            coEvery { service.searchChannels(any(), any(), any(), any(), any()) } returns
                (1..4).map { ChannelSearchDto(authorId = "UC$it") }
            coEvery { service.getChannelShorts(any(), null) } answers {
                val channel = firstArg<String>()
                ChannelVideosDto(
                    videos =
                        (1..20).map { i ->
                            videoDto("${channel}_$i", length = 30).copy(authorId = "")
                        },
                )
            }
            coJustRun { dao.insertAll(any()) }

            val shorts = repository.getShorts(1)

            assertEquals(28, shorts.size)
        }

    @Test
    fun `getShorts rotates through fallback pool when discovery is empty`() =
        runBlocking {
            coEvery { service.searchChannels(any(), any(), any(), any(), any()) } returns emptyList()
            coEvery { service.getChannelShorts(any(), null) } answers {
                val channel = firstArg<String>()
                ChannelVideosDto(videos = listOf(videoDto("fb-$channel", length = 30).copy(authorId = channel)))
            }
            coJustRun { dao.insertAll(any()) }

            val page1 = repository.getShorts(1)
            val page2 = repository.getShorts(2)

            assertTrue(page1.size >= 7)
            assertTrue(page1.map { it.id }.toSet() != page2.map { it.id }.toSet())
        }

    @Test
    fun `knownChannelsSkipDiscovery`() =
        runBlocking {
            val known = setOf("UCknown1", "UCknown2", "UCknown3")
            coEvery { shortsCache.knownChannelIds() } returns known
            coEvery { service.getChannelShorts(any(), null) } answers {
                val channel = firstArg<String>()
                ChannelVideosDto(videos = listOf(videoDto("s-$channel", length = 30).copy(authorId = channel)))
            }
            coJustRun { dao.insertAll(any()) }

            val shorts = repository.getShorts(1)

            assertEquals(3, shorts.size)
            coVerify(exactly = 0) { service.searchChannels(any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { shortsCache.rememberChannelIds(any()) }
        }

    @Test
    fun `fallsBackToDiscoveryWhenCachedChannelsFail`() =
        runBlocking {
            val known = setOf("UCknown1", "UCknown2", "UCknown3")
            coEvery { shortsCache.knownChannelIds() } returns known
            coEvery { service.getChannelShorts(any(), null) } throws IOException("500")
            coEvery { service.getChannelShorts("UCnew", null) } returns
                ChannelVideosDto(videos = listOf(videoDto("short-new", length = 30)))
            coEvery { service.searchChannels(any(), any(), any(), any(), any()) } returns
                listOf(ChannelSearchDto(authorId = "UCnew"))
            coJustRun { dao.insertAll(any()) }

            val shorts = repository.getShorts(1)

            assertEquals(listOf("short-new"), shorts.map { it.id })
            coVerify { service.searchChannels(any(), any(), any(), any(), any()) }
            coVerify { shortsCache.rememberChannelIds(known + "UCnew") }
        }

    @Test
    fun `preservesCuratedFallback`() =
        runBlocking {
            coEvery { service.searchChannels(any(), any(), any(), any(), any()) } returns emptyList()
            coEvery { service.getChannelShorts(any(), null) } returns
                ChannelVideosDto(videos = listOf(videoDto("fallback", length = 30)))
            coJustRun { dao.insertAll(any()) }

            val shorts = repository.getShorts(1)

            assertEquals(listOf("fallback"), shorts.map { it.id }.distinct())
            coVerify { service.getChannelShorts(any(), null) }
            coVerify(exactly = 0) { shortsCache.rememberChannelIds(any()) }
        }

    @Test
    fun `getTrending picks the largest avatar at or below 176 px`() =
        runBlocking {
            val dto =
                videoDto(id = "1").copy(
                    authorThumbnails =
                        listOf(
                            ThumbnailDto(url = "https://example.com/32.jpg", width = 32, height = 32),
                            ThumbnailDto(url = "https://example.com/176.jpg", width = 176, height = 176),
                            ThumbnailDto(url = "https://example.com/512.jpg", width = 512, height = 512),
                        ),
                )
            coEvery { service.getTrending(any(), any(), any()) } returns listOf(dto)
            coJustRun { dao.insertAll(any()) }

            assertEquals("https://example.com/176.jpg", repository.getTrending().single().authorAvatarUrl)
        }

    @Test
    fun `avatar falls back to the largest available when every size is above 176`() =
        runBlocking {
            val dto =
                videoDto(id = "1").copy(
                    authorThumbnails =
                        listOf(
                            ThumbnailDto(url = "https://example.com/512.jpg", width = 512, height = 512),
                            ThumbnailDto(url = "https://example.com/900.jpg", width = 900, height = 900),
                        ),
                )
            coEvery { service.getTrending(any(), any(), any()) } returns listOf(dto)
            coJustRun { dao.insertAll(any()) }

            assertEquals("https://example.com/900.jpg", repository.getTrending().single().authorAvatarUrl)
        }

    @Test
    fun `missing author thumbnails map to an empty avatar`() =
        runBlocking {
            coEvery { service.getTrending(any(), any(), any()) } returns listOf(videoDto(id = "1"))
            coJustRun { dao.insertAll(any()) }

            assertEquals("", repository.getTrending().single().authorAvatarUrl)
        }

    @Test
    fun `getShortsShelf is one search and keeps only 1 to 60 second non-live clips`() =
        runBlocking {
            coEvery { service.getShorts(any(), any(), any(), any(), any(), any()) } returns
                listOf(
                    videoDto("short", length = 30),
                    videoDto("live", length = 30).copy(liveNow = true),
                    videoDto("long", length = 61),
                    videoDto("zero", length = 0),
                    videoDto("short", length = 30),
                )
            coJustRun { dao.insertAll(any()) }

            val shelf = repository.getShortsShelf(limit = 8)

            assertEquals(listOf("short"), shelf.map { it.id })
            coVerify(exactly = 1) { service.getShorts(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `getShortsShelf honours the limit`() =
        runBlocking {
            coEvery { service.getShorts(any(), any(), any(), any(), any(), any()) } returns
                (1..10).map { videoDto("s$it", length = 30) }
            coJustRun { dao.insertAll(any()) }

            assertEquals(3, repository.getShortsShelf(limit = 3).size)
        }

    @Test
    fun `getVideoDetails respects a 480p cap`() =
        runBlocking {
            val dto =
                videoDto(id = "1").copy(
                    adaptiveFormats =
                        listOf(
                            adaptiveVideo("https://example.com/360.mp4", "360p", "avc1.4D401E"),
                            adaptiveVideo("https://example.com/480.mp4", "480p", "avc1.4D401F"),
                            adaptiveVideo("https://example.com/720.mp4", "720p", "avc1.4D401F"),
                            adaptiveAudio("https://example.com/audio.m4a", "130668", "mp4a.40.2"),
                        ),
                )
            coEvery { service.getVideoDetails("1", any(), any()) } returns dto
            coJustRun { dao.insertAll(any()) }

            assertEquals(
                PlaybackSource.Merged(
                    videoUrl = "https://example.com/480.mp4",
                    audioUrl = "https://example.com/audio.m4a",
                ),
                repository.getVideoDetails("1", maxHeight = 480).playback,
            )
        }

    @Test
    fun `getVideoDetails allows 1080p when the cap is 1080`() =
        runBlocking {
            val dto =
                videoDto(id = "1").copy(
                    adaptiveFormats =
                        listOf(
                            adaptiveVideo("https://example.com/720.mp4", "720p", "avc1.4D401F"),
                            adaptiveVideo("https://example.com/1080.mp4", "1080p", "avc1.640028"),
                            adaptiveAudio("https://example.com/audio.m4a", "130668", "mp4a.40.2"),
                        ),
                )
            coEvery { service.getVideoDetails("1", any(), any()) } returns dto
            coJustRun { dao.insertAll(any()) }

            assertEquals(
                PlaybackSource.Merged(
                    videoUrl = "https://example.com/1080.mp4",
                    audioUrl = "https://example.com/audio.m4a",
                ),
                repository.getVideoDetails("1", maxHeight = 1080).playback,
            )
        }

    @Test
    fun `a progressive track above the cap is skipped when adaptive is available`() =
        runBlocking {
            val dto =
                videoDto(id = "1").copy(
                    formatStreams =
                        listOf(
                            FormatStreamDto(
                                url = "https://example.com/progressive-720.mp4",
                                type = "video/mp4",
                                qualityLabel = "720p",
                            ),
                        ),
                    adaptiveFormats =
                        listOf(
                            adaptiveVideo("https://example.com/adaptive-360.mp4", "360p", "avc1.4D401E"),
                            adaptiveAudio("https://example.com/audio.m4a", "130668", "mp4a.40.2"),
                        ),
                )
            coEvery { service.getVideoDetails("1", any(), any()) } returns dto
            coJustRun { dao.insertAll(any()) }

            assertEquals(
                PlaybackSource.Merged(
                    videoUrl = "https://example.com/adaptive-360.mp4",
                    audioUrl = "https://example.com/audio.m4a",
                ),
                repository.getVideoDetails("1", maxHeight = 480).playback,
            )
        }

    private fun adaptiveVideo(
        url: String,
        qualityLabel: String,
        codecs: String,
    ) = FormatStreamDto(
        url = url,
        type = "video/mp4; codecs=\"$codecs\"",
        qualityLabel = qualityLabel,
    )

    private fun adaptiveAudio(
        url: String,
        bitrate: String,
        codecs: String,
    ) = FormatStreamDto(
        url = url,
        type = "audio/mp4; codecs=\"$codecs\"",
        bitrate = bitrate,
    )

    private fun videoDto(id: String, length: Long = 120) =
        VideoDto(
            type = "video",
            videoId = id,
            title = "Video $id",
            author = "Author",
            authorId = "UC$id",
            videoThumbnails = listOf(ThumbnailDto(url = "https://example.com/$id.jpg", width = 480, height = 360)),
            lengthSeconds = length,
        )
}
