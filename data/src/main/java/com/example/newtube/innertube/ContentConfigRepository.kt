package com.example.newtube.innertube

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.newtube.data.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private const val DEFAULT_TTL_MILLIS = 21_600_000L

internal val CONTENT_CONFIG_KEY = stringPreferencesKey("content_config_json")

val Context.contentConfigStore by preferencesDataStore(name = "content_config")

/** Defaults bundled with the app; they work when no config server is reachable. */
fun bundledContentConfig(): ContentConfig {
    val clients =
        listOf(
            ContentClient(name = "ANDROID", version = "20.10.38"),
            ContentClient(name = "ANDROID_VR", version = "1.60.19"),
            ContentClient(name = "IOS", version = "20.10.4"),
        )
    return ContentConfig(
        contentClients = clients,
        playerClients = clients,
        endpoints =
            mapOf(
                ContentEndpoints.BROWSE to ContentEndpoints.BROWSE,
                ContentEndpoints.SEARCH to ContentEndpoints.SEARCH,
                ContentEndpoints.PLAYER to ContentEndpoints.PLAYER,
                ContentEndpoints.NEXT to ContentEndpoints.NEXT,
                ContentEndpoints.SUGGESTIONS to ContentEndpoints.SUGGESTIONS,
            ),
        ttlMillis = DEFAULT_TTL_MILLIS,
        fetchedAt = 0L,
    )
}

interface ContentConfigRepository {
    val config: Flow<ContentConfig>

    suspend fun refresh(): Result<ContentConfig>

    fun bundled(): ContentConfig

    fun decodeConfig(text: String): ContentConfig?
}

/** Reads our own config URL from build config, caches the last good config in DataStore. */
class DataStoreContentConfigRepository(
    private val client: OkHttpClient,
    private val dataStore: DataStore<Preferences>,
    private val configUrl: String = BuildConfig.CONTENT_CONFIG_URL,
    private val clock: () -> Long = System::currentTimeMillis,
) : ContentConfigRepository {
    override val config: Flow<ContentConfig> =
        dataStore.data.map { preferences ->
            preferences[CONTENT_CONFIG_KEY]?.let(::decodeConfig) ?: bundledContentConfig()
        }

    override fun bundled(): ContentConfig = bundledContentConfig()

    override fun decodeConfig(text: String): ContentConfig? =
        try {
            val root = JSON.parseToJsonElement(text).jsonObject
            ContentConfig(
                contentClients = root["contentClients"]?.jsonArray.orEmpty().map(::decodeClient),
                playerClients = root["playerClients"]?.jsonArray.orEmpty().map(::decodeClient),
                endpoints =
                    root["endpoints"]?.jsonObject?.mapValues { (_, value) -> value.jsonPrimitive.content }
                        ?: emptyMap(),
                ttlMillis = root["ttlMillis"]?.jsonPrimitive?.long ?: DEFAULT_TTL_MILLIS,
                fetchedAt = root["fetchedAt"]?.jsonPrimitive?.long ?: 0L,
            )
        } catch (_: Exception) {
            null
        }

    override suspend fun refresh(): Result<ContentConfig> =
        withContext(Dispatchers.IO) {
            val current = config.first()
            if (configUrl.isBlank()) {
                Result.success(current)
            } else if (current.fetchedAt > 0 && clock() - current.fetchedAt < current.ttlMillis) {
                Result.success(current)
            } else {
                runCatching {
                    val decoded =
                        decodeConfig(fetch())
                            ?: throw IOException("Remote content config is not valid JSON")
                    require(decoded.contentClients.isNotEmpty() && decoded.playerClients.isNotEmpty()) {
                        "Remote content config has no usable clients"
                    }
                    val fresh = decoded.copy(fetchedAt = clock())
                    dataStore.edit { it[CONTENT_CONFIG_KEY] = encodeConfig(fresh) }
                    fresh
                }
            }
        }

    private fun fetch(): String {
        val request = Request.Builder().url(configUrl).get().build()
        return client
            .newCall(request)
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Content config answered HTTP ${response.code}")
                }
                response.body?.string().orEmpty()
            }
    }

    private fun decodeClient(element: JsonElement): ContentClient {
        val obj = element.jsonObject
        return ContentClient(
            name = obj["name"]?.jsonPrimitive?.content ?: error("Client name is missing"),
            version = obj["version"]?.jsonPrimitive?.content ?: error("Client version is missing"),
            hl = obj["hl"]?.jsonPrimitive?.content ?: "uk",
            gl = obj["gl"]?.jsonPrimitive?.content ?: "UA",
        )
    }

    private fun encodeConfig(config: ContentConfig): String =
        buildJsonObject {
            put("contentClients", buildJsonArray { config.contentClients.forEach { add(encodeClient(it)) } })
            put("playerClients", buildJsonArray { config.playerClients.forEach { add(encodeClient(it)) } })
            put(
                "endpoints",
                buildJsonObject {
                    config.endpoints.forEach { (key, value) -> put(key, value) }
                },
            )
            put("ttlMillis", config.ttlMillis)
            put("fetchedAt", config.fetchedAt)
        }.toString()

    private fun encodeClient(client: ContentClient) =
        buildJsonObject {
            put("name", client.name)
            put("version", client.version)
            put("hl", client.hl)
            put("gl", client.gl)
        }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
