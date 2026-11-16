package com.example.newtube.model

/** One locally watched video. No playback position: DataStore owns resume state. */
data class WatchHistoryEntry(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val authorAvatarUrl: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val watchedAt: Long,
)
