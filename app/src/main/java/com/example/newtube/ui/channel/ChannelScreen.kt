package com.example.newtube.ui.channel

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.example.newtube.model.Channel
import com.example.newtube.ui.common.ErrorScreen
import com.example.newtube.ui.common.LoadingScreen
import com.example.newtube.ui.common.UiState
import com.example.newtube.ui.common.VideoCard
import com.example.newtube.ui.common.rememberShowMetadata
import com.example.newtube.ui.common.formatCount

@Composable
fun ChannelScreen(
    navController: NavHostController,
    channelId: String,
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    LaunchedEffect(channelId) {
        viewModel.loadChannelDetails(channelId)
    }

    val state by viewModel.uiState.collectAsState()

    when (val channelState = state.channel) {
        UiState.Loading -> LoadingScreen()
        is UiState.Error -> ErrorScreen(channelState.message, onRetry = viewModel::retry)
        is UiState.Success ->
            ChannelContent(
                channel = channelState.data,
                state = state,
                navController = navController,
                onLoadMore = viewModel::loadMore,
            )
    }
}

@Composable
private fun ChannelContent(
    channel: Channel,
    state: ChannelUiState,
    navController: NavHostController,
    onLoadMore: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            ChannelHeader(channel)
        }
        item {
            Text(
                text = "Videos",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(state.videos, key = { it.id }) { video ->
            VideoCard(
                video = video,
                onClick = { videoId -> navController.navigate("watch/$videoId") },
                onChannelClick = { id -> navController.navigate("channel/$id") },
                showMetadata = rememberShowMetadata(),
            )
        }
        item {
            LaunchedEffect(state.videos.size, state.isLoadingMore) {
                if (state.videosError == null) onLoadMore()
            }
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.isLoadingMore -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    state.videosError != null -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.videosError, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = onLoadMore) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelHeader(channel: Channel) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (channel.avatarUrl.isNotBlank()) {
            Image(
                painter = rememberAsyncImagePainter(channel.avatarUrl),
                contentDescription = "Channel avatar",
                modifier = Modifier.size(96.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            text = channel.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text =
                buildString {
                    append("${formatCount(channel.subscriberCount)} subscribers")
                    if (channel.totalVideos > 0) append(" • ${channel.totalVideos} videos")
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (channel.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = channel.description,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
