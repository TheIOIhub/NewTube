package com.example.newtube.repository

import com.example.newtube.model.Channel
import com.example.newtube.model.ChannelVideosPage
import com.example.newtube.model.CommentPage
import com.example.newtube.model.HomeCategory
import com.example.newtube.model.Video
import com.example.newtube.model.VideoDetails

interface VideoRepository {
    /** Trending is not paginated by Invidious: one request returns the whole feed. */
    suspend fun getTrending(): List<Video>

    /** Mixed feed for a Home category; sources are shuffled on every call. */
    suspend fun getHomeFeed(category: HomeCategory): List<Video>

    /** Shorts feed emulated with a `#shorts` search, filtered to 1..60 seconds. */
    suspend fun getShorts(page: Int = 1): List<Video>

    /** One lightweight `#shorts` search for the Home shelf; does not touch the Shorts tab rotation. */
    suspend fun getShortsShelf(limit: Int = 8): List<Video>

    suspend fun search(query: String, page: Int = 1): List<Video>

    suspend fun getVideoById(videoId: String): Video

    suspend fun getVideoDetails(videoId: String): VideoDetails

    suspend fun getChannelDetails(channelId: String): Channel

    suspend fun getChannelVideos(channelId: String, continuation: String? = null): ChannelVideosPage

    suspend fun getComments(videoId: String, continuation: String? = null): CommentPage
}
