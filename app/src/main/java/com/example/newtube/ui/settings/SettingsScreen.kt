package com.example.newtube.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.newtube.R
import com.example.newtube.model.AppSettings
import com.example.newtube.model.ThemeMode
import com.example.newtube.ui.shell.DetailTopBar

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val hasLocalHistory by viewModel.hasLocalHistory.collectAsState()
    var themeDialogOpen by remember { mutableStateOf(false) }
    var qualityDialogOpen by remember { mutableStateOf(false) }
    var clearDialogOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(
            title = stringResource(R.string.settings_title),
            onBack = { navController.popBackStack() },
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
        ) {
            SettingsSection(stringResource(R.string.settings_appearance))
            SettingsRow(
                title = stringResource(R.string.settings_theme),
                subtitle = stringResource(settings.themeMode.labelRes()),
                onClick = { themeDialogOpen = true },
            )

            SettingsSection(stringResource(R.string.settings_history))
            SettingsSwitchRow(
                title = stringResource(R.string.settings_record_history),
                subtitle = stringResource(R.string.settings_record_history_summary),
                checked = settings.recordHistory,
                onCheckedChange = viewModel::setRecordHistory,
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_use_remote_history),
                subtitle = stringResource(R.string.settings_use_remote_history_disabled),
                checked = settings.useRemoteHistory,
                enabled = false,
                onCheckedChange = {},
            )
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                TextButton(
                    onClick = { clearDialogOpen = true },
                    enabled = hasLocalHistory,
                ) {
                    Text(
                        text = stringResource(R.string.settings_clear_history),
                        color =
                            if (hasLocalHistory) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }

            SettingsSection(stringResource(R.string.settings_playback))
            SettingsRow(
                title = stringResource(R.string.settings_max_quality),
                subtitle = stringResource(qualityLabelRes(settings.defaultQuality)),
                onClick = { qualityDialogOpen = true },
            )

            SettingsSection(stringResource(R.string.settings_about))
            SettingsRow(
                title = stringResource(R.string.settings_about),
                onClick = { navController.navigate("about") },
            )
        }
    }

    if (themeDialogOpen) {
        ThemeModeDialog(
            selected = settings.themeMode,
            onSelect = {
                viewModel.setThemeMode(it)
                themeDialogOpen = false
            },
            onDismiss = { themeDialogOpen = false },
        )
    }

    if (qualityDialogOpen) {
        QualityDialog(
            selected = settings.defaultQuality,
            onSelect = {
                viewModel.setDefaultQuality(it)
                qualityDialogOpen = false
            },
            onDismiss = { qualityDialogOpen = false },
        )
    }

    if (clearDialogOpen) {
        ClearHistoryDialog(
            onConfirm = {
                viewModel.clearHistory()
                clearDialogOpen = false
            },
            onDismiss = { clearDialogOpen = false },
        )
    }
}

@Composable
private fun ThemeModeDialog(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(mode) }
                                .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == mode, onClick = { onSelect(mode) })
                        Text(
                            text = stringResource(mode.labelRes()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun QualityDialog(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_max_quality)) },
        text = {
            Column {
                AppSettings.QUALITY_OPTIONS.forEach { option ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(option) }
                                .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == option, onClick = { onSelect(option) })
                        Text(
                            text = stringResource(qualityLabelRes(option)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun ClearHistoryDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_clear_history_title)) },
        text = { Text(stringResource(R.string.settings_clear_history_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.settings_clear),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
