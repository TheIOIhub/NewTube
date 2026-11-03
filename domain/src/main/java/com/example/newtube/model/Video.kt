package com.example.newtube.model

data class Video(
    val id: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val duration: Long,
    val views: Long,
    val uploadedAt: String,
    val isLive: Boolean
)
