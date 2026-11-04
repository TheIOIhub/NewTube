package com.example.newtube.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface InvidiousService {
    @GET("api/v1/videos")
    suspend fun getTrending(
        @Query("page") page: Int = 1,
        @Query("sort") sort: String = "popular",
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 25
    ): List<VideoDto>

    @GET("api/v1/videos")
    suspend fun getShorts(
        @Query("filter") filter: String = "shorts",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 25
    ): List<VideoDto>

    @GET("api/v1/videos")
    suspend fun getChannelVideos(
        @Query("channel_id") channelId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 25
    ): List<VideoDto>

    @GET("api/v1/search")
    suspend fun searchVideos(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("sort") sort: String = "relevance",
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 25
    ): List<VideoDto>

    @GET("api/v1/videos/{videoId}")
    suspend fun getVideoDetails(
        @Path("videoId") videoId: String
    ): VideoDto

    @GET("api/v1/streams/{videoId}")
    suspend fun getStream(
        @Path("videoId") videoId: String
    ): String
}
