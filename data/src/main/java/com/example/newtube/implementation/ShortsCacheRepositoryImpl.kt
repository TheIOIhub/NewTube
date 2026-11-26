package com.example.newtube.implementation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.newtube.database.ShortsFeedDao
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

    override suspend fun replaceFeed(videos: List<Video>) {
        val cachedAt = System.currentTimeMillis()
        dao.replaceAll(
            videos.mapIndexed { index, video ->
                video.toShortsFeedEntity(position = index, cachedAt = cachedAt)
            },
        )
    }

    override suspend fun knownChannelIds(): Set<String> =
        dataStore.data.first()[KNOWN_CHANNELS_KEY].orEmpty()

    override suspend fun rememberChannelIds(ids: Set<String>) {
        dataStore.edit { prefs -> prefs[KNOWN_CHANNELS_KEY] = ids }
    }
}
