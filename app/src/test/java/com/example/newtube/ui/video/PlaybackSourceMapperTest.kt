package com.example.newtube.ui.video

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.newtube.model.PlaybackSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSourceMapperTest {
    @After
    fun tearDown() {
        unmockkAll()
    }

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

    @Test
    fun `merged source builds a merging media source with all audio tracks`() {
        val videoUrl = "https://x/video.mp4"
        val chosenAudio = "https://x/audio-uk.mp4"
        val firstAlternative = "https://x/audio-en.mp4"
        val secondAlternative = "https://x/audio-de.mp4"
        val source =
            PlaybackSource.Merged(
                videoUrl = videoUrl,
                audioUrl = chosenAudio,
                audioAlternatives = listOf(firstAlternative, secondAlternative),
            )
        stubUris()

        val result =
            source.toMediaSource(mockk<Context>(relaxed = true), mockk<DataSource.Factory>(relaxed = true))

        assertEquals(MergingMediaSource::class.java, result::class.java)
        assertEquals(
            listOf(videoUrl, chosenAudio, firstAlternative, secondAlternative),
            result.childUris(),
        )
    }

    @Test
    fun `merged source deduplicates repeated audio urls`() {
        val source =
            PlaybackSource.Merged(
                videoUrl = "https://x/video.mp4",
                audioUrl = "https://x/audio-uk.mp4",
                audioAlternatives =
                    listOf("https://x/audio-uk.mp4", "https://x/audio-en.mp4", "https://x/audio-en.mp4"),
            )
        stubUris()

        val result =
            source.toMediaSource(mockk<Context>(relaxed = true), mockk<DataSource.Factory>(relaxed = true))

        assertEquals(
            listOf("https://x/video.mp4", "https://x/audio-uk.mp4", "https://x/audio-en.mp4"),
            result.childUris(),
        )
    }

    @Test
    fun `progressive source produces a progressive media source`() {
        stubUris()

        val result =
            PlaybackSource
                .Progressive("https://x/video.mp4")
                .toMediaSource(mockk<Context>(relaxed = true), mockk<DataSource.Factory>(relaxed = true))

        assertEquals(ProgressiveMediaSource::class.java, result::class.java)
    }

    @Test
    fun `hls source produces an hls media source`() {
        stubUris()

        val result =
            PlaybackSource
                .Hls("https://x/master.m3u8")
                .toMediaSource(mockk<Context>(relaxed = true), mockk<DataSource.Factory>(relaxed = true))

        assertEquals(HlsMediaSource::class.java, result::class.java)
    }

    private fun stubUris() {
        mockkStatic(Uri::class)
        every { Uri.parse(any<String>()) } answers {
            val value = firstArg<String>()
            mockk<Uri>(relaxed = true).also { every { it.toString() } returns value }
        }
    }

    /** Media3 1.4.1 keeps the MergingMediaSource children in a private field. */
    private fun MediaSource.childUris(): List<String> {
        val field = MergingMediaSource::class.java.getDeclaredField("mediaSources")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val children = field.get(this) as Array<MediaSource>
        return children.map { checkNotNull(it.mediaItem.localConfiguration?.uri).toString() }
    }
}
