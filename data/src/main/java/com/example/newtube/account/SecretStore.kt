package com.example.newtube.account

/**
 * Small string store used for both the scraped client credentials and the
 * account tokens. Sign-out removes the token keys through [remove] and keeps
 * the stable device id and client pair.
 */
interface SecretStore {
    fun get(key: String): String?

    fun put(
        key: String,
        value: String,
    )

    fun remove(key: String)
}
