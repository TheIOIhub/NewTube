package com.example.newtube.ui.settings

import com.example.newtube.model.AccountState
import com.example.newtube.model.AppSettings
import com.example.newtube.model.ThemeMode
import com.example.newtube.model.WatchHistoryEntry
import com.example.newtube.repository.AccountRepository
import com.example.newtube.repository.HistoryRepository
import com.example.newtube.repository.PlaybackPositionRepository
import com.example.newtube.repository.SettingsRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val repository: SettingsRepository = mockk(relaxed = true)
    private val history: HistoryRepository = mockk(relaxed = true)
    private val positions: PlaybackPositionRepository = mockk(relaxed = true)
    private val account: AccountRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { history.observeLocal(1) } returns flowOf(emptyList())
        every { account.state } returns MutableStateFlow(AccountState.SignedOut)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `settings flow is exposed with its stored values`() =
        runTest {
            every { repository.settings } returns flowOf(AppSettings(themeMode = ThemeMode.DARK))

            val viewModel = viewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(ThemeMode.DARK, viewModel.settings.value.themeMode)
        }

    @Test
    fun `setThemeMode delegates to the repository`() =
        runTest {
            every { repository.settings } returns flowOf(AppSettings())

            val viewModel = viewModel()
            viewModel.setThemeMode(ThemeMode.LIGHT)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { repository.setThemeMode(ThemeMode.LIGHT) }
        }

    @Test
    fun `history flag is exposed`() =
        runTest {
            every { history.observeLocal(1) } returns flowOf(listOf(entry()))
            every { repository.settings } returns flowOf(AppSettings())

            val viewModel = viewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.hasLocalHistory.value)
        }

    @Test
    fun `clearing history also clears resume positions`() =
        runTest {
            every { repository.settings } returns flowOf(AppSettings())

            val viewModel = viewModel()
            viewModel.clearHistory()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { history.clearLocal() }
            coVerify { positions.clearAll() }
        }

    @Test
    fun `every settings setter delegates`() =
        runTest {
            every { repository.settings } returns flowOf(AppSettings())

            val viewModel = viewModel()
            viewModel.setRecordHistory(false)
            viewModel.setUseRemoteHistory(false)
            viewModel.setDefaultQuality(1080)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { repository.setRecordHistory(false) }
            coVerify { repository.setUseRemoteHistory(false) }
            coVerify { repository.setDefaultQuality(1080) }
        }

    @Test
    fun `settings exposes the account state`() =
        runTest {
            every { repository.settings } returns flowOf(AppSettings())

            val viewModel = viewModel()

            assertEquals(AccountState.SignedOut, viewModel.account.value)
        }

    private fun viewModel() = SettingsViewModel(repository, history, positions, account)

    private fun entry() =
        WatchHistoryEntry(
            videoId = "v1",
            title = "Video",
            channelName = "Channel",
            channelId = "UC1",
            authorAvatarUrl = "",
            thumbnailUrl = "thumb",
            durationMs = 1,
            watchedAt = 1,
        )
}
