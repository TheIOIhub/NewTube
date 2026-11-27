package com.example.newtube.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newtube.model.HomeCategory
import com.example.newtube.model.Video
import com.example.newtube.repository.FeedKey
import com.example.newtube.repository.SettingsRepository
import com.example.newtube.repository.VideoRepository
import com.example.newtube.ui.common.UiState
import com.example.newtube.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<List<Video>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Video>>> = _uiState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _selectedCategory = MutableStateFlow(HomeCategory.ALL)
    val selectedCategory: StateFlow<HomeCategory> = _selectedCategory

    private val _shortsShelf = MutableStateFlow<List<Video>>(emptyList())
    val shortsShelf: StateFlow<List<Video>> = _shortsShelf

    private var shelfJob: Job? = null

    private val cache = mutableMapOf<HomeCategory, List<Video>>()

    /** The category whose feed is currently shown; guards cross-category state. */
    private var displayedCategory: HomeCategory? = null

    /** Incremented by every fetch; stale responses compare against it and bail out. */
    private var requestToken = 0

    /** Live fetches, so a stale response cannot clear a newer refresh indicator. */
    private var inFlight = 0

    /** While caches are being cleared, pre-clear Room emissions must be ignored. */
    private var clearInFlight = 0
    private var preferencesGeneration = 0

    /** After a preference change, only a fresh refresh may publish a feed. */
    private var awaitFreshFeed = false

    init {
        // Stale-while-revalidate: Room publishes the cached feed before the
        // network result, and a failed refresh never overwrites that state.
        viewModelScope.launch {
            _selectedCategory
                .flatMapLatest { category ->
                    repository.observeFeed(FeedKey.Home(category)).map { category to it }
                }
                .collect { (category, cached) ->
                    if (clearInFlight > 0 || awaitFreshFeed) return@collect
                    if (category == _selectedCategory.value && cached.isNotEmpty()) {
                        cache[category] = cached
                        displayedCategory = category
                        _uiState.value = UiState.Success(cached)
                    }
                }
        }
        load()
        loadShortsShelf()
        viewModelScope.launch {
            settingsRepository.settings
                .distinctUntilChangedBy { it.contentCountry to it.contentLanguage }
                .drop(1)
                .collect { onContentPreferencesChanged() }
        }
    }

    /** Region/language changed: cached feeds must be replaced, not shown stale. */
    fun onContentPreferencesChanged() {
        val generation = ++preferencesGeneration
        clearInFlight += 1
        awaitFreshFeed = true
        cache.clear()
        displayedCategory = null
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                repository.clearFeedCaches()
            } catch (_: Exception) {
                // Clearing is best effort: the reload below still publishes a
                // fresh feed and must not leave the screen stuck on Loading.
            } finally {
                clearInFlight = (clearInFlight - 1).coerceAtLeast(0)
                if (generation == preferencesGeneration) {
                    load()
                    loadShortsShelf()
                }
            }
        }
    }

    fun selectCategory(category: HomeCategory) {
        if (category == _selectedCategory.value) return
        _selectedCategory.value = category
        val cached = cache[category]
        if (cached != null) {
            displayedCategory = category
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
    fun refresh() {
        fetch(showLoading = false)
        loadShortsShelf()
    }

    private fun loadShortsShelf() {
        shelfJob?.cancel()
        shelfJob =
            viewModelScope.launch {
                _shortsShelf.value =
                    runCatching { repository.getShortsShelf().shuffled() }.getOrDefault(emptyList())
            }
    }

    private fun fetch(showLoading: Boolean) {
        val token = ++requestToken
        val category = _selectedCategory.value
        // Only a same-key refresh may keep an existing Success on screen.
        val keepCached = displayedCategory == category
        inFlight += 1
        _isRefreshing.value = true
        if (showLoading && !keepCached) _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val feed = repository.refreshFeed(FeedKey.Home(category)).getOrThrow()
                if (token != requestToken) return@launch
                cache[category] = feed
                displayedCategory = category
                awaitFreshFeed = false
                _uiState.value = UiState.Success(feed)
            } catch (e: Exception) {
                if (token != requestToken) return@launch
                if (displayedCategory != category) {
                    _uiState.value = UiState.Error(e.toUserMessage())
                }
            } finally {
                inFlight = (inFlight - 1).coerceAtLeast(0)
                if (inFlight == 0) _isRefreshing.value = false
            }
        }
    }
}
