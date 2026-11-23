package com.example.newtube.ui.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newtube.model.AccountState
import com.example.newtube.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YouViewModel
    @Inject
    constructor(
        private val accountRepository: AccountRepository,
    ) : ViewModel() {
        val account: StateFlow<AccountState> = accountRepository.state

        fun signOut() {
            viewModelScope.launch { accountRepository.signOut() }
        }
    }
