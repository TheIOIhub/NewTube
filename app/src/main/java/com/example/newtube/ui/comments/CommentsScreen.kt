package com.example.newtube.ui.comments

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.example.newtube.model.Comment
import com.example.newtube.ui.common.ErrorScreen
import com.example.newtube.ui.common.LoadingScreen
import com.example.newtube.ui.common.UiState
import com.example.newtube.ui.common.formatCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(
    navController: NavHostController,
    videoId: String,
    viewModel: CommentsViewModel = hiltViewModel(),
) {
    LaunchedEffect(videoId) {
        viewModel.load(videoId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val totalCount = (uiState as? UiState.Success)?.data?.totalCount ?: 0L

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (totalCount > 0) {
                            "Comments (${formatCount(totalCount)})"
                        } else {
                            "Comments"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val state = uiState) {
                UiState.Loading -> LoadingScreen()
                is UiState.Error -> ErrorScreen(state.message, onRetry = viewModel::retry)
                is UiState.Success ->
                    if (state.data.comments.isEmpty()) {
                        Text(
                            text = "No comments yet",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        CommentsList(
                            data = state.data,
                            onLoadMore = viewModel::loadMore,
                        )
                    }
            }
        }
    }
}

@Composable
private fun CommentsList(
    data: CommentsUiState,
    onLoadMore: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(data.comments, key = { it.id }) { comment ->
            CommentItem(comment)
            HorizontalDivider()
        }
        item {
            if (data.isLoadingMore) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else if (data.hasMore) {
                Button(
                    onClick = onLoadMore,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text("Load more")
                }
            }
        }
    }
}

@Composable
private fun CommentItem(comment: Comment) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (comment.authorAvatarUrl.isNotBlank()) {
            Image(
                painter = rememberAsyncImagePainter(comment.authorAvatarUrl),
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.author,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (comment.isPinned) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pinned",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = comment.publishedText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (comment.likeCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${formatCount(comment.likeCount)} likes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
