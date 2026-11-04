package com.example.newtube.di

import android.content.Context
import androidx.room.Room
import com.example.newtube.database.NewTubeDatabase
import com.example.newtube.database.VideoDao
import com.example.newtube.database.ChannelDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NewTubeDatabase {
        return Room.databaseBuilder(
            context,
            NewTubeDatabase::class.java,
            "newtube_db"
        ).build()
    }

    @Provides
    fun provideVideoDao(db: NewTubeDatabase): VideoDao = db.videoDao()

    @Provides
    fun provideChannelDao(db: NewTubeDatabase): ChannelDao = db.channelDao()
}
