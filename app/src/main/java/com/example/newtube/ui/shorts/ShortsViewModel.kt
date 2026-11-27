package com.example.newtube.ui.shorts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

@OptIn(ExperimentalPagingApi::class)
@HiltViewModel
class ShortsViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val shortsCache: ShortsCacheRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<List<Video>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Video>>> = _uiState

    /**
     * Paged view of the Room feed cache. The Pager is built on first collection
     * so the list-based pager contract below remains the single active feed
     * path; `cachedIn` keeps generated PagingData stable across re-collections.
     * Room invalidates the source whenever the mediator writes a page.
     */
    val pagingData: StateFlow<PagingData<Video>> =
        flow<PagingData<Video>> {
            emitAll(
                Pager(
                    config = PAGING_CONFIG,
                    remoteMediator = ShortsRemoteMediator(repository, shortsCache),
                    pagingSourceFactory = { shortsCache.pagingSource() },
                ).flow.cachedIn(viewModelScope),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(PAGING_STOP_TIMEOUT_MS),
            initialValue = PagingData.empty(),
        )

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

    /**
     * Monotonic token included in the pager's prepare key so Retry re-prepares
     * through the controller even when the re-resolved [PlaybackSource] is
     * equals-identical (a Media3 `PlaybackSource` equality key alone would not
     * re-invoke prepare). See spec §7.7.
     */
    private val _retryNonce = MutableStateFlow(0)
    val retryNonce: StateFlow<Int> = _retryNonce

    /**
     * Explicit scroll-to-head signal (spec §7.3): after a refresh, when the
     * currently active clip is no longer in the feed, the pager must go to page
     * 0 rather than silently keep its numeric index. Counter so repeated
     * triggers re-emit.
     */
    private val _scrollToHead = MutableStateFlow(0)
    val scrollToHead: StateFlow<Int> = _scrollToHead

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

    /**
     * Pages appended by [loadMore] (and persisted via `appendFeed`), keyed by id.
     * Kept so a background [reload]'s `replaceFeed` re-applies them instead of
     * wiping appended pages before the next revalidate (spec §7.5). Metadata
     * only; never signed stream URLs.
     */
    private val appended = linkedMapOf<String, Video>()

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
        val requested =
            runCatching { repository.getVideoById(id) }.getOrNull()
                ?: run {
                    // An id we cannot fetch must not block refresh reconciliation
                    // forever: the pager can never consume an absent clip.
                    if (_requestedStartId.value == id) _requestedStartId.value = null
                    return
                }
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
                        val requested =
                            runCatching { repository.getVideoById(requestedId) }.getOrNull()
                        if (requested != null) {
                            result = listOf(requested) + result
                        } else if (_requestedStartId.value == requestedId) {
                            // Same as prependRequested: an unresolvable deep link
                            // would otherwise suppress every future reconciliation.
                            _requestedStartId.value = null
                        }
                    }
                    // Ordering (spec §7.5, I2): `result` is the freshly
                    // revalidated first page; pages appended by loadMore() are
                    // NOT wiped — replaceFeed is given first page + appended,
                    // and appendFeed (in loadMore) is never followed by a
                    // replaceFeed that would drop them. Cached feed was read
                    // before any write above. `loading` serializes reload vs
                    // loadMore so appends can't interleave with a refresh.
                    val resultIds = result.map { it.id }.toSet()
                    val appendedKept = appended.values.filter { it.id !in resultIds }
                    val finalList = result + appendedKept
                    shortsCache.replaceFeed(finalList)
                    _uiState.value = UiState.Success(finalList)
                    val currentId = currentVideoId
                    // A pending deep link wins: it was asked for explicitly and
                    // must not be replaced by the refresh's index restoration.
                    if (currentId != null && _requestedStartId.value == null) {
                        _requestedStartId.value =
                            currentId.takeIf { id -> result.any { it.id == id } }
                    }
                    // Spec §7.3 / M1: current clip gone after refresh -> go to
                    // head (page 0), not silently keep the numeric index.
                    // Checked against the revalidated `result` (not appended).
                    if (currentId != null && currentId !in resultIds) {
                        _scrollToHead.value += 1
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
                    // "only-new" items: not already shown, de-duplicated. Both
                    // the on-screen list and the Room cache get exactly these;
                    // appendFeed is a no-op on empty (handled in the repo) and
                    // continues after the current maximum position (spec §7.5).
                    val onlyNew =
                        collected
                            .filter { it.id !in currentIds }
                            .distinctBy { it.id }
                    if (onlyNew.isEmpty()) return@launch
                    _uiState.value =
                        UiState.Success((current.data + onlyNew).distinctBy { it.id })
                    // Persist appended pages; do NOT replaceFeed afterwards.
                    for (video in onlyNew) appended[video.id] = video
                    shortsCache.appendFeed(onlyNew)
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
                // Drop any stale resolved source: on failure `_playbackSources`
                // must not keep a previous (possibly expired) source that would
                // re-trigger prepare with the same value (spec §7.7).
                _playbackSources.value = _playbackSources.value - videoId
                _playbackErrors.value = _playbackErrors.value + (videoId to e.toUserMessage())
            }
        }
    }

    /**
     * Retry after a Media3 `PlaybackException` or a source-resolution failure
     * (spec §7.7). Clears the per-clip error, drops any requested marker,
     * bumps [retryNonce] so the pager re-prepares through the controller even
     * when the re-resolved [PlaybackSource] is equals-identical, and re-resolves
     * a fresh source. Stale `_playbackSources[id]` is removed on resolve
     * failure inside [ensurePlayback]. One prepare path is preserved.
     */
    fun retryPlayback(videoId: String) {
        _playbackErrors.value = _playbackErrors.value - videoId
        requestedSources -= videoId
        _retryNonce.value += 1
        ensurePlayback(videoId, force = true)
    }

    /**
     * Reports a Media3 `PlaybackException` from the active clip into the same
     * per-clip [playbackErrors] map as source-resolution failures so the Shorts
     * Retry overlay surfaces it (regression guard vs pre-Task9
     * `VideoPlayer.onPlayerError`; spec §7.7). [videoId] may be null; falls
     * back to the active clip. Never fabricates success.
     */
    fun reportPlayerError(videoId: String?, message: String?) {
        val id = videoId ?: currentVideoId ?: return
        val text = message?.takeIf { it.isNotBlank() } ?: PLAYBACK_ERROR_MESSAGE
        _playbackErrors.value = _playbackErrors.value + (id to text)
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
                        // Surface the failure for the active clip (the pager no
                        // longer calls ensurePlayback), and let the next page
                        // event or an explicit Retry try again.
                        requestedSources -= id
                        // Drop any stale resolved source on resolve failure
                        // (spec §7.7); a successful re-resolve re-adds it.
                        _playbackSources.value = _playbackSources.value - id
                        _playbackErrors.value =
                            _playbackErrors.value + (id to e.toUserMessage())
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
        // A successful re-resolve must not leave a stale Retry overlay behind.
        _playbackErrors.value = _playbackErrors.value - videoId
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
        const val PLAYBACK_ERROR_MESSAGE = "Playback failed"
        const val SHORTS_HISTORY_DELAY_MS = 3_000L
        const val PAGING_STOP_TIMEOUT_MS = 5_000L
        val PAGING_CONFIG =
            PagingConfig(
                pageSize = 10,
                prefetchDistance = 2,
                initialLoadSize = 10,
                maxSize = 40,
            )
    }
}
