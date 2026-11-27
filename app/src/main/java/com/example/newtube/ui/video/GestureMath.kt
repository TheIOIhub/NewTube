package com.example.newtube.ui.video

import com.example.newtube.model.GestureSide

/** Pure gesture arithmetic, unit-testable without a device. */
internal object GestureMath {
    fun classifyThird(
        x: Int,
        width: Int,
    ): Int =
        when {
            width <= 0 -> 1
            x < width / 3 -> 0
            x >= 2 * width / 3 -> 2
            else -> 1
        }

    fun classifyHalf(
        x: Int,
        width: Int,
    ): Int = if (width > 0 && x >= width / 2) 1 else 0

    fun seekDelta(
        taps: Int,
        side: Int,
        seconds: Int,
    ): Long =
        when (side) {
            0 -> -(taps * seconds * 1_000L)
            2 -> taps * seconds * 1_000L
            else -> 0L
        }

    fun dragFraction(
        dy: Int,
        height: Int,
        current: Float,
    ): Float {
        if (height <= 0) return current.coerceIn(0.01f, 1f)
        return (current + dy.toFloat() / height).coerceIn(0.01f, 1f)
    }

    fun resolveSides(
        volume: GestureSide,
        brightness: GestureSide,
    ): Pair<GestureSide, GestureSide> {
        if (volume == GestureSide.OFF || brightness == GestureSide.OFF || volume != brightness) {
            return volume to brightness
        }
        val moved = if (volume == GestureSide.LEFT) GestureSide.RIGHT else GestureSide.LEFT
        return volume to moved
    }
}
