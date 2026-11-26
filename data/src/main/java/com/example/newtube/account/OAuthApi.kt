package com.example.newtube.account

import java.io.IOException

data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int,
)

data class Tokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long,
) {
    /** Never print the token values: this object travels through logs by accident. */
    override fun toString(): String =
        "Tokens(accessToken=***, refreshToken=***, expiresInSeconds=$expiresInSeconds)"
}

sealed interface TokenPoll {
    data class Granted(val tokens: Tokens) : TokenPoll

    data object Pending : TokenPoll

    data object SlowDown : TokenPoll

    data object Denied : TokenPoll

    data object Expired : TokenPoll
}

/** The stored refresh token is no longer accepted; the user must sign in again. */
class InvalidGrantException(message: String) : IOException(message)

/**
 * Google no longer accepts the scraped client pair. The cached pair must be
 * dropped and the TV page scraped once more before retrying.
 */
class InvalidClientException(message: String) : IOException(message)

interface OAuthApi {
    suspend fun requestDeviceCode(
        credentials: ClientCredentials,
        deviceId: String,
    ): DeviceCode

    suspend fun pollToken(
        credentials: ClientCredentials,
        deviceCode: String,
    ): TokenPoll

    suspend fun refresh(
        credentials: ClientCredentials,
        refreshToken: String,
    ): Tokens
}
