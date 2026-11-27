package com.example.newtube.implementation

import com.example.newtube.database.FeedCacheDao
import com.example.newtube.database.FeedCacheEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Raw-JSON cache contract: keys are stable across processes and app restarts. */
interface FeedCacheRepository {
    fun observe(key: String): Flow<String?>

    suspend fun put(key: String, json: String)

    suspend fun clearAll()
}

/** Stores InnerTube responses verbatim; parsing stays in the caller. */
@Singleton
class FeedCacheRepositoryImpl @Inject constructor(
    private val dao: FeedCacheDao,
) : FeedCacheRepository {
    override fun observe(key: String): Flow<String?> = dao.observe(key)

    override suspend fun put(key: String, json: String) {
        dao.insert(FeedCacheEntity(key = key, json = json, cachedAt = System.currentTimeMillis()))
    }

    override suspend fun clearAll() {
        dao.clear()
    }
}
