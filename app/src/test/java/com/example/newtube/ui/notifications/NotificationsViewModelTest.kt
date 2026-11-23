package com.example.newtube.ui.notifications

import com.example.newtube.model.AccountResult
import com.example.newtube.model.AccountState
import com.example.newtube.model.RemoteVideo
import com.example.newtube.model.UserAccount
import com.example.newtube.repository.AccountRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
class NotificationsViewModelTest {
    private val account = mockk<AccountRepository>(relaxed = true)
    private val vm by lazy { NotificationsViewModel(account) }
    private val item = RemoteVideo("v1", "T", "C", "UC1", "", 0, "", "1 day ago", false)
    private val signedIn = AccountState.SignedIn(UserAccount("N", "e", "a"))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `signed out shows the CTA`() =
        runTest {
            every { account.state } returns MutableStateFlow(AccountState.SignedOut)
            vm.load()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.signedOut)
        }

    @Test
    fun `items render from one request`() =
        runTest {
            every { account.state } returns MutableStateFlow(signedIn)
            coEvery { account.notifications() } returns AccountResult.Success(listOf(item))
            vm.load()
            advanceUntilIdle()
            assertEquals(1, vm.uiState.value.items.size)
            coVerify(exactly = 1) { account.notifications() }
        }

    @Test
    fun `parse empty becomes the empty state`() =
        runTest {
            every { account.state } returns MutableStateFlow(signedIn)
            coEvery { account.notifications() } returns AccountResult.ParseEmpty
            vm.load()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.items.isEmpty())
            assertFalse(vm.uiState.value.error)
        }

    @Test
    fun `network failure exposes retry`() =
        runTest {
            every { account.state } returns MutableStateFlow(signedIn)
            coEvery { account.notifications() } returns AccountResult.Network
            vm.load()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.error)
        }
}
