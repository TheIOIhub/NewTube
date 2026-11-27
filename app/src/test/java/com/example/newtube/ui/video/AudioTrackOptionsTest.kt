package com.example.newtube.ui.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTrackOptionsTest {
    @Test
    fun `audio button is visible whenever the control is enabled`() {
        assertTrue(audioButtonVisible(showControl = true, optionCount = 0, alternativeCount = 0))
        assertTrue(audioButtonVisible(showControl = true, optionCount = 1, alternativeCount = 0))
        assertFalse(audioButtonVisible(showControl = false, optionCount = 3, alternativeCount = 2))
    }
}
