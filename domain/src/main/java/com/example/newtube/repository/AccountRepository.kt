package com.example.newtube.repository

import com.example.newtube.model.AccountPage
import com.example.newtube.model.AccountResult
import com.example.newtube.model.AccountState
import com.example.newtube.model.LoginState
import com.example.newtube.model.RemoteVideo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only view of the signed-in YouTube account. Remote history is never
 * written to Room; signing out only clears the account state.
 */
interface AccountRepository {
    val state: StateFlow<AccountState>

    fun loginFlow(): Flow<LoginState>

    suspend fun signOut()

    suspend fun history(continuation: String? = null): AccountResult<AccountPage<RemoteVideo>>

    suspend fun subscriptions(continuation: String? = null): AccountResult<AccountPage<RemoteVideo>>

    suspend fun notifications(): AccountResult<List<RemoteVideo>>
}
