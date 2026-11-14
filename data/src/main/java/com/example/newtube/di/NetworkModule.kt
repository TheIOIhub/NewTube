package com.example.newtube.di

import com.example.newtube.data.BuildConfig
import com.example.newtube.remote.InvidiousService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    /**
     * Public Invidious instances get shut down/blocked regularly, so the URL is
     * a build config field instead of a hardcoded constant:
     * `./gradlew assembleDebug -PinvidiousBaseUrl=https://yewtu.be/`
     * A trailing slash is required by Retrofit.
     */
    private val BASE_URL: String = BuildConfig.INVIDIOUS_BASE_URL

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level =
                        if (BuildConfig.DEBUG) {
                            HttpLoggingInterceptor.Level.BASIC
                        } else {
                            HttpLoggingInterceptor.Level.NONE
                        }
                },
            )
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .client(client)
            .build()
    }

    @Provides
    @Singleton
    fun provideInvidiousService(retrofit: Retrofit): InvidiousService = retrofit.create(InvidiousService::class.java)
}
