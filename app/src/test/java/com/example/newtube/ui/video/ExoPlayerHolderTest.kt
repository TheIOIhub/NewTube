package com.example.newtube.ui.video

import android.content.Context
import android.net.Uri
import android.os.Looper
import com.example.newtube.model.PlaybackSource
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.upstream.DataSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ExoPlayerHolderTest {
    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `buffer durations are tuned for short clips`() {
        assertEquals(
            BufferDurations(1_000, 5_000, 1_000, 1_500),
            SHORTS_BUFFER_DURATIONS,
        )
        assertEquals(DefaultLoadControl::class.java, shortsLoadControl()::class.java)
    }

    @Test
    fun `prepareSequence stops before replacing the source`() {
        val player = mockk<ExoPlayer>(relaxed = true)
        val holder = holderWith(player)
        stubUri("https://x/a.mp4")

        holder.prepare(PlaybackSource.Progressive("https://x/a.mp4"))

        verifyOrder {
            player.stop()
            player.clearMediaItems()
            player.setMediaSource(any())
            player.prepare()
            player.setPlayWhenReady(true)
        }
    }

    @Test
    fun `stop clears the media source`() {
        val player = mockk<ExoPlayer>(relaxed = true)
        val holder = holderWith(player)

        holder.stop()

        verifyOrder {
            player.stop()
            player.clearMediaItems()
        }
    }

    private fun holderWith(player: ExoPlayer): ExoPlayerHolder {
        // Make the current thread look like the player's application looper so
        // the dispatched calls run inline in a JVM test.
        val looper = mockk<Looper>(relaxed = true)
        every { player.applicationLooper } returns looper
        mockkStatic(Looper::class)
        every { Looper.myLooper() } returns looper
        val builder = mockk<ExoPlayer.Builder>(relaxed = true)
        mockkConstructor(ExoPlayer.Builder::class)
        every { anyConstructed<ExoPlayer.Builder>().setLoadControl(any()) } returns builder
        every { builder.build() } returns player
        return ExoPlayerHolder(
            mockk<Context>(),
            mockk<DataSource.Factory>(relaxed = true),
            shortsLoadControl(),
        )
    }

    private fun stubUri(url: String) {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.toString() } returns url
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns uri
    }
}
