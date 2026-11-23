package com.example.newtube.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newtube.model.AccountState
import com.example.newtube.model.AppSettings
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
}
