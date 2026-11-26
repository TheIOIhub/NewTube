package com.example.newtube.ui.shorts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.newtube.R
import com.example.newtube.di.PlaybackCacheEntryPoint
import com.example.newtube.model.PlaybackSource
import com.example.newtube.model.Video
import com.example.newtube.ui.common.EmptyScreen
import com.example.newtube.ui.common.ErrorScreen
import com.example.newtube.ui.common.LoadingScreen
import com.example.newtube.ui.common.UiState
import com.example.newtube.ui.video.ExoPlayerHolder
import com.example.newtube.ui.video.VideoPlayer
import com.example.newtube.ui.video.shortsLoadControl
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import dagger.hilt.android.EntryPointAccessors

@Composable
fun ShortsScreen(
    navController: NavController,
    startVideoId: String? = null,
    viewModel: ShortsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val sources by viewModel.playbackSources.collectAsState()
    val playbackErrors by viewModel.playbackErrors.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val refreshError by viewModel.refreshError.collectAsState()

    LaunchedEffect(startVideoId) {
        if (startVideoId != null) viewModel.startFrom(startVideoId)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.cancelPendingRecord() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            UiState.Loading -> LoadingScreen()
            is UiState.Error -> ErrorScreen(state.message, onRetry = viewModel::reload)
            is UiState.Success ->
                if (state.data.isEmpty()) {
                    EmptyScreen(stringResource(R.string.shorts_empty))
                } else {
                    ShortsPager(
                        videos = state.data,
                        sources = sources,
                        playbackErrors = playbackErrors,
                        viewModel = viewModel,
                        navController = navController,
                    )
                }
        }

        if (isRefreshing) {
            LinearProgressIndicator(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
            )
        }

        refreshError?.let { message ->
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp)
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = viewModel::dismissRefreshError) {
                    Text(stringResource(R.string.action_cancel), color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortsPager(
    videos: List<Video>,
    sources: Map<String, PlaybackSource>,
    playbackErrors: Map<String, String>,
    viewModel: ShortsViewModel,
    navController: NavController,
) {
    val pagerState = rememberPagerState(pageCount = { videos.size })

    // One player for the whole pager; it is released only when Shorts leaves
    // composition, never between pages.
    val context = LocalContext.current
    val holder =
        remember {
            val dataSourceFactory =
                EntryPointAccessors
                    .fromApplication(
                        context.applicationContext,
                        PlaybackCacheEntryPoint::class.java,
                    ).cacheDataSourceFactory()
            ExoPlayerHolder(context, dataSourceFactory, shortsLoadControl())
        }
    DisposableEffect(Unit) {
        onDispose { holder.release() }
    }

    val requestedStartId by viewModel.requestedStartId.collectAsState()
    val targetIndex = if (requestedStartId == null) -1 else videos.indexOfFirst { it.id == requestedStartId }

    LaunchedEffect(requestedStartId, targetIndex, videos.size) {
        if (requestedStartId != null && targetIndex >= 0) {
            pagerState.scrollToPage(targetIndex)
            viewModel.consumeStartRequest()
        }
    }

    LaunchedEffect(pagerState.currentPage, videos.size) {
        val currentVideo = videos.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        viewModel.onPageChanged(pagerState.currentPage)
        // VideoPlayer prepares the resolved source on mount; only clear the
        // shared player here while the active page has no source yet.
        if (sources[currentVideo.id] == null) holder.stop()
        if (pagerState.currentPage >= videos.size - LOAD_MORE_THRESHOLD) {
            viewModel.loadMore()
        }
    }

    // History starts on the settled page: currentPage changes mid-swipe and
    // would count a clip the user is only passing through.
    LaunchedEffect(pagerState.settledPage, videos.size) {
        val video = videos.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
        viewModel.onPageSettled(video)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().background(Color.Black),
        ) { page ->
            val video = videos[page]
            ShortPage(
                video = video,
                holder = holder,
                source = sources[video.id],
                playbackError = playbackErrors[video.id],
                isActive = pagerState.currentPage == page,
                onOpen = { navController.navigate("watch/${video.id}") },
                onChannelClick = {
                    if (video.channelId.isNotBlank()) {
                        navController.navigate("channel/${video.channelId}")
                    }
                },
                onRetry = { viewModel.ensurePlayback(video.id, force = true) },
            )
        }

        IconButton(
            onClick = viewModel::reload,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape),
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = stringResource(R.string.shorts_shuffle),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun ShortPage(
    video: Video,
    holder: ExoPlayerHolder,
    source: PlaybackSource?,
    playbackError: String?,
    isActive: Boolean,
    onOpen: () -> Unit,
    onChannelClick: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // The thumbnail stays visible until the player has a surface of its own,
        // and is used as a placeholder for the pages around the active one.
        Image(
            painter = rememberAsyncImagePainter(video.thumbnailUrl),
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (isActive && source != null) {
            VideoPlayer(
                playback = source,
                holder = holder,
                modifier = Modifier.fillMaxSize(),
                loop = true,
                useController = false,
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                showQualityControl = false,
                showFullscreenControl = false,
                showPipControl = false,
                tapToTogglePlayback = true,
            )
        }

        if (isActive && playbackError != null) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = playbackError, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
            }
        }

        if (isActive && source == null && playbackError == null) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, end = 72.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = video.title,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = video.channelName,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable(onClick = onChannelClick),
            )
        }

        TextButton(
            onClick = onOpen,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
        ) {
            Text(stringResource(R.string.shorts_open), color = Color.White)
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 2
