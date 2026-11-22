package com.example.newtube.innertube

import com.example.newtube.account.YouTubeTvUserAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.TimeZone

/**
 * Posts the TV client body to InnerTube and returns the raw response so the
 * parser stays independently testable. Only account endpoints are called here;
 * browsing and playback keep using Invidious.
 */
class YouTubeInnerTubeService(
    private val client: OkHttpClient,
    private val pageIdProvider: () -> String?,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : InnerTubeService {
    override suspend fun browseVideos(
        browseId: String,
        continuation: String?,
        accessToken: String,
    ): String =
        withContext(Dispatchers.IO) {
            val body =
                buildJsonObject {
                    put("context", defaultContext())
                    put("racyCheckOk", true)
                    put("contentCheckOk", true)
                    if (continuation != null) {
                        put("continuation", continuation)
                    } else {
                        put("browseId", browseId)
                    }
                }
            post(BROWSE_PATH, body, accessToken)
        }

    override suspend fun browseSubscriptions(
        continuation: String?,
        accessToken: String,
    ): String = browseVideos(SUBSCRIPTIONS_ID, continuation, accessToken)

    override suspend fun fetchProfile(accessToken: String): String =
        withContext(Dispatchers.IO) {
            val body =
                buildJsonObject {
                    put("context", defaultContext())
                    put(
                        "accountReadMask",
                        buildJsonObject {
                            put("returnOwner", true)
                            put("returnBrandAccounts", true)
                            put("returnPersonaAccounts", true)
                        },
                    )
                }
            post(PROFILE_PATH, body, accessToken)
        }

    private fun post(
        path: String,
        body: JsonObject,
        accessToken: String,
    ): String {
        val builder =
            Request.Builder()
                .url(baseUrl.trimEnd('/') + path)
                .header("User-Agent", YouTubeTvUserAgent.VALUE)
                .header("Referer", "https://www.youtube.com/tv")
                .header("Accept-Language", "en-US,en;q=0.9")
                .post(body.toString().toRequestBody(JSON))
        if (accessToken.isNotBlank()) {
            builder.header("Authorization", "Bearer $accessToken")
        }
        pageIdProvider()?.takeIf { it.isNotBlank() }?.let { builder.header("X-Goog-Pageid", it) }

        return client
            .newCall(builder.build())
            .execute()
            .use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw InnerTubeHttpException(response.code, "InnerTube answered HTTP ${response.code}")
                }
                text
            }
    }

    private fun defaultContext(): JsonObject {
        val locale = Locale.getDefault()
        val language = locale.language.ifBlank { "en" }
        val region = locale.country.ifBlank { "US" }
        val offsetMinutes = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
        return buildJsonObject {
            put(
                "client",
                buildJsonObject {
                    put("clientName", "TVHTML5")
                    put("clientVersion", CLIENT_VERSION)
                    put("clientScreen", "WATCH")
                    put("userAgent", YouTubeTvUserAgent.VALUE)
                    put("acceptLanguage", language)
                    put("acceptRegion", region)
                    put("utcOffsetMinutes", offsetMinutes.toString())
                },
            )
            put(
                "user",
                buildJsonObject {
                    put("enableSafetyMode", false)
                    put("lockedSafetyMode", false)
                },
            )
            put(
                "tvAppInfo",
                buildJsonObject {
                    put("appQuality", "TV_APP_QUALITY_FULL_ANIMATION")
                    put("zylonLeftNav", true)
                },
            )
            put("webpSupport", false)
            put("animatedWebpSupport", true)
        }
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://www.youtube.com"
        const val BROWSE_PATH = "/youtubei/v1/browse"
        const val PROFILE_PATH = "/youtubei/v1/account/accounts_list"
        const val SUBSCRIPTIONS_ID = "FEsubscriptions"
        const val CLIENT_VERSION = "7.20260901.15.00"
        val JSON = "application/json".toMediaType()
    }
}
