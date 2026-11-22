package com.example.newtube.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Google's device-code and token endpoints for the YouTube TV client.
 *
 * The token client must never get a logging interceptor: request bodies carry
 * the refresh token and the client secret.
 */
class YouTubeOAuthApi(
    private val client: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : OAuthApi {
    private val jsonFormat = Json { ignoreUnknownKeys = true }

    override suspend fun requestDeviceCode(
        credentials: ClientCredentials,
        deviceId: String,
    ): DeviceCode =
        withContext(Dispatchers.IO) {
            val body =
                buildJsonObject {
                    put("client_id", credentials.clientId)
                    put("device_id", deviceId)
                    put("model_name", "ytlr::")
                    put("scope", SCOPE)
                }
            val response = post(DEVICE_CODE_PATH, body)
            if (response.code !in 200..299) {
                throw IOException("device code request failed with HTTP ${response.code}")
            }
            val obj = jsonFormat.parseToJsonElement(response.body).jsonObject
            DeviceCode(
                deviceCode = obj.string("device_code"),
                userCode = obj.string("user_code"),
                verificationUrl = obj.string("verification_url"),
                expiresInSeconds = obj["expires_in"]?.jsonPrimitive?.int ?: 1800,
                intervalSeconds = obj["interval"]?.jsonPrimitive?.int ?: 5,
            )
        }

    override suspend fun pollToken(
        credentials: ClientCredentials,
        deviceCode: String,
    ): TokenPoll =
        withContext(Dispatchers.IO) {
            val body =
                buildJsonObject {
                    put("code", deviceCode)
                    put("client_id", credentials.clientId)
                    put("client_secret", credentials.clientSecret)
                    put("grant_type", DEVICE_GRANT)
                }
            val response = post(TOKEN_PATH, body)
            if (response.code in 200..299) {
                TokenPoll.Granted(tokens(response.body))
            } else {
                val error = errorCode(response.body)
                when (error) {
                    "authorization_pending" -> TokenPoll.Pending
                    "slow_down" -> TokenPoll.SlowDown
                    "access_denied" -> TokenPoll.Denied
                    "expired_token" -> TokenPoll.Expired
                    else -> throw IOException("token poll failed with HTTP ${response.code}: $error")
                }
            }
        }

    override suspend fun refresh(
        credentials: ClientCredentials,
        refreshToken: String,
    ): Tokens =
        withContext(Dispatchers.IO) {
            val body =
                buildJsonObject {
                    put("refresh_token", refreshToken)
                    put("client_id", credentials.clientId)
                    put("client_secret", credentials.clientSecret)
                    put("grant_type", "refresh_token")
                }
            val response = post(TOKEN_PATH, body)
            if (response.code in 200..299) {
                tokens(response.body)
            } else {
                val error = errorCode(response.body)
                if (error == "invalid_grant") {
                    throw InvalidGrantException("refresh token is no longer valid")
                }
                throw IOException("token refresh failed with HTTP ${response.code}: $error")
            }
        }

    private fun tokens(body: String): Tokens {
        val obj = jsonFormat.parseToJsonElement(body).jsonObject
        return Tokens(
            accessToken = obj.string("access_token"),
            refreshToken = obj["refresh_token"]?.jsonPrimitive?.contentOrNull,
            expiresInSeconds = obj["expires_in"]?.jsonPrimitive?.long ?: 0L,
        )
    }

    private fun errorCode(body: String): String? =
        runCatching {
            jsonFormat.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()

    private fun post(
        path: String,
        body: JsonObject,
    ): HttpResponse {
        val request =
            Request.Builder()
                .url(baseUrl.trimEnd('/') + path)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("User-Agent", YouTubeTvUserAgent.VALUE)
                .post(body.toString().toRequestBody(JSON))
                .build()
        return client
            .newCall(request)
            .execute()
            .use { response -> HttpResponse(response.code, response.body?.string().orEmpty()) }
    }

    private data class HttpResponse(
        val code: Int,
        val body: String,
    )

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.content
            ?: throw IOException("OAuth response is missing \"$key\"")

    private companion object {
        const val DEFAULT_BASE_URL = "https://www.youtube.com"
        const val DEVICE_CODE_PATH = "/o/oauth2/device/code"
        const val TOKEN_PATH = "/o/oauth2/token"
        const val DEVICE_GRANT = "http://oauth.net/grant_type/device/1.0"
        const val SCOPE = "http://gdata.youtube.com https://www.googleapis.com/auth/youtube-paid-content"
        val JSON = "application/json".toMediaType()
    }
}
