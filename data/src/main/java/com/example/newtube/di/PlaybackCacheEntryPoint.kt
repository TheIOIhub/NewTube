package com.example.newtube.di

import androidx.media3.datasource.cache.CacheDataSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlaybackCacheEntryPoint {
    fun cacheDataSourceFactory(): CacheDataSource.Factory
}
