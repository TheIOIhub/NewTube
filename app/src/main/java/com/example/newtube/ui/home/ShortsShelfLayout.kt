package com.example.newtube.ui.home

/** Every Nth video gets its own Shorts shelf (1-based positions). */
internal const val SHORTS_SHELF_EVERY = 7
internal const val SHORTS_SHELF_SIZE = 4

internal fun shortsInsertionIndexes(videoCount: Int): List<Int> =
    if (videoCount <= SHORTS_SHELF_EVERY) {
        emptyList()
    } else {
        (SHORTS_SHELF_EVERY until videoCount step SHORTS_SHELF_EVERY).toList()
    }

/** Deterministic rotating window so repeated shelves show different Shorts. */
internal fun <T> shortsWindow(
    items: List<T>,
    block: Int,
    size: Int,
): List<T> {
    if (items.isEmpty() || size <= 0) return emptyList()
    if (size >= items.size) return items
    val start = (block * size) % items.size
    return (0 until size).map { items[(start + it) % items.size] }
}
