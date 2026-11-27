package com.example.newtube.implementation

import com.example.newtube.model.AppSettings
import com.example.newtube.remote.VideoDto

/**
 * Best-effort client-side content filter. Invidious exposes no documented
 * safe-search parameter, so this runs before DTOs are mapped/persisted and
 * never adds a Room column.
 */
object VideoContentFilter {
    private val ADULT_TERMS =
        listOf("18+", "adult", "porn", "porno", "xnxx", "секс", "порно", "erotic")

    fun List<VideoDto>.applyContentSettings(settings: AppSettings): List<VideoDto> =
        filter { dto ->
            val allowedByAge = settings.ageRestrictedContent || dto.isFamilyFriendly
            val allowedBySafeMode =
                !settings.safeMode ||
                    ADULT_TERMS.none { term ->
                        dto.title.contains(term, ignoreCase = true) ||
                            dto.author.contains(term, ignoreCase = true)
                    }
            allowedByAge && allowedBySafeMode
        }
}
