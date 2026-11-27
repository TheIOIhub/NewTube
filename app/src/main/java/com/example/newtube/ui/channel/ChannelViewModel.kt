package com.example.newtube.ui.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newtube.model.Channel
import com.example.newtube.model.Video
import com.example.newtube.repository.FeedKey
import com.example.newtube.repository.VideoRepository
import com.example.newtube.ui.common.UiState
import com.example.newtube.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelUiState(
    val channel: UiState<Channel> = UiState.Loading,
    val videos: List<Video> = emptyList(),
    val videosError: String? = null,
    val isLoadingMore: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val repository: VideoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChannelUiState())
    val uiState: StateFlow<ChannelUiState> = _uiState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    /** Page 1 of the active channel; the pager's token comes from loadMore(). */
    private val activeFeed = MutableStateFlow<FeedKey.Channel?>(null)

    /** Last cached uploads, kept so a retry of the same key can re-publish them. */
    private var cachedVideos: List<Video> = emptyList()

    private var channelId: String = ""
    private var continuation: String? = null
    private var needsContinuation = false
    private var loadingVideos = false
    private var reachedEnd = false
    private var requestToken = 0

    init {
        viewModelScope.launch {
            activeFeed
                .flatMapLatest { key ->
                    if (key == null) {
                        flowOf(emptyList<Video>())
                    } else {
                        repository.observeFeed(key)
                    }
                }
                .collect { cached ->
                    if (cached.isNotEmpty() && activeFeed.value?.channelId == channelId) {
                        cachedVideos = cached
                        _uiState.update { it.copy(videos = cached, videosError = null) }
                    }
                }
        }
    }

    fun loadChannelDetails(channelId: String) = loadChannelDetails(channelId, force = false)

    private fun loadChannelDetails(
        channelId: String,
        force: Boolean,
    ) {
        if (!force && this.channelId == channelId && _uiState.value.channel is UiState.Success) return
        val token = ++requestToken
        val sameChannel = this.channelId == channelId
        this.channelId = channelId
        if (sameChannel) {
            // Re-subscribe so flatMapLatest re-emits the cached uploads on retry.
            activeFeed.value = null
            cachedVideos = _uiState.value.videos
        }
        activeFeed.value = FeedKey.Channel(channelId)
        continuation = null
        needsContinuation = false
        loadingVideos = false
        reachedEnd = false
        _uiState.value = ChannelUiState(videos = cachedVideos.takeIf { sameChannel }.orEmpty())
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                val channel = repository.getChannelDetails(channelId)
                if (token != requestToken) return@launch
                _uiState.update { it.copy(channel = UiState.Success(channel)) }
            } catch (e: Exception) {
                if (token != requestToken) return@launch
                _uiState.update { it.copy(channel = UiState.Error(e.toUserMessage())) }
                _isRefreshing.value = false
                return@launch
            }
            try {
                val videos = repository.refreshFeed(FeedKey.Channel(channelId)).getOrThrow()
                if (token != requestToken) return@launch
                // refreshFeed returns videos only; the continuation token is
                // discovered lazily by loadMore() through getChannelVideos().
                needsContinuation = true
                if (videos.isEmpty()) reachedEnd = true
                _uiState.update { it.copy(videos = videos, videosError = null) }
            } catch (e: Exception) {
                if (token == requestToken) {
                    // Keep the cached uploads instead of replacing them with an error.
                    _uiState.update { it.copy(videosError = e.toUserMessage()) }
                }
            } finally {
                if (token == requestToken) _isRefreshing.value = false
            }
        }
    }

    fun retry() {
        if (channelId.isBlank()) return
        loadChannelDetails(channelId, force = true)
    }

    fun loadMore() {
        loadVideos()
    }

    private fun loadVideos() {
        if (channelId.isBlank() || loadingVideos || reachedEnd) return
        val token = requestToken
        val id = channelId
        loadingVideos = true
        _uiState.update { it.copy(isLoadingMore = true, videosError = null) }
        viewModelScope.launch {
            try {
                val first =
                    if (needsContinuation) {
                        repository.getChannelVideos(id, null)
                    } else {
                        null
                    }
                // Switching channels invalidates this page; never mix videos.
                if (token != requestToken || id != channelId) return@launch
                if (first != null) {
                    continuation = first.continuation
                    needsContinuation = false
                    if (first.continuation == null) reachedEnd = true
                    _uiState.update { it.copy(videos = (it.videos + first.videos).distinctBy { it.id }) }
                    if (reachedEnd) return@launch
                }
                val page = repository.getChannelVideos(id, continuation)
                if (token != requestToken || id != channelId) return@launch
                continuation = page.continuation
                if (page.continuation == null) reachedEnd = true
                _uiState.update { it.copy(videos = (it.videos + page.videos).distinctBy { it.id }) }
            } catch (e: Exception) {
                if (token == requestToken && id == channelId) {
                    _uiState.update { it.copy(videosError = e.toUserMessage()) }
                }
            } finally {
                if (token == requestToken && id == channelId) {
                    loadingVideos = false
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            }
        }
    }
}
