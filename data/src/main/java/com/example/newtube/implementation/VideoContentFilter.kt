package com.example.newtube.implementation

import com.example.newtube.model.AppSettings
import com.example.newtube.model.Video

/**
 * Best-effort client-side content filter applied to parsed feeds before they
 * are persisted. InnerTube has no per-item family-friendly flag in its renderer
 * payloads, so the denylist is a conservative proxy: it applies unless age
 * restricted content is explicitly enabled, and safe mode always wins.
 */
object VideoContentFilter {
    private val ADULT_TERMS =
        listOf("18+", "adult", "porn", "porno", "xnxx", "секс", "порно", "erotic")

    fun List<Video>.applyContentSettings(settings: AppSettings): List<Video> =
        filter { video ->
            val flagged =
                ADULT_TERMS.any { term ->
                    video.title.contains(term, ignoreCase = true) ||
                        video.channelName.contains(term, ignoreCase = true)
                }
            when {
                settings.safeMode && flagged -> false
                settings.ageRestrictedContent -> true
                else -> !flagged
            }
        }
}
