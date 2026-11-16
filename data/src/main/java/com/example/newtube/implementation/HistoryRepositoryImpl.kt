package com.example.newtube.implementation

import com.example.newtube.database.WatchHistoryDao
import com.example.newtube.database.WatchHistoryEntity
import com.example.newtube.model.Video
import com.example.newtube.model.WatchHistoryEntry
import com.example.newtube.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val dao: WatchHistoryDao,
) : HistoryRepository {
    override fun observeLocal(limit: Int): Flow<List<WatchHistoryEntry>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun record(video: Video) {
        dao.upsert(
            WatchHistoryEntity(
                videoId = video.id,
                title = video.title,
                channelName = video.channelName,
                channelId = video.channelId,
                authorAvatarUrl = video.authorAvatarUrl,
                thumbnailUrl = video.thumbnailUrl,
                durationMs = video.duration * 1_000L,
                watchedAt = System.currentTimeMillis(),
            ),
        )
        dao.trim(HISTORY_LIMIT)
    }

    override suspend fun clearLocal() {
        dao.clear()
    }

    private fun WatchHistoryEntity.toDomain(): WatchHistoryEntry =
        WatchHistoryEntry(
            videoId = videoId,
            title = title,
            channelName = channelName,
            channelId = channelId,
            authorAvatarUrl = authorAvatarUrl,
            thumbnailUrl = thumbnailUrl,
            durationMs = durationMs,
            watchedAt = watchedAt,
        )

    private companion object {
        const val HISTORY_LIMIT = 500
    }
}
