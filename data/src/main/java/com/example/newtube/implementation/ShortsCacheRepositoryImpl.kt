package com.example.newtube.implementation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.newtube.database.ShortsFeedDao
import com.example.newtube.database.ShortsFeedEntity
import com.example.newtube.database.toShortsFeedEntity
import com.example.newtube.database.toVideo
import com.example.newtube.model.Video
import com.example.newtube.preferences.KNOWN_CHANNELS_KEY
import com.example.newtube.repository.ShortsCacheRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortsCacheRepositoryImpl @Inject constructor(
    private val dao: ShortsFeedDao,
    private val dataStore: DataStore<Preferences>,
) : ShortsCacheRepository {
    override fun observeFeed(): Flow<List<Video>> =
        dao.observeAll().map { entities -> entities.map { it.toVideo() } }

    override fun pagingSource(): PagingSource<Int, Video> =
        // The DAO stays inside the data module: only mapped domain models escape.
        MappedShortsPagingSource(dao.pagingSource())

    override suspend fun replaceFeed(videos: List<Video>) {
        val cachedAt = System.currentTimeMillis()
        dao.replaceAll(
            videos.mapIndexed { index, video ->
                video.toShortsFeedEntity(position = index, cachedAt = cachedAt)
            },
        )
    }

    override suspend fun appendFeed(videos: List<Video>) {
        if (videos.isEmpty()) return
        // Continue after the current maximum; an empty cache starts at zero.
        val nextPosition = (dao.observeAll().first().maxOfOrNull { it.position } ?: -1) + 1
        val cachedAt = System.currentTimeMillis()
        dao.insertAll(
            videos.mapIndexed { offset, video ->
                video.toShortsFeedEntity(position = nextPosition + offset, cachedAt = cachedAt)
            },
        )
    }

    override suspend fun knownChannelIds(): Set<String> =
        dataStore.data.first()[KNOWN_CHANNELS_KEY].orEmpty()

    override suspend fun rememberChannelIds(ids: Set<String>) {
        dataStore.edit { prefs -> prefs[KNOWN_CHANNELS_KEY] = ids }
    }

    override suspend fun clearKnownChannelIds() {
        dataStore.edit { prefs -> prefs.remove(KNOWN_CHANNELS_KEY) }
    }
}

/** Maps Room's rows to domain videos without exposing the DAO to callers. */
private class MappedShortsPagingSource(
    private val delegate: PagingSource<Int, ShortsFeedEntity>,
) : PagingSource<Int, Video>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Video> =
        when (val result = delegate.load(params)) {
            is LoadResult.Page ->
                LoadResult.Page(
                    data = result.data.map { it.toVideo() },
                    prevKey = result.prevKey,
                    nextKey = result.nextKey,
                    itemsBefore = result.itemsBefore,
                    itemsAfter = result.itemsAfter,
                )
            is LoadResult.Error -> LoadResult.Error(result.throwable)
            is LoadResult.Invalid -> LoadResult.Invalid()
        }

    override fun getRefreshKey(state: PagingState<Int, Video>): Int? {
        val anchor = state.anchorPosition ?: return null
        // Placeholders are disabled for this feed, so there are no leading ones.
        var start = 0
        for (page in state.pages) {
            if (anchor in start until start + page.data.size) {
                return (page.prevKey ?: 0) + (anchor - start)
            }
            start += page.data.size
        }
        return null
    }
}
