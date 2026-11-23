package com.example.newtube.ui.subscriptions

import com.example.newtube.model.AccountPage
import com.example.newtube.model.AccountResult
import com.example.newtube.model.AccountState
import com.example.newtube.model.RemoteVideo
import com.example.newtube.model.UserAccount
import com.example.newtube.repository.AccountRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class SubscriptionsViewModelTest {
    private val account = mockk<AccountRepository>(relaxed = true)
    private val vm by lazy { SubscriptionsViewModel(account) }
    private val signedIn = AccountState.SignedIn(UserAccount("N", "e", "a"))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun video(id: String) =
        RemoteVideo(id, "Video $id", "Channel", "UC1", "thumb", 60_000, "1.2M views", "1 day ago", false)

    @Test
    fun `signed out produces the CTA state`() =
        runTest {
            every { account.state } returns MutableStateFlow(AccountState.SignedOut)
            vm.load()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.signedOut)
        }

    @Test
    fun `first page fills the grid`() =
        runTest {
            every { account.state } returns MutableStateFlow(signedIn)
            coEvery { account.subscriptions(null) } returns
                AccountResult.Success(AccountPage(listOf(video("a")), "c2"))
            vm.load()
            advanceUntilIdle()
            assertEquals(1, vm.uiState.value.items.size)
            assertTrue(vm.uiState.value.hasMore)
        }

    @Test
    fun `loadMore appends without duplicates`() =
        runTest {
            every { account.state } returns MutableStateFlow(signedIn)
            coEvery { account.subscriptions(null) } returns
                AccountResult.Success(
                    AccountPage(listOf(video("a"), video("b")), "c2"),
                )
            coEvery { account.subscriptions("c2") } returns
                AccountResult.Success(
                    AccountPage(listOf(video("b"), video("c")), null),
                )
            vm.load()
            advanceUntilIdle()
            vm.loadMore()
            advanceUntilIdle()
            assertEquals(listOf("a", "b", "c"), vm.uiState.value.items.map { it.id })
        }

    @Test
    fun `needs sign in shows the expired banner`() =
        runTest {
            every { account.state } returns MutableStateFlow(AccountState.Expired(null))
            coEvery { account.subscriptions(null) } returns AccountResult.NeedsSignIn
            vm.load()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.expired)
        }

    @Test
    fun `network error exposes retry`() =
        runTest {
            every { account.state } returns MutableStateFlow(signedIn)
            coEvery { account.subscriptions(null) } returns AccountResult.Network
            vm.load()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.error)
            assertTrue(vm.uiState.value.items.isEmpty())
        }

    @Test
    fun `retry loads again after an error`() =
        runTest {
            every { account.state } returns MutableStateFlow(signedIn)
            coEvery { account.subscriptions(null) } returnsMany
                listOf(
                    AccountResult.Network,
                    AccountResult.Success(AccountPage(listOf(video("a")), null)),
                )
            vm.load()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.error)
            vm.retry()
            advanceUntilIdle()
            assertEquals(1, vm.uiState.value.items.size)
            assertFalse(vm.uiState.value.error)
        }
}
