package com.example.newtube.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newtube.model.Video
import com.example.newtube.repository.VideoRepository
import com.example.newtube.ui.common.UiState
import com.example.newtube.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: VideoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<List<Video>>>(UiState.Success(emptyList()))
    val uiState: StateFlow<UiState<List<Video>>> = _uiState

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private var query = ""
    private var nextPage = 1
    private var loading = false
    private var reachedEnd = false
    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        val trimmed = value.trim()
        if (trimmed == query) return
        query = trimmed
        searchJob?.cancel()
        reachedEnd = false
        nextPage = 1
        if (trimmed.isBlank()) {
            _uiState.value = UiState.Success(emptyList())
            return
        }
        searchJob =
            viewModelScope.launch {
                delay(DEBOUNCE_MS)
                loadFirstPage()
            }
    }

    fun retry() {
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob =
            viewModelScope.launch {
                loadFirstPage()
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

    private suspend fun loadFirstPage() {
        loading = true
        nextPage = 2
        reachedEnd = false
        _uiState.value = UiState.Loading
        try {
            val results = repository.search(query, page = 1)
            if (results.isEmpty()) reachedEnd = true
            _uiState.value = UiState.Success(results)
        } catch (e: Exception) {
            _uiState.value = UiState.Error(e.toUserMessage())
        } finally {
            loading = false
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 500L
    }
}
