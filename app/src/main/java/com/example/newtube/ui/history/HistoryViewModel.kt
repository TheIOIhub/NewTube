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

        /**
         * True once a remote fetch reached a terminal answer. A failure keeps
         * this true so local-history or settings emissions do not silently
         * re-hit InnerTube (and its token refresh) on a dead session; only an
         * explicit [retry] or a source change clears it.
         */
        private var remoteAttempted = false
        private var loadingRemote = false
        private var lastSignedIn = false

        init {
            viewModelScope.launch {
                combine(
                    historyRepository.observeLocal(),
                    accountRepository.state,
                    settingsRepository.settings,
                ) { local, account, settings ->
                    HistoryInput(
                        local = local,
                        signedIn = account is AccountState.SignedIn,
                        remoteEnabled = account !is AccountState.SignedOut && settings.useRemoteHistory,
                    )
                }.collect { input ->
                    // A fresh sign-in (e.g. after the session expired) must
                    // retry the remote feed; a failed session must not.
                    if (input.signedIn && !lastSignedIn) {
                        remoteAttempted = false
                        continuation = null
                    }
                    lastSignedIn = input.signedIn
                    mutableState.value = mutableState.value.copy(local = input.local)
                    if (input.remoteEnabled) {
                        if (!remoteAttempted && !loadingRemote) loadRemote()
                    } else {
                        remoteAttempted = false
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
            remoteAttempted = false
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
                        remoteAttempted = true
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
                        remoteAttempted = true
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
                        remoteAttempted = true
                        mutableState.value =
                            if (replace) {
                                mutableState.value.copy(
                                    remote = emptyList(),
                                    remoteSource = false,
                                    hasMore = false,
                                    error = true,
                                    loadingMore = false,
                                )
                            } else {
                                // A failed page must not wipe the rows already on screen.
                                mutableState.value.copy(error = true, loadingMore = false)
                            }
                    }
                    AccountResult.ParseEmpty -> {
                        remoteAttempted = true
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
            val signedIn: Boolean,
            val remoteEnabled: Boolean,
        )
    }
