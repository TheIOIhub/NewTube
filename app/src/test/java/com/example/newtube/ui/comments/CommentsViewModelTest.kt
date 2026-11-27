package com.example.newtube.ui.comments

import com.example.newtube.model.Comment
import com.example.newtube.model.CommentPage
import com.example.newtube.repository.VideoRepository
import com.example.newtube.ui.common.UiState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CommentsViewModelTest {
    private val repository: VideoRepository = mockk()
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
    fun `first page exposes comments, count and hasMore`() =
        runTest {
            val comment = comment("c1")
            coEvery { repository.getComments("v1", null) } returns
                CommentPage(listOf(comment), continuation = "next", totalCount = 10)

            val viewModel = CommentsViewModel(repository)
            viewModel.load("v1")
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is UiState.Success)
            val data = (state as UiState.Success).data
            assertEquals(listOf(comment), data.comments)
            assertEquals(10L, data.totalCount)
            assertTrue(data.hasMore)
            assertFalse(viewModel.isRefreshing.value)
        }

    @Test
    fun `a failed retry keeps the loaded comments`() =
        runTest {
            val comment = comment("c1")
            coEvery { repository.getComments("v1", null) } returns
                CommentPage(listOf(comment), continuation = null, totalCount = 1)

            val viewModel = CommentsViewModel(repository)
            viewModel.load("v1")
            testDispatcher.scheduler.advanceUntilIdle()

            coEvery { repository.getComments("v1", null) } throws IOException("offline")
            viewModel.retry()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(comment), (viewModel.uiState.value as UiState.Success).data.comments)
            assertFalse(viewModel.isRefreshing.value)
        }

    @Test
    fun `load more appends the next page`() =
        runTest {
            val first = comment("c1")
            val second = comment("c2")
            coEvery { repository.getComments("v1", null) } returns
                CommentPage(listOf(first), continuation = "next", totalCount = 2)
            coEvery { repository.getComments("v1", "next") } returns
                CommentPage(listOf(second), continuation = null, totalCount = 2)

            val viewModel = CommentsViewModel(repository)
            viewModel.load("v1")
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.loadMore()
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value as UiState.Success
            assertEquals(listOf(first, second), state.data.comments)
            assertTrue(!state.data.hasMore)
        }

    private fun comment(id: String) =
        Comment(
            id = id,
            author = "Author",
            authorChannelId = "UC1",
            authorAvatarUrl = "",
            content = "Comment $id",
            likeCount = 3,
            publishedText = "1 day ago",
            isPinned = false,
        )
}
