package com.example.newtube.ui.shorts

/** Indices to keep in the preload window around [index], clamped to [size]. */
internal fun preloadWindow(
    index: Int,
    size: Int,
    radius: Int = 2,
): IntRange {
    if (size <= 0 || radius < 0) return 0..-1
    val current = index.coerceIn(0, size - 1)
    return maxOf(0, current - radius)..minOf(size - 1, current + radius)
}
