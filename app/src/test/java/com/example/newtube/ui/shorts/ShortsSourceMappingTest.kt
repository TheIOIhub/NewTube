package com.example.newtube.ui.shorts

import com.example.newtube.model.PlaybackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ShortsSourceMappingTest {
    @Test
    fun `merged short exposes both video and audio urls`() {
        val source = PlaybackSource.Merged("https://x/v.mp4", "https://x/a.mp4")

        assertEquals(listOf("https://x/v.mp4", "https://x/a.mp4"), source.shortSourceUrls())
    }

    @Test
    fun `merged short is not reduced to a single uri on the shorts path`() {
        val source = PlaybackSource.Merged("https://x/v.mp4", "https://x/a.mp4")

        // The primary MediaItem only carries the video URL, so the Shorts path
        // must consult shortSourceUrls() instead of mediaItem() alone.
        assertEquals(2, source.shortSourceUrls().size)
        assertNotEquals(source.shortSourceUrls().take(1), source.shortSourceUrls())
    }

    @Test
    fun `single stream shorts expose one url`() {
        assertEquals(
            listOf("https://x/a.m3u8"),
            PlaybackSource.Hls("https://x/a.m3u8").shortSourceUrls(),
        )
        assertEquals(
            listOf("https://x/v.mp4"),
            PlaybackSource.Progressive("https://x/v.mp4").shortSourceUrls(),
        )
    }
}
