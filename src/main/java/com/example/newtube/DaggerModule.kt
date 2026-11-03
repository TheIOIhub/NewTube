package com.example.newtube

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DaggerModule {

    @Provides
    @Singleton
    fun provideHelloString(): String = "Hello, Hilt!"
}
