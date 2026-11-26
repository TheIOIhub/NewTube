package com.example.newtube.account

import com.example.newtube.innertube.InnerTubeHttpException
import com.example.newtube.innertube.InnerTubeParser
import com.example.newtube.innertube.InnerTubeService
import com.example.newtube.model.AccountPage
import com.example.newtube.model.AccountResult
import com.example.newtube.model.AccountState
import com.example.newtube.model.LoginError
import com.example.newtube.model.LoginState
import com.example.newtube.model.RemoteVideo
import com.example.newtube.model.UserAccount
import com.example.newtube.repository.AccountRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException

/**
 * Owns the TV OAuth session and the account-only InnerTube calls.
 *
 * State is restored lazily from the encrypted store so a repository created
 * before the store is populated still reports the stored profile.
 */
class AccountRepositoryImpl(
    private val credentials: ClientCredentialsProvider,
    private val oauth: OAuthApi,
    private val innerTube: InnerTubeService,
    private val store: SecretStore,
    private val clock: () -> Long,
    private val deviceId: String,
) : AccountRepository {
    private val mutableState by lazy {
        MutableStateFlow(
            readAccount()?.let { AccountState.SignedIn(it) } ?: AccountState.SignedOut,
        )
    }

    override val state: StateFlow<AccountState> get() = mutableState

    private val refreshMutex = Mutex()

    override fun loginFlow(): Flow<LoginState> =
        flow {
            emit(LoginState.Requesting)
            if (credentialsOrNull() == null) {
                emit(LoginState.Error(LoginError.UNAVAILABLE))
                return@flow
            }
            val code =
                try {
                    withCredentials { credentials -> oauth.requestDeviceCode(credentials, deviceId) }
                } catch (_: CredentialsUnavailableException) {
                    emit(LoginState.Error(LoginError.UNAVAILABLE))
                    return@flow
                } catch (_: IOException) {
                    emit(LoginState.Error(LoginError.NETWORK))
                    return@flow
                }
            val expiresAtMs = clock() + code.expiresInSeconds * 1_000L
            emit(LoginState.Waiting(code.userCode, code.verificationUrl, expiresAtMs))
            var intervalSeconds = code.intervalSeconds.coerceAtLeast(1)
            while (true) {
                if (clock() >= expiresAtMs) {
                    emit(LoginState.Error(LoginError.EXPIRED))
                    return@flow
                }
                delay(intervalSeconds * 1_000L)
                val poll =
                    try {
                        withCredentials { credentials -> oauth.pollToken(credentials, code.deviceCode) }
                    } catch (_: CredentialsUnavailableException) {
                        emit(LoginState.Error(LoginError.UNAVAILABLE))
                        return@flow
                    } catch (_: IOException) {
                        intervalSeconds = (intervalSeconds * 2).coerceAtMost(MAX_BACKOFF_SECONDS)
                        continue
                    }
                when (poll) {
                    is TokenPoll.Granted -> {
                        storeTokens(poll.tokens)
                        val account = fetchAccount(poll.tokens.accessToken)
                        writeAccount(account)
                        mutableState.value = AccountState.SignedIn(account)
                        emit(LoginState.Success)
                        return@flow
                    }
                    TokenPoll.Pending -> Unit
                    TokenPoll.SlowDown -> intervalSeconds += 5
                    TokenPoll.Denied -> {
                        emit(LoginState.Error(LoginError.ACCESS_DENIED))
                        return@flow
                    }
                    TokenPoll.Expired -> {
                        emit(LoginState.Error(LoginError.EXPIRED))
                        return@flow
                    }
                }
            }
        }

    override suspend fun history(continuation: String?): AccountResult<AccountPage<RemoteVideo>> =
        loadVideos(continuation) { token, next ->
            innerTube.browseVideos(HISTORY_ID, next, token)
        }

    override suspend fun subscriptions(continuation: String?): AccountResult<AccountPage<RemoteVideo>> =
        loadVideos(continuation) { token, next ->
            innerTube.browseSubscriptions(next, token)
        }

    override suspend fun notifications(): AccountResult<List<RemoteVideo>> {
        val body =
            try {
                authorized { token -> innerTube.browseVideos(NOTIFICATIONS_ID, null, token) }
            } catch (_: NeedsSignInException) {
                return AccountResult.NeedsSignIn
            } catch (e: InnerTubeHttpException) {
                // `FEnotifications` is best effort: the TV client answers 400
                // when it has nothing for this account, which means "no new
                // notifications", not a network failure with a dead retry.
                if (e.code in 400..499 && e.code != 429) {
                    return AccountResult.ParseEmpty
                }
                return mapHttp(e.code)
            } catch (_: IOException) {
                return AccountResult.Network
            }
        val parsed = InnerTubeParser.parseVideos(body)
        return if (parsed.items.isEmpty()) {
            AccountResult.ParseEmpty
        } else {
            AccountResult.Success(parsed.items)
        }
    }

    override suspend fun signOut() {
        clearSession()
        mutableState.value = AccountState.SignedOut
    }

    private suspend fun loadVideos(
        continuation: String?,
        browse: suspend (token: String, continuation: String?) -> String,
    ): AccountResult<AccountPage<RemoteVideo>> {
        val body =
            try {
                authorized { token -> browse(token, continuation) }
            } catch (_: NeedsSignInException) {
                return AccountResult.NeedsSignIn
            } catch (e: InnerTubeHttpException) {
                return mapHttp(e.code)
            } catch (_: IOException) {
                return AccountResult.Network
            }
        val parsed = InnerTubeParser.parseVideos(body)
        return if (parsed.items.isEmpty()) {
            AccountResult.ParseEmpty
        } else {
            AccountResult.Success(AccountPage(parsed.items, parsed.continuation))
        }
    }

    /** Runs [block] with a valid bearer token, refreshing once after a 401/403. */
    private suspend fun <T> authorized(block: suspend (String) -> T): T {
        val token = requireToken()
        return try {
            block(token)
        } catch (e: InnerTubeHttpException) {
            if (e.code != 401 && e.code != 403) throw e
            val fresh = refreshLocked(failedToken = token) ?: throw NeedsSignInException()
            try {
                block(fresh)
            } catch (again: InnerTubeHttpException) {
                if (again.code == 401 || again.code == 403) {
                    markExpired()
                    throw NeedsSignInException()
                }
                throw again
            }
        }
    }

    private suspend fun requireToken(): String = ensureToken() ?: throw NeedsSignInException()

    private suspend fun ensureToken(): String? {
        val access = store.get(KEY_ACCESS_TOKEN)
        val expiresAt = store.get(KEY_EXPIRES_AT)?.toLongOrNull() ?: 0L
        if (access != null && clock() < expiresAt - REFRESH_SKEW_MS) return access
        return refreshLocked(failedToken = null)
    }

    private suspend fun refreshLocked(failedToken: String?): String? =
        refreshMutex.withLock {
            val current = store.get(KEY_ACCESS_TOKEN)
            val expiresAt = store.get(KEY_EXPIRES_AT)?.toLongOrNull() ?: 0L
            val alreadyRefreshed = failedToken != null && current != null && current != failedToken
            val stillFresh = failedToken == null && current != null && clock() < expiresAt - REFRESH_SKEW_MS
            if (alreadyRefreshed || stillFresh) {
                return@withLock current
            }

            val refreshToken =
                store.get(KEY_REFRESH_TOKEN)
                    ?: run {
                        mutableState.value = AccountState.SignedOut
                        return@withLock null
                    }
            try {
                val tokens = withCredentials { credentials -> oauth.refresh(credentials, refreshToken) }
                storeTokens(tokens)
                tokens.accessToken
            } catch (_: InvalidGrantException) {
                clearSession()
                mutableState.value = AccountState.SignedOut
                null
            }
        }

    /** Runs [block] with the scraped pair, re-scraping once if Google rejects it. */
    private suspend fun <T> withCredentials(block: suspend (ClientCredentials) -> T): T {
        val first = credentials.get()
        return try {
            block(first)
        } catch (_: InvalidClientException) {
            credentials.invalidate()
            block(credentials.get())
        }
    }

    private suspend fun credentialsOrNull(): ClientCredentials? =
        try {
            credentials.get()
        } catch (_: IOException) {
            null
        }

    /**
     * Clears the session but keeps the stable device id and the cached client
     * pair, so signing in again does not look like a new device and needs no
     * fresh scrape.
     */
    private fun clearSession() {
        store.remove(KEY_ACCESS_TOKEN)
        store.remove(KEY_REFRESH_TOKEN)
        store.remove(KEY_EXPIRES_AT)
        store.remove(KEY_ACCOUNT_JSON)
    }

    private suspend fun fetchAccount(accessToken: String): UserAccount =
        try {
            InnerTubeParser.parseAccount(innerTube.fetchProfile(accessToken))
                ?: UserAccount("", "", "")
        } catch (_: IOException) {
            UserAccount("", "", "")
        }

    private fun storeTokens(tokens: Tokens) {
        store.put(KEY_ACCESS_TOKEN, tokens.accessToken)
        tokens.refreshToken?.let { store.put(KEY_REFRESH_TOKEN, it) }
        store.put(KEY_EXPIRES_AT, (clock() + tokens.expiresInSeconds * 1_000L).toString())
    }

    private fun markExpired() {
        val account =
            (mutableState.value as? AccountState.SignedIn)?.account
                ?: (mutableState.value as? AccountState.Expired)?.account
        mutableState.value = AccountState.Expired(account)
    }

    private fun mapHttp(code: Int): AccountResult<Nothing> =
        if (code in 500..599 || code == 429) AccountResult.Server else AccountResult.Network

    private fun readAccount(): UserAccount? = parseStoredAccount(store.get(KEY_ACCOUNT_JSON))

    private fun writeAccount(account: UserAccount) {
        val json =
            buildJsonObject {
                put("name", account.name)
                put("email", account.email)
                put("avatarUrl", account.avatarUrl)
                account.pageId?.let { put("pageId", it) }
            }
        store.put(KEY_ACCOUNT_JSON, json.toString())
    }

    private class NeedsSignInException : Exception()

    private companion object {
        const val HISTORY_ID = "FEhistory"
        const val NOTIFICATIONS_ID = "FEnotifications"
        const val REFRESH_SKEW_MS = 5 * 60 * 1_000L
        const val MAX_BACKOFF_SECONDS = 30
        const val KEY_ACCESS_TOKEN = AccountStoreKeys.ACCESS_TOKEN
        const val KEY_REFRESH_TOKEN = AccountStoreKeys.REFRESH_TOKEN
        const val KEY_EXPIRES_AT = AccountStoreKeys.EXPIRES_AT
        const val KEY_ACCOUNT_JSON = AccountStoreKeys.ACCOUNT_JSON
    }
}
