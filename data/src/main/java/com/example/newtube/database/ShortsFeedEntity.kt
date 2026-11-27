package com.example.newtube.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.newtube.model.Video

/**
 * Cached Shorts feed row. Metadata only — signed stream URLs are never
 * persisted here; they are resolved fresh at playback time.
 */
@Entity(
    tableName = "shorts_feed",
    indices = [Index("position")],
)
data class ShortsFeedEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String,
    val duration: Long,
    val views: Long,
    val uploadedAt: String,
    val isLive: Boolean,
    val authorAvatarUrl: String,
    val position: Int,
    val cachedAt: Long,
)

internal fun ShortsFeedEntity.toVideo(): Video =
    Video(
        id = videoId,
        title = title,
        channelName = channelName,
        channelId = channelId,
        thumbnailUrl = thumbnailUrl,
        duration = duration,
        views = views,
        uploadedAt = uploadedAt,
        isLive = isLive,
        authorAvatarUrl = authorAvatarUrl,
    )

internal fun Video.toShortsFeedEntity(
    position: Int,
    cachedAt: Long,
): ShortsFeedEntity =
    ShortsFeedEntity(
        videoId = id,
        title = title,
        channelName = channelName,
        channelId = channelId,
        thumbnailUrl = thumbnailUrl,
        duration = duration,
        views = views,
        uploadedAt = uploadedAt,
        isLive = isLive,
        authorAvatarUrl = authorAvatarUrl,
        position = position,
        cachedAt = cachedAt,
    )
