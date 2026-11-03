package com.example.newtube.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.newtube.model.Video
import com.example.newtube.repository.VideoRepository
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {

    private val _results = MutableStateFlow<List<Video>>(emptyList())
    val results: StateFlow<List<Video>> = _results

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun search(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val videos = repository.search(query)
                _results.value = videos
            } catch (e: Exception) {
                // Optionally log or handle exception
                _results.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
