package com.example.newtube.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.newtube.R
import com.example.newtube.model.HomeCategory
import com.example.newtube.model.Video
import com.example.newtube.ui.common.ShortsTile
import com.example.newtube.ui.common.UiState
import com.example.newtube.ui.common.VideoCard
import com.example.newtube.ui.common.VideoCardSkeleton
import com.example.newtube.ui.shell.HomeTopBar

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val shelf by viewModel.shortsShelf.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(selectedCategory) {
        listState.scrollToItem(0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HomeTopBar(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            onNotifications = { navController.navigate("notifications") },
            onSearch = { navController.navigate("search?focus=true") },
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item(key = "categories") {
                CategoryChips(
                    selected = selectedCategory,
                    onSelect = viewModel::selectCategory,
                )
            }
            if (shelf.isNotEmpty()) {
                item(key = "shorts-shelf") {
                    ShortsShelf(
                        videos = shelf,
                        onOpen = { videoId ->
                            navController.navigate("shorts?videoId=$videoId") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
            when (val state = uiState) {
                UiState.Loading -> items(count = 5) { VideoCardSkeleton() }
                is UiState.Error ->
                    item(key = "error") {
                        HomeMessage(message = state.message, onRetry = viewModel::load)
                    }
                is UiState.Success ->
                    if (state.data.isEmpty()) {
                        item(key = "empty") {
                            HomeMessage(message = stringResource(R.string.home_empty))
                        }
                    } else {
                        items(state.data, key = { it.id }) { video ->
                            VideoCard(
                                video = video,
                                onClick = { navController.navigate("watch/$it") },
                                onChannelClick = { navController.navigate("channel/$it") },
                            )
                        }
                    }
            }
            item(key = "bottom-spacer") { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun CategoryChips(
    selected: HomeCategory,
    onSelect: (HomeCategory) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(HomeCategory.entries) { category ->
            FilterChip(
                selected = category == selected,
                onClick = { onSelect(category) },
                label = { Text(stringResource(category.labelRes())) },
                shape = CircleShape,
                border = null,
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.onBackground,
                        selectedLabelColor = MaterialTheme.colorScheme.background,
                    ),
            )
        }
    }
}

@Composable
private fun ShortsShelf(
    videos: List<Video>,
    onOpen: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shorts),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.section_shorts),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(videos, key = { it.id }) { video ->
                ShortsTile(video = video, onClick = onOpen)
            }
        }
    }
}

@Composable
private fun HomeMessage(
    message: String,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}
