package com.example.newtube.di

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

internal const val PLAYBACK_CACHE_MAX_BYTES = 268_435_456L // 256 MiB

@Module
@InstallIn(SingletonComponent::class)
object PlaybackCacheModule {
    @Provides
    @Singleton
    fun provideSimpleCache(
        @ApplicationContext context: Context,
    ): SimpleCache =
        SimpleCache(
            File(context.filesDir, "media_cache"),
            LeastRecentlyUsedCacheEvictor(PLAYBACK_CACHE_MAX_BYTES),
            StandaloneDatabaseProvider(context),
        )

    @Provides
    @Singleton
    fun provideCacheDataSourceFactory(
        cache: SimpleCache,
        @ApplicationContext context: Context,
    ): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
}
