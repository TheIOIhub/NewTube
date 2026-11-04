package com.example.newtube.ui.channel

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
class ChannelViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {

    private val _channelVideos = MutableStateFlow<List<Video>>(emptyList())
    val channelVideos: StateFlow<List<Video>> = _channelVideos

    fun loadChannelVideos(channelId: String) {
        viewModelScope.launch {
            try {
                val videos = repository.getChannelVideos(channelId)
                _channelVideos.value = videos
            } catch (e: Exception) {
                _channelVideos.value = emptyList()
            }
        }
    }
}
