package com.example.newtube.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Raw InnerTube JSON for one feed key. Stale rows stay readable while a
 * refresh runs; metadata only, never signed stream URLs.
 */
@Entity(
    tableName = "feed_cache",
    indices = [Index("cachedAt")],
)
data class FeedCacheEntity(
    @PrimaryKey val key: String,
    val json: String,
    val cachedAt: Long,
)
