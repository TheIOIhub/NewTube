package com.example.newtube.ui.common

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest

/** Canonical InnerTube thumbnail URLs and sized prefetch requests. */
object ThumbnailUrls {
    const val THUMBNAIL_SIZE_PX = 480
    const val SHORTS_TILE_SIZE_PX = 360
    const val AVATAR_SIZE_PX = 96
    const val PREFETCH_COUNT = 8

    fun hq(videoId: String): String = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    fun mq(videoId: String): String = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"

    /** Warm the shared image loader with one fixed-size request per URL. */
    fun prefetch(context: Context, urls: List<String>, sizePx: Int) {
        val imageLoader = context.imageLoader
        urls.forEach { url ->
            imageLoader.enqueue(ImageRequest.Builder(context).data(url).size(sizePx).build())
        }
    }
}
