package com.example.newtube.innertube

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Proves that a fetched config (not just a bundled default) drives requests. */
class RemoteConfigConsumptionTest {
    private val configServer = MockWebServer()
    private val apiServer = MockWebServer()
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
    private val http = OkHttpClient()

    @Before
    fun setUp() {
        configServer.start()
        apiServer.start()
    }

    @After
    fun tearDown() {
        configServer.shutdown()
        apiServer.shutdown()
    }

    @Test
    fun aRefreshedRemoteConfigIsConsumedByYouTubeContentClient() =
        runTest {
            val repository =
                DataStoreContentConfigRepository(
                    client = http,
                    dataStore = dataStore,
                    configUrl = configServer.url("/config.json").toString(),
                    clock = { 1_000_000L },
                )
            configServer.enqueue(
                MockResponse().setResponseCode(200).setBody(remoteConfigJson()),
            )
            apiServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"contents":{}}"""))

            assertTrue(repository.refresh().isSuccess)
            val client = YouTubeContentClient(http, repository, apiServer.url("/").toString())
            client.browse("FEtrending")

            assertEquals(1, configServer.requestCount)
            val request = apiServer.takeRequest()
            assertEquals("/youtubei/v1/browse-remote", request.path)
            val context =
                kotlinx.serialization.json.Json
                    .parseToJsonElement(request.body.readUtf8())
                    .jsonObject["context"]!!
                    .jsonObject["client"]!!
                    .jsonObject
            assertEquals("REMOTE", context["clientName"]!!.jsonPrimitive.content)
            assertEquals("de", context["hl"]!!.jsonPrimitive.content)
            assertEquals("DE", context["gl"]!!.jsonPrimitive.content)
            assertEquals("REMOTE", client.lastSuccessfulClient)
        }

    private fun remoteConfigJson() =
        """
        {"contentClients":[{"name":"REMOTE","version":"9.9","hl":"de","gl":"DE"}],
         "playerClients":[{"name":"REMOTE","version":"9.9","hl":"de","gl":"DE"}],
         "endpoints":{"/youtubei/v1/browse":"/youtubei/v1/browse-remote"},
         "ttlMillis":21600000,"fetchedAt":0}
        """.trimIndent()
}
