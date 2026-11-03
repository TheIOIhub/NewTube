package com.example.newtube.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.newtube.ui.common.VideoListItem

@Composable
fun HomeScreen(navController: NavController) {
    val viewModel: HomeViewModel = hiltViewModel()
    val videos by viewModel.videos.collectAsState(initial = emptyList())

    LazyColumn(modifier = Modifier.fillMaxSize()) {
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
