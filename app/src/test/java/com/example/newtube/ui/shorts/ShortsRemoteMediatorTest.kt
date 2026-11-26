package com.example.newtube.ui.shorts

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.example.newtube.model.Video
import com.example.newtube.repository.ShortsCacheRepository
import com.example.newtube.repository.VideoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalPagingApi::class)
class ShortsRemoteMediatorTest {
    private val repository: VideoRepository = mockk()
    private val cache: ShortsCacheRepository = mockk(relaxed = true)
    private val mediator = ShortsRemoteMediator(repository, cache)

    @Test fun refreshWritesFirstPageToCache() =
        runTest {
            val feed = (1..10).map { video("$it") }
            coEvery { repository.getShorts(1) } returns feed

            val result = mediator.load(LoadType.REFRESH, emptyState())

            assertTrue(result is RemoteMediator.MediatorResult.Success)
            assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
            coVerify(exactly = 1) { repository.getShorts(1) }
            coVerify(exactly = 1) { cache.replaceFeed(feed) }
            coVerify(exactly = 0) { cache.appendFeed(any()) }
        }

    @Test fun appendUsesNextPageAndSetsEndOfPagination() =
        runTest {
            val firstPage = (1..10).map { video("$it") }
            coEvery { repository.getShorts(1) } returns firstPage
            coEvery { repository.getShorts(2) } returns emptyList()
            mediator.load(LoadType.REFRESH, emptyState())

            val state =
                PagingState(
                    pages = listOf(PagingSource.LoadResult.Page(firstPage, null, 2)),
                    anchorPosition = 9,
                    config = PAGING_CONFIG,
                    leadingPlaceholderCount = 0,
                )

            val result = mediator.load(LoadType.APPEND, state)

            assertTrue(result is RemoteMediator.MediatorResult.Success)
            assertEquals(true, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
            coVerify(exactly = 1) { repository.getShorts(2) }
            coVerify(exactly = 1) { cache.appendFeed(emptyList()) }
            coVerify(exactly = 1) { cache.replaceFeed(any()) }
        }

    @Test fun refreshFailureReturnsErrorWithoutClearingCache() =
        runTest {
            val failure = IOException("offline")
            coEvery { repository.getShorts(1) } throws failure

            val result = mediator.load(LoadType.REFRESH, emptyState())

            assertTrue(result is RemoteMediator.MediatorResult.Error)
            assertEquals(failure, (result as RemoteMediator.MediatorResult.Error).throwable)
            coVerify(exactly = 0) { cache.replaceFeed(any()) }
            coVerify(exactly = 0) { cache.appendFeed(any()) }
        }

    @Test fun appendFailureReturnsErrorWithoutClearingCache() =
        runTest {
            val firstPage = (1..10).map { video("$it") }
            val failure = IOException("offline")
            coEvery { repository.getShorts(1) } returns firstPage
            coEvery { repository.getShorts(2) } throws failure
            mediator.load(LoadType.REFRESH, emptyState())

            val state =
                PagingState(
                    pages = listOf(PagingSource.LoadResult.Page(firstPage, null, 2)),
                    anchorPosition = 9,
                    config = PAGING_CONFIG,
                    leadingPlaceholderCount = 0,
                )

            val result = mediator.load(LoadType.APPEND, state)

            assertTrue(result is RemoteMediator.MediatorResult.Error)
            assertEquals(failure, (result as RemoteMediator.MediatorResult.Error).throwable)
            coVerify(exactly = 0) { cache.appendFeed(any()) }
            coVerify(exactly = 1) { cache.replaceFeed(any()) }
        }

    private fun emptyState() =
        PagingState<Int, Video>(
            pages = emptyList(),
            anchorPosition = null,
            config = PAGING_CONFIG,
            leadingPlaceholderCount = 0,
        )

    private fun video(id: String) =
        Video(
            id = id,
            title = "Short $id",
            channelName = "Channel",
            channelId = "UC$id",
            thumbnailUrl = "https://example.com/$id.jpg",
            duration = 30L,
            views = 100L,
            uploadedAt = "1 day ago",
            isLive = false,
        )

    private companion object {
        val PAGING_CONFIG =
            PagingConfig(
                pageSize = 10,
                prefetchDistance = 2,
                initialLoadSize = 10,
                maxSize = 40,
            )
    }
}
