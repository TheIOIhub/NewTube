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

/**
 * The manager-set changes needed after the sliding window moves. Keys are the
 * managed items (MediaItems in production), values are their ranking indices.
 * Pure so the window-advancement logic can be tested without a real player.
 */
internal data class ManagedWindowDiff<T>(
    val remove: Set<T>,
    val add: Map<T, Int>,
)

internal fun <T> managedWindowDiff(
    managed: Map<T, Int>,
    items: List<T>,
    currentIndex: Int,
    radius: Int = 2,
): ManagedWindowDiff<T> {
    val wanted = preloadWindow(currentIndex, items.size, radius).associate { items[it] to it }
    val remove = managed.keys.filterNot { managed[it] == wanted[it] }.toSet()
    val add = wanted.filter { (item, index) -> managed[item] != index }
    return ManagedWindowDiff(remove, add)
}
