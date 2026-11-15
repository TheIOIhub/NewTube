package com.example.newtube.ui.common

import com.example.newtube.model.Video

data class VideoMetaLine(
    val showViews: Boolean,
    val uploadedAt: String?,
)

/**
 * Invidious' default instance returns `viewCount = 0`, `lengthSeconds = 0` and
 * `publishedText = "0 seconds ago"` for broken trending items. Hide those
 * segments, but keep a genuine zero-view upload that has a duration.
 */
fun videoMetaLine(video: Video): VideoMetaLine {
    val uploaded = video.uploadedAt.takeIf { it.isNotBlank() && it != "0 seconds ago" }
    return VideoMetaLine(
        showViews = video.duration > 0L || video.views > 0L,
        uploadedAt = uploaded,
    )
}
