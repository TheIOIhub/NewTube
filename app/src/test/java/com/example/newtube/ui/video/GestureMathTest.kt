package com.example.newtube.ui.video

import com.example.newtube.model.AppSettings
import com.example.newtube.model.GestureSide
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureMathTest {
    @Test
    fun `thirds use width boundaries`() {
        assertEquals(0, GestureMath.classifyThird(0, 300))
        assertEquals(1, GestureMath.classifyThird(100, 300))
        assertEquals(1, GestureMath.classifyThird(199, 300))
        assertEquals(2, GestureMath.classifyThird(200, 300))
    }

    @Test
    fun `halves use width midpoint`() {
        assertEquals(0, GestureMath.classifyHalf(0, 300))
        assertEquals(0, GestureMath.classifyHalf(149, 300))
        assertEquals(1, GestureMath.classifyHalf(150, 300))
    }

    @Test
    fun `three right taps accumulate forward seek`() {
        assertEquals(30_000L, GestureMath.seekDelta(taps = 3, side = 2, seconds = 10))
    }

    @Test
    fun `two left taps accumulate rewind`() {
        assertEquals(-20_000L, GestureMath.seekDelta(taps = 2, side = 0, seconds = 10))
    }

    @Test
    fun `middle third does not seek`() {
        assertEquals(0L, GestureMath.seekDelta(taps = 5, side = 1, seconds = 10))
    }

    @Test
    fun `drag fraction is clamped to the usable brightness range`() {
        assertEquals(0.01f, GestureMath.dragFraction(-10_000, 1000, 0.5f), 0.001f)
        assertEquals(1f, GestureMath.dragFraction(10_000, 1000, 0.5f), 0.001f)
        assertEquals(0.6f, GestureMath.dragFraction(100, 1000, 0.5f), 0.001f)
    }

    @Test
    fun `colliding gesture sides are swapped so each side has one action`() {
        val (volume, brightness) =
            GestureMath.resolveSides(GestureSide.LEFT, GestureSide.LEFT)

        assertEquals(GestureSide.LEFT, volume)
        assertEquals(GestureSide.RIGHT, brightness)
    }

    @Test
    fun `seek target accumulates from the sequence base not the moving position`() {
        assertEquals(21_000L, GestureMath.seekTarget(1_000L, taps = 2, side = 2, seconds = 10, duration = 300_000L))
        assertEquals(31_000L, GestureMath.seekTarget(1_000L, taps = 3, side = 2, seconds = 10, duration = 300_000L))
        assertEquals(10_000L, GestureMath.seekTarget(30_000L, taps = 2, side = 0, seconds = 10, duration = 300_000L))
    }

    @Test
    fun `reassigned gesture sides drive the drag action`() {
        val swapped = GestureMath.resolveSides(GestureSide.LEFT, GestureSide.RIGHT)
        assertEquals(
            DragAction.VOLUME,
            GestureMath.dragAction(0, swapped.first, swapped.second, true, true),
        )
        assertEquals(
            DragAction.BRIGHTNESS,
            GestureMath.dragAction(1, swapped.first, swapped.second, true, true),
        )
        assertEquals(
            null,
            GestureMath.dragAction(0, GestureSide.RIGHT, GestureSide.LEFT, false, false),
        )
    }

    @Test
    fun `off sides stay off`() {
        val (volume, brightness) =
            GestureMath.resolveSides(GestureSide.OFF, GestureSide.LEFT)

        assertEquals(GestureSide.OFF, volume)
        assertEquals(GestureSide.LEFT, brightness)
        assertEquals(AppSettings.DEFAULT_SEEK_SECONDS, 10)
    }
}
