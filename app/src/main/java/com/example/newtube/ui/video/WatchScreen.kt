package com.example.newtube.ui.video

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.newtube.model.Video
import com.example.newtube.ui.common.ErrorScreen
import com.example.newtube.ui.common.LoadingScreen
import com.example.newtube.ui.common.LocalPipMode
import com.example.newtube.ui.common.UiState
import com.example.newtube.ui.common.VideoListItem
import com.example.newtube.ui.common.formatCount

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
                WatchContent(navController, videoId, watch, viewModel::savePosition)
            }
        }
    }
}

@Composable
private fun WatchContent(
    navController: NavHostController,
    videoId: String,
    watch: WatchUiState,
    onSavePosition: (Long) -> Unit,
) {
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    val details = watch.details
    val video = details.video

    Column(modifier = Modifier.fillMaxSize()) {
        VideoPlayer(
            playback = details.playback,
            startPositionMs = watch.startPositionMs,
            modifier =
                if (isFullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                },
            isFullscreen = isFullscreen,
            onToggleFullscreen = { isFullscreen = !isFullscreen },
            onSavePosition = onSavePosition,
        )

        if (!isFullscreen) {
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
                        Text("Comments")
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
                if (details.related.isNotEmpty()) {
                    item {
                        Text(
                            text = "Related videos",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(details.related, key = { it.id }) { related ->
                        VideoListItem(
                            video = related,
                            onClick = { relatedId -> navController.navigate("watch/$relatedId") },
                            onChannelClick = { channelId ->
                                navController.navigate("channel/$channelId")
                            },
                        )
                    }
                }
            }
        }
    }
}

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
        Text(
            text =
                "${formatCount(video.views)} views • ${video.uploadedAt}" +
                    if (video.isLive) " • LIVE" else "",
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
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = video.channelName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = video.channelName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "View channel",
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
                Text(if (expanded) "Show less" else "Show more")
            }
        }
    }
}
