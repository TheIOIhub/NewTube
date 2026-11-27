package com.example.newtube.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ShortsFeedDao : ShortsFeedPagingDao {
    @Query("SELECT * FROM shorts_feed ORDER BY position ASC")
    fun observeAll(): Flow<List<ShortsFeedEntity>>

    @Transaction
    suspend fun replaceAll(items: List<ShortsFeedEntity>) {
        clear()
        insertAll(items)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ShortsFeedEntity>)

    @Query("DELETE FROM shorts_feed")
    suspend fun clear()
}
