package com.example.newtube.repository

import com.example.newtube.model.Video
import com.example.newtube.model.Channel

interface VideoRepository {
    suspend fun getTrending(page: Int = 1): List<Video>
    suspend fun getShorts(page: Int = 1): List<Video>
    suspend fun search(query: String, page: Int = 1): List<Video>
    suspend fun getVideoById(videoId: String): Video
    suspend fun getChannelVideos(channelId: String, page: Int = 1): List<Video>
    suspend fun getStreamUrl(videoId: String): String
    suspend fun getChannelDetails(channelId: String): Channel
}
