package com.example.newtube.account

/**
 * Keys of the encrypted store. DI and the repository share them so a rename
 * cannot silently break the page id, the device id or the token lookup.
 */
internal object AccountStoreKeys {
    const val DEVICE_ID = "device_id"
    const val CLIENT_ID = "client_id"
    const val CLIENT_SECRET = "client_secret"
    const val CLIENT_FETCHED_AT = "client_fetched_at"
    const val ACCESS_TOKEN = "access_token"
    const val REFRESH_TOKEN = "refresh_token"
    const val EXPIRES_AT = "expires_at"
    const val ACCOUNT_JSON = "account_json"
}
