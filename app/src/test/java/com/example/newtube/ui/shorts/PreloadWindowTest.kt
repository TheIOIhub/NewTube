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
}
