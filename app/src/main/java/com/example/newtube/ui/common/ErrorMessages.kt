package com.example.newtube.ui.common

import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Turns an exception thrown by the repository into a short message suitable for
 * the error screen. The raw exception is intentionally not shown to the user.
 */
fun Throwable.toUserMessage(): String =
    when (this) {
        is UnknownHostException -> "No internet connection"
        is SocketTimeoutException -> "The server is not responding"
        is HttpException -> "Server error (${code()})"
        is IOException -> "Network error. Check your connection"
        else -> "Something went wrong. Try again"
    }
