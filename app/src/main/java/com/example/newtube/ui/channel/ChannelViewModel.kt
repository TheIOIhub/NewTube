package com.example.newtube.ui.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newtube.model.Channel
import com.example.newtube.model.Video
import com.example.newtube.repository.VideoRepository
import com.example.newtube.ui.common.UiState
import com.example.newtube.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelUiState(
    val channel: UiState<Channel> = UiState.Loading,
    val videos: List<Video> = emptyList(),
    val videosError: String? = null,
    val isLoadingMore: Boolean = false,
)

@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val repository: VideoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChannelUiState())
    val uiState: StateFlow<ChannelUiState> = _uiState

    private var channelId: String = ""
    private var continuation: String? = null
    private var loadingVideos = false
    private var reachedEnd = false

    fun loadChannelDetails(channelId: String) {
        if (this.channelId == channelId && _uiState.value.channel is UiState.Success) return
        this.channelId = channelId
        continuation = null
        loadingVideos = false
        reachedEnd = false
        _uiState.value = ChannelUiState()
        viewModelScope.launch {
            try {
                val channel = repository.getChannelDetails(channelId)
                _uiState.update { it.copy(channel = UiState.Success(channel)) }
                loadVideos()
            } catch (e: Exception) {
                _uiState.update { it.copy(channel = UiState.Error(e.toUserMessage())) }
            }
        }
    }

    fun retry() {
        if (channelId.isBlank()) return
        val id = channelId
        this.channelId = ""
        loadChannelDetails(id)
    }

    fun loadMore() {
        loadVideos()
    }

    private fun loadVideos() {
        if (channelId.isBlank() || loadingVideos || reachedEnd) return
        loadingVideos = true
        _uiState.update { it.copy(isLoadingMore = true, videosError = null) }
        viewModelScope.launch {
            try {
                val page = repository.getChannelVideos(channelId, continuation)
                continuation = page.continuation
                if (page.continuation == null) reachedEnd = true
                _uiState.update {
                    it.copy(
                        videos = (it.videos + page.videos).distinctBy { video -> video.id },
                        isLoadingMore = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoadingMore = false, videosError = e.toUserMessage())
                }
            } finally {
                loadingVideos = false
            }
        }
    }
}
