package com.example.newtube.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Upsert
    suspend fun upsert(entry: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WatchHistoryEntity>>

    @Query("DELETE FROM watch_history")
    suspend fun clear()

    @Query(
        "DELETE FROM watch_history WHERE videoId NOT IN " +
            "(SELECT videoId FROM watch_history ORDER BY watchedAt DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int)
}
