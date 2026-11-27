package com.example.newtube.ui.video

import androidx.media3.common.C
import androidx.media3.common.Tracks
import com.example.newtube.ui.settings.languageDisplayName

internal data class AudioTrackOption(
    val key: String,
    val label: String,
    val group: Tracks.Group,
    val index: Int,
)

/** The control stays visible even with one track so users can always inspect it. */
internal fun audioButtonVisible(
    showControl: Boolean,
    optionCount: Int,
    alternativeCount: Int,
): Boolean = showControl

/** Builds the audio menu from live Media3 metadata, not from hard-coded indices. */
internal fun Tracks.audioOptions(): List<AudioTrackOption> =
    groups
        .filter { it.type == C.TRACK_TYPE_AUDIO }
        .flatMap { group ->
            (0 until group.length).mapNotNull { index ->
                val format = group.getTrackFormat(index)
                val language =
                    format.language
                        ?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
                        ?: "Невідома"
                val role = roleLabel(format.roleFlags)
                AudioTrackOption(
                    key = "$language|$role|$index",
                    label = "${languageDisplayName(language)} ($role)",
                    group = group,
                    index = index,
                )
            }
        }

private fun roleLabel(flags: Int): String =
    when {
        flags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0 -> "Опис"
        flags and C.ROLE_FLAG_DUB != 0 -> "Дубльована"
        flags and C.ROLE_FLAG_ALTERNATE != 0 -> "Альтернативна"
        flags and C.ROLE_FLAG_MAIN != 0 -> "Оригінал"
        else -> "Доріжка"
    }
