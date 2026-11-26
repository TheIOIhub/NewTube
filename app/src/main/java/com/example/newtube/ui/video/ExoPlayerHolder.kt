package com.example.newtube.ui.video

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.newtube.model.PlaybackSource
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.LoadControl
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.upstream.DataSource

internal data class BufferDurations(
    val min: Int,
    val max: Int,
    val forPlayback: Int,
    val afterRebuffer: Int,
)

internal val SHORTS_BUFFER_DURATIONS =
    BufferDurations(
        min = 1_000,
        max = 5_000,
        forPlayback = 1_000,
        afterRebuffer = 1_500,
    )

internal fun shortsLoadControl(): DefaultLoadControl =
    DefaultLoadControl
        .Builder()
        .setBufferDurationsMs(
            // ExoPlayer 2.19 rejects minBufferMs < bufferForPlaybackAfterRebufferMs.
            maxOf(SHORTS_BUFFER_DURATIONS.min, SHORTS_BUFFER_DURATIONS.afterRebuffer),
            SHORTS_BUFFER_DURATIONS.max,
            SHORTS_BUFFER_DURATIONS.forPlayback,
            SHORTS_BUFFER_DURATIONS.afterRebuffer,
        ).setPrioritizeTimeOverSizeThresholds(true)
        .build()

/** One ExoPlayer reused by every Shorts page; the watch screen owns its own. */
class ExoPlayerHolder(
    private val context: Context,
    private val dataSourceFactory: DataSource.Factory,
    loadControl: LoadControl,
) {
    val player: ExoPlayer =
        ExoPlayer
            .Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply { repeatMode = Player.REPEAT_MODE_ONE }

    fun prepare(source: PlaybackSource) {
        onApplicationLooper {
            stopPlayer()
            player.setMediaSource(source.toMediaSource(context, dataSourceFactory))
            player.prepare()
            player.playWhenReady = true
        }
    }

    fun stop() {
        onApplicationLooper { stopPlayer() }
    }

    fun release() {
        player.release()
    }

    private fun stopPlayer() {
        player.stop()
        player.clearMediaItems()
    }

    private fun onApplicationLooper(action: () -> Unit) {
        val applicationLooper = player.applicationLooper
        if (Looper.myLooper() == applicationLooper) {
            action()
        } else {
            Handler(applicationLooper).post(action)
        }
    }
}
