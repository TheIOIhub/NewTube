package com.example.newtube.repository

import androidx.paging.PagingSource
import com.example.newtube.model.Video
import kotlinx.coroutines.flow.Flow

interface ShortsCacheRepository {
    fun observeFeed(): Flow<List<Video>>

    /** Paged view of the cache, ordered by feed position. */
    fun pagingSource(): PagingSource<Int, Video>

    suspend fun replaceFeed(videos: List<Video>)

    /** Adds a page after the current highest position; never clears the feed. */
    suspend fun appendFeed(videos: List<Video>)

    suspend fun knownChannelIds(): Set<String>

    suspend fun rememberChannelIds(ids: Set<String>)
}
