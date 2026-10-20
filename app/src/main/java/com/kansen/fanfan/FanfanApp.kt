package com.kansen.fanfan

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.kansen.fanfan.core.notifications.MentionNotifications
import com.kansen.fanfan.core.notifications.MentionWorker
import com.kansen.fanfan.di.AuthWarmup
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class FanfanApp : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject
    lateinit var authWarmup: AuthWarmup

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /** On-demand WorkManager init: the default initializer is removed in the manifest. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        authWarmup.warm()
        MentionNotifications.ensureChannel(this)
        scheduleMentionPolling()
    }

    /**
     * 15 minutes is the platform minimum for periodic work. The design accepts
     * Doze deferral; the on-device measurement decides whether an
     * `AlarmManager.setAndAllowWhileIdle` fallback is needed.
     */
    private fun scheduleMentionPolling() {
        val request = PeriodicWorkRequestBuilder<MentionWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Coil 3 needs an explicit network fetcher; reuse OkHttp and its disk cache. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .build()

    private companion object {
        const val WORK_NAME = "mention-poll"
    }
}
