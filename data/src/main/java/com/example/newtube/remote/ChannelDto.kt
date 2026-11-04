package com.example.newtube.remote

/**
 * Minimal DTO for channel details from Invidious API.
 * It mirrors the fields used by the domain model.
 */

data class ChannelDto(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val subscriberCount: Long,
    val totalVideos: Int,
    val description: String
)
