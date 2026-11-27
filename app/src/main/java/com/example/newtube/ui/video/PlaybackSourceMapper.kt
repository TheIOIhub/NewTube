package com.example.newtube.ui.video

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.newtube.model.PlaybackSource

internal fun PlaybackSource.mediaItem(): MediaItem =
    when (this) {
        is PlaybackSource.Hls -> MediaItem.fromUri(url)
        is PlaybackSource.Progressive -> MediaItem.fromUri(url)
        is PlaybackSource.Merged -> MediaItem.fromUri(videoUrl)
    }

@Suppress("UNUSED_PARAMETER")
internal fun PlaybackSource.toMediaSource(
    context: Context,
    dataSourceFactory: DataSource.Factory,
): MediaSource =
    when (this) {
        is PlaybackSource.Hls ->
            HlsMediaSource
                .Factory(dataSourceFactory)
                .createMediaSource(mediaItem())
        is PlaybackSource.Progressive ->
            ProgressiveMediaSource
                .Factory(dataSourceFactory)
                .createMediaSource(mediaItem())
        is PlaybackSource.Merged -> {
            // The chosen audio is the first adaptive track; the alternatives
            // follow it so track selection prefers the chosen one by default.
            val items =
                listOf(mediaItem()) +
                    (listOf(audioUrl) + audioAlternatives).distinct().map(MediaItem::fromUri)
            val sources =
                items.map { item ->
                    ProgressiveMediaSource
                        .Factory(dataSourceFactory)
                        .createMediaSource(item)
                }
            MergingMediaSource(*sources.toTypedArray())
        }
    }
