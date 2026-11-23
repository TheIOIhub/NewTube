package com.example.newtube.ui.login

import com.example.newtube.model.LoginState
import com.example.newtube.repository.AccountRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val repo = mockk<AccountRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `start exposes the waiting code from the repository`() =
        runTest {
            every { repo.loginFlow() } returns
                flowOf(
                    LoginState.Waiting("SPPX-SFWV", "https://www.google.com/device", 1_800_000L),
                )
            val vm = LoginViewModel(repo)
            vm.start()
            advanceUntilIdle()
            assertEquals("SPPX-SFWV", (vm.state.value as LoginState.Waiting).userCode)
        }

    @Test
    fun `cancel cancels the collecting job`() =
        runTest {
            var collectorFinished = false
            every { repo.loginFlow() } returns
                flow {
                    try {
                        emit(LoginState.Requesting)
                        awaitCancellation()
                    } finally {
                        collectorFinished = true
                    }
                }
            val vm = LoginViewModel(repo)
            vm.start()
            vm.cancel()
            assertTrue(collectorFinished)
        }
}
