package com.example.newtube.ui.channel

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.example.newtube.ui.common.VideoListItem
import androidx.navigation.NavHostController

@Composable
fun ChannelScreen(navController: NavHostController, channelId: String) {
    val viewModel: ChannelViewModel = hiltViewModel()
    LaunchedEffect(channelId) {
        viewModel.loadChannelDetails(channelId)
    }
    val channel by viewModel.channelState.collectAsState(initial = null)

    channel?.let { ch ->
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Image(
                painter = rememberAsyncImagePainter(ch.avatarUrl),
                contentDescription = "Channel avatar",
                modifier = Modifier.size(120.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(ch.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("${ch.subscriberCount} subscribers", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(8.dp))
            Text(ch.description, style = MaterialTheme.typography.bodySmall)
        }
    }
}
