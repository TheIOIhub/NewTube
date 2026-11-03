package com.example.newtube.implementation

import com.example.newtube.remote.InvidiousService
import com.example.newtube.remote.VideoDto
import com.example.newtube.database.VideoDao
import com.example.newtube.database.VideoEntity
import com.example.newtube.model.Video
import com.example.newtube.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val service: InvidiousService,
    private val dao: VideoDao
) : VideoRepository {

    override suspend fun getTrending(page: Int): List<Video> = withContext(Dispatchers.IO) {
        val dtos = service.getTrending(page = page)
        val entities = dtos.map { it.toEntity() }
        dao.insertAll(entities)
        entities.map { it.toDomain() }
    }

    override suspend fun search(query: String, page: Int): List<Video> = withContext(Dispatchers.IO) {
        val dtos = service.searchVideos(query, page = page)
        val entities = dtos.map { it.toEntity() }
        dao.insertAll(entities)
        entities.map { it.toDomain() }
    }

    override suspend fun getVideoById(videoId: String): Video = withContext(Dispatchers.IO) {
        // Try from cache first
        dao.getVideoById(videoId)?.toDomain()
            ?: service.getVideoDetails(videoId).toEntity().toDomain()
    }

    override suspend fun getChannelVideos(channelId: String, page: Int): List<Video> = withContext(Dispatchers.IO) {
        // Placeholder – Invidious currently does not provide a dedicated endpoint for channel videos
        listOf()
    }

    override suspend fun getStreamUrl(videoId: String): String = withContext(Dispatchers.IO) {
        service.getStream(videoId)
    }


    // Mapping helpers
    private fun VideoDto.toEntity(): VideoEntity = VideoEntity(
        id = id,
        title = title,
        channelName = author,
        thumbnailUrl = thumbnail,
        duration = duration,
        views = viewCount,
        uploadedAt = uploadedAt,
        isLive = live
    )

    private fun VideoEntity.toDomain(): Video = Video(
        id = id,
        title = title,
        channelName = channelName,
        thumbnailUrl = thumbnailUrl,
        duration = duration,
        views = views,
        uploadedAt = uploadedAt,
        isLive = isLive
    )
}
