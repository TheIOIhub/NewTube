package com.example.newtube.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.newtube.ui.settings.SettingsViewModel

/** Shared read of the "show metadata" preference for card composables. */
@Composable
fun rememberShowMetadata(): Boolean {
    val viewModel: SettingsViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsState()
    return settings.showMetadata
}
