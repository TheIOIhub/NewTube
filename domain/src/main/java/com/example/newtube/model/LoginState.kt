package com.example.newtube.model

sealed interface LoginState {
    data object Requesting : LoginState

    data class Waiting(
        val userCode: String,
        val verificationUrl: String,
        val expiresAtMs: Long,
    ) : LoginState

    data object Success : LoginState

    data class Error(val reason: LoginError) : LoginState
}

enum class LoginError {
    ACCESS_DENIED,
    EXPIRED,
    NETWORK,
}
