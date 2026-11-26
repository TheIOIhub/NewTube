package com.example.newtube.ui.shorts

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRendererCapabilitiesList
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.example.newtube.model.PlaybackSource
import com.example.newtube.ui.video.mediaItem

/**
 * Owns the single player used by the Shorts pager and the preload manager that
 * feeds it. Media3 1.4.1 has no [DefaultPreloadManager.Builder], so the shared
 * components are created once here and handed to both objects explicitly; this
 * is the equivalent of `buildExoPlayer()` on newer Media3 versions.
 *
 * All manager and player calls happen on the application looper, which is also
 * the looper the manager preloads on, so no cross-thread access is possible.
 */
class Media3ShortsPlaybackController(
    context: Context,
    dataSourceFactory: DataSource.Factory,
) {
    private val applicationContext = context.applicationContext
    private val looper = Looper.getMainLooper()
    private val statusControl = ShortsPreloadStatusControl()

    // Media3 1.4.1 rejects minBufferMs < bufferForPlaybackAfterRebufferMs, so
    // the effective minimum is 1500 ms.
    private val loadControl =
        DefaultLoadControl
            .Builder()
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(
                maxOf(1_000, 1_500),
                5_000,
                1_000,
                1_500,
            ).setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(PRELOAD_TARGET_BUFFER_BYTES)
            .build()
    private val bandwidthMeter = DefaultBandwidthMeter.getSingletonInstance(applicationContext)
    private val renderersFactory: RenderersFactory = DefaultRenderersFactory(applicationContext)
    private val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
    private val trackSelector = DefaultTrackSelector(applicationContext)

    /** Built before the manager so the track selector is initialised by ExoPlayer. */
    val player: ExoPlayer =
        ExoPlayer
            .Builder(applicationContext)
            .setLooper(looper)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .build()
            .apply { repeatMode = Player.REPEAT_MODE_ONE }

    val preloadManager: DefaultPreloadManager =
        DefaultPreloadManager(
            statusControl,
            mediaSourceFactory,
            trackSelector,
            bandwidthMeter,
            DefaultRendererCapabilitiesList.Factory(renderersFactory),
            loadControl.allocator,
            looper,
        )

    private val managedItems = mutableMapOf<MediaItem, Int>()

    /** Keeps the manager's window aligned with the resolved feed around [currentIndex]. */
    fun setItems(items: List<MediaItem>) {
        onApplicationLooper {
            val window = preloadWindow(currentIndex, items.size)
            val wanted = window.map { items[it] }.toSet()
            managedItems.keys.toList().forEach { item ->
                if (item !in wanted) {
                    preloadManager.remove(item)
                    managedItems.remove(item)
                }
            }
            for (index in window) {
                val item = items[index]
                if (managedItems[item] == index) continue
                if (managedItems.containsKey(item)) preloadManager.remove(item)
                preloadManager.add(item, index)
                managedItems[item] = index
            }
            statusControl.itemCount = items.size
            preloadManager.invalidate()
        }
    }

    fun setCurrentIndex(index: Int) {
        onApplicationLooper {
            currentIndex = index
            statusControl.currentIndex = index
            preloadManager.setCurrentPlayingIndex(index)
            preloadManager.invalidate()
        }
    }

    /** The only prepare path in Shorts; the pager never prepares the player itself. */
    fun play(source: PlaybackSource) {
        onApplicationLooper {
            val item = source.mediaItem()
            val mediaSource = preloadManager.getMediaSource(item) ?: mediaSourceFactory.createMediaSource(item)
            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true
        }
    }

    fun stop() {
        onApplicationLooper {
            player.stop()
            player.clearMediaItems()
        }
    }

    /** The manager must release its sources before the player is torn down. */
    fun release() {
        preloadManager.release()
        player.release()
    }

    private var currentIndex = 0

    private fun onApplicationLooper(action: () -> Unit) {
        if (Looper.myLooper() == looper) {
            action()
        } else {
            Handler(looper).post(action)
        }
    }

    private class ShortsPreloadStatusControl : TargetPreloadStatusControl<Int> {
        var currentIndex: Int = 0
        var itemCount: Int = 0

        override fun getTargetPreloadStatus(rankingData: Int): TargetPreloadStatusControl.PreloadStatus? =
            if (rankingData in preloadWindow(currentIndex, itemCount)) {
                specifiedRangeLoaded(PRELOAD_DURATION_MS)
            } else {
                null
            }
    }

    private companion object {
        const val PRELOAD_DURATION_MS = 3_000L
        const val PRELOAD_TARGET_BUFFER_BYTES = 96 * 1024 * 1024
    }
}

/**
 * Media3 1.4.1 calls this stage [DefaultPreloadManager.Status.STAGE_LOADED_TO_POSITION_MS]
 * and measures a position from the default start, which is the same range as
 * the duration-based `specifiedRangeLoaded` on newer Media3 versions.
 */
private fun specifiedRangeLoaded(durationMs: Long): TargetPreloadStatusControl.PreloadStatus =
    DefaultPreloadManager.Status(
        DefaultPreloadManager.Status.STAGE_LOADED_TO_POSITION_MS,
        durationMs,
    )
