package com.example.newtube.di

/** HTTP headers that make direct googlevideo URLs look like the InnerTube client that issued them. */
object MediaRequestHeaders {
    const val CLIENT_VERSION = "20.10.38"

    fun userAgent(): String =
        "com.google.android.youtube/$CLIENT_VERSION (Linux; U; Android 12; en_US) gzip"

    fun headers(): Map<String, String> =
        mapOf(
            "User-Agent" to userAgent(),
            "Referer" to "https://www.youtube.com/",
            "Origin" to "https://www.youtube.com",
        )
}
