package com.example.newtube.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val channelName: String,
    val channelId: String = "",
    @ColumnInfo(defaultValue = "")
    val authorAvatarUrl: String = "",
    val thumbnailUrl: String,
    val duration: Long,
    val views: Long,
    val uploadedAt: String,
    val isLive: Boolean,
)
