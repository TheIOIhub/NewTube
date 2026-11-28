package com.example.newtube.ui.video

import com.example.newtube.model.GestureSide
import kotlin.math.abs

internal enum class DragAction { VOLUME, BRIGHTNESS }

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

    /** Total seek is measured from the first tap of the sequence, never from the moving position. */
    fun seekTarget(
        basePosition: Long,
        taps: Int,
        side: Int,
        seconds: Int,
        duration: Long,
    ): Long {
        val delta = seekDelta(taps, side, seconds)
        if (delta == 0L) return basePosition.coerceIn(0L, duration.coerceAtLeast(0L))
        val upper = if (duration > 0) duration else Long.MAX_VALUE
        return (basePosition + delta).coerceIn(0L, upper)
    }

    fun shouldMinimize(
        dy: Int,
        dx: Int,
        isFullscreen: Boolean,
        canMinimize: Boolean,
    ): Boolean =
        canMinimize && !isFullscreen && dy >= 80 && abs(dy) >= abs(dx)

    /**
     * Fullscreen volume/brightness drags must start away from system-gesture
     * edges and must be predominantly vertical.
     */
    fun canStartFullscreenGesture(
        x: Float,
        y: Float,
        dx: Float,
        dy: Float,
        width: Float,
        height: Float,
        edgePx: Float,
    ): Boolean =
        x >= edgePx &&
            x <= width - edgePx &&
            y >= edgePx &&
            y <= height - edgePx &&
            abs(dy) >= abs(dx)

    /** Honours reassigned sides and independent enable flags. */
    fun dragAction(
        half: Int,
        volumeSide: GestureSide,
        brightnessSide: GestureSide,
        volumeEnabled: Boolean,
        brightnessEnabled: Boolean,
    ): DragAction? =
        when {
            half == 0 && volumeSide == GestureSide.LEFT && volumeEnabled -> DragAction.VOLUME
            half == 0 && brightnessSide == GestureSide.LEFT && brightnessEnabled -> DragAction.BRIGHTNESS
            half == 1 && volumeSide == GestureSide.RIGHT && volumeEnabled -> DragAction.VOLUME
            half == 1 && brightnessSide == GestureSide.RIGHT && brightnessEnabled -> DragAction.BRIGHTNESS
            else -> null
        }

    fun dragFraction(
        dy: Int,
        height: Int,
        current: Float,
    ): Float {
        if (height <= 0) return current.coerceIn(0.01f, 1f)
        return (current - dy.toFloat() / height).coerceIn(0.01f, 1f)
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
