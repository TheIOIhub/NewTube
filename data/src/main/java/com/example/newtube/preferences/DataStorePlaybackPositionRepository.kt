package com.example.newtube.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.newtube.repository.PlaybackPositionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackDataStore by preferencesDataStore(name = "playback_positions")

/**
 * Stores the last playback position per video id in DataStore.
 *
 * Android only keeps one DataStore instance per file, so the delegate lives at
 * file scope and the repository is a Hilt singleton.
 */
@Singleton
class DataStorePlaybackPositionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlaybackPositionRepository {
    override suspend fun positionFor(videoId: String): Long =
        context.playbackDataStore.data.first()[longPreferencesKey(videoId)] ?: 0L

    override suspend fun savePosition(videoId: String, positionMs: Long) {
        if (positionMs < 0) return
        context.playbackDataStore.edit { preferences ->
            preferences[longPreferencesKey(videoId)] = positionMs
        }
    }
}
