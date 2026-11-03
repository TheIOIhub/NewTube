package com.example.newtube.ui.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.newtube.ui.video.PlayerViewModel
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView

@Composable
fun PlayerScreen(videoId: String) {
    val viewModel = hiltViewModel<PlayerViewModel>()
    LaunchedEffect(videoId) {
        viewModel.loadStream(videoId)
    }

    val playerState by viewModel.state.collectAsState()

    val context = LocalContext.current

    if (playerState.streamUrl == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Loading video...")
        }
    } else {
        val exoPlayer = remember(playerState.streamUrl) {
            ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri(playerState.streamUrl!!)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        DisposableEffect(exoPlayer) {
            onDispose { exoPlayer.release() }
        }
    }
}