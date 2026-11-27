package com.example.newtube.innertube

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContentConfigRepositoryTest {
    private val server = MockWebServer()
    private val stored = MutableStateFlow<Preferences>(emptyPreferences())
    private val dataStore =
        mockk<DataStore<Preferences>>(relaxed = true) {
            every { data } returns stored
            coEvery { updateData(any()) } coAnswers {
                val transform = firstArg<suspend (Preferences) -> Preferences>()
                val result = transform(stored.value)
                stored.value = result
                result
            }
        }
    private lateinit var repository: DataStoreContentConfigRepository

    @Before
    fun setUp() {
        server.start()
        repository =
            DataStoreContentConfigRepository(
                client = OkHttpClient(),
                dataStore = dataStore,
                configUrl = server.url("/config.json").toString(),
                clock = { FIXED_TIME },
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun bundledStartsWithTheAndroidClient() {
        val bundled = repository.bundled()
        assertEquals("ANDROID", bundled.contentClients[0].name)
        assertEquals("20.10.38", bundled.contentClients[0].version)
        assertEquals(listOf("ANDROID", "ANDROID_VR", "IOS"), bundled.playerClients.map { it.name })
    }

    @Test
    fun decodeConfigReadsAClientFromJson() {
        val config =
            checkNotNull(
                repository.decodeConfig(
                    """{"contentClients":[{"name":"ANDROID_VR","version":"1.60.19"}],""" +
                        """"playerClients":[],"endpoints":{},"ttlMillis":21600000,"fetchedAt":0}""",
                ),
            )
        assertEquals("ANDROID_VR", config.contentClients[0].name)
        assertEquals("1.60.19", config.contentClients[0].version)
    }

    @Test
    fun http500LeavesThePreviousConfigUnchanged() =
        runTest {
            stored.value = preferencesOf(CONTENT_CONFIG_KEY to cachedJson())
            server.enqueue(MockResponse().setResponseCode(500).setBody("failed"))

            assertTrue(repository.refresh().isFailure)
            assertEquals("CACHED", repository.config.first().contentClients[0].name)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun successfulRefreshIsCachedAndNotRepeatedWithinTtl() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """{"contentClients":[{"name":"REMOTE","version":"9.9"}],""" +
                            """"playerClients":[{"name":"REMOTE","version":"9.9"}],""" +
                            """"endpoints":{},"ttlMillis":21600000,"fetchedAt":0}""",
                    ),
            )

            val first = repository.refresh().getOrThrow()
            assertEquals("REMOTE", first.contentClients[0].name)
            assertEquals("REMOTE", repository.config.first().contentClients[0].name)
            assertEquals(FIXED_TIME, repository.config.first().fetchedAt)

            val second = repository.refresh().getOrThrow()
            assertEquals("REMOTE", second.contentClients[0].name)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun emptyClientListsAreNotCached() =
        runTest {
            stored.value = preferencesOf(CONTENT_CONFIG_KEY to cachedJson())
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """{"contentClients":[],"playerClients":[{"name":"REMOTE","version":"9.9"}],""" +
                            """"endpoints":{},"ttlMillis":21600000,"fetchedAt":0}""",
                    ),
            )

            assertTrue(repository.refresh().isFailure)
            assertEquals("CACHED", repository.config.first().contentClients[0].name)
        }

    private fun cachedJson(): String =
        """
        {"contentClients":[{"name":"CACHED","version":"1.0","hl":"en","gl":"US"}],
         "playerClients":[{"name":"ANDROID","version":"20.10.38"}],
         "endpoints":{},"ttlMillis":21600000,"fetchedAt":0}
        """.trimIndent()

    private companion object {
        const val FIXED_TIME = 1_000_000L
    }
}
