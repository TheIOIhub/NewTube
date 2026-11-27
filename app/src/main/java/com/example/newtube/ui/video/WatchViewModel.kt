package com.example.newtube.ui.video

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newtube.model.AppSettings
import com.example.newtube.model.VideoDetails
import com.example.newtube.repository.HistoryRepository
import com.example.newtube.repository.PlaybackPositionRepository
import com.example.newtube.repository.SettingsRepository
import com.example.newtube.repository.VideoRepository
import com.example.newtube.ui.common.UiState
import com.example.newtube.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WatchUiState(
    val details: VideoDetails,
    val startPositionMs: Long,
    val nextVideo: com.example.newtube.model.Video? = null,
)

@HiltViewModel
class WatchViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val playbackPositions: PlaybackPositionRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<WatchUiState>>(UiState.Loading)
    val uiState: StateFlow<UiState<WatchUiState>> = _uiState

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings(),
        )

    private var videoId: String = ""

    fun load(videoId: String) {
        if (this.videoId == videoId && _uiState.value is UiState.Success) return
        loadInternal(videoId)
    }

    fun retry() {
        if (videoId.isBlank()) return
        loadInternal(videoId)
    }

    private fun loadInternal(videoId: String) {
        this.videoId = videoId
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val maxHeight =
                    runCatching { settingsRepository.settings.first().defaultQuality }
                        .getOrDefault(AppSettings.DEFAULT_QUALITY)
                val details = repository.getVideoDetails(videoId, maxHeight)
                val prefs = runCatching { settingsRepository.settings.first() }.getOrDefault(AppSettings())
                val position = if (prefs.resumePlayback) playbackPositions.positionFor(videoId) else 0L
                val autoplay = prefs.autoplayNext
                val next =
                    if (autoplay) {
                        details.related.firstOrNull { it.id != videoId }
                    } else {
                        null
                    }
                _uiState.value = UiState.Success(WatchUiState(details, position, next))
                runCatching {
                    if (settingsRepository.settings.first().recordHistory) {
                        historyRepository.record(details.video)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load watch screen for $videoId", e)
                _uiState.value = UiState.Error(e.toUserMessage())
            }
        }
    }

    fun setResizeMode(mode: Int) {
        viewModelScope.launch { settingsRepository.update { it.copy(resizeMode = mode) } }
    }

    fun onPlaybackEnded(onNavigateNext: () -> Unit) {
        val next = (_uiState.value as? UiState.Success)?.data?.nextVideo ?: return
        viewModelScope.launch { onNavigateNext() }
    }

    fun setVideoBrightness(brightness: Float) {
        viewModelScope.launch { settingsRepository.update { it.copy(videoBrightness = brightness) } }
    }

    fun savePosition(positionMs: Long) {
        val id = videoId
        if (id.isBlank()) return
        viewModelScope.launch {
            playbackPositions.savePosition(id, positionMs)
        }
    }
}

private const val TAG = "WatchViewModel"
