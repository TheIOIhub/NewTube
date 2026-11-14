package com.example.newtube.model

/**
 * Everything the watch screen needs: the video itself, its description, the
 * videos recommended by the instance and a resolved playback source.
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
 * Invidious is inconsistent between instances: some return an HLS manifest,
 * some only progressive `formatStreams`, and some only DASH (`adaptiveFormats`)
 * tracks where audio and video are separate.
 */
sealed interface PlaybackSource {
    data class Hls(val url: String) : PlaybackSource

    data class Progressive(val url: String) : PlaybackSource

    data class Merged(val videoUrl: String, val audioUrl: String) : PlaybackSource
}
