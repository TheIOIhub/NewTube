package com.example.newtube.repository

import com.example.newtube.model.Video
import com.example.newtube.model.WatchHistoryEntry
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun observeLocal(limit: Int = 500): Flow<List<WatchHistoryEntry>>

    /** Upserts one row per video id and trims the table to the newest 500 rows. */
    suspend fun record(video: Video)

    suspend fun clearLocal()
}
