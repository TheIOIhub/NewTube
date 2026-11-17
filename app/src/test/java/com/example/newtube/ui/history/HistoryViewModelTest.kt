package com.example.newtube.ui.history

import com.example.newtube.model.WatchHistoryEntry
import com.example.newtube.repository.HistoryRepository
import com.example.newtube.ui.common.UiState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
class HistoryViewModelTest {
    private val repository: HistoryRepository = mockk()
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
    fun `history starts loading and then exposes rows`() =
        runTest {
            val flow = MutableStateFlow<List<WatchHistoryEntry>>(emptyList())
            every { repository.observeLocal() } returns flow

            val viewModel = HistoryViewModel(repository)
            assertTrue(viewModel.uiState.value is UiState.Loading)

            flow.value = listOf(entry("v1"))
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                UiState.Success(listOf(entry("v1"))),
                viewModel.uiState.first { it is UiState.Success },
            )
        }

    private fun entry(id: String) =
        WatchHistoryEntry(
            videoId = id,
            title = "Video $id",
            channelName = "Channel",
            channelId = "UC1",
            authorAvatarUrl = "",
            thumbnailUrl = "https://example.com/$id.jpg",
            durationMs = 30_000,
            watchedAt = 1,
        )
}
