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
import com.kansen.fanfan.core.notifications.FanfouPollWorker
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
        schedulePoll()
    }

    /**
     * 15 minutes is the platform minimum for periodic work. The design accepts
     * Doze deferral; the on-device measurement decides whether an
     * `AlarmManager.setAndAllowWhileIdle` fallback is needed.
     */
    private fun schedulePoll() {
        val request = PeriodicWorkRequestBuilder<FanfouPollWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        val workManager = WorkManager.getInstance(this)
        // The worker class was renamed (MentionWorker -> FanfouPollWorker).
        // A work item scheduled under the old name still points at the class
        // that no longer exists: WorkManager cannot instantiate it and the
        // poll stays dead after an app update. Cancel that leftover and
        // enqueue under a fresh name.
        workManager.cancelUniqueWork(LEGACY_WORK_NAME)
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
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
        const val WORK_NAME = "fanfou-poll"

        /** The unique name used before the worker was renamed. */
        const val LEGACY_WORK_NAME = "mention-poll"
    }
}
