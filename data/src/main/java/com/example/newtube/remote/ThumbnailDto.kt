package com.example.newtube.remote

import com.squareup.moshi.JsonClass

/**
 * One entry of an Invidious `videoThumbnails` array.
 * `quality` is only present on video thumbnails, author avatars have just width/height.
 */
@JsonClass(generateAdapter = true)
data class ThumbnailDto(
    val url: String,
    val quality: String? = null,
    val width: Int = 0,
    val height: Int = 0,
)
