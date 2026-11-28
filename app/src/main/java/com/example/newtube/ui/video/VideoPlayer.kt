package com.example.newtube.ui.video

import android.app.Activity
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.newtube.R
import com.example.newtube.di.PlaybackCacheEntryPoint
import com.example.newtube.model.AppSettings
import com.example.newtube.model.GestureSide
import com.example.newtube.model.PlaybackSource
import com.example.newtube.ui.common.LocalPipMode
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
    preferredAudioLanguage: String = "uk",
    preferOriginalAudio: Boolean = true,
    showAudioControl: Boolean = true,
    onResizeModeChange: (Int) -> Unit = {},
    gesturesEnabled: Boolean = true,
    isShorts: Boolean = false,
    doubleTapSeekSeconds: Int = 10,
    gestureVolume: Boolean = true,
    gestureBrightness: Boolean = true,
    volumeGestureSide: GestureSide = GestureSide.RIGHT,
    brightnessGestureSide: GestureSide = GestureSide.LEFT,
    videoBrightness: Float? = null,
    onBrightnessChange: (Float) -> Unit = {},
    onPlaybackEnded: () -> Unit = {},
    canMinimize: Boolean = false,
    onMinimize: () -> Unit = {},
    settings: PlayerUiSettings = PlayerUiSettings(),
    onSettingsChange: (PlayerUiSettings) -> Unit = {},
    onPip: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var availableHeights by remember { mutableStateOf<List<Int>>(emptyList()) }
    var audioOptions by remember { mutableStateOf<List<AudioTrackOption>>(emptyList()) }
    var selectedAudioKey by rememberSaveable { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var settingsPage by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsEpoch by remember { mutableStateOf(0) }

    fun revealControls() {
        controlsVisible = true
        controlsEpoch++
    }

    LaunchedEffect(controlsEpoch) {
        if (controlsEpoch > 0) {
            kotlinx.coroutines.delay(3_000)
            controlsVisible = false
        }
    }

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
                    .setLoadControl(watchLoadControl())
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
    val currentOnPlaybackEnded by rememberUpdatedState(onPlaybackEnded)

    LaunchedEffect(exoPlayer) {
        Log.i(TAG, "Preparing ${playback.describe()} for ${startPositionMs}ms")
    }

    if (holder != null) {
        LaunchedEffect(playback) {
            holder.prepare(playback)
        }
    }

    LaunchedEffect(settings.quality, preferredAudioLanguage, preferOriginalAudio) {
        val parameters =
            trackSelector
                .buildUponParameters()
                .setPreferredAudioLanguage(preferredAudioLanguage)
                .setPreferredAudioRoleFlags(if (preferOriginalAudio) C.ROLE_FLAG_MAIN else 0)
        if (settings.quality == Int.MAX_VALUE) {
            parameters.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        } else {
            parameters.setMaxVideoSize(Int.MAX_VALUE, settings.quality)
        }
        trackSelector.setParameters(parameters.build())
    }

    if (!isShorts) {
        LaunchedEffect(settings.speed) {
            exoPlayer.playbackParameters = PlaybackParameters(settings.speed)
        }
        LaunchedEffect(settings.repeat) {
            exoPlayer.repeatMode = if (settings.repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
    }

    LaunchedEffect(settings.audioLabel) {
        val parameters = trackSelector.buildUponParameters()
        if (settings.audioLabel == "Авто") {
            parameters.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        } else {
            audioOptions.firstOrNull { it.label == settings.audioLabel }?.let { option ->
                parameters.setOverrideForType(
                    TrackSelectionOverride(option.group.mediaTrackGroup, option.index),
                )
            }
        }
        trackSelector.setParameters(parameters.build())
    }

    DisposableEffect(exoPlayer) {
        var restored = false
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_READY) {
                        playbackError = null
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        currentOnPlaybackEnded()
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
                    val options = tracks.audioOptions()
                    audioOptions = options
                    val key = selectedAudioKey
                    if (key != null) {
                        options.firstOrNull { it.key == key }?.let { option ->
                            trackSelector.setParameters(
                                trackSelector
                                    .buildUponParameters()
                                    .setOverrideForType(
                                        TrackSelectionOverride(option.group.mediaTrackGroup, option.index),
                                    ),
                            )
                        }
                    }
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
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH
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
        PlayerGestureOverlay(
            player = exoPlayer,
            isFullscreen = isFullscreen,
            isShorts = isShorts,
            settings =
                AppSettings(
                    doubleTapSeekSeconds = doubleTapSeekSeconds,
                    gestureVolume = gestureVolume,
                    gestureBrightness = gestureBrightness,
                    volumeGestureSide = volumeGestureSide,
                    brightnessGestureSide = settings.brightnessGestureSide,
                    videoBrightness = videoBrightness,
                ),
            onBrightness = onBrightnessChange,
            onTap = { revealControls() },
            enabled = gesturesEnabled,
            canMinimize = canMinimize && !isFullscreen,
            onMinimize = onMinimize,
        ) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        player = exoPlayer
                        this.useController = useController && isShorts
                        this.resizeMode = resizeMode
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view -> view.resizeMode = resizeMode },
            )

            if (tapToTogglePlayback && !gesturesEnabled) {
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
                    Text(text = stringResource(R.string.player_error), color = Color.White)
                    Text(text = errorCode, color = Color.White.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            Log.i(TAG, "Retrying playback after $errorCode")
                            playbackError = null
                            onRetry()
                        },
                    ) {
                        Text(stringResource(R.string.player_retry), color = Color.White)
                    }
                }
            }

            if (!isShorts && !LocalPipMode.current && playbackError == null) {
                if (controlsVisible) {
                    PlayerTopBar(
                        isFullscreen = isFullscreen,
                        qualityLabel = settings.qualityLabel(),
                        showPip =
                            settings.pipEnabled &&
                                showPipControl &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
                        onBack = {
                            if (isFullscreen) onToggleFullscreen() else onMinimize()
                            revealControls()
                        },
                        onQuality = {
                            settingsPage = PlayerLabels.QUALITY
                            settingsOpen = true
                            revealControls()
                        },
                        onPip = {
                            onPip()
                            revealControls()
                        },
                        onSettings = {
                            settingsPage = null
                            settingsOpen = true
                            revealControls()
                        },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                    PlayerCenterControls(
                        isPlaying = isPlaying,
                        onPlayPause = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            revealControls()
                        },
                        onSeek = { seconds ->
                            exoPlayer.seekTo(exoPlayer.currentPosition + seconds * 1_000L)
                            revealControls()
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                    PlayerBottomBar(
                        positionMs = exoPlayer.currentPosition,
                        durationMs = exoPlayer.duration.coerceAtLeast(0L),
                        isLive = playback is PlaybackSource.Hls,
                        isFullscreen = isFullscreen,
                        onSeekTo = { ms ->
                            exoPlayer.seekTo(ms)
                            revealControls()
                        },
                        onFullscreen = {
                            onToggleFullscreen()
                            revealControls()
                        },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                if (settingsOpen) {
                    PlayerSettingsSheet(
                        settings = settings,
                        heights = (AppSettings.QUALITY_OPTIONS + availableHeights).distinct().sortedDescending(),
                        audioOptions = audioOptions.map { it.label },
                        onSettingsChange = { next ->
                            onSettingsChange(next)
                            revealControls()
                        },
                        onPip = onPip,
                        onDismiss = { settingsOpen = false },
                        initialPage = settingsPage,
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

/**
 * Watch uses the same tuned buffering as Media3ShortsPlaybackController
 * (1500/5000/1000/1500 ms, 96 MiB target) so the shared media cache serves
 * both screens with identical buffer settings.
 */
private fun watchLoadControl(): DefaultLoadControl =
    DefaultLoadControl
        .Builder()
        .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
        .setBufferDurationsMs(
            // Media3 1.4.1 rejects minBufferMs < bufferForPlaybackAfterRebufferMs.
            maxOf(1_000, 1_500),
            5_000,
            1_000,
            1_500,
        ).setPrioritizeTimeOverSizeThresholds(true)
        .setTargetBufferBytes(WATCH_TARGET_BUFFER_BYTES)
        .build()

private fun PlaybackSource.describe(): String =
    when (this) {
        is PlaybackSource.Hls -> "hls source (host=${url.hostOrUnknown()})"
        is PlaybackSource.Progressive -> "progressive source (host=${url.hostOrUnknown()})"
        is PlaybackSource.Merged ->
            "merged source (video host=${videoUrl.hostOrUnknown()}, " +
                "audio host=${audioUrl.hostOrUnknown()})"
    }

private fun String.hostOrUnknown(): String = runCatching { java.net.URI(this).host }.getOrDefault("unknown")

private const val RESUME_TOLERANCE_MS = 5_000L
private const val WATCH_TARGET_BUFFER_BYTES = 96 * 1024 * 1024
private const val TAG = "VideoPlayer"
