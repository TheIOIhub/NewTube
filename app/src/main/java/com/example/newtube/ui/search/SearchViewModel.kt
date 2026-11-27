package com.example.newtube.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newtube.innertube.ContentSourceUnavailableException
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

    /** True when the source failed but cached results for this query remain. */
    private val _sourceUnavailable = MutableStateFlow(false)
    val sourceUnavailable: StateFlow<Boolean> = _sourceUnavailable

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions

    /** Page 1 of the active query; null while the search field is blank. */
    private val activeQuery = MutableStateFlow<String?>(null)

    /** The query whose results are currently shown; guards cross-query state. */
    private var displayedQuery: String? = null

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
                        if (displayedQuery == term) return@collect
                        displayedQuery = term
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
        displayedQuery = null
        _sourceUnavailable.value = false
        searchJob?.cancel()
        suggestionsJob?.cancel()
        reachedEnd = false
        nextPage = 1
        activeQuery.value = trimmed.takeIf { it.isNotBlank() }
        // Results from the previous query must not appear under the new one.
        _uiState.value = UiState.Success(emptyList())
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
        displayedQuery = null
        _sourceUnavailable.value = false
        _uiState.value = UiState.Success(emptyList())
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
        if (_uiState.value !is UiState.Success || query.isBlank() || loading || reachedEnd) return
        val token = requestToken
        loading = true
        _isLoadingMore.value = true
        viewModelScope.launch {
            try {
                val page = nextPage
                // Page > 1 needs the in-memory continuation cursor, so it keeps
                // the existing search() path instead of refreshFeed().
                val results = repository.search(query, page)
                if (token != requestToken || query != activeQuery.value) return@launch
                // A page-1 refresh may have finished while this page was in
                // flight: append to what is on screen now, not to a snapshot.
                val current = _uiState.value as? UiState.Success ?: return@launch
                nextPage = page + 1
                if (results.isEmpty()) reachedEnd = true
                displayedQuery = query
                _sourceUnavailable.value = false
                _uiState.value =
                    UiState.Success((current.data + results).distinctBy { it.id })
            } catch (e: Exception) {
                // Keep the current results, the footer stays so the user can retry.
                if (token == requestToken && query == activeQuery.value) {
                    _sourceUnavailable.value = e is ContentSourceUnavailableException
                }
            } finally {
                if (token == requestToken) {
                    loading = false
                    _isLoadingMore.value = false
                }
            }
        }
    }

    private suspend fun loadFirstPage(token: Int) {
        loading = true
        nextPage = 2
        reachedEnd = false
        // Page 1 supersedes any in-flight pagination; its footer no longer applies.
        _isLoadingMore.value = false
        _isRefreshing.value = true
        try {
            val results = repository.refreshFeed(FeedKey.Search(query, 1)).getOrThrow()
            if (token != requestToken) return
            if (results.isEmpty()) reachedEnd = true
            displayedQuery = query
            _sourceUnavailable.value = false
            _uiState.value = UiState.Success(results)
        } catch (e: Exception) {
            if (token != requestToken) return
            // Preserve results only when they belong to this query; a failed
            // new query must not inherit the previous query's list.
            _sourceUnavailable.value = e is ContentSourceUnavailableException
            if (displayedQuery != query) {
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
