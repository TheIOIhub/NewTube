package com.example.newtube.innertube

import com.example.newtube.model.PlaybackSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentParserTest {
    private val browse = resource("/innertube/browse.json")
    private val search = resource("/innertube/search.json")
    private val player = resource("/innertube/player.json")
    private val next = resource("/innertube/next.json")
    private val channel = resource("/innertube/channel.json")

    @Test
    fun `parseFeed reads browse videos and continuation`() {
        val result = ContentParser.parseFeed(browse)

        assertEquals(5, result.items.size)
        assertTrue(result.items.all { it.id.isNotBlank() })
        assertTrue(result.items.all { it.thumbnailUrl.isNotBlank() })
        assertEquals("BROWSE_CONTINUATION_TOKEN", result.continuation)

        val first = result.items.first { it.id == "browse000001" }
        assertEquals("First video", first.title)
        assertEquals("Fixture Channel", first.channelName)
        assertEquals("UCfixture000000000000001", first.channelId)
        assertEquals(754L, first.duration)
        assertEquals(1_200_000L, first.views)
        assertEquals("3 days ago", first.uploadedAt)
        assertEquals("https://example.invalid/avatar/176.jpg", first.authorAvatarUrl)
        assertEquals("https://example.invalid/vi/browse000001/hq.jpg", first.thumbnailUrl)

        assertEquals("Wrapped in a rich item", result.items.first { it.id == "browse000002" }.title)
        assertEquals("Compact video", result.items.first { it.id == "browse000003" }.title)
        assertEquals("A short clip", result.items.first { it.id == "browse000004" }.title)
        assertTrue(result.items.first { it.id == "browse000005" }.isLive)
    }

    @Test
    fun `parseFeed reads compactVideoRenderer from search`() {
        val result = ContentParser.parseFeed(search)

        assertEquals(listOf("search000001", "search000002", "search000003"), result.items.map { it.id })
        assertEquals("Search result one", result.items.first().title)
        assertEquals("UCfixture000000000000005", result.items.first().channelId)
        assertEquals(390L, result.items.first().duration)
        assertEquals("SEARCH_CONTINUATION_TOKEN", result.continuation)
    }

    @Test
    fun `parseFeed understands Ukrainian view counts`() {
        val feed =
            """{"videoRenderer":{"videoId":"ukrainian0001","title":{"simpleText":"Ukrainian title"},"viewCountText":{"simpleText":"1,2 млн переглядів"},"thumbnail":{"thumbnails":[{"url":"https://example.invalid/uk.jpg","width":480}]}}}"""

        assertEquals(1_200_000L, ContentParser.parseFeed(feed).items.single().views)
    }

    @Test
    fun `parseVideoDetails reads streams and related videos`() {
        val parsed = checkNotNull(ContentParser.parseVideoDetails(player))

        assertEquals("player000001", parsed.video.id)
        assertEquals("Player fixture video", parsed.video.title)
        assertEquals("Fixture Channel", parsed.video.channelName)
        assertEquals(612L, parsed.video.duration)
        assertEquals(98_765L, parsed.video.views)
        assertEquals("2026-01-02", parsed.video.uploadedAt)
        assertEquals("Line one\nLine two", parsed.description)
        assertEquals(listOf("related000001", "related000002"), parsed.related.map { it.id })
        assertEquals(PlaybackSource.Progressive("https://example.invalid/progressive720.mp4"), parsed.playback)
    }

    @Test
    fun `adaptiveFormats produce a merged playback source`() {
        val root = Json.parseToJsonElement(player).jsonObject
        val streaming = root.getValue("streamingData").jsonObject
        val withoutMuxed = JsonObject(root + ("streamingData" to JsonObject(streaming - "formats"))).toString()

        val parsed = checkNotNull(ContentParser.parseVideoDetails(withoutMuxed))

        assertEquals(
            PlaybackSource.Merged(
                videoUrl = "https://example.invalid/video1080.mp4",
                audioUrl = "https://example.invalid/audio.mp4",
                audioAlternatives = listOf("https://example.invalid/audio2.webm"),
            ),
            parsed.playback,
        )
    }

    @Test
    fun `adBreaksAreIgnored`() {
        val expected = checkNotNull(ContentParser.parseVideoDetails(player)).playback
        val withAds =
            withField(
                player,
                "adBreaks",
                """[{"adBreakDuration":"PT15S","ads":[{"adId":"ad-1","adUrl":"https://example.invalid/ad.mp4"}]}]""",
            )

        assertEquals(expected, ContentParser.parseVideoDetails(withAds)?.playback)
    }

    @Test
    fun `unknownRenderersAreSkipped`() {
        val expectedCount = ContentParser.parseFeed(browse).items.size
        val withUnknown =
            withField(
                browse,
                "lockupViewModel",
                """{"contentId":"unknown","metadata":{"lockupMetadataViewModel":{"title":{"content":"Unknown"}}}}""",
            )

        val result = ContentParser.parseFeed(withUnknown)

        assertEquals(expectedCount, result.items.size)
        assertEquals("BROWSE_CONTINUATION_TOKEN", result.continuation)
    }

    @Test
    fun `parseComments reads next response`() {
        val page = ContentParser.parseComments(next)

        assertEquals(2, page.comments.size)
        assertEquals("COMMENTS_CONTINUATION_TOKEN", page.continuation)
        assertEquals(1_234L, page.totalCount)
        val first = page.comments.first()
        assertEquals("comment-1", first.id)
        assertEquals("Alice", first.author)
        assertEquals("UCfixture000000000000006", first.authorChannelId)
        assertEquals("https://example.invalid/avatar/alice.jpg", first.authorAvatarUrl)
        assertEquals("First comment", first.content)
        assertEquals(1_200L, first.likeCount)
        assertEquals("2 days ago", first.publishedText)
        assertTrue(first.isPinned)
        assertEquals("Second comment", page.comments[1].content)
    }

    @Test
    fun `parseChannel reads header and videos continuation`() {
        val parsed = ContentParser.parseChannel(channel)

        assertEquals("UCfixture000000000000001", parsed.channel.id)
        assertEquals("Fixture Channel", parsed.channel.name)
        assertEquals("https://example.invalid/avatar/176.jpg", parsed.channel.avatarUrl)
        assertEquals(123_000L, parsed.channel.subscriberCount)
        assertEquals(456, parsed.channel.totalVideos)
        assertEquals("A fixture channel for parser tests", parsed.channel.description)
        assertEquals("CHANNEL_VIDEOS_CONTINUATION_TOKEN", parsed.continuation)
    }

    @Test
    fun `malformed responses are tolerated`() {
        assertNull(ContentParser.parseFeed("not json").continuation)
        assertEquals(0, ContentParser.parseFeed("{}").items.size)
        assertNull(ContentParser.parseVideoDetails("not json"))
        assertNull(ContentParser.parseVideoDetails("""{"videoDetails":{"videoId":"player000001"}}"""))
        assertTrue(ContentParser.parseComments("not json").comments.isEmpty())
        assertEquals("", ContentParser.parseChannel("not json").channel.id)
    }

    private fun withField(
        json: String,
        key: String,
        value: String,
    ): String {
        val root = Json.parseToJsonElement(json).jsonObject
        return JsonObject(root + (key to Json.parseToJsonElement(value))).toString()
    }

    private fun resource(path: String): String = checkNotNull(javaClass.getResource(path)).readText()
}
