package com.example.newtube.innertube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerTubeParserTest {
    private val history = resource("/spike/browse_FEhistory.json")
    private val subs = resource("/spike/browse_FEsubscriptions.json")
    private val account = resource("/spike/accounts_list.json")

    @Test
    fun `parses the history fixture`() {
        val r = InnerTubeParser.parseVideos(history)
        assertEquals(15, r.items.size) // 10 videos + 5 shorts
        assertNotNull(r.continuation)
        val first = r.items.first()
        assertTrue(first.id.isNotBlank())
        assertTrue(first.title.isNotBlank())
        assertEquals(15, r.items.map { it.id }.distinct().size)
    }

    @Test
    fun `parses the subscriptions feed and dedupes videos`() {
        val r = InnerTubeParser.parseVideos(subs)
        assertEquals(27, r.items.size) // 29 tiles, 2 repeated videos
        assertTrue(r.items.all { it.id.isNotBlank() })
        assertTrue(r.items.all { it.title.isNotBlank() })
        assertEquals(27, r.items.map { it.id }.distinct().size)
        assertNotNull(r.continuation)
    }

    @Test
    fun `parses a shorts tile`() {
        val r = InnerTubeParser.parseVideos(history)
        val short = r.items.first { it.id == "FmsoQUD2Sj4" }
        assertTrue(short.title.isNotBlank())
        assertTrue(short.thumbnailUrl.contains("/vi/FmsoQUD2Sj4/"))
        assertEquals(0L, short.durationMs) // shorts show an icon, not a duration
    }

    @Test
    fun `parses the account fixture`() {
        val a = InnerTubeParser.parseAccount(account)!!
        assertEquals("Test Account", a.name)
        assertEquals("user@example.com", a.email)
        assertTrue(a.avatarUrl.startsWith("https://example.invalid/"))
        assertNull(a.pageId)
    }

    @Test
    fun `tolerates unknown shapes and odd durations`() {
        assertEquals(0, InnerTubeParser.parseVideos("""{"contents":{"x":1}}""").items.size)
        assertEquals(0, InnerTubeParser.parseVideos("not json").items.size)
        assertEquals(250_000L, InnerTubeParser.durationMs("4:10"))
        assertEquals(3_723_000L, InnerTubeParser.durationMs("1:02:03"))
        assertEquals(0L, InnerTubeParser.durationMs("LIVE"))
    }

    @Test
    fun `skips entries without a video id and joins run titles`() {
        val one =
            """{"a":{"title":{"runs":[{"text":"Hello "},{"text":"world"}]},
                 "videoId":"abcdefghijk","viewCountText":{"simpleText":"1.2M views"}}}"""
        val r = InnerTubeParser.parseVideos("""{"contents":[$one]}""")
        assertEquals(1, r.items.size)
        assertEquals("Hello world", r.items[0].title)
        assertEquals("1.2M views", r.items[0].viewsText)
        val none = """{"a":{"title":{"simpleText":"no id"}}}"""
        assertEquals(0, InnerTubeParser.parseVideos("""{"contents":[$none]}""").items.size)
    }

    @Test
    fun `reads tv tile metadata and the continuation`() {
        val r = InnerTubeParser.parseVideos(history)
        val tile = r.items.first { it.id == "icNX8x7hfCA" }
        assertTrue(tile.title.startsWith("ІНОЗЕМЦІ"))
        assertEquals(598_000L, tile.durationMs)
        assertEquals("Подробиці", tile.channelName)
        assertEquals("UC9SqppNMkijU0fCJRDDg9YA", tile.channelId)
        assertTrue(tile.viewsText.startsWith("243"))
        assertTrue(tile.thumbnailUrl.startsWith("https://i.ytimg.com/vi/icNX8x7hfCA/"))
        assertEquals(false, tile.isLive)
        assertEquals(
            "4qmFsgI9EglGRWhpc3RvcnkaMENBRjZHM2x5WVRWb2QyOVBRMmQzU1d0UWVVRXlRVmxSZVV0TFJIVjNSUSUzRCUzRA%3D%3D",
            r.continuation,
        )
    }

    private fun resource(path: String): String = checkNotNull(javaClass.getResource(path)).readText()
}
