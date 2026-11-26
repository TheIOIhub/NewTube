package com.example.newtube.ui.shorts

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.example.newtube.model.Video
import com.example.newtube.repository.ShortsCacheRepository
import com.example.newtube.repository.VideoRepository
import javax.inject.Inject

/**
 * Loads Shorts pages into the Room feed cache. The paging source reads only
 * from that cache, so every network page is written here before Paging can
 * observe it. Room invalidates the source when `shorts_feed` changes.
 */
@OptIn(ExperimentalPagingApi::class)
class ShortsRemoteMediator @Inject constructor(
    private val repository: VideoRepository,
    private val shortsCache: ShortsCacheRepository,
) : RemoteMediator<Int, Video>() {
    /** Next network page, advanced only after a successful load. */
    private var nextPage = 1

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, Video>,
    ): MediatorResult {
        val page =
            when (loadType) {
                LoadType.REFRESH -> 1
                // Room paging keys are row offsets (10, 20, ...), not network
                // page numbers. Always use our own sequential counter, including
                // on recovery paths after a failed REFRESH.
                LoadType.APPEND -> nextPage
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            }

        val videos =
            try {
                repository.getShorts(page)
            } catch (e: Exception) {
                // A failed page must never clear the cache: the already loaded
                // clips stay browsable and the next hint retries this page.
                return MediatorResult.Error(e)
            }

        when (loadType) {
            LoadType.REFRESH ->
                // An empty refresh must not wipe a usable cached feed either.
                if (videos.isNotEmpty()) shortsCache.replaceFeed(videos)
            LoadType.APPEND -> shortsCache.appendFeed(videos)
            LoadType.PREPEND -> Unit
        }
        nextPage = page + 1
        return MediatorResult.Success(endOfPaginationReached = videos.isEmpty())
    }
}
