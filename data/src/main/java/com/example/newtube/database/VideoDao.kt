package com.example.newtube.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VideoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(videos: List<VideoEntity>)

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getVideoById(id: String): VideoEntity?

    @Query("SELECT * FROM videos ORDER BY uploadedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getTrending(limit: Int = 25, offset: Int = 0): List<VideoEntity>
}
