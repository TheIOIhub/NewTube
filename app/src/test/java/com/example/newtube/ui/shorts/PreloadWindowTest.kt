package com.example.newtube.ui.shorts

import org.junit.Assert.assertEquals
import org.junit.Test

class PreloadWindowTest {
    @Test
    fun `window is clamped around the current index`() {
        assertEquals(0..4, preloadWindow(2, 10))
    }

    @Test
    fun `window at start is clamped to the list`() {
        assertEquals(0..2, preloadWindow(0, 10))
    }

    @Test
    fun `window at end is clamped to the list`() {
        assertEquals(7..9, preloadWindow(9, 10))
    }

    @Test
    fun `short lists never exceed their bounds`() {
        assertEquals(0..1, preloadWindow(1, 2))
    }

    @Test
    fun `custom radius is honoured`() {
        assertEquals(3..7, preloadWindow(5, 10, radius = 2))
        assertEquals(4..6, preloadWindow(5, 10, radius = 1))
    }

    @Test
    fun `managed set follows the settled index`() {
        val items = (0..9).map { "clip$it" }
        // Window built around index 2 keeps 0..4.
        val managed = items.take(5).mapIndexed { index, item -> item to index }.toMap()

        val diff = managedWindowDiff(managed, items, currentIndex = 7)

        assertEquals(items.take(5).toSet(), diff.remove)
        assertEquals(items.drop(5).mapIndexed { offset, item -> item to (offset + 5) }.toMap(), diff.add)
    }

    @Test
    fun `overlapping windows keep shared items and only move the edges`() {
        val items = (0..9).map { "clip$it" }
        val managed = items.take(5).mapIndexed { index, item -> item to index }.toMap()

        val diff = managedWindowDiff(managed, items, currentIndex = 4)

        assertEquals(setOf(items[0], items[1]), diff.remove)
        assertEquals(mapOf(items[5] to 5, items[6] to 6), diff.add)
    }

    @Test
    fun `a retained item whose rank changed is removed and re-added`() {
        val items = listOf("a", "b", "c")
        val managed = mapOf("a" to 2, "b" to 1, "c" to 0)

        val diff = managedWindowDiff(managed, items, currentIndex = 1)

        assertEquals(setOf("a", "c"), diff.remove)
        assertEquals(mapOf("a" to 0, "c" to 2), diff.add)
    }

    @Test
    fun `empty feed clears the managed set`() {
        val diff = managedWindowDiff(mapOf("a" to 0), emptyList(), currentIndex = 0)

        assertEquals(setOf("a"), diff.remove)
        assertEquals(emptyMap<String, Int>(), diff.add)
    }
}
