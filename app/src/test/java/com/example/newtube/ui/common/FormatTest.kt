package com.example.newtube.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun `counts keep one decimal at every boundary`() {
        assertEquals("0", formatCount(0))
        assertEquals("999", formatCount(999))
        assertEquals("1.0K", formatCount(1_000))
        assertEquals("1.2K", formatCount(1_200))
        assertEquals("1.0M", formatCount(1_000_000))
        assertEquals("1.2M", formatCount(1_200_000))
        assertEquals("1.0B", formatCount(1_000_000_000))
    }

    @Test
    fun `durations switch to hours only after 3600 seconds`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:59", formatDuration(59))
        assertEquals("1:00", formatDuration(60))
        assertEquals("59:59", formatDuration(3_599))
        assertEquals("1:00:00", formatDuration(3_600))
    }
}
