package com.example.newtube.account

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Encrypted store for the OAuth client credentials, tokens and profile. */
class EncryptedPrefsSecretStore(
    context: Context,
) : SecretStore {
    private val prefs: SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(
        key: String,
        value: String,
    ) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    private companion object {
        const val FILE_NAME = "account_tokens"
    }
}
