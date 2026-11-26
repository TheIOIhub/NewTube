package com.example.newtube.ui.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.newtube.R
import com.example.newtube.di.PlaybackCacheEntryPoint
import com.example.newtube.model.PlaybackSource
import dagger.hilt.android.EntryPointAccessors

/**
 * ExoPlayer surface with the app's controls: play/pause and seeking come from
 * [PlayerView] itself, quality selection is a menu that constrains the track
 * selector, plus fullscreen and picture-in-picture.
 *
 * Fullscreen is not a dialog: the caller swaps the modifier to
 * `fillMaxSize()` and drops the rest of the screen, so the same player instance
 * survives the switch. The system bars are hidden while fullscreen.
 */
@Composable
fun VideoPlayer(
    playback: PlaybackSource,
    holder: ExoPlayerHolder? = null,
    modifier: Modifier = Modifier,
    startPositionMs: Long = 0L,
    loop: Boolean = false,
    useController: Boolean = true,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    showQualityControl: Boolean = true,
    showFullscreenControl: Boolean = true,
    showPipControl: Boolean = true,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onSavePosition: (Long) -> Unit = {},
    tapToTogglePlayback: Boolean = false,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var quality by rememberSaveable { mutableStateOf(AUTO_QUALITY) }
    var availableHeights by remember { mutableStateOf<List<Int>>(emptyList()) }
    var qualityMenuOpen by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    val trackSelector = remember { DefaultTrackSelector(context) }
    // Route Watch's media source through the SAME singleton cached
    // CacheDataSource.Factory as Shorts (spec G4 / plan Task 6; M4). The cache
    // itself is shared; this only reuses it, Shorts behavior is untouched.
    val cachedDataSourceFactory =
        remember(context) {
            EntryPointAccessors
                .fromApplication(
                    context.applicationContext,
                    PlaybackCacheEntryPoint::class.java,
                ).cacheDataSourceFactory()
        }
    // The watch screen owns its player; Shorts passes a holder so the same
    // instance survives page changes and no per-page player is created.
    val ownedPlayer =
        remember(playback, holder) {
            if (holder == null) {
                ExoPlayer
                    .Builder(context)
                    .setTrackSelector(trackSelector)
                    .build()
                    .apply {
                        repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                        setMediaSource(playback.toMediaSource(context, cachedDataSourceFactory))
                        playWhenReady = true
                        prepare()
                    }
            } else {
                null
            }
        }
    val exoPlayer = holder?.player ?: ownedPlayer ?: error("No player available")

    val currentOnSavePosition by rememberUpdatedState(onSavePosition)

    LaunchedEffect(exoPlayer) {
        Log.i(TAG, "Preparing ${playback.describe()} for ${startPositionMs}ms")
    }

    if (holder != null) {
        LaunchedEffect(playback) {
            holder.prepare(playback)
        }
    }

    LaunchedEffect(quality) {
        val parameters = trackSelector.buildUponParameters()
        if (quality == AUTO_QUALITY) {
            parameters.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        } else {
            parameters.setMaxVideoSize(Int.MAX_VALUE, quality)
        }
        trackSelector.setParameters(parameters.build())
    }

    DisposableEffect(exoPlayer) {
        var restored = false
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_READY) {
                        playbackError = null
                    }
                    if (playbackState == Player.STATE_READY && !restored) {
                        restored = true
                        val duration = exoPlayer.duration
                        if (startPositionMs > 0 && duration > 0 && startPositionMs < duration - RESUME_TOLERANCE_MS) {
                            exoPlayer.seekTo(startPositionMs)
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    // Surfaced in the UI instead of leaving the user on a black
                    // screen; the code and cause are also written to logcat.
                    Log.e(TAG, "Playback failed: ${error.errorCodeName}", error)
                    playbackError = error.errorCodeName
                }

                override fun onTracksChanged(tracks: Tracks) {
                    availableHeights =
                        tracks.groups
                            .flatMap { group ->
                                (0 until group.length).mapNotNull { index ->
                                    val format = group.getTrackFormat(index)
                                    val isVideo = format.sampleMimeType?.startsWith("video/") == true
                                    if (isVideo && format.height > 0) format.height else null
                                }
                            }.distinct()
                            .sortedDescending()
                }
            }
        exoPlayer.addListener(listener)
        onDispose {
            currentOnSavePosition(exoPlayer.currentPosition)
            exoPlayer.removeListener(listener)
            if (holder == null) exoPlayer.release()
        }
    }

    LaunchedEffect(isFullscreen) {
        val currentActivity = activity ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(currentActivity.window, currentActivity.window.decorView)
        if (isFullscreen) {
            currentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            currentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val currentActivity = activity ?: return@onDispose
            currentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat
                .getInsetsController(currentActivity.window, currentActivity.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(
        modifier = modifier.background(Color.Black),
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    player = exoPlayer
                    this.useController = useController
                    this.resizeMode = resizeMode
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (tapToTogglePlayback) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
            )
        }

        if (isBuffering && playbackError == null) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        val errorCode = playbackError
        if (errorCode != null) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "Playback failed ($errorCode)", color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        Log.i(TAG, "Retrying playback after $errorCode")
                        playbackError = null
                        if (holder != null) {
                            holder.prepare(playback)
                        } else {
                            exoPlayer.prepare()
                            exoPlayer.play()
                        }
                    },
                ) {
                    Text("Retry", color = Color.White)
                }
            }
        }

        Row(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showQualityControl) {
                Box {
                    TextButton(
                        onClick = { qualityMenuOpen = true },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) {
                        Text(if (quality == AUTO_QUALITY) "Auto" else "${quality}p")
                    }
                    DropdownMenu(
                        expanded = qualityMenuOpen,
                        onDismissRequest = { qualityMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Auto") },
                            onClick = {
                                quality = AUTO_QUALITY
                                qualityMenuOpen = false
                            },
                        )
                        availableHeights.forEach { height ->
                            DropdownMenuItem(
                                text = { Text("$height" + "p") },
                                onClick = {
                                    quality = height
                                    qualityMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }

            if (showPipControl && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                IconButton(
                    onClick = {
                        runCatching {
                            activity.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pip),
                        contentDescription = "Picture in picture",
                        tint = Color.White,
                    )
                }
            }

            if (showFullscreenControl) {
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        painter =
                            painterResource(
                                if (isFullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen,
                            ),
                        contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun PlaybackSource.describe(): String =
    when (this) {
        is PlaybackSource.Hls -> "hls source (host=${url.hostOrUnknown()})"
        is PlaybackSource.Progressive -> "progressive source (host=${url.hostOrUnknown()})"
        is PlaybackSource.Merged ->
            "merged source (video host=${videoUrl.hostOrUnknown()}, " +
                "audio host=${audioUrl.hostOrUnknown()})"
    }

private fun String.hostOrUnknown(): String = runCatching { java.net.URI(this).host }.getOrDefault("unknown")

private const val AUTO_QUALITY = 0
private const val RESUME_TOLERANCE_MS = 5_000L
private const val TAG = "VideoPlayer"
