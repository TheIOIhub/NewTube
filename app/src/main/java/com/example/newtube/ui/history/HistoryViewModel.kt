package com.example.newtube.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newtube.model.WatchHistoryEntry
import com.example.newtube.repository.HistoryRepository
import com.example.newtube.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    historyRepository: HistoryRepository,
) : ViewModel() {
    val uiState: StateFlow<UiState<List<WatchHistoryEntry>>> =
        historyRepository
            .observeLocal()
            .map<List<WatchHistoryEntry>, UiState<List<WatchHistoryEntry>>> { UiState.Success(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UiState.Loading,
            )
}
