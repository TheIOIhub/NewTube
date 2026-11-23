package com.example.newtube.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newtube.model.AccountResult
import com.example.newtube.model.AccountState
import com.example.newtube.model.RemoteVideo
import com.example.newtube.model.WatchHistoryEntry
import com.example.newtube.repository.AccountRepository
import com.example.newtube.repository.HistoryRepository
import com.example.newtube.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val local: List<WatchHistoryEntry> = emptyList(),
    val remote: List<RemoteVideo> = emptyList(),
    val remoteSource: Boolean = false,
    val loadingMore: Boolean = false,
    val expired: Boolean = false,
    val error: Boolean = false,
    val hasMore: Boolean = false,
)

/**
 * Shows the local Room history by default and switches to YouTube's read-only
 * remote history when the user is signed in and opted in. Remote rows are never
 * persisted; a failure falls back to the local list with an inline banner.
 */
@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        private val historyRepository: HistoryRepository,
        private val accountRepository: AccountRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(HistoryUiState())
        val uiState: StateFlow<HistoryUiState> = mutableState.asStateFlow()

        private var continuation: String? = null
        private var remoteLoaded = false
        private var loadingRemote = false

        init {
            viewModelScope.launch {
                combine(
                    historyRepository.observeLocal(),
                    accountRepository.state,
                    settingsRepository.settings,
                ) { local, account, settings ->
                    HistoryInput(
                        local = local,
                        remoteEnabled = account !is AccountState.SignedOut && settings.useRemoteHistory,
                    )
                }.collect { input ->
                    mutableState.value = mutableState.value.copy(local = input.local)
                    if (input.remoteEnabled) {
                        if (!remoteLoaded && !loadingRemote) loadRemote()
                    } else {
                        remoteLoaded = false
                        continuation = null
                        mutableState.value =
                            mutableState.value.copy(
                                remote = emptyList(),
                                remoteSource = false,
                                hasMore = false,
                                expired = false,
                                error = false,
                                loadingMore = false,
                            )
                    }
                }
            }
        }

        fun retry() {
            remoteLoaded = false
            loadRemote()
        }

        fun loadMore() {
            val current = mutableState.value
            if (!current.remoteSource || !current.hasMore || current.loadingMore) return
            mutableState.value = current.copy(loadingMore = true, error = false)
            viewModelScope.launch { fetch(continuation, replace = false) }
        }

        private fun loadRemote() {
            if (loadingRemote) return
            loadingRemote = true
            mutableState.value = mutableState.value.copy(error = false)
            viewModelScope.launch { fetch(continuation = null, replace = true) }
        }

        private suspend fun fetch(
            continuation: String?,
            replace: Boolean,
        ) {
            try {
                when (val result = accountRepository.history(continuation)) {
                    is AccountResult.Success -> {
                        val merged =
                            if (replace) result.data.items else mutableState.value.remote + result.data.items
                        val unique = LinkedHashMap<String, RemoteVideo>()
                        merged.forEach { unique.putIfAbsent(it.id, it) }
                        this.continuation = result.data.continuation
                        remoteLoaded = true
                        mutableState.value =
                            mutableState.value.copy(
                                remote = unique.values.toList(),
                                remoteSource = true,
                                hasMore = result.data.continuation != null,
                                expired = false,
                                error = false,
                                loadingMore = false,
                            )
                    }
                    AccountResult.NeedsSignIn -> {
                        remoteLoaded = false
                        mutableState.value =
                            mutableState.value.copy(
                                remote = emptyList(),
                                remoteSource = false,
                                hasMore = false,
                                expired = true,
                                loadingMore = false,
                            )
                    }
                    AccountResult.Network, AccountResult.Server -> {
                        remoteLoaded = false
                        mutableState.value =
                            mutableState.value.copy(
                                remote = emptyList(),
                                remoteSource = false,
                                hasMore = false,
                                error = true,
                                loadingMore = false,
                            )
                    }
                    AccountResult.ParseEmpty -> {
                        remoteLoaded = true
                        this.continuation = null
                        mutableState.value =
                            mutableState.value.copy(
                                remote = emptyList(),
                                remoteSource = true,
                                hasMore = false,
                                expired = false,
                                error = false,
                                loadingMore = false,
                            )
                    }
                }
            } finally {
                loadingRemote = false
            }
        }

        private data class HistoryInput(
            val local: List<WatchHistoryEntry>,
            val remoteEnabled: Boolean,
        )
    }
