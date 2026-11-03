package com.example.newtube.ui.home

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {
    val message: Flow<String> = flow { emit("Home Screen") }
}

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val msg = viewModel.message.collectAsState(initial = "Loading...").value
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = msg, style = MaterialTheme.typography.titleLarge)
    }
}