package com.example.newtube.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newtube.model.HomeCategory
import com.example.newtube.model.Video
import com.example.newtube.repository.VideoRepository
import com.example.newtube.ui.common.UiState
import com.example.newtube.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VideoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<List<Video>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Video>>> = _uiState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _selectedCategory = MutableStateFlow(HomeCategory.ALL)
    val selectedCategory: StateFlow<HomeCategory> = _selectedCategory

    private val cache = mutableMapOf<HomeCategory, List<Video>>()

    /** Incremented by every fetch; stale responses compare against it and bail out. */
    private var requestToken = 0

    init {
        load()
    }

    fun selectCategory(category: HomeCategory) {
        if (category == _selectedCategory.value) return
        _selectedCategory.value = category
        val cached = cache[category]
        if (cached != null) {
            _uiState.value = UiState.Success(cached)
            return
        }
        load()
    }

    fun load() = fetch(showLoading = true)

    /**
     * The feed is a shuffled mix, so "load more" becomes "refresh": the current
     * list stays on screen while a new mix is fetched.
     */
    fun refresh() = fetch(showLoading = false)

    private fun fetch(showLoading: Boolean) {
        val token = ++requestToken
        val category = _selectedCategory.value
        if (showLoading) _uiState.value = UiState.Loading else _isRefreshing.value = true
        viewModelScope.launch {
            try {
                val feed = repository.getHomeFeed(category)
                if (token != requestToken) return@launch
                cache[category] = feed
                _uiState.value = UiState.Success(feed)
            } catch (e: Exception) {
                if (token != requestToken) return@launch
                if (showLoading || _uiState.value !is UiState.Success) {
                    _uiState.value = UiState.Error(e.toUserMessage())
                }
            } finally {
                if (token == requestToken) _isRefreshing.value = false
            }
        }
    }
}
