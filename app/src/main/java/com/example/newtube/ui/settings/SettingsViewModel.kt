package com.example.newtube.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newtube.model.AccountState
import com.example.newtube.model.AppSettings
import com.example.newtube.model.GestureSide
import com.example.newtube.model.ThemeMode
import com.example.newtube.repository.AccountRepository
import com.example.newtube.repository.HistoryRepository
import com.example.newtube.repository.PlaybackPositionRepository
import com.example.newtube.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val playbackPositions: PlaybackPositionRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {
    val account: StateFlow<AccountState> = accountRepository.state

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings(),
        )

    /** Only used to enable the destructive clear action; one row is enough. */
    val hasLocalHistory: StateFlow<Boolean> =
        historyRepository
            .observeLocal(limit = 1)
            .map { it.isNotEmpty() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setRecordHistory(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRecordHistory(enabled) }
    }

    fun setUseRemoteHistory(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUseRemoteHistory(enabled) }
    }

    fun setDefaultQuality(maxHeight: Int) {
        viewModelScope.launch { settingsRepository.setDefaultQuality(maxHeight) }
    }

    fun setContentCountry(value: String) = update { it.copy(contentCountry = value) }

    fun setContentLanguage(value: String) = update { it.copy(contentLanguage = value) }

    fun setPreferredAudioLanguage(value: String) = update { it.copy(preferredAudioLanguage = value) }

    fun setAppLanguage(value: String) = update { it.copy(appLanguage = value) }

    fun setPreferOriginalAudio(enabled: Boolean) = update { it.copy(preferOriginalAudio = enabled) }

    fun setShowSearchSuggestions(enabled: Boolean) = update { it.copy(showSearchSuggestions = enabled) }

    fun setAgeRestrictedContent(enabled: Boolean) = update { it.copy(ageRestrictedContent = enabled) }

    fun setSafeMode(enabled: Boolean) = update { it.copy(safeMode = enabled) }

    fun setShowMetadata(enabled: Boolean) = update { it.copy(showMetadata = enabled) }

    fun setAutoplayNext(enabled: Boolean) = update { it.copy(autoplayNext = enabled) }

    fun setResumePlayback(enabled: Boolean) = update { it.copy(resumePlayback = enabled) }

    fun setDoubleTapSeekSeconds(seconds: Int) = update { it.copy(doubleTapSeekSeconds = seconds) }

    fun setResizeMode(mode: Int) = update { it.copy(resizeMode = mode) }

    fun setVideoBrightness(brightness: Float?) = update { it.copy(videoBrightness = brightness) }

    fun setGestureVolume(enabled: Boolean) = update { it.copy(gestureVolume = enabled) }

    fun setGestureBrightness(enabled: Boolean) = update { it.copy(gestureBrightness = enabled) }

    fun setGestureZoom(enabled: Boolean) = update { it.copy(gestureZoom = enabled) }

    fun setGestureSpeed(enabled: Boolean) = update { it.copy(gestureSpeed = enabled) }

    fun setVolumeGestureSide(side: GestureSide) =
        update { current ->
            val (volume, brightness) = assignGestureSide(side, forVolume = true, current = current)
            current.copy(volumeGestureSide = volume, brightnessGestureSide = brightness)
        }

    fun setBrightnessGestureSide(side: GestureSide) =
        update { current ->
            val (volume, brightness) = assignGestureSide(side, forVolume = false, current = current)
            current.copy(volumeGestureSide = volume, brightnessGestureSide = brightness)
        }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun signOut() {
        viewModelScope.launch { accountRepository.signOut() }
    }

    /** Clearing history also drops resume positions, as the design requires. */
    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearLocal()
            playbackPositions.clearAll()
        }
    }

    internal companion object {
        /** One action per side: a collision moves the other action to the free side. */
        fun assignGestureSide(
            side: GestureSide,
            forVolume: Boolean,
            current: AppSettings,
        ): Pair<GestureSide, GestureSide> {
            if (side == GestureSide.OFF) {
                return if (forVolume) {
                    GestureSide.OFF to current.brightnessGestureSide
                } else {
                    current.volumeGestureSide to GestureSide.OFF
                }
            }
            val other = if (side == GestureSide.LEFT) GestureSide.RIGHT else GestureSide.LEFT
            return if (forVolume) {
                val brightness =
                    if (current.brightnessGestureSide == side) other else current.brightnessGestureSide
                side to brightness
            } else {
                val volume =
                    if (current.volumeGestureSide == side) other else current.volumeGestureSide
                volume to side
            }
        }
    }
}
