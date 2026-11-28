package com.example.newtube.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.newtube.R
import com.example.newtube.model.AppSettings
import com.example.newtube.model.GestureSide

object PlayerLabels {
    const val QUALITY = "Якість"
    const val SPEED = "Швидкість"
    const val AUDIO = "Аудіодоріжка"
    const val REPEAT = "Повторювати"
    const val SCREEN = "Екран"
    const val BRIGHTNESS_GESTURE = "Жест яскравості"
    const val ZOOM_GESTURE = "Жест збільшення"
    const val HOLD_SPEED = "Швидкість під час утримання"
    const val PIP = "PiP"
    const val SUBTITLES = "Субтитри"
    const val TIMER = "Таймер"
    const val CAST = "Кастинг"
    const val BROWSER = "Браузер"
    const val UNAVAILABLE = "Недоступно"
}

@Composable
fun PlayerTopBar(
    isFullscreen: Boolean,
    qualityLabel: String,
    showPip: Boolean,
    onBack: () -> Unit,
    onPip: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = if (isFullscreen) "Вийти з повноекранного режиму" else "Згорнути",
                tint = Color.White,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(qualityLabel, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        if (showPip) {
            IconButton(onClick = onPip) {
                Icon(
                    painter = painterResource(R.drawable.ic_pip),
                    contentDescription = stringResource(R.string.player_pip),
                    tint = Color.White,
                )
            }
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.player_settings), tint = Color.White)
        }
    }
}

@Composable
fun PlayerCenterControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onSeek(-10) }) {
            Text("«", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        }
        IconButton(onClick = onPlayPause) {
            if (isPlaying) {
                Text("❚❚", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            } else {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Відтворити",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        IconButton(onClick = { onSeek(10) }) {
            Text("»", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
fun PlayerBottomBar(
    positionMs: Long,
    durationMs: Long,
    isLive: Boolean,
    isFullscreen: Boolean,
    onSeekTo: (Long) -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        if (isLive) {
            Text("НАЖИВО", color = Color.Red, style = MaterialTheme.typography.labelSmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatPlayerTime(positionMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
            Slider(
                value = positionMs.coerceAtMost(durationMs.coerceAtLeast(1L)).toFloat(),
                valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                onValueChange = { onSeekTo(it.toLong()) },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(formatPlayerTime(durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onFullscreen) {
                Icon(
                    painter = painterResource(if (isFullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen),
                    contentDescription = if (isFullscreen) "Вийти з повноекранного режиму" else "Повноекранний режим",
                    tint = Color.White,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(
    settings: PlayerUiSettings,
    heights: List<Int>,
    audioOptions: List<String>,
    onSettingsChange: (PlayerUiSettings) -> Unit,
    onPip: () -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(32.dp, 4.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (page != null) {
                    IconButton(onClick = { page = null }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
                Text(page ?: stringResource(R.string.player_settings), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрити")
                }
            }
            LazyColumn {
                if (page == null) {
                    item { SettingsRow(PlayerLabels.QUALITY, settings.qualityLabel()) { page = PlayerLabels.QUALITY } }
                    item { SettingsRow(PlayerLabels.SPEED, settings.speedLabel()) { page = PlayerLabels.SPEED } }
                    item { SettingsRow(PlayerLabels.AUDIO, settings.audioLabel) { page = PlayerLabels.AUDIO } }
                    item {
                        SettingsToggle(PlayerLabels.REPEAT, settings.repeat) { onSettingsChange(settings.copy(repeat = it)) }
                    }
                    item { SettingsRow(PlayerLabels.SCREEN, resizeLabel(settings.resize)) { page = PlayerLabels.SCREEN } }
                    item {
                        SettingsToggle(PlayerLabels.ZOOM_GESTURE, settings.gestureZoom) {
                            onSettingsChange(settings.copy(gestureZoom = it))
                        }
                    }
                    item {
                        SettingsRow(
                            PlayerLabels.BRIGHTNESS_GESTURE,
                            sideLabel(settings.brightnessGestureSide),
                        ) {
                            page = PlayerLabels.BRIGHTNESS_GESTURE
                        }
                    }
                    item { SettingsRow(PlayerLabels.HOLD_SPEED, PlayerLabels.UNAVAILABLE) {} }
                    item {
                        SettingsRow(PlayerLabels.PIP, "Відкрити") {
                            onPip()
                            onDismiss()
                        }
                    }
                    item { SettingsRow(PlayerLabels.SUBTITLES, PlayerLabels.UNAVAILABLE) {} }
                    item { SettingsRow(PlayerLabels.TIMER, PlayerLabels.UNAVAILABLE) {} }
                    item { SettingsRow(PlayerLabels.CAST, PlayerLabels.UNAVAILABLE) {} }
                    item { SettingsRow(PlayerLabels.BROWSER, PlayerLabels.UNAVAILABLE) {} }
                } else {
                    when (page) {
                        PlayerLabels.QUALITY ->
                            items(listOf("Авто") + heights.map { "$it" + "p" }) { label ->
                                SettingsRow(label, "") {
                                    val value = if (label == "Авто") Int.MAX_VALUE else label.removeSuffix("p").toIntOrNull() ?: Int.MAX_VALUE
                                    onSettingsChange(settings.copy(quality = value))
                                    page = null
                                }
                            }
                        PlayerLabels.SPEED ->
                            items(listOf(1f, 1.5f, 2f, 3f)) { value ->
                                SettingsRow("${value}x", "") {
                                    onSettingsChange(settings.copy(speed = value))
                                    page = null
                                }
                            }
                        PlayerLabels.AUDIO ->
                            items(listOf("Авто") + audioOptions) { label ->
                                SettingsRow(label, "") {
                                    onSettingsChange(settings.copy(audioLabel = label))
                                    page = null
                                }
                            }
                        PlayerLabels.SCREEN ->
                            items(AppSettings.RESIZE_OPTIONS) { value ->
                                SettingsRow(resizeLabel(value), "") {
                                    onSettingsChange(settings.copy(resize = value))
                                    page = null
                                }
                            }
                        PlayerLabels.BRIGHTNESS_GESTURE ->
                            items(listOf(GestureSide.LEFT, GestureSide.RIGHT, GestureSide.OFF)) { side ->
                                SettingsRow(sideLabel(side), "") {
                                    onSettingsChange(settings.copy(brightnessGestureSide = side))
                                    page = null
                                }
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    androidx.compose.material3.TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(value, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun sideLabel(side: GestureSide): String =
    when (side) {
        GestureSide.LEFT -> "Ліва"
        GestureSide.RIGHT -> "Права"
        GestureSide.OFF -> "Вимкнено"
    }

private fun resizeLabel(value: Int): String =
    when (value) {
        AppSettings.RESIZE_FILL -> "Заповнити"
        AppSettings.RESIZE_ZOOM -> "Обрізати"
        else -> "Вписати"
    }
