package com.example.newtube.model

data class Channel(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val subscriberCount: Long,
    val totalVideos: Int
)
