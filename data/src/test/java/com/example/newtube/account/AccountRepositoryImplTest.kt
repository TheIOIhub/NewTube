package com.example.newtube.account

import com.example.newtube.innertube.InnerTubeHttpException
import com.example.newtube.innertube.InnerTubeService
import com.example.newtube.model.AccountResult
import com.example.newtube.model.AccountState
import com.example.newtube.model.LoginError
import com.example.newtube.model.LoginState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AccountRepositoryImplTest {
    private val store = FakeSecretStore()
    private val oauth = FakeOAuthApi()
    private val innerTube = FakeInnerTubeService()
    private val credentials = FakeCredentials()
    private val clock = FakeClock()
    private val repo =
        AccountRepositoryImpl(
            credentials = credentials,
            oauth = oauth,
            innerTube = innerTube,
            store = store,
            clock = { clock.now },
            deviceId = "0011223344556677",
        )

    @Test
    fun `restores a stored profile as signed in`() {
        store.put("account_json", """{"name":"N","email":"e","avatarUrl":"a"}""")
        assertEquals("N", (repo.state.value as AccountState.SignedIn).account.name)
    }

    @Test
    fun `login emits requesting waiting then success and stores tokens`() =
        runTest {
            oauth.pollResults.add(TokenPoll.Granted(Tokens("a1", "r1", 64001)))
            val states = repo.loginFlow().toList()
            assertEquals(LoginState.Requesting, states[0])
            assertTrue(states[1] is LoginState.Waiting)
            assertEquals(LoginState.Success, states.last())
            assertEquals("r1", store.get("refresh_token"))
            assertTrue(repo.state.value is AccountState.SignedIn)
        }

    @Test
    fun `denied code reports ACCESS_DENIED and writes no tokens`() =
        runTest {
            oauth.pollResults.add(TokenPoll.Denied)
            val states = repo.loginFlow().toList()
            assertEquals(LoginState.Error(LoginError.ACCESS_DENIED), states.last())
            assertNull(store.get("refresh_token"))
        }

    @Test
    fun `expired code reports EXPIRED`() =
        runTest {
            oauth.pollResults.add(TokenPoll.Expired)
            assertEquals(LoginState.Error(LoginError.EXPIRED), repo.loginFlow().toList().last())
            assertNull(store.get("refresh_token"))
        }

    @Test
    fun `history refreshes an expired token once and retries`() =
        runTest {
            seedSignedIn(expiresAt = clock.now - 1)
            val page = repo.history()
            assertTrue(page is AccountResult.Success)
            assertEquals(1, oauth.refreshCalls)
            assertEquals("Bearer fresh", innerTube.lastAuthorization)
        }

    @Test
    fun `two concurrent requests share one refresh`() =
        runTest {
            seedSignedIn(expiresAt = clock.now - 1)
            val a = async { repo.history() }
            val b = async { repo.history() }
            a.await()
            b.await()
            assertEquals(1, oauth.refreshCalls)
        }

    @Test
    fun `a second 401 marks the session expired`() =
        runTest {
            seedSignedIn(expiresAt = clock.now + 60_000)
            innerTube.unauthorized = true
            repo.history()
            assertTrue(repo.state.value is AccountState.Expired)
        }

    @Test
    fun `refresh invalid_grant signs out and clears tokens`() =
        runTest {
            seedSignedIn(expiresAt = clock.now - 1)
            oauth.refreshFailsWithInvalidGrant = true
            val r = repo.history()
            assertEquals(AccountResult.NeedsSignIn, r)
            assertNull(store.get("refresh_token"))
            assertEquals(AccountState.SignedOut, repo.state.value)
        }

    @Test
    fun `a credentials failure reports UNAVAILABLE instead of throwing`() =
        runTest {
            credentials.failWith = CredentialsUnavailableException("no client pair")
            val states = repo.loginFlow().toList()
            assertEquals(LoginState.Error(LoginError.UNAVAILABLE), states.last())
        }

    @Test
    fun `invalid_client re-scrapes the pair and retries the refresh`() =
        runTest {
            seedSignedIn(expiresAt = clock.now - 1)
            oauth.invalidClientOnce = true
            val result = repo.history()
            assertTrue(result is AccountResult.Success)
            assertEquals(1, credentials.invalidations)
            assertEquals(2, oauth.refreshCalls)
        }

    @Test
    fun `sign out keeps the device id and the client pair`() =
        runTest {
            seedSignedIn(expiresAt = clock.now + 60_000)
            store.put("device_id", "0011223344556677")
            store.put("client_id", "id")
            store.put("client_secret", "secret")
            store.put("client_fetched_at", "1")
            repo.signOut()
            assertNull(store.get("access_token"))
            assertNull(store.get("refresh_token"))
            assertNull(store.get("account_json"))
            assertEquals("0011223344556677", store.get("device_id"))
            assertEquals("id", store.get("client_id"))
            assertEquals("secret", store.get("client_secret"))
        }

    @Test
    fun `sign out keeps local history untouched`() =
        runTest {
            seedSignedIn(expiresAt = clock.now + 60_000)
            repo.signOut()
            assertEquals(AccountState.SignedOut, repo.state.value)
            assertNull(store.get("account_json"))
        }

    @Test
    fun `parse empty maps to ParseEmpty`() =
        runTest {
            seedSignedIn(expiresAt = clock.now + 60_000)
            innerTube.videosJson = """{"contents":{}}"""
            assertEquals(AccountResult.ParseEmpty, repo.history())
        }

    @Test
    fun `notifications maps an unsupported endpoint to the empty state`() =
        runTest {
            seedSignedIn(expiresAt = clock.now + 60_000)
            innerTube.notificationsHttpCode = 400
            assertEquals(AccountResult.ParseEmpty, repo.notifications())
        }

    @Test
    fun `notifications keeps a server failure as a server error`() =
        runTest {
            seedSignedIn(expiresAt = clock.now + 60_000)
            innerTube.notificationsHttpCode = 503
            assertEquals(AccountResult.Server, repo.notifications())
        }

    private fun seedSignedIn(expiresAt: Long) {
        store.put("refresh_token", "r1")
        store.put("access_token", "stale")
        store.put("expires_at", expiresAt.toString())
        store.put("account_json", """{"name":"N","email":"e","avatarUrl":"a"}""")
    }
}

private class FakeSecretStore : SecretStore {
    private val values = mutableMapOf<String, String>()

    override fun get(key: String): String? = values[key]

    override fun put(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}

private class FakeCredentials : ClientCredentialsProvider {
    var failWith: IOException? = null
    var invalidations = 0

    override suspend fun get(): ClientCredentials {
        failWith?.let { throw it }
        return ClientCredentials("id", "secret")
    }

    override fun invalidate() {
        invalidations++
    }
}

private class FakeOAuthApi : OAuthApi {
    val pollResults = ArrayDeque<TokenPoll>()
    var refreshCalls = 0
    var refreshFailsWithInvalidGrant = false
    var invalidClientOnce = false

    override suspend fun requestDeviceCode(
        credentials: ClientCredentials,
        deviceId: String,
    ): DeviceCode = DeviceCode("dc", "ABCD-EFGH", "https://www.google.com/device", 1800, 5)

    override suspend fun pollToken(
        credentials: ClientCredentials,
        deviceCode: String,
    ): TokenPoll = pollResults.removeFirst()

    override suspend fun refresh(
        credentials: ClientCredentials,
        refreshToken: String,
    ): Tokens {
        refreshCalls++
        if (invalidClientOnce) {
            invalidClientOnce = false
            throw InvalidClientException("invalid_client")
        }
        if (refreshFailsWithInvalidGrant) throw InvalidGrantException("invalid_grant")
        return Tokens("fresh", null, 64_001)
    }
}

private class FakeInnerTubeService : InnerTubeService {
    var videosJson: String = resource("/spike/browse_FEhistory.json")
    var subscriptionsJson: String = resource("/spike/browse_FEsubscriptions.json")
    var profileJson: String = resource("/spike/accounts_list.json")
    var unauthorized = false
    var notificationsHttpCode: Int? = null
    var lastAuthorization: String? = null

    override suspend fun browseVideos(
        browseId: String,
        continuation: String?,
        accessToken: String,
    ): String {
        lastAuthorization = "Bearer $accessToken"
        if (unauthorized) throw InnerTubeHttpException(401, "unauthorized")
        if (browseId == "FEnotifications") {
            notificationsHttpCode?.let { throw InnerTubeHttpException(it, "notifications") }
        }
        return if (browseId == "FEsubscriptions") subscriptionsJson else videosJson
    }

    override suspend fun browseSubscriptions(
        continuation: String?,
        accessToken: String,
    ): String = browseVideos("FEsubscriptions", continuation, accessToken)

    override suspend fun fetchProfile(accessToken: String): String {
        lastAuthorization = "Bearer $accessToken"
        return profileJson
    }

    private companion object {
        fun resource(path: String): String = checkNotNull(object {}.javaClass.getResource(path)).readText()
    }
}

private class FakeClock(
    var now: Long = 1_000_000L,
)
