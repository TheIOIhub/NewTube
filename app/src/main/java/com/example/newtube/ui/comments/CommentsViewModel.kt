package com.example.newtube.ui.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newtube.model.Comment
import com.example.newtube.repository.VideoRepository
import com.example.newtube.ui.common.UiState
import com.example.newtube.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CommentsUiState(
    val comments: List<Comment> = emptyList(),
    val totalCount: Long = 0,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
)

@HiltViewModel
class CommentsViewModel @Inject constructor(
    private val repository: VideoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<CommentsUiState>>(UiState.Loading)
    val uiState: StateFlow<UiState<CommentsUiState>> = _uiState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private var videoId: String = ""
    private var continuation: String? = null
    private var loading = false
    private var reachedEnd = false

    fun load(videoId: String) {
        if (this.videoId == videoId && _uiState.value is UiState.Success) return
        loadInternal(videoId)
    }

    fun retry() {
        if (videoId.isBlank()) return
        loadInternal(videoId)
    }

    private fun loadInternal(videoId: String) {
        this.videoId = videoId
        continuation = null
        reachedEnd = false
        // A retry keeps the already loaded page on screen while it revalidates.
        if (_uiState.value !is UiState.Success) _uiState.value = UiState.Loading
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                val page = repository.getComments(videoId)
                continuation = page.continuation
                reachedEnd = page.continuation == null
                _uiState.value =
                    UiState.Success(
                        CommentsUiState(
                            comments = page.comments,
                            totalCount = page.totalCount,
                            hasMore = page.continuation != null,
                        ),
                    )
            } catch (e: Exception) {
                if (_uiState.value !is UiState.Success) {
                    _uiState.value = UiState.Error(e.toUserMessage())
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value as? UiState.Success ?: return
        val token = continuation
        if (loading || reachedEnd || token == null) return
        loading = true
        _uiState.value = UiState.Success(current.data.copy(isLoadingMore = true))
        viewModelScope.launch {
            try {
                val page = repository.getComments(videoId, token)
                continuation = page.continuation
                if (page.continuation == null) reachedEnd = true
                _uiState.value =
                    UiState.Success(
                        current.data.copy(
                            comments = (current.data.comments + page.comments).distinctBy { it.id },
                            totalCount = page.totalCount.takeIf { it > 0 } ?: current.data.totalCount,
                            isLoadingMore = false,
                            hasMore = page.continuation != null,
                        ),
                    )
            } catch (e: Exception) {
                _uiState.value = UiState.Success(current.data.copy(isLoadingMore = false))
            } finally {
                loading = false
            }
        }
    }
}
