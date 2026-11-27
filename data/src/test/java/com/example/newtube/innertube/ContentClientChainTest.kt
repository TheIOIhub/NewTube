package com.example.newtube.innertube

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContentClientChainTest {
    private val server = MockWebServer()
    private val configFlow = MutableStateFlow(bundledContentConfig())
    private val repository =
        object : ContentConfigRepository {
            override val config: Flow<ContentConfig> = configFlow
            override suspend fun refresh(): Result<ContentConfig> = Result.success(configFlow.value)

            override fun bundled(): ContentConfig = bundledContentConfig()

            override fun decodeConfig(text: String): ContentConfig? = null
        }
    private lateinit var client: YouTubeContentClient

    @Before
    fun setUp() {
        server.start()
        client = YouTubeContentClient(OkHttpClient(), repository, server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun unplayablePlayerAdvancesToAndroidVr() =
        runTest {
            server.enqueue(json("""{"playabilityStatus":{"status":"UNPLAYABLE"}}"""))
            server.enqueue(json("""{"playabilityStatus":{"status":"OK","videoDetails":{"videoId":"dQw4w9WgXcQ"}}}"""))

            assertTrue(client.player("dQw4w9WgXcQ").contains(""""status":"OK""""))

            val first = server.takeRequest()
            val second = server.takeRequest()
            assertEquals("POST", first.method)
            assertEquals("/youtubei/v1/player", first.path)
            assertEquals("ANDROID", clientName(first))
            assertEquals("20.10.38", clientVersion(first))
            assertEquals("ANDROID_VR", clientName(second))
            assertEquals("1.60.19", clientVersion(second))
            assertTrue(body(first).contains(""""contentCheckOk":true"""))
            assertTrue(body(first).contains(""""racyCheckOk":true"""))
            assertEquals("ANDROID_VR", client.lastSuccessfulClient)
        }

    @Test
    fun threeUnplayableResponsesExhaustTheChain() =
        runTest {
            repeat(3) { server.enqueue(json("""{"playabilityStatus":{"status":"UNPLAYABLE"}}""")) }

            try {
                client.player("dQw4w9WgXcQ")
                throw AssertionError("expected ContentSourceUnavailableException")
            } catch (expected: ContentSourceUnavailableException) {
                assertEquals(3, server.requestCount)
                assertNull(client.lastSuccessfulClient)
            }
        }

    @Test
    fun browseSearchAndNextSendTheirOwnParameters() =
        runTest {
            repeat(3) { server.enqueue(json("""{"contents":{}}""")) }

            client.browse("FEtrending")
            client.search("test", params = "EgQIARAB")
            client.next("continuation-token")

            val browse = server.takeRequest()
            val search = server.takeRequest()
            val next = server.takeRequest()
            assertEquals("/youtubei/v1/browse", browse.path)
            assertEquals("ANDROID", clientName(browse))
            assertTrue(body(browse).contains(""""browseId":"FEtrending""""))
            assertEquals("/youtubei/v1/search", search.path)
            assertTrue(body(search).contains(""""query":"test""""))
            assertTrue(body(search).contains(""""params":"EgQIARAB""""))
            assertEquals("/youtubei/v1/next", next.path)
            assertTrue(body(next).contains(""""continuation":"continuation-token""""))
        }

    @Test
    fun suggestionsAreReadFromRendererRuns() =
        runTest {
            server.enqueue(
                json(
                    """{"items":[{"suggestionRenderer":{"suggestion":{"runs":[{"text":"test one"}]}}},""" +
                        """{"suggestionRenderer":{"suggestion":{"simpleText":"test two"}}}]}""",
                ),
            )

            assertEquals(listOf("test one", "test two"), client.suggestions("test"))
            val request = server.takeRequest()
            assertEquals("/youtubei/v1/search/suggestions", request.path)
            assertTrue(body(request).contains(""""query":"test""""))
        }

    @Test
    fun tvClientsUseTheTvUserAgentOnly() =
        runTest {
            configFlow.value =
                bundledContentConfig().copy(
                    playerClients = listOf(ContentClient("TVHTML5", "7.20260901.15.00")),
                )
            server.enqueue(json("""{"playabilityStatus":{"status":"OK"}}"""))

            client.player("dQw4w9WgXcQ")

            val request = server.takeRequest()
            assertTrue(request.getHeader("User-Agent")!!.contains("Cobalt/22.lts.3.306369"))
            assertEquals("TVHTML5", clientName(request))
        }

    private fun json(body: String) =
        MockResponse()
            .setResponseCode(200)
            .setBody(body)
            .addHeader("Content-Type", "application/json")

    private val bodies = mutableMapOf<RecordedRequest, String>()

    private fun body(request: RecordedRequest): String = bodies.getOrPut(request) { request.body.readUtf8() }

    private fun clientObject(request: RecordedRequest) =
        kotlinx.serialization.json.Json
            .parseToJsonElement(body(request))
            .jsonObject["context"]!!
            .jsonObject["client"]!!
            .jsonObject

    private fun clientName(request: RecordedRequest): String = clientObject(request)["clientName"]!!.jsonPrimitive.content

    private fun clientVersion(request: RecordedRequest): String =
        clientObject(request)["clientVersion"]!!.jsonPrimitive.content
}
