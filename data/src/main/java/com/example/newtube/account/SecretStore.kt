package com.example.newtube.account

/**
 * Small string store used for both the scraped client credentials and the
 * account tokens. [clear] wipes every key; sign-out uses it deliberately.
 */
interface SecretStore {
    fun get(key: String): String?

    fun put(
        key: String,
        value: String,
    )

    fun clear()
}
