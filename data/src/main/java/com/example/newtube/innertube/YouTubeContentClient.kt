package com.example.newtube.innertube

import com.example.newtube.account.YouTubeTvUserAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Every client in the configured chain returned a non-OK playability status. */
class ContentSourceUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/** Typed content calls against youtubei, with a client chain for blocked responses. */
class YouTubeContentClient(
    private val client: OkHttpClient,
    private val config: ContentConfigRepository,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    /** Name of the client that answered the last successful request, for diagnostics. */
    @Volatile
    var lastSuccessfulClient: String? = null
        private set

    suspend fun browse(
        browseId: String,
        continuation: String? = null,
    ): String =
        execute(ContentEndpoints.BROWSE) { contentClient ->
            buildJsonObject {
                put("context", context(contentClient))
                if (continuation != null) {
                    put("continuation", continuation)
                } else {
                    put("browseId", browseId)
                }
            }
        }

    suspend fun search(
        query: String,
        params: String? = null,
    ): String =
        execute(ContentEndpoints.SEARCH) { contentClient ->
            buildJsonObject {
                put("context", context(contentClient))
                put("query", query)
                if (params != null) {
                    put("params", params)
                }
            }
        }

    suspend fun player(videoId: String): String =
        execute(ContentEndpoints.PLAYER, requireOk = true) { contentClient ->
            buildJsonObject {
                put("context", context(contentClient))
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }
        }

    suspend fun next(continuation: String): String =
        execute(ContentEndpoints.NEXT) { contentClient ->
            buildJsonObject {
                put("context", context(contentClient))
                put("continuation", continuation)
            }
        }

    suspend fun suggestions(query: String): List<String> {
        val text =
            execute(ContentEndpoints.SUGGESTIONS) { contentClient ->
                buildJsonObject {
                    put("context", context(contentClient))
                    put("query", query)
                }
            }
        return Json
            .parseToJsonElement(text)
            .jsonObject["items"]
            ?.jsonArray
            .orEmpty()
            .mapNotNull { item ->
                val suggestion = item.jsonObject["suggestionRenderer"]?.jsonObject?.get("suggestion")?.jsonObject
                suggestion?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                    ?: suggestion?.get("simpleText")?.jsonPrimitive?.content
            }.filter { it.isNotBlank() }
    }

    private suspend fun execute(
        endpoint: String,
        requireOk: Boolean = false,
        body: (ContentClient) -> JsonObject,
    ): String =
        withContext(Dispatchers.IO) {
            val current = config.config.first()
            val path = current.endpoints[endpoint] ?: endpoint
            val chain =
                if (endpoint == ContentEndpoints.PLAYER) {
                    current.playerClients.ifEmpty { bundledContentConfig().playerClients }
                } else {
                    current.contentClients.ifEmpty { bundledContentConfig().contentClients }
                }
            var lastFailure: IOException? = null
            for (contentClient in chain) {
                try {
                    val response = post(path, body(contentClient), contentClient)
                    val status = playabilityStatus(response)
                    if (status == "OK" || (!requireOk && status == null)) {
                        lastSuccessfulClient = contentClient.name
                        return@withContext response
                    }
                    lastFailure =
                        ContentSourceUnavailableException(
                            "$endpoint rejected client ${contentClient.name}: ${status ?: "no status"}",
                        )
                } catch (failure: IOException) {
                    lastFailure = failure
                }
            }
            throw ContentSourceUnavailableException(
                "All ${chain.size} content clients failed for $endpoint",
                lastFailure,
            )
        }

    private fun post(
        path: String,
        body: JsonObject,
        contentClient: ContentClient,
    ): String {
        val url = if (path.startsWith("http")) path else baseUrl.trimEnd('/') + path
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", userAgent(contentClient))
                .post(body.toString().toRequestBody(JSON))
                .build()
        return client
            .newCall(request)
            .execute()
            .use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("InnerTube $path answered HTTP ${response.code}")
                }
                text
            }
    }

    private fun context(contentClient: ContentClient) =
        buildJsonObject {
            put(
                "client",
                buildJsonObject {
                    put("clientName", contentClient.name)
                    put("clientVersion", contentClient.version)
                    put("hl", contentClient.hl)
                    put("gl", contentClient.gl)
                },
            )
        }

    private fun playabilityStatus(response: String): String? =
        try {
            Json
                .parseToJsonElement(response)
                .jsonObject["playabilityStatus"]
                ?.jsonObject
                ?.get("status")
                ?.jsonPrimitive
                ?.content
        } catch (_: Exception) {
            null
        }

    private fun userAgent(contentClient: ContentClient): String =
        if (contentClient.name == "TVHTML5") {
            YouTubeTvUserAgent.VALUE
        } else {
            "com.google.android.youtube/${contentClient.version} (Linux; U; Android 12; en_US) gzip"
        }

    private companion object {
        const val DEFAULT_BASE_URL = "https://www.youtube.com"
        val JSON = "application/json".toMediaType()
    }
}
