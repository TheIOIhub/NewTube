package com.example.newtube.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scrapes the public TV web client credentials and caches them encrypted (the
 * [store] is backed by EncryptedSharedPreferences in production) for a week.
 *
 * The [fetcher] blocks (it runs an OkHttp `execute()`), so the whole call is
 * confined to [Dispatchers.IO]: callers collect the login flow and request
 * account pages from the main thread.
 */
class YouTubeTvCredentialsProvider(
    private val fetcher: suspend (String) -> String,
    private val store: SecretStore,
    private val clock: () -> Long,
) : ClientCredentialsProvider {
    override suspend fun get(): ClientCredentials =
        withContext(Dispatchers.IO) {
            cached()?.let { return@withContext it }

            val page = fetcher(TV_PAGE_URL)
            val baseJsUrl =
                YouTubeTvPage.findBaseJsUrl(page)
                    ?: error("Could not find the YouTube TV base JavaScript bundle")
            val js = fetcher(baseJsUrl)
            val credentials =
                YouTubeTvPage.findClientCredentials(js)
                    ?: error("Could not find the YouTube TV OAuth client credentials")

            store.put(KEY_CLIENT_ID, credentials.clientId)
            store.put(KEY_CLIENT_SECRET, credentials.clientSecret)
            store.put(KEY_FETCHED_AT, clock().toString())
            credentials
        }

    private fun cached(): ClientCredentials? {
        val clientId = store.get(KEY_CLIENT_ID) ?: return null
        val secret = store.get(KEY_CLIENT_SECRET) ?: return null
        val fetchedAt = store.get(KEY_FETCHED_AT)?.toLongOrNull() ?: return null
        val age = clock() - fetchedAt
        if (age < 0L || age >= CACHE_TTL_MS) return null
        return ClientCredentials(clientId, secret)
    }

    private companion object {
        const val TV_PAGE_URL = "https://www.youtube.com/tv"
        const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_CLIENT_SECRET = "client_secret"
        const val KEY_FETCHED_AT = "client_fetched_at"
    }
}
