package com.example.newtube.ui.home

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
class HomeViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos

    init {
        loadTrending()
    }

    private fun loadTrending() {
        viewModelScope.launch {
            try {
                val videos = repository.getTrending()
                _videos.value = videos
            } catch (e: Exception) {
                _videos.value = emptyList()
            }
        }
    }
}
