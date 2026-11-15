package com.example.newtube.remote

import com.squareup.moshi.JsonClass

/**
 * Video item as returned by `/api/v1/trending`, `/api/v1/search`,
 * `/api/v1/channels/{id}/videos` and `/api/v1/videos/{id}`.
 *
 * The Invidious field names are intentionally kept as-is (`videoId`, `author`,
 * `lengthSeconds`, `liveNow`) instead of being renamed with `@Json`, so that the
 * payload shape stays obvious. Search results are mixed: channels and playlists
 * come back in the same array without a `videoId`, hence the nullable fields —
 * [com.example.newtube.implementation.VideoRepositoryImpl] filters those out.
 *
 * `description` and `recommendedVideos` are only filled in by the single-video
 * endpoint; `recommendedVideos` entries lack `type` and `viewCount`.
 */
@JsonClass(generateAdapter = true)
data class VideoDto(
    val type: String? = null,
    val videoId: String? = null,
    val title: String = "",
    val author: String = "",
    val authorId: String = "",
    val authorThumbnails: List<ThumbnailDto> = emptyList(),
    val videoThumbnails: List<ThumbnailDto> = emptyList(),
    val description: String = "",
    val publishedText: String = "",
    val viewCount: Long = 0,
    val lengthSeconds: Long = 0,
    val liveNow: Boolean = false,
    // Only present in the /api/v1/videos/{videoId} response
    val hlsUrl: String? = null,
    val formatStreams: List<FormatStreamDto> = emptyList(),
    val adaptiveFormats: List<FormatStreamDto> = emptyList(),
    val recommendedVideos: List<VideoDto> = emptyList(),
)
