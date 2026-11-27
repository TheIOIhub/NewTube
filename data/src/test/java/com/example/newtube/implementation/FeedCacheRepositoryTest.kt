package com.example.newtube.implementation

import com.example.newtube.database.FeedCacheDao
import com.example.newtube.database.FeedCacheEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FeedCacheRepositoryTest {
    private val dao = FakeFeedCacheDao()
    private lateinit var repository: FeedCacheRepositoryImpl

    @Before
    fun setUp() {
        repository = FeedCacheRepositoryImpl(dao)
    }

    @Test
    fun `observe emits the stored JSON`() =
        runTest {
            val json = """{"contents":[]}"""
            dao.insert(FeedCacheEntity("home:ALL", json, 1L))

            assertEquals(json, repository.observe("home:ALL").first())
        }

    @Test
    fun `put overwrites the same key`() =
        runTest {
            repository.put("search:cats:1", """{"old":true}""")
            repository.put("search:cats:1", """{"new":true}""")

            assertEquals("""{"new":true}""", repository.observe("search:cats:1").first())
            assertEquals(1, dao.rows.size)
            assertTrue(dao.rows.values.single().cachedAt > 0L)
        }

    @Test
    fun `clearAll empties the table`() =
        runTest {
            repository.put("shorts", "{}")
            repository.put("home:ALL", "{}")

            repository.clearAll()

            assertNull(repository.observe("shorts").first())
            assertNull(repository.observe("home:ALL").first())
            assertTrue(dao.rows.isEmpty())
        }

    @Test
    fun `put does not throw on unparseable JSON`() =
        runTest {
            val malformed = "{not json"

            repository.put("channel:UC1", malformed)

            assertEquals(malformed, repository.observe("channel:UC1").first())
        }

    private class FakeFeedCacheDao : FeedCacheDao {
        val rows = mutableMapOf<String, FeedCacheEntity>()
        private val flows = mutableMapOf<String, MutableStateFlow<String?>>()

        override fun observe(key: String): Flow<String?> =
            flows.getOrPut(key) { MutableStateFlow(rows[key]?.json) }

        override suspend fun insert(entity: FeedCacheEntity) {
            rows[entity.key] = entity
            flows.getOrPut(entity.key) { MutableStateFlow(null) }.value = entity.json
        }

        override suspend fun clear() {
            rows.clear()
            flows.values.forEach { it.value = null }
        }
    }
}
