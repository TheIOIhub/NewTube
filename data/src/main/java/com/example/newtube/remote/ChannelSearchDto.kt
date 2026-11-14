package com.example.newtube.remote

import com.squareup.moshi.JsonClass

/** One entry of `/api/v1/search?type=channel`. */
@JsonClass(generateAdapter = true)
data class ChannelSearchDto(
    val authorId: String,
    val author: String = "",
    val subCount: Long = 0,
    val description: String = "",
)
