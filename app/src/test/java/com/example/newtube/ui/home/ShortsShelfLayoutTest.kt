package com.example.newtube.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortsShelfLayoutTest {
    private val items = (0 until 20).map { "v$it" }

    @Test
    fun `each block shows a different rotating window`() {
        assertEquals(listOf("v0", "v1", "v2"), shortsWindow(items, block = 0, size = 3))
        assertEquals(listOf("v3", "v4", "v5"), shortsWindow(items, block = 1, size = 3))
        assertEquals(listOf("v6", "v7", "v8"), shortsWindow(items, block = 2, size = 3))
    }

    @Test
    fun `window wraps around the end of the shelf`() {
        assertEquals(listOf("v18", "v19", "v0"), shortsWindow(items, block = 6, size = 3))
    }

    @Test
    fun `empty shelf and oversized window are safe`() {
        assertEquals(emptyList<String>(), shortsWindow(emptyList<String>(), block = 0, size = 3))
        assertEquals(items, shortsWindow(items, block = 0, size = 40))
    }

    @Test
    fun `shelf is inserted after every seven videos`() {
        assertEquals(listOf(7, 14), shortsInsertionIndexes(20))
        assertEquals(emptyList<Int>(), shortsInsertionIndexes(7))
        assertEquals(listOf(7), shortsInsertionIndexes(8))
    }
}
