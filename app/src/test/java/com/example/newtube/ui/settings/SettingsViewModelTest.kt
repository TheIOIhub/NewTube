package com.example.newtube.ui.settings

import com.example.newtube.model.AppSettings
import com.example.newtube.model.ThemeMode
import com.example.newtube.repository.SettingsRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val repository: SettingsRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `settings flow is exposed with its stored values`() =
        runTest {
            every { repository.settings } returns flowOf(AppSettings(themeMode = ThemeMode.DARK))

            val viewModel = SettingsViewModel(repository)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(ThemeMode.DARK, viewModel.settings.value.themeMode)
        }

    @Test
    fun `setThemeMode delegates to the repository`() =
        runTest {
            every { repository.settings } returns flowOf(AppSettings())

            val viewModel = SettingsViewModel(repository)
            viewModel.setThemeMode(ThemeMode.LIGHT)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { repository.setThemeMode(ThemeMode.LIGHT) }
        }
}
