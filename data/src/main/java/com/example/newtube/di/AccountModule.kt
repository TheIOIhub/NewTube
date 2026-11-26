package com.example.newtube.di

import android.content.Context
import com.example.newtube.account.AccountRepositoryImpl
import com.example.newtube.account.AccountStoreKeys
import com.example.newtube.account.ClientCredentialsProvider
import com.example.newtube.account.EncryptedPrefsSecretStore
import com.example.newtube.account.OAuthApi
import com.example.newtube.account.SecretStore
import com.example.newtube.account.YouTubeOAuthApi
import com.example.newtube.account.YouTubeTvCredentialsProvider
import com.example.newtube.account.YouTubeTvUserAgent
import com.example.newtube.account.parseStoredAccount
import com.example.newtube.data.BuildConfig
import com.example.newtube.innertube.InnerTubeService
import com.example.newtube.innertube.YouTubeInnerTubeService
import com.example.newtube.repository.AccountRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.security.SecureRandom
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AccountModule {
    private const val OAUTH_CLIENT = "oauth"
    private const val INNERTUBE_CLIENT = "innertube"
    private const val DEVICE_ID_QUALIFIER = "deviceId"

    @Provides
    @Singleton
    fun provideSecretStore(
        @ApplicationContext context: Context,
    ): SecretStore = EncryptedPrefsSecretStore(context)

    /** Token client: no logging interceptor, bodies carry the refresh token. */
    @Provides
    @Singleton
    @Named(OAUTH_CLIENT)
    fun provideOAuthClient(): OkHttpClient = OkHttpClient()

    @Provides
    @Singleton
    @Named(INNERTUBE_CLIENT)
    fun provideInnerTubeClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                            redactHeader("Authorization")
                            redactHeader("X-Goog-Pageid")
                        },
                    )
                }
            }.build()

    @Provides
    @Singleton
    fun provideOAuthApi(
        @Named(OAUTH_CLIENT) client: OkHttpClient,
    ): OAuthApi = YouTubeOAuthApi(client)

    @Provides
    @Singleton
    fun provideInnerTubeService(
        @Named(INNERTUBE_CLIENT) client: OkHttpClient,
        store: SecretStore,
    ): InnerTubeService =
        YouTubeInnerTubeService(
            client = client,
            pageIdProvider = {
                parseStoredAccount(store.get(AccountStoreKeys.ACCOUNT_JSON))?.pageId
            },
        )

    @Provides
    @Singleton
    fun provideClientCredentialsProvider(
        store: SecretStore,
        @Named(OAUTH_CLIENT) client: OkHttpClient,
    ): ClientCredentialsProvider =
        YouTubeTvCredentialsProvider(
            fetcher = { url -> fetchText(client, url) },
            store = store,
            clock = System::currentTimeMillis,
        )

    @Provides
    @Singleton
    @Named(DEVICE_ID_QUALIFIER)
    fun provideDeviceId(store: SecretStore): String {
        store.get(AccountStoreKeys.DEVICE_ID)?.let { return it }
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val id = bytes.joinToString("") { "%02x".format(it) }
        store.put(AccountStoreKeys.DEVICE_ID, id)
        return id
    }

    @Provides
    @Singleton
    fun provideAccountRepository(
        credentials: ClientCredentialsProvider,
        oauth: OAuthApi,
        innerTube: InnerTubeService,
        store: SecretStore,
        @Named(DEVICE_ID_QUALIFIER) deviceId: String,
    ): AccountRepository =
        AccountRepositoryImpl(
            credentials = credentials,
            oauth = oauth,
            innerTube = innerTube,
            store = store,
            clock = System::currentTimeMillis,
            deviceId = deviceId,
        )

    private fun fetchText(
        client: OkHttpClient,
        url: String,
    ): String {
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", YouTubeTvUserAgent.VALUE)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
        return client
            .newCall(request)
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    throw IOException("GET $url answered HTTP ${response.code}")
                }
                response.body?.string().orEmpty()
            }
    }
}
