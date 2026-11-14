package com.example.newtube.di

import com.example.newtube.preferences.DataStorePlaybackPositionRepository
import com.example.newtube.repository.PlaybackPositionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {
    @Binds
    @Singleton
    abstract fun bindPlaybackPositionRepository(
        impl: DataStorePlaybackPositionRepository,
    ): PlaybackPositionRepository
}
