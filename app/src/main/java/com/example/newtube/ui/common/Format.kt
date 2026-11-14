package com.example.newtube.ui.common

import java.util.Locale

/** `1234` -> `1.2K`, `1_200_000` -> `1.2M`. */
fun formatCount(count: Long): String =
    when {
        count >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000L -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000L -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }

/** Seconds to `m:ss` or `h:mm:ss`. */
fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainder)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, remainder)
    }
}
