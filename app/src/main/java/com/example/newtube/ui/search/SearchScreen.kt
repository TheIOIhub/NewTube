package com.example.newtube.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.newtube.ui.common.VideoListItem
import com.example.newtube.model.Video

@Composable
fun SearchScreen(navController: NavController) {
    val viewModel: SearchViewModel = hiltViewModel()
    val results by viewModel.results.collectAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.collectAsState(initial = false)

    var query by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TextField(
            value = query,
            onValueChange = {
                query = it
                viewModel.search(it)
            },
            placeholder = { Text("Search videos") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(results) { video ->
                    VideoListItem(
                        video = video,
                        onClick = { videoId ->
                            navController.navigate("videoPlayer/" + videoId)
                        }
                    )
                }
            }
        }
    }
}
