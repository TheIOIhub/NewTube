package com.example.newtube.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Invidious REST API, see https://docs.invidious.io/api/.
 *
 * Only the endpoints used by the app are declared here. Trending feeds are not
 * paginated by Invidious, so [getTrending] takes a region instead of a page
 * number; channel uploads are paginated with an opaque continuation token.
 */
interface InvidiousService {
    @GET("api/v1/trending")
    suspend fun getTrending(
        @Query("region") region: String? = null,
        @Query("type") type: String = "Default",
        @Query("hl") hl: String? = null,
    ): List<VideoDto>

    /** Popular feed: a second, larger source for the mixed Home feed. */
    @GET("api/v1/popular")
    suspend fun getPopular(): List<VideoDto>

    /**
     * Invidious has no dedicated shorts feed, the closest equivalent is a video
     * search restricted to short clips.
     */
    @GET("api/v1/search")
    suspend fun getShorts(
        @Query("q") query: String = "#shorts",
        @Query("type") type: String = "video",
        @Query("duration") duration: String = "short",
        @Query("page") page: Int = 1,
        @Query("region") region: String? = null,
        @Query("hl") hl: String? = null,
    ): List<VideoDto>

    @GET("api/v1/search/suggestions")
    suspend fun getSearchSuggestions(
        @Query("q") query: String,
        @Query("region") region: String? = null,
        @Query("hl") hl: String? = null,
    ): SearchSuggestionsDto

    @GET("api/v1/channels/{channelId}/videos")
    suspend fun getChannelVideos(
        @Path("channelId") channelId: String,
        @Query("continuation") continuation: String? = null,
    ): ChannelVideosDto

    /** Real vertical Shorts of a channel (the only reliable Shorts source). */
    @GET("api/v1/channels/{channelId}/shorts")
    suspend fun getChannelShorts(
        @Path("channelId") channelId: String,
        @Query("continuation") continuation: String? = null,
    ): ChannelVideosDto

    @GET("api/v1/search")
    suspend fun searchVideos(
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("page") page: Int = 1,
        @Query("region") region: String? = null,
        @Query("hl") hl: String? = null,
    ): List<VideoDto>

    @GET("api/v1/search")
    suspend fun searchChannels(
        @Query("q") query: String,
        @Query("type") type: String = "channel",
        @Query("page") page: Int = 1,
        @Query("region") region: String? = null,
        @Query("hl") hl: String? = null,
    ): List<ChannelSearchDto>

    /**
     * Single video endpoint: besides metadata it returns `hlsUrl`,
     * `formatStreams`, `adaptiveFormats` and `recommendedVideos`, which is where
     * the playable URL comes from (there is no `/api/v1/streams/{id}` endpoint).
     */
    @GET("api/v1/videos/{videoId}")
    suspend fun getVideoDetails(
        @Path("videoId") videoId: String,
        @Query("region") region: String? = null,
        @Query("hl") hl: String? = null,
    ): VideoDto

    @GET("api/v1/channels/{channelId}")
    suspend fun getChannelDetails(
        @Path("channelId") channelId: String,
    ): ChannelDto

    @GET("api/v1/comments/{videoId}")
    suspend fun getComments(
        @Path("videoId") videoId: String,
        @Query("continuation") continuation: String? = null,
    ): CommentsPageDto
}
