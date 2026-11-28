package com.example.newtube.ui.video

import com.example.newtube.model.AppSettings

/**
 * Player settings for the current video only. Nothing here is persisted:
 * leaving Watch creates a fresh instance with these defaults.
 */
data class PlayerUiSettings(
    val quality: Int = Int.MAX_VALUE,
    val speed: Float = 1f,
    val repeat: Boolean = false,
    val resize: Int = AppSettings.RESIZE_FIT,
    val audioLabel: String = "Авто",
    val gestureZoom: Boolean = true,
    val pipEnabled: Boolean = true,
)

fun PlayerUiSettings.qualityLabel(): String =
    if (quality == Int.MAX_VALUE) "Авто" else "${quality}p"

fun PlayerUiSettings.speedLabel(): String =
    when (speed) {
        1.5f -> "1.5x"
        2f -> "2x"
        3f -> "3x"
        else -> if (speed == 1f) "1x" else "${speed}x"
    }

fun PlayerUiSettings.nextResize(): Int =
    when (resize) {
        AppSettings.RESIZE_FIT -> AppSettings.RESIZE_FILL
        AppSettings.RESIZE_FILL -> AppSettings.RESIZE_ZOOM
        else -> AppSettings.RESIZE_FIT
    }

fun formatPlayerTime(ms: Long): String {
    val total = (ms / 1_000L).coerceAtLeast(0L)
    val seconds = total % 60L
    val minutes = (total / 60L) % 60L
    val hours = total / 3_600L
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
