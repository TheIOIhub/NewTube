package com.example.newtube.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history", indices = [Index("watchedAt")])
data class WatchHistoryEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val authorAvatarUrl: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val watchedAt: Long,
)
