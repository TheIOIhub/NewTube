package com.example.newtube.di

import com.example.newtube.database.ChannelDao
import com.example.newtube.database.VideoDao
import com.example.newtube.implementation.ShortsCacheRepositoryImpl
import com.example.newtube.implementation.VideoRepositoryImpl
import com.example.newtube.innertube.YouTubeContentClient
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
            contentClient: YouTubeContentClient,
            dao: VideoDao,
            channelDao: ChannelDao,
            shortsCache: ShortsCacheRepository,
            settingsRepository: com.example.newtube.repository.SettingsRepository,
            historyRepository: com.example.newtube.repository.HistoryRepository,
            accountRepository: com.example.newtube.repository.AccountRepository,
        ): VideoRepository =
            VideoRepositoryImpl(
                contentClient,
                dao,
                channelDao,
                shortsCache,
                settingsRepository,
                historyRepository,
                accountRepository,
            )
    }
}
