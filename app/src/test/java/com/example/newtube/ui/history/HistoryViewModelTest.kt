package com.example.newtube.ui.history

import com.example.newtube.model.AccountPage
import com.example.newtube.model.AccountResult
import com.example.newtube.model.AccountState
import com.example.newtube.model.AppSettings
import com.example.newtube.model.RemoteVideo
import com.example.newtube.model.UserAccount
import com.example.newtube.model.WatchHistoryEntry
import com.example.newtube.repository.AccountRepository
import com.example.newtube.repository.HistoryRepository
import com.example.newtube.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val local: HistoryRepository = mockk(relaxed = true)
    private val account: AccountRepository = mockk(relaxed = true)
    private val settings: SettingsRepository = mockk(relaxed = true)
    private val entry = WatchHistoryEntry("v1", "T", "C", "UC1", "", "", 60_000, 1)
    private val remote = RemoteVideo("v2", "R", "Ch", "UC2", "thumb", 60_000, "1.2M views", "1 day ago", false)
    private val signedIn = AccountState.SignedIn(UserAccount("N", "e", "a"))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { local.observeLocal() } returns flowOf(listOf(entry))
        every { settings.settings } returns flowOf(AppSettings())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `signed out uses local history`() =
        runTest {
            every { account.state } returns MutableStateFlow(AccountState.SignedOut)
            val vm = HistoryViewModel(local, account, settings)
            advanceUntilIdle()
            assertFalse(vm.uiState.value.remoteSource)
            assertEquals(1, vm.uiState.value.local.size)
        }

    @Test
    fun `signed in with remote history off uses local history`() =
        runTest {
            every { account.state } returns MutableStateFlow(signedIn)
            every { settings.settings } returns flowOf(AppSettings(useRemoteHistory = false))
            val vm = HistoryViewModel(local, account, settings)
            advanceUntilIdle()
            assertFalse(vm.uiState.value.remoteSource)
        }

    @Test
    fun `signed in with remote history on loads the first page`() =
        runTest {
            every { account.state } returns MutableStateFlow(signedIn)
            coEvery { account.history(null) } returns AccountResult.Success(AccountPage(listOf(remote), null))
            val vm = HistoryViewModel(local, account, settings)
            vm.retry()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.remoteSource)
            assertEquals(listOf("v2"), vm.uiState.value.remote.map { it.id })
        }

    @Test
    fun `remote history failure falls back to local with the banner`() =
        runTest {
            every { account.state } returns MutableStateFlow(AccountState.Expired(UserAccount("N", "e", "a")))
            coEvery { account.history(null) } returns AccountResult.NeedsSignIn
            val vm = HistoryViewModel(local, account, settings)
            vm.retry()
            advanceUntilIdle()
            assertFalse(vm.uiState.value.remoteSource)
            assertEquals(1, vm.uiState.value.local.size)
            assertTrue(vm.uiState.value.expired)
        }

    @Test
    fun `remote history is not written to Room`() =
        runTest {
            every { account.state } returns MutableStateFlow(signedIn)
            coEvery { account.history(null) } returns AccountResult.Success(AccountPage(listOf(remote), null))
            val vm = HistoryViewModel(local, account, settings)
            vm.retry()
            advanceUntilIdle()
            coVerify(exactly = 0) { local.record(any()) }
        }

    @Test
    fun `an expired session is not refetched after a settings emission`() =
        runTest {
            val settingsFlow = MutableStateFlow(AppSettings())
            every { local.observeLocal() } returns flowOf(listOf(entry))
            every { account.state } returns MutableStateFlow(AccountState.Expired(UserAccount("N", "e", "a")))
            every { settings.settings } returns settingsFlow
            coEvery { account.history(null) } returns AccountResult.NeedsSignIn
            val vm = HistoryViewModel(local, account, settings)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.expired)
            coVerify(exactly = 1) { account.history(null) }

            settingsFlow.value = AppSettings(defaultQuality = 1080)
            advanceUntilIdle()

            coVerify(exactly = 1) { account.history(null) }
        }

    @Test
    fun `signing in again after an expired session reloads the remote feed`() =
        runTest {
            val accountFlow = MutableStateFlow<AccountState>(AccountState.Expired(UserAccount("N", "e", "a")))
            every { account.state } returns accountFlow
            coEvery { account.history(null) } returnsMany
                listOf(
                    AccountResult.NeedsSignIn,
                    AccountResult.Success(AccountPage(listOf(remote), null)),
                )
            val vm = HistoryViewModel(local, account, settings)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.expired)
            coVerify(exactly = 1) { account.history(null) }

            accountFlow.value = signedIn
            advanceUntilIdle()

            coVerify(exactly = 2) { account.history(null) }
            assertTrue(vm.uiState.value.remoteSource)
            assertFalse(vm.uiState.value.expired)
        }
}
