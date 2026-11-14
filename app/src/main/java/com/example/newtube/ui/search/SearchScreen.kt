package com.example.newtube.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.newtube.ui.common.EmptyScreen
import com.example.newtube.ui.common.ErrorScreen
import com.example.newtube.ui.common.LoadingScreen
import com.example.newtube.ui.common.UiState
import com.example.newtube.ui.common.VideoListItem

@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    var query by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                viewModel.onQueryChange(it)
            },
            placeholder = { Text("Search videos") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )

        when (val state = uiState) {
            UiState.Loading -> LoadingScreen()
            is UiState.Error -> ErrorScreen(state.message, onRetry = viewModel::retry)
            is UiState.Success ->
                if (state.data.isEmpty()) {
                    if (query.isBlank()) {
                        EmptyScreen("Search for videos")
                    } else {
                        EmptyScreen("No results for \"$query\"")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.data, key = { it.id }) { video ->
                            VideoListItem(
                                video = video,
                                onClick = { videoId -> navController.navigate("watch/$videoId") },
                                onChannelClick = { channelId ->
                                    navController.navigate("channel/$channelId")
                                },
                            )
                        }
                        item {
                            LaunchedEffect(state.data.size) {
                                viewModel.loadMore()
                            }
                            if (isLoadingMore) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }
}
