package com.example.newtube.remote

import com.squareup.moshi.JsonClass

/**
 * Channel details from `/api/v1/channels/{channelId}`.
 *
 * Invidious does not expose the total upload count here, only `latestVideos`,
 * so the mapper derives `totalVideos` from that preview list.
 */
@JsonClass(generateAdapter = true)
data class ChannelDto(
    val authorId: String,
    val author: String,
    val authorThumbnails: List<ThumbnailDto> = emptyList(),
    val subCount: Long = 0,
    val description: String = "",
    val latestVideos: List<VideoDto> = emptyList(),
)
