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
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.example.newtube.model.PlaybackSource
import com.example.newtube.ui.video.mediaItem

/**
 * All URIs that must be fed to the player for [this] source. A [PlaybackSource.Merged]
 * has a separate audio track, so reducing it to [PlaybackSource.mediaItem] (video
 * only) would make the Shorts silent; both URIs must be registered.
 */
internal fun PlaybackSource.shortSourceUrls(): List<String> =
    when (this) {
        is PlaybackSource.Hls -> listOf(url)
        is PlaybackSource.Progressive -> listOf(url)
        is PlaybackSource.Merged -> listOf(videoUrl, audioUrl)
    }

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

    /** Resolved feed in pager order, retained so the window follows the settled index. */
    private var feed: List<PlaybackSource> = emptyList()
    private var currentIndex = 0
    private val managedItems = mutableMapOf<MediaItem, Int>()

    /**
     * Replaces the window sources. The controller keeps [feed] so [setCurrentIndex]
     * can recompute the window without waiting for the pager's `currentPage` event.
     */
    fun setItems(sources: List<PlaybackSource>) {
        onApplicationLooper {
            feed = sources
            syncManagedWindow()
        }
    }

    fun setCurrentIndex(index: Int) {
        onApplicationLooper {
            currentIndex = index
            statusControl.currentIndex = index
            preloadManager.setCurrentPlayingIndex(index)
            // The settled index may differ from the one setItems saw: re-run the
            // managed-set diff here so both forward neighbours are preloaded.
            syncManagedWindow()
        }
    }

    /** The only prepare path in Shorts; the pager never prepares the player itself. */
    fun play(source: PlaybackSource) {
        onApplicationLooper {
            val item = source.mediaItem()
            // A Merged source registered with the manager keeps its audio track;
            // the fallback also goes through shortSourceUrls(), never video-only.
            val mediaSource = preloadManager.getMediaSource(item) ?: source.asManagedMediaSource()
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

    private fun syncManagedWindow() {
        val diff =
            managedWindowDiff(
                managedItems,
                feed.map { it.mediaItem() },
                currentIndex,
            )
        diff.remove.forEach { item ->
            preloadManager.remove(item)
            managedItems.remove(item)
        }
        diff.add.forEach { (item, index) ->
            preloadManager.add(feed[index].asManagedMediaSource(), index)
            managedItems[item] = index
        }
        statusControl.itemCount = feed.size
        preloadManager.invalidate()
    }

    /**
     * Builds the source registered with the manager. Media3 1.4.1's
     * `BasePreloadManager.add(MediaSource, rank)` wraps this in a
     * `PreloadMediaSource`, whose `getMediaItem()` delegates to the first child —
     * so a MergingMediaSource is still found by `getMediaSource(video MediaItem)`.
     */
    private fun PlaybackSource.asManagedMediaSource(): MediaSource {
        val parts =
            shortSourceUrls().map { url ->
                mediaSourceFactory.createMediaSource(MediaItem.fromUri(url))
            }
        return if (parts.size == 1) parts.single() else MergingMediaSource(*parts.toTypedArray())
    }

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
