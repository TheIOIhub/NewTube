package com.example.newtube.repository

import com.example.newtube.model.AppSettings
import com.example.newtube.model.Channel
import com.example.newtube.model.ChannelVideosPage
import com.example.newtube.model.CommentPage
import com.example.newtube.model.HomeCategory
import com.example.newtube.model.Video
import com.example.newtube.model.VideoDetails
import kotlinx.coroutines.flow.Flow

/** Identifies one cached feed; its string form is the stable Room cache key. */
sealed interface FeedKey {
    data class Home(val category: HomeCategory) : FeedKey

    data object Shorts : FeedKey

    data class Search(val query: String, val page: Int) : FeedKey

    data class Channel(val channelId: String) : FeedKey

    data object Notifications : FeedKey
}

interface VideoRepository {
    /** Trending is one browse response; pagination uses its continuation token. */
    suspend fun getTrending(): List<Video>

    /** One native YouTube browse feed per Home category. */
    suspend fun getHomeFeed(category: HomeCategory): List<Video>

    /** Regionalised feed ranked by local history and subscriptions. */
    suspend fun getForYouFeed(): List<Video>

    /** Called after a region/language change; drops channel rotation and cached feeds. */
    suspend fun clearFeedCaches()

    /** Stale-while-revalidate read: emits cached videos, or empty until the first refresh. */
    fun observeFeed(key: FeedKey): Flow<List<Video>>

    /** Refreshes one feed from InnerTube; failures leave the cached feed untouched. */
    suspend fun refreshFeed(key: FeedKey): Result<List<Video>>

    /** Native Shorts feed, capped at 180 seconds (missing duration means a reel). */
    suspend fun getShorts(page: Int = 1): List<Video>

    /** One lightweight browse for the Home reel shelf; does not move the Shorts cursor. */
    suspend fun getShortsShelf(limit: Int = 8): List<Video>

    suspend fun search(query: String, page: Int = 1): List<Video>

    /** Up to a handful of query completions from YouTube; empty on failure. */
    suspend fun searchSuggestions(query: String): List<String>

    suspend fun getVideoById(videoId: String): Video

    suspend fun getVideoDetails(
        videoId: String,
        maxHeight: Int = AppSettings.DEFAULT_QUALITY,
    ): VideoDetails

    suspend fun getChannelDetails(channelId: String): Channel

    suspend fun getChannelVideos(channelId: String, continuation: String? = null): ChannelVideosPage

    suspend fun getComments(videoId: String, continuation: String? = null): CommentPage
}
