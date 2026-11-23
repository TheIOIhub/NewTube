package com.example.newtube.ui.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.newtube.R
import com.example.newtube.ui.common.EmptyScreen
import com.example.newtube.ui.common.ErrorScreen
import com.example.newtube.ui.common.RemoteVideoCard
import com.example.newtube.ui.common.VideoCardSkeleton
import com.example.newtube.ui.shell.TabTopBar

@Composable
fun SubscriptionsScreen(
    navController: NavController,
    viewModel: SubscriptionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier = Modifier.fillMaxSize()) {
        TabTopBar(title = stringResource(R.string.subscriptions_title))
        when {
            state.signedOut -> SignedOutCta(onSignIn = { navController.navigate("login") })
            state.expired && state.items.isEmpty() -> ExpiredCta(onSignIn = { navController.navigate("login") })
            state.loading && state.items.isEmpty() -> SkeletonGrid()
            state.error && state.items.isEmpty() ->
                ErrorScreen(
                    message = stringResource(R.string.login_error_network),
                    onRetry = viewModel::retry,
                )
            state.items.isEmpty() -> EmptyScreen(message = stringResource(R.string.subscriptions_empty))
            else -> Feed(state = state, navController = navController, onLoadMore = viewModel::loadMore)
        }
    }
}

@Composable
private fun Feed(
    state: SubscriptionsUiState,
    navController: NavController,
    onLoadMore: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.expired) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.session_expired),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { navController.navigate("login") }) {
                    Text(stringResource(R.string.session_expired_action))
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(state.items, key = { _, video -> video.id }) { index, video ->
                RemoteVideoCard(
                    video = video,
                    onClick = { navController.navigate("watch/$it") },
                )
                if (index == state.items.lastIndex) {
                    LaunchedEffect(video.id) { onLoadMore() }
                }
            }
            if (state.loadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SignedOutCta(onSignIn: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.subscriptions_sign_in_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.subscriptions_sign_in_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onSignIn) {
            Text(stringResource(R.string.account_sign_in))
        }
    }
}

@Composable
private fun ExpiredCta(onSignIn: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.session_expired),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSignIn) {
            Text(stringResource(R.string.session_expired_action))
        }
    }
}

@Composable
private fun SkeletonGrid() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(6) { VideoCardSkeleton() }
    }
}
