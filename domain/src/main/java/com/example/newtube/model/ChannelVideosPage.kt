package com.example.newtube.model

/**
 * One page of channel uploads. Invidious paginates this endpoint with an
 * opaque `continuation` token instead of a page number.
 */
data class ChannelVideosPage(
    val videos: List<Video>,
    val continuation: String?,
)
