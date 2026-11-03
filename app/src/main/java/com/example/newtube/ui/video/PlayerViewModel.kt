package com.example.newtube.ui.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.example.newtube.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    fun loadStream(videoId: String) {
        viewModelScope.launch {
            try {
                val url = repository.getStreamUrl(videoId)
                _state.update { it.copy(streamUrl = url) }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Failed to load stream", e)
            }
        }
    }
}

data class PlayerState(val streamUrl: String? = null)