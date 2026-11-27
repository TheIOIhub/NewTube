package com.example.newtube.innertube

import com.example.newtube.account.YouTubeTvUserAgent
import com.example.newtube.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
    private val settingsRepository: SettingsRepository? = null,
) {
    /** Null fields fall back to the hl/gl of the configured client. */
    private data class ContentLocale(
        val hl: String?,
        val gl: String?,
    )

    private sealed interface ResponseStatus {
        data object Ok : ResponseStatus

        data class Rejected(val status: String) : ResponseStatus

        data object Missing : ResponseStatus

        data object Malformed : ResponseStatus
    }

    /** Name of the client that answered the last successful request, for diagnostics. */
    @Volatile
    var lastSuccessfulClient: String? = null
        private set

    suspend fun browse(
        browseId: String,
        continuation: String? = null,
    ): String =
        execute(ContentEndpoints.BROWSE) { contentClient, locale ->
            buildJsonObject {
                put("context", context(contentClient, locale))
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
        continuation: String? = null,
    ): String =
        execute(ContentEndpoints.SEARCH) { contentClient, locale ->
            buildJsonObject {
                put("context", context(contentClient, locale))
                if (continuation != null) {
                    put("continuation", continuation)
                } else {
                    put("query", query)
                    if (params != null) {
                        put("params", params)
                    }
                }
            }
        }

    suspend fun player(videoId: String): String =
        execute(ContentEndpoints.PLAYER, requireOk = true) { contentClient, locale ->
            buildJsonObject {
                put("context", context(contentClient, locale))
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }
        }

    suspend fun next(continuation: String): String =
        execute(ContentEndpoints.NEXT) { contentClient, locale ->
            buildJsonObject {
                put("context", context(contentClient, locale))
                put("continuation", continuation)
            }
        }

    suspend fun suggestions(query: String): List<String> {
        val text =
            execute(ContentEndpoints.SUGGESTIONS) { contentClient, locale ->
                buildJsonObject {
                    put("context", context(contentClient, locale))
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
        body: (ContentClient, ContentLocale) -> JsonObject,
    ): String =
        withContext(Dispatchers.IO) {
            val current = config.config.first()
            val settings = settingsRepository?.settings?.first()
            val locale =
                ContentLocale(
                    hl = settings?.contentLanguage?.takeIf { it.isNotBlank() },
                    gl = settings?.contentCountry?.takeIf { it.isNotBlank() },
                )
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
                    val response = post(path, body(contentClient, locale), contentClient)
                    when (val status = responseStatus(response)) {
                        ResponseStatus.Ok -> {
                            lastSuccessfulClient = contentClient.name
                            return@withContext response
                        }
                        is ResponseStatus.Rejected ->
                            lastFailure =
                                ContentSourceUnavailableException(
                                    "$endpoint rejected client ${contentClient.name}: ${status.status}",
                                )
                        ResponseStatus.Missing ->
                            if (!requireOk) {
                                lastSuccessfulClient = contentClient.name
                                return@withContext response
                            } else {
                                lastFailure =
                                    ContentSourceUnavailableException(
                                        "$endpoint returned no playability status",
                                    )
                            }
                        ResponseStatus.Malformed ->
                            lastFailure =
                                ContentSourceUnavailableException("$endpoint returned an unparseable response")
                    }
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

    private fun context(
        contentClient: ContentClient,
        locale: ContentLocale,
    ) = buildJsonObject {
        put(
            "client",
            buildJsonObject {
                put("clientName", contentClient.name)
                put("clientVersion", contentClient.version)
                put("hl", locale.hl ?: contentClient.hl)
                put("gl", locale.gl ?: contentClient.gl)
            },
        )
    }

    /**
     * A present rejection (or a non-JSON body such as a bot-check page) must
     * advance the chain. Only a well-formed response without any
     * playabilityStatus is acceptable for non-player endpoints.
     */
    private fun responseStatus(response: String): ResponseStatus {
        val root =
            try {
                Json.parseToJsonElement(response).jsonObject
            } catch (_: Exception) {
                return ResponseStatus.Malformed
            }
        if (root.isEmpty()) return ResponseStatus.Malformed
        val status =
            try {
                root["playabilityStatus"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull
            } catch (_: Exception) {
                return ResponseStatus.Malformed
            }
        return when {
            status == "OK" -> ResponseStatus.Ok
            status != null -> ResponseStatus.Rejected(status)
            root.containsKey("playabilityStatus") -> ResponseStatus.Rejected("missing status")
            else -> ResponseStatus.Missing
        }
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
