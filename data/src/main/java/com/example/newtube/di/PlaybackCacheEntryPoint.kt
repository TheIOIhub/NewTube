package com.example.newtube.di

import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlaybackCacheEntryPoint {
    fun cacheDataSourceFactory(): CacheDataSource.Factory
}
