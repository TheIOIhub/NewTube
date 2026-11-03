package com.example.newtube.ui.shorts

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@Composable
fun ShortsScreen(navController: NavController, viewModel: ShortsViewModel = hiltViewModel()) {
    val videos = viewModel.videos.collectAsState(initial = emptyList()).value
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Shorts: ${videos.size} videos loaded",
            style = MaterialTheme.typography.titleLarge
        )
    }
}
