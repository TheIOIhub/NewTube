package com.example.newtube.ui.subscriptions

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

data class SubscriptionsUiState(
    val signedOut: Boolean = false,
    val items: List<RemoteVideo> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: Boolean = false,
    val expired: Boolean = false,
    val hasMore: Boolean = false,
)

@HiltViewModel
class SubscriptionsViewModel
    @Inject
    constructor(
        private val accountRepository: AccountRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(SubscriptionsUiState())
        val uiState: StateFlow<SubscriptionsUiState> = mutableState.asStateFlow()

        private var continuation: String? = null

        fun load() {
            if (mutableState.value.loading || mutableState.value.loadingMore) return
            if (accountRepository.state.value is AccountState.SignedOut) {
                mutableState.value = SubscriptionsUiState(signedOut = true)
                return
            }
            continuation = null
            mutableState.value =
                mutableState.value.copy(loading = true, error = false, expired = false, signedOut = false)
            viewModelScope.launch { fetch(continuation = null, replace = true) }
        }

        fun loadMore() {
            val current = mutableState.value
            if (!current.hasMore || current.loading || current.loadingMore) return
            mutableState.value = current.copy(loadingMore = true)
            viewModelScope.launch { fetch(continuation = continuation, replace = false) }
        }

        fun retry() {
            // Resume a failed later page instead of throwing away the rows the
            // user already has; a failed first page simply loads again.
            if (mutableState.value.items.isEmpty()) load() else loadMore()
        }

        private suspend fun fetch(
            continuation: String?,
            replace: Boolean,
        ) {
            when (val result = accountRepository.subscriptions(continuation)) {
                is AccountResult.Success -> {
                    val merged = if (replace) result.data.items else mutableState.value.items + result.data.items
                    val unique = LinkedHashMap<String, RemoteVideo>()
                    merged.forEach { unique.putIfAbsent(it.id, it) }
                    this.continuation = result.data.continuation
                    mutableState.value =
                        mutableState.value.copy(
                            items = unique.values.toList(),
                            loading = false,
                            loadingMore = false,
                            error = false,
                            expired = false,
                            hasMore = result.data.continuation != null,
                        )
                }
                AccountResult.NeedsSignIn ->
                    mutableState.value =
                        mutableState.value.copy(loading = false, loadingMore = false, expired = true)
                AccountResult.Network, AccountResult.Server ->
                    mutableState.value =
                        mutableState.value.copy(loading = false, loadingMore = false, error = true)
                AccountResult.ParseEmpty ->
                    mutableState.value =
                        mutableState.value.copy(loading = false, loadingMore = false, hasMore = false)
            }
        }
    }
