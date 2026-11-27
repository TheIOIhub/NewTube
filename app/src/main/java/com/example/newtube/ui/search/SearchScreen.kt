package com.example.newtube.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.newtube.R
import com.example.newtube.ui.common.EmptyScreen
import com.example.newtube.ui.common.ErrorScreen
import com.example.newtube.ui.common.InlineBanner
import com.example.newtube.ui.common.UiState
import com.example.newtube.ui.common.VideoCard
import com.example.newtube.ui.common.VideoCardSkeleton
import com.example.newtube.ui.common.rememberShowMetadata

@Composable
fun SearchScreen(
    navController: NavController,
    focus: Boolean = false,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val sourceUnavailable by viewModel.sourceUnavailable.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(focus) {
        if (focus) focusRequester.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        viewModel.onQueryChange(it)
                    },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = {
                                query = ""
                                viewModel.onQueryChange("")
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }
            if (suggestions.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    suggestions.forEach { suggestion ->
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        query = suggestion
                                        viewModel.acceptSuggestion(suggestion)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }

        if (isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
        }
        if (sourceUnavailable && uiState is UiState.Success) {
            InlineBanner(
                message = stringResource(R.string.source_unavailable),
                action = stringResource(R.string.retry),
                onAction = viewModel::retry,
            )
        }

        when (val state = uiState) {
            UiState.Loading ->
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(SEARCH_SKELETON_COUNT) { VideoCardSkeleton() }
                }
            is UiState.Error -> ErrorScreen(state.message, onRetry = viewModel::retry)
            is UiState.Success ->
                if (state.data.isEmpty()) {
                    if (query.isNotBlank()) EmptyScreen(stringResource(R.string.search_empty))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.data, key = { it.id }) { video ->
                            VideoCard(
                                video = video,
                                onClick = { videoId -> navController.navigate("watch/$videoId") },
                                onChannelClick = { channelId ->
                                    navController.navigate("channel/$channelId")
                                },
                                showMetadata = rememberShowMetadata(),
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

private const val SEARCH_SKELETON_COUNT = 5
