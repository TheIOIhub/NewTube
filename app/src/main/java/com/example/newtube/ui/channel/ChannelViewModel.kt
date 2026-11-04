package com.example.newtube.ui.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.newtube.model.Channel
import com.example.newtube.repository.VideoRepository
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {

    private val _channelState = MutableStateFlow<Channel?>(null)
    val channelState: StateFlow<Channel?> = _channelState

    fun loadChannelDetails(channelId: String) {
        viewModelScope.launch {
            try {
                val channel = repository.getChannelDetails(channelId)
                _channelState.value = channel
            } catch (e: Exception) {
                _channelState.value = null
            }
        }
    }
}
