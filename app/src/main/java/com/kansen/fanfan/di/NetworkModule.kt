package com.kansen.fanfan.di

import com.kansen.fanfan.core.datastore.TokenStore
import com.kansen.fanfan.core.network.ApiErrorLogInterceptor
import com.kansen.fanfan.core.network.OAuthSigningInterceptor
import com.kansen.fanfan.core.network.UserAgentInterceptor
import com.kansen.fanfan.core.network.api.FanfouApi
import com.kansen.fanfan.core.network.api.FavoritesApi
import com.kansen.fanfan.core.network.api.DmApi
import com.kansen.fanfan.core.network.api.StatusesApi
import com.kansen.fanfan.core.network.api.UsersApi
import com.kansen.fanfan.core.network.token.TokenCache
import com.kansen.fanfan.core.common.JsonCodec
import com.kansen.fanfan.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import com.kansen.fanfan.core.network.api.PhotosApi

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = JsonCodec.json

    @Provides
    @Singleton
    fun provideOkHttpClient(
        userAgentInterceptor: UserAgentInterceptor,
        signingInterceptor: OAuthSigningInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(userAgentInterceptor)
        .addInterceptor(signingInterceptor)
        .apply {
            // Empirical debugging on device: BASIC logs the request line and
            // response status only — no Authorization header (xAuth carries
            // the password; never log it).
            val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            addInterceptor(logging)
            addInterceptor(ApiErrorLogInterceptor())
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.fanfou.com/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideFanfouApi(retrofit: Retrofit): FanfouApi = retrofit.create(FanfouApi::class.java)

    @Provides
    @Singleton
    fun provideStatusesApi(retrofit: Retrofit): StatusesApi = retrofit.create(StatusesApi::class.java)

    @Provides
    @Singleton
    fun provideFavoritesApi(retrofit: Retrofit): FavoritesApi = retrofit.create(FavoritesApi::class.java)

    @Provides
    @Singleton
    fun provideUsersApi(retrofit: Retrofit): UsersApi = retrofit.create(UsersApi::class.java)

    @Provides
    @Singleton
    fun providePhotosApi(retrofit: Retrofit): PhotosApi = retrofit.create(PhotosApi::class.java)

    @Provides
    @Singleton
    fun provideDmApi(retrofit: Retrofit): DmApi = retrofit.create(DmApi::class.java)

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Loads the persisted access token into TokenCache at app start so the
     * signing interceptor has it before any request is made.
     */
    @Provides
    @Singleton
    fun provideAuthWarmup(
        tokenStore: TokenStore,
        tokenCache: TokenCache,
        @ApplicationScope scope: CoroutineScope,
    ): AuthWarmup = AuthWarmup {
        scope.launch {
            try {
                tokenStore.warmCache()
            } catch (_: Throwable) {
                tokenCache.update(null, null)
            }
        }
    }
}

/** App-start hook that warms the token cache. Invoked by FanfanApp. */
fun interface AuthWarmup {
    fun warm()
}
