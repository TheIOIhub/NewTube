package com.example.newtube.di

import com.example.newtube.database.ChannelDao
import com.example.newtube.database.VideoDao
import com.example.newtube.implementation.ShortsCacheRepositoryImpl
import com.example.newtube.implementation.VideoRepositoryImpl
import com.example.newtube.remote.InvidiousService
import com.example.newtube.repository.ShortsCacheRepository
import com.example.newtube.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindShortsCacheRepository(impl: ShortsCacheRepositoryImpl): ShortsCacheRepository

    companion object {
        @Provides
        @Singleton
        fun provideVideoRepository(
            service: InvidiousService,
            dao: VideoDao,
            channelDao: ChannelDao,
        ): VideoRepository = VideoRepositoryImpl(service, dao, channelDao)
    }
}
