package com.example.newtube.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarUrl: String,
    val subscriberCount: Long,
    val totalVideos: Int,
    val description: String
)
