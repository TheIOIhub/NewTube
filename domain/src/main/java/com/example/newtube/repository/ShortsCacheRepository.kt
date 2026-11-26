package com.example.newtube.repository

import com.example.newtube.model.Video
import kotlinx.coroutines.flow.Flow

interface ShortsCacheRepository {
    fun observeFeed(): Flow<List<Video>>

    suspend fun replaceFeed(videos: List<Video>)

    suspend fun knownChannelIds(): Set<String>

    suspend fun rememberChannelIds(ids: Set<String>)
}
