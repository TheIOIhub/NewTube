package com.example.newtube.ui.shorts

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.newtube.ui.common.VideoListItem

@Composable
fun ShortsScreen(navController: NavController, viewModel: ShortsViewModel = hiltViewModel()) {
    val videos by viewModel.videos.collectAsState(initial = emptyList())

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(videos) { video ->
            VideoListItem(
                video = video,
                onClick = { videoId ->
                    navController.navigate("videoPlayer/$videoId")
                },
                onChannelClick = { channelName -> navController.navigate("channel/" + channelName) }
            )
        }
    }
}
