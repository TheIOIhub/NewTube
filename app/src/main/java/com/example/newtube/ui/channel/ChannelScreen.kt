package com.example.newtube.ui.channel

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.newtube.ui.common.VideoListItem

@Composable
fun ChannelScreen(navController: NavHostController, channelId: String) {
    val viewModel: ChannelViewModel = hiltViewModel()
    LaunchedEffect(channelId) {
        viewModel.loadChannelVideos(channelId)
    }
    val videos by viewModel.channelVideos.collectAsState(initial = emptyList())
    LazyColumn {
        items(videos) { video ->
            VideoListItem(
                video = video,
                onClick = { videoId ->
                    navController.navigate("videoPlayer/$videoId")
                }
            )
        }
    }
}
