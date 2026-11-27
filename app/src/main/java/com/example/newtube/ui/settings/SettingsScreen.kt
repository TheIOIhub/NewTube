package com.example.newtube.ui.settings

import android.os.Build
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
import com.example.newtube.model.AccountState
import com.example.newtube.model.AppSettings
import com.example.newtube.model.GestureSide
import com.example.newtube.model.ThemeMode
import com.example.newtube.ui.common.SignOutDialog
import com.example.newtube.ui.shell.DetailTopBar

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val hasLocalHistory by viewModel.hasLocalHistory.collectAsState()
    val account by viewModel.account.collectAsState()
    var clearDialogOpen by remember { mutableStateOf(false) }
    var signOutDialogOpen by remember { mutableStateOf(false) }

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
            SettingsSection(stringResource(R.string.settings_account))
            val signedInAccount = (account as? AccountState.SignedIn)?.account
            if (signedInAccount != null) {
                SettingsRow(
                    title = signedInAccount.name.ifBlank { stringResource(R.string.you_guest) },
                    subtitle = signedInAccount.email,
                )
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    TextButton(onClick = { signOutDialogOpen = true }) {
                        Text(
                            text = stringResource(R.string.account_sign_out),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            } else {
                SettingsRow(
                    title = stringResource(R.string.account_sign_in),
                    onClick = { navController.navigate("login") },
                )
            }

            SettingsSection(stringResource(R.string.settings_section_appearance))
            SettingsRow(
                title = stringResource(R.string.settings_section_appearance),
                subtitle = stringResource(settings.themeMode.labelRes()),
                onClick = { navController.navigate("settings/appearance") },
            )

            SettingsSection(stringResource(R.string.settings_section_video_audio))
            SettingsRow(
                title = stringResource(R.string.settings_section_video_audio),
                subtitle = stringResource(qualityLabelRes(settings.defaultQuality)),
                onClick = { navController.navigate("settings/video") },
            )

            SettingsSection(stringResource(R.string.settings_section_content))
            SettingsRow(
                title = stringResource(R.string.settings_section_content),
                subtitle = settings.contentCountry,
                onClick = { navController.navigate("settings/content") },
            )

            SettingsSection(stringResource(R.string.settings_section_gestures))
            SettingsRow(
                title = stringResource(R.string.settings_section_gestures),
                onClick = { navController.navigate("settings/gestures") },
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
                subtitle =
                    if (signedInAccount == null) {
                        stringResource(R.string.settings_use_remote_history_disabled)
                    } else {
                        null
                    },
                checked = settings.useRemoteHistory,
                enabled = signedInAccount != null,
                onCheckedChange = viewModel::setUseRemoteHistory,
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

            SettingsSection(stringResource(R.string.settings_about))
            SettingsRow(
                title = stringResource(R.string.settings_about),
                onClick = { navController.navigate("about") },
            )
        }
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

    if (signOutDialogOpen) {
        SignOutDialog(
            onConfirm = {
                viewModel.signOut()
                signOutDialogOpen = false
            },
            onDismiss = { signOutDialogOpen = false },
        )
    }
}

@Composable
internal fun AppearanceSettingsScreen(
    viewModel: SettingsViewModel,
    settings: AppSettings,
    onBack: () -> Unit,
) {
    var themeDialogOpen by remember { mutableStateOf(false) }
    SettingsSubScreen(title = stringResource(R.string.settings_section_appearance), onBack = onBack) {
        SettingsRow(
            title = stringResource(R.string.settings_theme),
            subtitle = stringResource(settings.themeMode.labelRes()),
            onClick = { themeDialogOpen = true },
        )
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
}

@Composable
internal fun VideoAudioSettingsScreen(
    viewModel: SettingsViewModel,
    settings: AppSettings,
    onBack: () -> Unit,
) {
    var qualityDialogOpen by remember { mutableStateOf(false) }
    var seekDialogOpen by remember { mutableStateOf(false) }
    var resizeDialogOpen by remember { mutableStateOf(false) }
    var audioLanguageDialogOpen by remember { mutableStateOf(false) }

    SettingsSubScreen(title = stringResource(R.string.settings_section_video_audio), onBack = onBack) {
        SettingsRow(
            title = stringResource(R.string.settings_max_quality),
            subtitle = stringResource(qualityLabelRes(settings.defaultQuality)),
            onClick = { qualityDialogOpen = true },
        )
        SettingsRow(
            title = stringResource(R.string.settings_seek_seconds),
            subtitle = stringResource(R.string.settings_seek_seconds_value, settings.doubleTapSeekSeconds),
            onClick = { seekDialogOpen = true },
        )
        SettingsRow(
            title = stringResource(R.string.settings_preferred_audio_language),
            subtitle = settings.preferredAudioLanguage,
            onClick = { audioLanguageDialogOpen = true },
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_prefer_original_audio),
            checked = settings.preferOriginalAudio,
            onCheckedChange = viewModel::setPreferOriginalAudio,
        )
        SettingsRow(
            title = stringResource(R.string.settings_resize_mode),
            subtitle = stringResource(resizeLabelRes(settings.resizeMode)),
            onClick = { resizeDialogOpen = true },
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_autoplay_next),
            checked = settings.autoplayNext,
            onCheckedChange = viewModel::setAutoplayNext,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_resume_playback),
            checked = settings.resumePlayback,
            onCheckedChange = viewModel::setResumePlayback,
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
    if (seekDialogOpen) {
        SettingsChoiceDialog(
            title = stringResource(R.string.settings_seek_seconds),
            options = AppSettings.SEEK_OPTIONS,
            selected = settings.doubleTapSeekSeconds,
            label = { stringResource(R.string.settings_seek_seconds_value, it) },
            onSelect = {
                viewModel.setDoubleTapSeekSeconds(it)
                seekDialogOpen = false
            },
            onDismiss = { seekDialogOpen = false },
        )
    }
    if (audioLanguageDialogOpen) {
        SettingsChoiceDialog(
            title = stringResource(R.string.settings_preferred_audio_language),
            options = AppSettings.VALID_LANGUAGES.sorted(),
            selected = settings.preferredAudioLanguage,
            label = { it },
            onSelect = {
                viewModel.setPreferredAudioLanguage(it)
                audioLanguageDialogOpen = false
            },
            onDismiss = { audioLanguageDialogOpen = false },
        )
    }
    if (resizeDialogOpen) {
        SettingsChoiceDialog(
            title = stringResource(R.string.settings_resize_mode),
            options = AppSettings.RESIZE_OPTIONS,
            selected = settings.resizeMode,
            label = { stringResource(resizeLabelRes(it)) },
            onSelect = {
                viewModel.setResizeMode(it)
                resizeDialogOpen = false
            },
            onDismiss = { resizeDialogOpen = false },
        )
    }
}

@Composable
internal fun ContentSettingsScreen(
    viewModel: SettingsViewModel,
    settings: AppSettings,
    onBack: () -> Unit,
) {
    var appLanguageDialogOpen by remember { mutableStateOf(false) }
    var countryDialogOpen by remember { mutableStateOf(false) }
    var languageDialogOpen by remember { mutableStateOf(false) }

    SettingsSubScreen(title = stringResource(R.string.settings_section_content), onBack = onBack) {
        SettingsRow(
            title = stringResource(R.string.settings_app_language),
            subtitle =
                stringResource(appLanguageLabelRes(settings.appLanguage)) +
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        " · " + stringResource(R.string.settings_app_language_limited)
                    } else {
                        ""
                    },
            onClick = { appLanguageDialogOpen = true },
        )
        SettingsRow(
            title = stringResource(R.string.settings_content_country),
            subtitle = settings.contentCountry,
            onClick = { countryDialogOpen = true },
        )
        SettingsRow(
            title = stringResource(R.string.settings_content_language),
            subtitle = settings.contentLanguage,
            onClick = { languageDialogOpen = true },
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_show_search_suggestions),
            checked = settings.showSearchSuggestions,
            onCheckedChange = viewModel::setShowSearchSuggestions,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_age_restricted_content),
            checked = settings.ageRestrictedContent,
            onCheckedChange = viewModel::setAgeRestrictedContent,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_safe_mode),
            checked = settings.safeMode,
            onCheckedChange = viewModel::setSafeMode,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_show_metadata),
            checked = settings.showMetadata,
            onCheckedChange = viewModel::setShowMetadata,
        )
    }

    if (appLanguageDialogOpen) {
        SettingsChoiceDialog(
            title = stringResource(R.string.settings_app_language),
            options = AppSettings.VALID_APP_LANGUAGES.toList(),
            selected = settings.appLanguage,
            label = { stringResource(appLanguageLabelRes(it)) },
            onSelect = {
                viewModel.setAppLanguage(it)
                appLanguageDialogOpen = false
            },
            onDismiss = { appLanguageDialogOpen = false },
        )
    }
    if (countryDialogOpen) {
        SettingsChoiceDialog(
            title = stringResource(R.string.settings_content_country),
            options = AppSettings.VALID_COUNTRIES.sorted(),
            selected = settings.contentCountry,
            label = { it },
            onSelect = {
                viewModel.setContentCountry(it)
                countryDialogOpen = false
            },
            onDismiss = { countryDialogOpen = false },
        )
    }
    if (languageDialogOpen) {
        SettingsChoiceDialog(
            title = stringResource(R.string.settings_content_language),
            options = AppSettings.VALID_LANGUAGES.sorted(),
            selected = settings.contentLanguage,
            label = { it },
            onSelect = {
                viewModel.setContentLanguage(it)
                languageDialogOpen = false
            },
            onDismiss = { languageDialogOpen = false },
        )
    }
}

@Composable
internal fun GestureSettingsScreen(
    viewModel: SettingsViewModel,
    settings: AppSettings,
    onBack: () -> Unit,
) {
    var volumeSideDialogOpen by remember { mutableStateOf(false) }
    var brightnessSideDialogOpen by remember { mutableStateOf(false) }

    SettingsSubScreen(title = stringResource(R.string.settings_section_gestures), onBack = onBack) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_gesture_volume),
            checked = settings.gestureVolume,
            onCheckedChange = viewModel::setGestureVolume,
        )
        SettingsRow(
            title = stringResource(R.string.settings_gesture_side_volume),
            subtitle = stringResource(gestureSideLabelRes(settings.volumeGestureSide)),
            onClick = { volumeSideDialogOpen = true },
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_gesture_brightness),
            checked = settings.gestureBrightness,
            onCheckedChange = viewModel::setGestureBrightness,
        )
        SettingsRow(
            title = stringResource(R.string.settings_gesture_side_brightness),
            subtitle = stringResource(gestureSideLabelRes(settings.brightnessGestureSide)),
            onClick = { brightnessSideDialogOpen = true },
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_gesture_zoom),
            subtitle = stringResource(R.string.settings_gesture_zoom_summary),
            checked = settings.gestureZoom,
            onCheckedChange = viewModel::setGestureZoom,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_gesture_speed),
            subtitle = stringResource(R.string.settings_gesture_speed_summary),
            checked = settings.gestureSpeed,
            onCheckedChange = viewModel::setGestureSpeed,
        )
    }

    if (volumeSideDialogOpen) {
        SettingsChoiceDialog(
            title = stringResource(R.string.settings_gesture_side_volume),
            options = GestureSide.entries.toList(),
            selected = settings.volumeGestureSide,
            label = { stringResource(gestureSideLabelRes(it)) },
            onSelect = {
                viewModel.setVolumeGestureSide(it)
                volumeSideDialogOpen = false
            },
            onDismiss = { volumeSideDialogOpen = false },
        )
    }
    if (brightnessSideDialogOpen) {
        SettingsChoiceDialog(
            title = stringResource(R.string.settings_gesture_side_brightness),
            options = GestureSide.entries.toList(),
            selected = settings.brightnessGestureSide,
            label = { stringResource(gestureSideLabelRes(it)) },
            onSelect = {
                viewModel.setBrightnessGestureSide(it)
                brightnessSideDialogOpen = false
            },
            onDismiss = { brightnessSideDialogOpen = false },
        )
    }
}

@Composable
private fun SettingsSubScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(title = title, onBack = onBack)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            content = { content() },
        )
    }
}

private fun resizeLabelRes(mode: Int): Int =
    when (mode) {
        AppSettings.RESIZE_FILL -> R.string.resize_fill
        AppSettings.RESIZE_ZOOM -> R.string.resize_zoom
        else -> R.string.resize_fit
    }

private fun gestureSideLabelRes(side: GestureSide): Int =
    when (side) {
        GestureSide.LEFT -> R.string.gesture_side_left
        GestureSide.RIGHT -> R.string.gesture_side_right
        GestureSide.OFF -> R.string.gesture_side_off
    }

private fun appLanguageLabelRes(code: String): Int =
    when (code) {
        "uk" -> R.string.app_language_uk
        "en" -> R.string.app_language_en
        else -> R.string.app_language_system
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
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) {
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
