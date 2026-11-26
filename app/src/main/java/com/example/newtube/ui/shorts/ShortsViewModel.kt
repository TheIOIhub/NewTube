package com.example.newtube.ui.shorts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newtube.model.AppSettings
import com.example.newtube.model.PlaybackSource
import com.example.newtube.model.Video
import com.example.newtube.repository.HistoryRepository
import com.example.newtube.repository.SettingsRepository
import com.example.newtube.repository.ShortsCacheRepository
import com.example.newtube.repository.VideoRepository
import com.example.newtube.ui.common.UiState
import com.example.newtube.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

@HiltViewModel
class ShortsViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val shortsCache: ShortsCacheRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<List<Video>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Video>>> = _uiState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    /** Playback sources resolved lazily, one per visible short. */
    private val _playbackSources = MutableStateFlow<Map<String, PlaybackSource>>(emptyMap())
    val playbackSources: StateFlow<Map<String, PlaybackSource>> = _playbackSources

    private val _playbackErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val playbackErrors: StateFlow<Map<String, String>> = _playbackErrors

    private val requestedSources = mutableSetOf<String>()
    private val prefetchSemaphore = Semaphore(2)
    private var nextPage = 1
    private var loading = false
    private var reachedEnd = false
    private var historyJob: Job? = null

    private val _requestedStartId = MutableStateFlow<String?>(null)
    val requestedStartId: StateFlow<String?> = _requestedStartId

    /** The last id handed to the pager; keeps a restored tab from jumping twice. */
    private var handledStartId: String? = null

    /** Active clip, used to restore the pager index after a background refresh. */
    private var currentVideoId: String? = null

    init {
        reload()
    }

    fun dismissRefreshError() {
        _refreshError.value = null
    }

    /**
     * A Home shelf tap asks the tab to open at this clip. If the clip is not in
     * the loaded feed yet, fetch it once and prepend it, so the pager can start
     * at index 0 without waiting for the whole Shorts rotation.
     */
    fun startFrom(videoId: String) {
        if (videoId == handledStartId) return
        if (_requestedStartId.value == videoId) return
        _requestedStartId.value = videoId
        val current = _uiState.value as? UiState.Success ?: return
        if (current.data.any { it.id == videoId }) return
        viewModelScope.launch { prependRequested() }
    }

    /** Called by the pager after it jumped to [requestedStartId]. */
    fun consumeStartRequest() {
        handledStartId = _requestedStartId.value
        _requestedStartId.value = null
    }

    private suspend fun prependRequested() {
        val id = _requestedStartId.value ?: return
        val current = _uiState.value as? UiState.Success ?: return
        if (current.data.any { it.id == id }) return
        val requested = runCatching { repository.getVideoById(id) }.getOrNull() ?: return
        val latest = _uiState.value as? UiState.Success ?: return
        _uiState.value =
            UiState.Success(listOf(requested) + latest.data.filterNot { it.id == id })
    }

    /** Cache-first reload: show what we already have, then refresh in the background. */
    fun reload() {
        if (loading) return
        loading = true
        nextPage = 1
        reachedEnd = false
        _isRefreshing.value = true
        _refreshError.value = null
        viewModelScope.launch {
            var cached = emptyList<Video>()
            try {
                cached = shortsCache.observeFeed().first()
                _uiState.value =
                    if (cached.isNotEmpty()) UiState.Success(cached) else UiState.Loading
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
                    // Never overwrite a usable cached feed with an empty refresh.
                    if (cached.isEmpty()) {
                        _uiState.value = UiState.Error(NO_SHORTS_MESSAGE)
                    } else {
                        _refreshError.value = NO_SHORTS_MESSAGE
                    }
                } else {
                    var result = collected.distinctBy { it.id }
                    val requestedId = _requestedStartId.value
                    if (requestedId != null && result.none { it.id == requestedId }) {
                        runCatching { repository.getVideoById(requestedId) }.getOrNull()?.let {
                            result = listOf(it) + result
                        }
                    }
                    shortsCache.replaceFeed(result)
                    _uiState.value = UiState.Success(result)
                    val currentId = currentVideoId
                    // A pending deep link wins: it was asked for explicitly and
                    // must not be replaced by the refresh's index restoration.
                    if (currentId != null && _requestedStartId.value == null) {
                        _requestedStartId.value =
                            currentId.takeIf { id -> result.any { it.id == id } }
                    }
                }
            } catch (e: Exception) {
                _refreshError.value = e.toUserMessage()
                if (cached.isEmpty()) {
                    _uiState.value = UiState.Error(e.toUserMessage())
                }
            } finally {
                loading = false
                _isRefreshing.value = false
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
                resolveSource(videoId)
            } catch (e: Exception) {
                requestedSources -= videoId
                _playbackErrors.value = _playbackErrors.value + (videoId to e.toUserMessage())
            }
        }
    }

    /**
     * Called on every pager page change. Resolves the active clip and its
     * neighbours so a swipe does not have to wait for the source request, but
     * caps each event at four ids and two concurrent resolutions.
     */
    fun onPageChanged(index: Int) {
        val videos = (_uiState.value as? UiState.Success)?.data ?: return
        // currentPage updates mid-swipe; track it so a refresh can restore the
        // clip the user is moving to, not the one that last settled.
        videos.getOrNull(index)?.let { currentVideoId = it.id }
        val windowIds =
            ((index - 1)..(index + 2)).mapNotNull { videos.getOrNull(it)?.id }
        for (id in windowIds) {
            if (id in requestedSources) continue
            requestedSources += id
            viewModelScope.launch {
                prefetchSemaphore.withPermit {
                    try {
                        resolveSource(id)
                    } catch (e: Exception) {
                        // Let the next page event or an explicit Retry try again.
                        requestedSources -= id
                    }
                }
            }
        }
    }

    private suspend fun resolveSource(videoId: String) {
        val maxHeight =
            runCatching { settingsRepository.settings.first().defaultQuality }
                .getOrDefault(AppSettings.DEFAULT_QUALITY)
        val playback = repository.getVideoDetails(videoId, maxHeight).playback
        _playbackSources.value = _playbackSources.value + (videoId to playback)
    }

    /** Leaving the Shorts tab must not record a clip that was only passing by. */
    fun cancelPendingRecord() {
        historyJob?.cancel()
    }

    fun onPageSettled(video: Video) {
        currentVideoId = video.id
        historyJob?.cancel()
        historyJob =
            viewModelScope.launch {
                delay(SHORTS_HISTORY_DELAY_MS)
                runCatching {
                    if (settingsRepository.settings.first().recordHistory) {
                        historyRepository.record(video)
                    }
                }
            }
    }

    private companion object {
        const val MIN_SHORTS = 10
        const val MAX_PAGE_ATTEMPTS = 3
        const val NO_SHORTS_MESSAGE = "No shorts available right now"
        const val SHORTS_HISTORY_DELAY_MS = 3_000L
    }
}
