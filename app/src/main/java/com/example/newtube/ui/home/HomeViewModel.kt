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

    /** Incremented by every fetch; stale responses compare against it and bail out. */
    private var requestToken = 0

    /** Live fetches, so a stale response cannot clear a newer refresh indicator. */
    private var inFlight = 0

    init {
        // Stale-while-revalidate: Room publishes the cached feed before the
        // network result, and a failed refresh never overwrites that state.
        viewModelScope.launch {
            _selectedCategory
                .flatMapLatest { category ->
                    repository.observeFeed(FeedKey.Home(category)).map { category to it }
                }
                .collect { (category, cached) ->
                    if (category == _selectedCategory.value && cached.isNotEmpty()) {
                        cache[category] = cached
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
        cache.clear()
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            repository.clearFeedCaches()
            load()
            loadShortsShelf()
        }
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
        inFlight += 1
        _isRefreshing.value = true
        // A cached feed is kept on screen while it is revalidated.
        if (showLoading && _uiState.value !is UiState.Success) _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val feed = repository.refreshFeed(FeedKey.Home(category)).getOrThrow()
                if (token != requestToken) return@launch
                cache[category] = feed
                _uiState.value = UiState.Success(feed)
            } catch (e: Exception) {
                if (token != requestToken) return@launch
                if (_uiState.value !is UiState.Success) {
                    _uiState.value = UiState.Error(e.toUserMessage())
                }
            } finally {
                inFlight = (inFlight - 1).coerceAtLeast(0)
                if (inFlight == 0) _isRefreshing.value = false
            }
        }
    }
}
