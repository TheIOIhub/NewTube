package com.example.newtube.innertube

import java.io.IOException

/** The TV InnerTube client answered with a non-2xx status; [code] drives auth mapping. */
class InnerTubeHttpException(
    val code: Int,
    message: String,
) : IOException(message)

interface InnerTubeService {
    suspend fun browseVideos(
        browseId: String,
        continuation: String? = null,
        accessToken: String,
    ): String

    suspend fun browseSubscriptions(
        continuation: String? = null,
        accessToken: String,
    ): String

    suspend fun fetchProfile(accessToken: String): String
}
