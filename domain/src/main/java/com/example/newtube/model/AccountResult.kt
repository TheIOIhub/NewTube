package com.example.newtube.model

sealed interface AccountResult<out T> {
    data class Success<T>(val data: T) : AccountResult<T>

    data object NeedsSignIn : AccountResult<Nothing>

    data object Network : AccountResult<Nothing>

    data object Server : AccountResult<Nothing>

    data object ParseEmpty : AccountResult<Nothing>
}
