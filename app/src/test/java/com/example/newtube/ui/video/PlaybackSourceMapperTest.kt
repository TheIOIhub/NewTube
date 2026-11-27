package com.example.newtube.ui.video

import android.net.Uri
import com.example.newtube.model.PlaybackSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSourceMapperTest {
    @Test
    fun `mediaItem uses the source uri`() {
        val url = "https://x/a.m3u8"
        // JVM unit tests use the stubbed android.jar, where Uri.parse returns null.
        // Stub it so MediaItem.fromUri (the production path) keeps the real URL.
        val uri = mockk<Uri>(relaxed = true)
        every { uri.toString() } returns url
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns uri
        try {
            assertEquals(url, PlaybackSource.Hls(url).mediaItem().localConfiguration?.uri?.toString())
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `merged source keeps video and audio uris`() {
        val s = PlaybackSource.Merged("https://x/v", "https://x/a")
        assertEquals("https://x/v", s.videoUrl)
        assertEquals("https://x/a", s.audioUrl)
        assertEquals(emptyList<String>(), s.audioAlternatives)
    }

    @Test
    fun `merged source keeps language tagged audio alternatives`() {
        val s =
            PlaybackSource.Merged(
                videoUrl = "https://x/v",
                audioUrl = "https://x/a-uk",
                audioAlternatives = listOf("https://x/a-uk", "https://x/a-en"),
            )

        assertEquals(listOf("https://x/a-uk", "https://x/a-en"), s.audioAlternatives)
    }
}
