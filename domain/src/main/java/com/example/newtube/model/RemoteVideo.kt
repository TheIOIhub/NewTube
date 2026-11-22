package com.example.newtube.model

data class RemoteVideo(
    val id: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val viewsText: String,
    val publishedText: String,
    val isLive: Boolean,
)
