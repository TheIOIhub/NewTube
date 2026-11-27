package com.example.newtube.model

/**
 * Everything the watch screen needs: the video itself, its description, the
 * videos recommended by YouTube and a resolved playback source.
 */
data class VideoDetails(
    val video: Video,
    val description: String,
    val related: List<Video>,
    val playback: PlaybackSource,
)

/**
 * Where the player should get the media from.
 *
 * InnerTube responses vary: some expose an HLS manifest, some muxed progressive
 * tracks, and some only DASH tracks where audio and video are separate.
 */
sealed interface PlaybackSource {
    data class Hls(val url: String) : PlaybackSource

    data class Progressive(val url: String) : PlaybackSource

    data class Merged(
        val videoUrl: String,
        val audioUrl: String,
        val audioAlternatives: List<String> = emptyList(),
    ) : PlaybackSource
}
