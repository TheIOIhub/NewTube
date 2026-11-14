package com.example.newtube.ui.shorts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newtube.model.PlaybackSource
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
class ShortsViewModel @Inject constructor(
    private val repository: VideoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<List<Video>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Video>>> = _uiState

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    /** Playback sources resolved lazily, one per visible short. */
    private val _playbackSources = MutableStateFlow<Map<String, PlaybackSource>>(emptyMap())
    val playbackSources: StateFlow<Map<String, PlaybackSource>> = _playbackSources

    private val _playbackErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val playbackErrors: StateFlow<Map<String, String>> = _playbackErrors

    private val requestedSources = mutableSetOf<String>()
    private var nextPage = 1
    private var loading = false
    private var reachedEnd = false

    init {
        reload()
    }

    fun reload() {
        if (loading) return
        loading = true
        nextPage = 1
        reachedEnd = false
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val collected = mutableListOf<Video>()
                var attempts = 0
                while (collected.size < MIN_SHORTS && attempts < MAX_PAGE_ATTEMPTS && !reachedEnd) {
                    val batch = repository.getShorts(nextPage)
                    nextPage++
                    attempts++
                    if (batch.isEmpty()) reachedEnd = true
                    collected += batch
                }
                if (collected.isEmpty()) {
                    _uiState.value = UiState.Error(NO_SHORTS_MESSAGE)
                } else {
                    _uiState.value = UiState.Success(collected.distinctBy { it.id })
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.toUserMessage())
            } finally {
                loading = false
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value as? UiState.Success ?: return
        if (loading || reachedEnd) return
        loading = true
        _isLoadingMore.value = true
        viewModelScope.launch {
            try {
                val currentIds = current.data.map { it.id }.toSet()
                val collected = mutableListOf<Video>()
                var attempts = 0
                // A page may consist only of ids we already show; that is not
                // the end of the feed, so keep asking until something new
                // arrives or the attempt budget runs out.
                while (attempts < MAX_PAGE_ATTEMPTS && !reachedEnd) {
                    val batch = repository.getShorts(nextPage)
                    nextPage++
                    attempts++
                    if (batch.isEmpty()) {
                        reachedEnd = true
                        break
                    }
                    collected += batch.filter { it.id !in currentIds }
                    if (collected.isNotEmpty()) break
                }
                if (collected.isNotEmpty()) {
                    _uiState.value =
                        UiState.Success((current.data + collected).distinctBy { it.id })
                }
            } catch (e: Exception) {
                // Keep the shorts that are already on screen; the next swipe retries.
            } finally {
                loading = false
                _isLoadingMore.value = false
            }
        }
    }

    /** Called when a page becomes the active one; resolves its stream once. */
    fun ensurePlayback(videoId: String, force: Boolean = false) {
        if (!force && videoId in requestedSources) return
        requestedSources += videoId
        if (force) {
            _playbackErrors.value = _playbackErrors.value - videoId
        }
        viewModelScope.launch {
            try {
                val playback = repository.getVideoDetails(videoId).playback
                _playbackSources.value = _playbackSources.value + (videoId to playback)
            } catch (e: Exception) {
                requestedSources -= videoId
                _playbackErrors.value = _playbackErrors.value + (videoId to e.toUserMessage())
            }
        }
    }

    private companion object {
        const val MIN_SHORTS = 10
        const val MAX_PAGE_ATTEMPTS = 3
        const val NO_SHORTS_MESSAGE = "No shorts available right now"
    }
}
