package com.example.newtube.di

import com.example.newtube.remote.InvidiousService
import com.example.newtube.database.VideoDao
import com.example.newtube.repository.VideoRepository
import com.example.newtube.implementation.VideoRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideVideoRepository(
        service: InvidiousService,
        dao: VideoDao
    ): VideoRepository = VideoRepositoryImpl(service, dao)
}
