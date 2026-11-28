package com.example.newtube.ui.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.example.newtube.model.AppSettings
import kotlinx.coroutines.delay
import kotlin.math.abs

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Gesture layer above the player surface. It never consumes pointer events, so
 * PlayerView's own controller keeps working on Watch; on Shorts, single tap is
 * handled here because the controller is disabled.
 */
@Composable
fun PlayerGestureOverlay(
    player: Player,
    isFullscreen: Boolean,
    isShorts: Boolean,
    settings: AppSettings,
    onBrightness: (Float) -> Unit,
    canMinimize: Boolean = false,
    onMinimize: () -> Unit = {},
    onTap: () -> Unit = {},
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val topControlsPx = with(LocalDensity.current) { 72.dp.toPx() }
    var seekFeedback by remember { mutableStateOf<Int?>(null) }
    var indicator by remember { mutableStateOf<Pair<Boolean, Float>?>(null) }

    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(700)
            seekFeedback = null
        }
    }
    LaunchedEffect(indicator) {
        if (indicator != null) {
            delay(700)
            indicator = null
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(
                    isFullscreen,
                    isShorts,
                    settings.doubleTapSeekSeconds,
                    settings.gestureVolume,
                    settings.gestureBrightness,
                    settings.volumeGestureSide,
                    settings.brightnessGestureSide,
                ) {
                    var lastTapAt = 0L
                    var taps = 0
                    var tapSide = 1
                    var startX = 0f
                    var startY = 0f
                    var dragging = false
                    var minimizePending = false
                    var dragAction: DragAction? = null
                    var sequenceBasePosition = 0L
                    var currentFraction = 0.5f

                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            when (event.type) {
                                PointerEventType.Press -> {
                                    startX = change.position.x
                                    startY = change.position.y
                                    dragging = false
                                    minimizePending = false
                                    dragAction = null
                                }

                                PointerEventType.Move -> {
                                    val dy = change.position.y - startY
                                    val dx = change.position.x - startX
                                    if (!isFullscreen) {
                                        if (!minimizePending &&
                                            GestureMath.shouldMinimize(
                                                dy.toInt(),
                                                dx.toInt(),
                                                isFullscreen,
                                                canMinimize,
                                            )
                                        ) {
                                            change.consume()
                                            minimizePending = true
                                        }
                                        continue
                                    }
                                    if (dragging) continue
                                    if (abs(dy) < 40f) continue
                                    if (
                                        !GestureMath.canStartFullscreenGesture(
                                            startX,
                                            startY,
                                            dx,
                                            dy,
                                            size.width.toFloat(),
                                            size.height.toFloat(),
                                            48f,
                                        )
                                    ) {
                                        continue
                                    }
                                    val half = GestureMath.classifyHalf(startX.toInt(), size.width)
                                    val (volumeSide, brightnessSide) =
                                        GestureMath.resolveSides(
                                            settings.volumeGestureSide,
                                            settings.brightnessGestureSide,
                                        )
                                    dragAction =
                                        GestureMath.dragAction(
                                            half,
                                            volumeSide,
                                            brightnessSide,
                                            settings.gestureVolume,
                                            settings.gestureBrightness,
                                        )
                                    if (dragAction == null) continue
                                    change.consume()
                                    dragging = true
                                    currentFraction =
                                        if (dragAction == DragAction.VOLUME) {
                                            val max = audioManager.getStreamMaxVolume(C.STREAM_TYPE_MUSIC)
                                            val vol = audioManager.getStreamVolume(C.STREAM_TYPE_MUSIC)
                                            vol.toFloat() / max.coerceAtLeast(1)
                                        } else {
                                            settings.videoBrightness
                                                ?: activity?.window?.attributes?.screenBrightness?.takeIf { it > 0f }
                                                ?: 0.5f
                                        }
                                }

                                PointerEventType.Release -> {
                                    if (minimizePending) {
                                        change.consume()
                                        minimizePending = false
                                        onMinimize()
                                        continue
                                    }
                                    if (dragging) {
                                        change.consume()
                                        val dy = (change.position.y - startY).toInt()
                                        val next =
                                            GestureMath.dragFraction(dy, size.height, currentFraction)
                                        if (dragAction == DragAction.VOLUME) {
                                            val max = audioManager.getStreamMaxVolume(C.STREAM_TYPE_MUSIC)
                                            audioManager.setStreamVolume(
                                                C.STREAM_TYPE_MUSIC,
                                                kotlin.math.round(next * max).toInt(),
                                                0,
                                            )
                                        } else {
                                            val params = activity?.window?.attributes
                                            if (params != null) {
                                                params.screenBrightness = next
                                                activity.window.attributes = params
                                                onBrightness(next)
                                            }
                                        }
                                        indicator = (dragAction == DragAction.VOLUME) to next
                                        dragging = false
                                        continue
                                    }

                                    if (startY < topControlsPx) continue
                                    val now = System.currentTimeMillis()
                                    val side =
                                        GestureMath.classifyThird(startX.toInt(), size.width)
                                    val continuing = now - lastTapAt <= 550L && side != 1
                                    if (!continuing || taps == 0 || tapSide != side) {
                                        taps = 1
                                        sequenceBasePosition = player.currentPosition
                                    } else {
                                        taps += 1
                                    }
                                    tapSide = side
                                    lastTapAt = now
                                    if (isShorts && taps == 1) {
                                        if (player.isPlaying) player.pause() else player.play()
                                    }
                                    if (taps >= 2 && side != 1) {
                                        val target =
                                            GestureMath.seekTarget(
                                                sequenceBasePosition,
                                                taps,
                                                side,
                                                settings.doubleTapSeekSeconds,
                                                player.duration,
                                            )
                                        player.seekTo(target)
                                        seekFeedback =
                                            ((target - sequenceBasePosition) / 1000L).toInt()
                                    }
                                    onTap()
                                }

                                else -> Unit
                            }
                        }
                    }
                },
    ) {
        content()

        seekFeedback?.let { seconds ->
            val label = if (seconds > 0) "+$seconds s" else "$seconds s"
            Text(
                text = label,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .align(if (seconds > 0) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(32.dp)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(12.dp),
            )
        }

        indicator?.let { (isVolume, fraction) ->
            Box(
                modifier =
                    Modifier
                        .align(if (isVolume) Alignment.CenterEnd else Alignment.CenterStart)
                        .fillMaxHeight(0.55f)
                        .width(20.dp)
                        .padding(vertical = 16.dp)
                        .background(Color.Black.copy(alpha = 0.6f)),
            ) {
                Text(
                    text = "${(fraction * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
