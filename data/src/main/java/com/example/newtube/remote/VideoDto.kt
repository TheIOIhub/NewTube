package com.example.newtube.remote

/**
 * Minimal DTO for mapping Invidious JSON.
 * Real Invidious API returns many more fields – this is a simplified subset
 * sufficient for compilation and further mapping to the domain model.
 */
data class VideoDto(
    val id: String,
    val title: String,
    val author: String,
    val thumbnail: String,
    val duration: Long,
    val viewCount: Long,
    val uploadedAt: String,
    val live: Boolean
)
