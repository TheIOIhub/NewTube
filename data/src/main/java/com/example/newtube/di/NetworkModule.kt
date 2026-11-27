package com.example.newtube.di

import android.content.Context
import com.example.newtube.data.BuildConfig
import com.example.newtube.innertube.ContentConfigRepository
import com.example.newtube.innertube.DataStoreContentConfigRepository
import com.example.newtube.innertube.YouTubeContentClient
import com.example.newtube.innertube.contentConfigStore
import com.example.newtube.remote.InvidiousService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
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
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level =
                        if (BuildConfig.DEBUG) {
                            HttpLoggingInterceptor.Level.BASIC
                        } else {
                            HttpLoggingInterceptor.Level.NONE
                        }
                },
            ).build()

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

    @Provides
    @Singleton
    fun provideContentConfigRepository(
        client: OkHttpClient,
        @ApplicationContext context: Context,
    ): ContentConfigRepository =
        DataStoreContentConfigRepository(
            client = client,
            dataStore = context.contentConfigStore,
            configUrl = BuildConfig.CONTENT_CONFIG_URL,
        )

    @Provides
    @Singleton
    fun provideYouTubeContentClient(
        client: OkHttpClient,
        config: ContentConfigRepository,
    ): YouTubeContentClient = YouTubeContentClient(client, config)
}
