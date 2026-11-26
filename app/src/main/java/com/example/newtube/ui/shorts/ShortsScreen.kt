package com.example.newtube.ui.shorts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import com.example.newtube.R
import com.example.newtube.di.PlaybackCacheEntryPoint
import com.example.newtube.model.PlaybackSource
import com.example.newtube.model.Video
import com.example.newtube.ui.common.EmptyScreen
import com.example.newtube.ui.common.ErrorScreen
import com.example.newtube.ui.common.LoadingScreen
import com.example.newtube.ui.common.UiState
import com.example.newtube.ui.video.mediaItem
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

    // Warm the shared ImageLoader so the first pages don't show a blank box.
    val context = LocalContext.current
    LaunchedEffect(uiState) {
        val state = uiState
        if (state is UiState.Success) {
            state.data.take(4).forEach { video ->
                context.imageLoader.enqueue(
                    ImageRequest.Builder(context).data(video.thumbnailUrl).build(),
                )
            }
        }
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

    // One player and one preload manager for the whole pager; both are created
    // from the same components and released only when Shorts leaves composition.
    val context = LocalContext.current
    val controller =
        remember {
            val dataSourceFactory =
                EntryPointAccessors
                    .fromApplication(
                        context.applicationContext,
                        PlaybackCacheEntryPoint::class.java,
                    ).cacheDataSourceFactory()
            Media3ShortsPlaybackController(context, dataSourceFactory)
        }
    DisposableEffect(Unit) {
        onDispose { controller.release() }
    }

    // Ranking indices in the preload manager follow the resolved feed, so an
    // unresolved clip never occupies a slot with an empty MediaItem.
    val resolvedVideos = remember(videos, sources) { videos.filter { sources[it.id] != null } }
    val mediaItems =
        remember(resolvedVideos, sources) { resolvedVideos.map { sources[it.id]!!.mediaItem() } }

    val requestedStartId by viewModel.requestedStartId.collectAsState()
    val targetIndex = if (requestedStartId == null) -1 else videos.indexOfFirst { it.id == requestedStartId }

    LaunchedEffect(requestedStartId, targetIndex, videos.size) {
        if (requestedStartId != null && targetIndex >= 0) {
            pagerState.scrollToPage(targetIndex)
            viewModel.consumeStartRequest()
        }
    }

    LaunchedEffect(pagerState.currentPage, videos.size) {
        val page = pagerState.currentPage
        if (videos.getOrNull(page) == null) return@LaunchedEffect
        viewModel.onPageChanged(page)
        if (page >= videos.size - LOAD_MORE_THRESHOLD) {
            viewModel.loadMore()
        }
    }

    // The controller owns the sliding window; the pager only tells it which
    // items exist and which one the user settled on.
    LaunchedEffect(pagerState.currentPage, mediaItems) {
        controller.setItems(mediaItems)
    }

    // A single prepare path: play only when the active clip's source is ready.
    val activeSource = sources[videos.getOrNull(pagerState.currentPage)?.id]
    LaunchedEffect(pagerState.currentPage, activeSource) {
        if (activeSource != null) controller.play(activeSource) else controller.stop()
    }

    // History starts on the settled page: currentPage changes mid-swipe and
    // would count a clip the user is only passing through.
    LaunchedEffect(pagerState.settledPage, videos.size) {
        val video = videos.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
        viewModel.onPageSettled(video)
    }
    LaunchedEffect(pagerState.settledPage, resolvedVideos) {
        val video = videos.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
        val index = resolvedVideos.indexOfFirst { it.id == video.id }
        if (index >= 0) controller.setCurrentIndex(index)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().background(Color.Black),
        ) { page ->
            val video = videos[page]
            ShortPage(
                video = video,
                player = controller.player,
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
    player: ExoPlayer,
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
            // No prepare here: the controller prepared this clip before the
            // surface was attached, so first frames come from the preloaded
            // source.
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier =
                    Modifier.fillMaxSize().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (player.isPlaying) player.pause() else player.play()
                    },
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
