package com.example.newtube.ui.notifications

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.newtube.R
import com.example.newtube.model.RemoteVideo
import com.example.newtube.ui.common.EmptyScreen
import com.example.newtube.ui.common.ErrorScreen
import com.example.newtube.ui.common.LoadingScreen
import com.example.newtube.ui.shell.DetailTopBar

@Composable
fun NotificationsScreen(
    navController: NavController,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(
            title = stringResource(R.string.notifications_title),
            onBack = { navController.popBackStack() },
        )
        when {
            state.signedOut -> SignedOutCta(onSignIn = { navController.navigate("login") })
            state.loading -> LoadingScreen()
            state.error ->
                ErrorScreen(
                    message = stringResource(R.string.login_error_network),
                    onRetry = viewModel::retry,
                )
            state.items.isEmpty() -> EmptyScreen(message = stringResource(R.string.notifications_empty))
            else ->
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { video ->
                        NotificationRow(
                            video = video,
                            onClick = { navController.navigate("watch/${video.id}") },
                        )
                    }
                }
        }
    }
}

@Composable
private fun NotificationRow(
    video: RemoteVideo,
    onClick: () -> Unit,
) {
    val metadata =
        listOfNotNull(
            video.channelName.takeIf { it.isNotBlank() },
            video.publishedText.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = video.id.isNotBlank(), onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (video.thumbnailUrl.isNotBlank()) {
            Image(
                painter = rememberAsyncImagePainter(video.thumbnailUrl),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(120.dp).height(68.dp).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (metadata.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
