package com.kansen.fanfan

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.kansen.fanfan.di.AuthWarmup
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FanfanApp : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var authWarmup: AuthWarmup

    override fun onCreate() {
        super.onCreate()
        authWarmup.warm()
    }

    /** Coil 3 needs an explicit network fetcher; reuse OkHttp and its disk cache. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
}
