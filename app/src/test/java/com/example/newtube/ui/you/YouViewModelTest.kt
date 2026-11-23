package com.example.newtube.ui.you

import com.example.newtube.model.AccountState
import com.example.newtube.model.UserAccount
import com.example.newtube.repository.AccountRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class YouViewModelTest {
    private val account = mockk<AccountRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `signed out exposes SignedOut`() =
        runTest {
            every { account.state } returns MutableStateFlow(AccountState.SignedOut)
            val vm = YouViewModel(account)
            assertEquals(AccountState.SignedOut, vm.account.value)
        }

    @Test
    fun `signed in exposes the profile`() =
        runTest {
            val user = UserAccount("N", "e", "a")
            every { account.state } returns MutableStateFlow(AccountState.SignedIn(user))
            val vm = YouViewModel(account)
            assertEquals(user, (vm.account.value as AccountState.SignedIn).account)
        }

    @Test
    fun `signOut delegates to the repository`() =
        runTest {
            every { account.state } returns MutableStateFlow(AccountState.SignedOut)
            val vm = YouViewModel(account)
            vm.signOut()
            coVerify(exactly = 1) { account.signOut() }
        }
}
