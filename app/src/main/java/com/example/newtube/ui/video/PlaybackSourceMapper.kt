package com.example.newtube.ui.video

import android.content.Context
import com.example.newtube.model.PlaybackSource
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DataSource

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
        is PlaybackSource.Merged ->
            MergingMediaSource(
                ProgressiveMediaSource
                    .Factory(dataSourceFactory)
                    .createMediaSource(mediaItem()),
                ProgressiveMediaSource
                    .Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(audioUrl)),
            )
    }
