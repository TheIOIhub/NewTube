package com.example.newtube.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedCacheDao {
    @Query("SELECT json FROM feed_cache WHERE key = :key")
    fun observe(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FeedCacheEntity)

    @Query("DELETE FROM feed_cache")
    suspend fun clear()
}
