package com.example.newtube.remote

import com.squareup.moshi.JsonClass

/**
 * One entry of an Invidious `formatStreams` / `adaptiveFormats` array.
 *
 * Some entries are DASH-only and come without a direct [url] (they are sliced
 * through `init`/`index` ranges), so [url] has to stay nullable.
 */
@JsonClass(generateAdapter = true)
data class FormatStreamDto(
    val url: String? = null,
    val itag: String? = null,
    val type: String? = null,
    val container: String? = null,
    val qualityLabel: String? = null,
    val audioQuality: String? = null,
    val language: String? = null,
    val bitrate: String? = null,
)
