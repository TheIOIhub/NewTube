package com.example.newtube.ui.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.newtube.R
import com.example.newtube.model.AppSettings
import com.example.newtube.model.Video
import com.example.newtube.ui.common.ChannelAvatar
import com.example.newtube.ui.common.ErrorScreen
import com.example.newtube.ui.common.LoadingScreen
import com.example.newtube.ui.common.LocalPipMode
import com.example.newtube.ui.common.UiState
import com.example.newtube.ui.common.VideoCard
import com.example.newtube.ui.common.formatCount
import com.example.newtube.ui.common.rememberShowMetadata
import com.example.newtube.ui.home.HomeScreen

@Composable
fun WatchScreen(
    navController: NavHostController,
    videoId: String,
    viewModel: WatchViewModel = hiltViewModel(),
) {
    LaunchedEffect(videoId) {
        viewModel.load(videoId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val inPip = LocalPipMode.current

    when (val state = uiState) {
        UiState.Loading -> LoadingScreen()
        is UiState.Error -> ErrorScreen(state.message, onRetry = viewModel::retry)
        is UiState.Success -> {
            val watch = state.data
            if (inPip) {
                VideoPlayer(
                    playback = watch.details.playback,
                    startPositionMs = watch.startPositionMs,
                    modifier = Modifier.fillMaxSize(),
                    useController = false,
                    showQualityControl = false,
                    showFullscreenControl = false,
                    showPipControl = false,
                    onSavePosition = viewModel::savePosition,
                )
            } else {
                WatchStage(
                    navController = navController,
                    videoId = videoId,
                    watch = watch,
                    settings = settings,
                    onSavePosition = viewModel::savePosition,
                    onBrightnessChange = viewModel::setVideoBrightness,
                    onRetry = viewModel::retry,
                    onPlaybackEnded = {
                        watch.nextVideo?.id?.let { nextId ->
                            viewModel.onPlaybackEnded { navController.navigate("watch/$nextId") }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun WatchStage(
    navController: NavHostController,
    videoId: String,
    watch: WatchUiState,
    settings: AppSettings,
    onSavePosition: (Long) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onRetry: () -> Unit,
    onPlaybackEnded: () -> Unit,
) {
    var isMinimized by rememberSaveable { mutableStateOf(false) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var playerSettings by remember { mutableStateOf(PlayerUiSettings()) }
    val context = LocalContext.current
    val activity =
        remember(context) {
            generateSequence(context) { current ->
                (current as? ContextWrapper)?.baseContext
            }.filterIsInstance<Activity>().firstOrNull()
        }

    BackHandler(enabled = isFullscreen) { isFullscreen = false }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isMinimized) {
            HomeScreen(navController)
        } else if (!isFullscreen) {
            WatchContent(
                navController = navController,
                videoId = videoId,
                watch = watch,
                topPadding = PLAYER_HEIGHT,
            )
        }

        val playerModifier =
            if (isMinimized) {
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .size(width = MINI_WIDTH, height = MINI_HEIGHT)
                    .clickable { isMinimized = false }
            } else if (isFullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            }

        VideoPlayer(
            playback = watch.details.playback,
            startPositionMs = watch.startPositionMs,
            modifier = playerModifier,
            isFullscreen = isFullscreen,
            onToggleFullscreen = { isFullscreen = !isFullscreen },
            onSavePosition = onSavePosition,
            resizeMode = playerSettings.resize,
            preferredAudioLanguage = settings.preferredAudioLanguage,
            preferOriginalAudio = settings.preferOriginalAudio,
            doubleTapSeekSeconds = settings.doubleTapSeekSeconds,
            gestureVolume = settings.gestureVolume,
            gestureBrightness = settings.gestureBrightness,
            volumeGestureSide = settings.volumeGestureSide,
            brightnessGestureSide = settings.brightnessGestureSide,
            videoBrightness = settings.videoBrightness,
            onBrightnessChange = onBrightnessChange,
            onPlaybackEnded = onPlaybackEnded,
            canMinimize = !isMinimized && !isFullscreen,
            onMinimize = { isMinimized = true },
            settings = playerSettings,
            onSettingsChange = { playerSettings = it },
            onPip = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                    runCatching {
                        activity.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                    }
                }
            },
            onRetry = onRetry,
        )

        if (isMinimized) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear))
            }
        }
    }
}

@Composable
private fun WatchContent(
    navController: NavHostController,
    videoId: String,
    watch: WatchUiState,
    topPadding: Dp,
) {
    val details = watch.details
    val video = details.video

    Column(modifier = Modifier.fillMaxSize().padding(top = topPadding)) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                VideoHeader(video = video)
            }
            item {
                ChannelRow(
                    video = video,
                    onClick = {
                        if (video.channelId.isNotBlank()) {
                            navController.navigate("channel/${video.channelId}")
                        }
                    },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { navController.navigate("comments/$videoId") },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(stringResource(R.string.watch_comments))
                }
            }
            if (details.description.isNotBlank()) {
                item {
                    DescriptionBlock(description = details.description)
                }
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            val next = watch.nextVideo
            if (next != null) {
                item {
                    TextButton(
                        onClick = { navController.navigate("watch/${next.id}") },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    ) {
                        Text(stringResource(R.string.watch_next, next.title))
                    }
                }
            }
            if (details.related.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.watch_related),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(details.related, key = { it.id }) { related ->
                    VideoCard(
                        video = related,
                        onClick = { relatedId -> navController.navigate("watch/$relatedId") },
                        onChannelClick = { channelId ->
                            navController.navigate("channel/$channelId")
                        },
                        showMetadata = rememberShowMetadata(),
                    )
                }
            }
        }
    }
}

private val PLAYER_HEIGHT: Dp = 202.dp
private val MINI_WIDTH: Dp = 200.dp
private val MINI_HEIGHT: Dp = 112.dp

@Composable
private fun VideoHeader(video: Video) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val views = stringResource(R.string.views_count, formatCount(video.views))
        Text(
            text = "$views • ${video.uploadedAt}" + if (video.isLive) " • LIVE" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChannelRow(video: Video, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = video.channelId.isNotBlank(), onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelAvatar(
            url = video.authorAvatarUrl,
            name = video.channelName,
            size = 40.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = video.channelName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.watch_view_channel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DescriptionBlock(description: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                    )
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
        )
        if (description.length > 140) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    if (expanded) {
                        stringResource(R.string.watch_show_less)
                    } else {
                        stringResource(R.string.watch_show_more)
                    },
                )
            }
        }
    }
}
