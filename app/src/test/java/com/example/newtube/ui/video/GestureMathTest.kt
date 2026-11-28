package com.example.newtube.ui.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureMathTest {
    @Test
    fun `fullscreen gesture near the top edge is ignored`() {
        assertFalse(GestureMath.canStartFullscreenGesture(200f, 40f, 0f, 100f, 720f, 1280f, 48f))
    }

    @Test
    fun `fullscreen gesture in the middle is allowed`() {
        assertTrue(GestureMath.canStartFullscreenGesture(200f, 400f, 0f, 100f, 720f, 1280f, 48f))
    }

    @Test
    fun `horizontal drag does not start a fullscreen gesture`() {
        assertFalse(GestureMath.canStartFullscreenGesture(200f, 400f, 100f, 10f, 720f, 1280f, 48f))
    }
}
