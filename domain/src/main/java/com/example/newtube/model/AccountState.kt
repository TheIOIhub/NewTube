package com.example.newtube.model

sealed interface AccountState {
    data object SignedOut : AccountState

    data class SignedIn(val account: UserAccount) : AccountState

    data class Expired(val account: UserAccount?) : AccountState
}
