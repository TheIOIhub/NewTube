package com.example.newtube.remote

import com.squareup.moshi.JsonClass

/**
 * Wrapper returned by `/api/v1/channels/{channelId}/videos` (and `/shorts`,
 * `/streams`): the uploads themselves plus an opaque continuation token.
 */
@JsonClass(generateAdapter = true)
data class ChannelVideosDto(
    val videos: List<VideoDto> = emptyList(),
    val continuation: String? = null,
)
