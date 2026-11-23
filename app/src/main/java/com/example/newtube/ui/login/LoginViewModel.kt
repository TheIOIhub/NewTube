package com.example.newtube.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newtube.model.LoginState
import com.example.newtube.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val repository: AccountRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow<LoginState>(LoginState.Requesting)
        val state: StateFlow<LoginState> = mutableState.asStateFlow()

        private var job: Job? = null

        fun start() {
            if (job?.isActive == true) return
            mutableState.value = LoginState.Requesting
            job =
                viewModelScope.launch {
                    repository.loginFlow().collect { mutableState.value = it }
                }
        }

        fun cancel() {
            job?.cancel()
            job = null
        }

        override fun onCleared() {
            job?.cancel()
            job = null
            super.onCleared()
        }
    }
