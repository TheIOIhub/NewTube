package com.example.newtube.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newtube.model.AccountResult
import com.example.newtube.model.AccountState
import com.example.newtube.model.RemoteVideo
import com.example.newtube.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val signedOut: Boolean = false,
    val items: List<RemoteVideo> = emptyList(),
    val loading: Boolean = false,
    val error: Boolean = false,
)

/** Best-effort read of `FEnotifications`; no pagination and no caching. */
@HiltViewModel
class NotificationsViewModel
    @Inject
    constructor(
        private val accountRepository: AccountRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(NotificationsUiState())
        val uiState: StateFlow<NotificationsUiState> = mutableState.asStateFlow()

        private val mutableRefreshing = MutableStateFlow(false)
        val isRefreshing: StateFlow<Boolean> = mutableRefreshing.asStateFlow()

        fun load() {
            if (mutableState.value.loading) return
            if (accountRepository.state.value is AccountState.SignedOut) {
                mutableState.value = NotificationsUiState(signedOut = true)
                mutableRefreshing.value = false
                return
            }
            // Keep any already-loaded notifications on screen during a retry.
            val previous = mutableState.value
            mutableState.value = previous.copy(loading = true, error = false)
            mutableRefreshing.value = true
            viewModelScope.launch {
                try {
                    mutableState.value =
                        when (val result = accountRepository.notifications()) {
                            is AccountResult.Success -> NotificationsUiState(items = result.data)
                            AccountResult.NeedsSignIn -> NotificationsUiState(signedOut = true)
                            AccountResult.Network, AccountResult.Server ->
                                previous.copy(loading = false, error = true)
                            AccountResult.ParseEmpty -> NotificationsUiState()
                        }
                } finally {
                    mutableRefreshing.value = false
                    mutableState.value = mutableState.value.copy(loading = false)
                }
            }
        }

        fun retry() {
            load()
        }
    }
