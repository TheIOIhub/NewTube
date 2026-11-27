package com.example.newtube.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newtube.model.Video
import com.example.newtube.repository.FeedKey
import com.example.newtube.repository.SettingsRepository
import com.example.newtube.repository.VideoRepository
import com.example.newtube.ui.common.UiState
import com.example.newtube.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<List<Video>>>(UiState.Success(emptyList()))
    val uiState: StateFlow<UiState<List<Video>>> = _uiState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions

    /** Page 1 of the active query; null while the search field is blank. */
    private val activeQuery = MutableStateFlow<String?>(null)

    private var query = ""
    private var nextPage = 1
    private var loading = false
    private var reachedEnd = false
    private var requestToken = 0
    private var searchJob: Job? = null
    private var suggestionsJob: Job? = null

    init {
        // Cached results for the current query arrive immediately; switching
        // queries cancels the previous flow so it cannot overwrite new results.
        viewModelScope.launch {
            activeQuery
                .flatMapLatest { term ->
                    if (term == null) {
                        flowOf(null to emptyList<Video>())
                    } else {
                        repository.observeFeed(FeedKey.Search(term, 1)).map { term to it }
                    }
                }
                .collect { (term, cached) ->
                    if (term != null && term == activeQuery.value && cached.isNotEmpty()) {
                        _uiState.value = UiState.Success(cached)
                        reachedEnd = false
                    }
                }
        }
    }

    fun onQueryChange(value: String) {
        val trimmed = value.trim()
        if (trimmed == query) return
        query = trimmed
        val token = ++requestToken
        searchJob?.cancel()
        suggestionsJob?.cancel()
        reachedEnd = false
        nextPage = 1
        activeQuery.value = trimmed.takeIf { it.isNotBlank() }
        if (trimmed.isBlank()) {
            _suggestions.value = emptyList()
            _uiState.value = UiState.Success(emptyList())
            _isRefreshing.value = false
            return
        }
        searchJob =
            viewModelScope.launch {
                delay(DEBOUNCE_MS)
                loadFirstPage(token)
            }
        suggestionsJob =
            viewModelScope.launch {
                delay(SUGGESTION_DEBOUNCE_MS)
                if (settingsRepository.settings.first().showSearchSuggestions) {
                    _suggestions.value =
                        runCatching { repository.searchSuggestions(trimmed) }.getOrDefault(emptyList())
                }
            }
    }

    /** Pick a completion: fill the field and search now, without another debounce. */
    fun acceptSuggestion(value: String) {
        val trimmed = value.trim()
        query = trimmed
        val token = ++requestToken
        suggestionsJob?.cancel()
        _suggestions.value = emptyList()
        if (trimmed.isBlank()) {
            activeQuery.value = null
            _isRefreshing.value = false
            return
        }
        searchJob?.cancel()
        reachedEnd = false
        nextPage = 1
        activeQuery.value = trimmed
        searchJob =
            viewModelScope.launch {
                loadFirstPage(token)
            }
    }

    fun retry() {
        if (query.isBlank()) return
        val token = ++requestToken
        searchJob?.cancel()
        searchJob =
            viewModelScope.launch {
                loadFirstPage(token)
            }
    }

    fun loadMore() {
        val current = _uiState.value as? UiState.Success ?: return
        if (query.isBlank() || loading || reachedEnd) return
        loading = true
        _isLoadingMore.value = true
        viewModelScope.launch {
            try {
                val page = nextPage
                // Page > 1 needs the in-memory continuation cursor, so it keeps
                // the existing search() path instead of refreshFeed().
                val results = repository.search(query, page)
                nextPage = page + 1
                if (results.isEmpty()) reachedEnd = true
                _uiState.value =
                    UiState.Success((current.data + results).distinctBy { it.id })
            } catch (e: Exception) {
                // Keep the current results, the footer stays so the user can retry.
            } finally {
                loading = false
                _isLoadingMore.value = false
            }
        }
    }

    private suspend fun loadFirstPage(token: Int) {
        loading = true
        nextPage = 2
        reachedEnd = false
        _isRefreshing.value = true
        try {
            val results = repository.refreshFeed(FeedKey.Search(query, 1)).getOrThrow()
            if (token != requestToken) return
            if (results.isEmpty()) reachedEnd = true
            _uiState.value = UiState.Success(results)
        } catch (e: Exception) {
            if (token != requestToken) return
            // A failed refresh must not replace a usable cached result list.
            if (_uiState.value !is UiState.Success) {
                _uiState.value = UiState.Error(e.toUserMessage())
            }
        } finally {
            if (token == requestToken) {
                loading = false
                _isRefreshing.value = false
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 500L
        const val SUGGESTION_DEBOUNCE_MS = 250L
    }
}
