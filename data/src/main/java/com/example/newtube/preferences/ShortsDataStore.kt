package com.example.newtube.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.shortsDataStore by preferencesDataStore(name = "shorts")

internal val KNOWN_CHANNELS_KEY = stringSetPreferencesKey("known_channels")

@Module
@InstallIn(SingletonComponent::class)
object ShortsDataStoreModule {
    @Provides
    @Singleton
    fun provideShortsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.shortsDataStore
}
