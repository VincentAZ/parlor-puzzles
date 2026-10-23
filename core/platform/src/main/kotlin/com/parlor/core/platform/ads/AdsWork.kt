package com.parlor.core.platform.ads

import android.content.Context
import androidx.work.*

/** WorkManager is reserved for the ad SDK. Billing uses Android's JobScheduler directly. */
class AdsWork(private val context: Context, private val permitted: () -> Boolean) {
    @Volatile private var manager: WorkManager? = null
    val configuration: Configuration get() = Configuration.Builder().setWorkerFactory(GatedAdWorkerFactory(permitted)).build()

    fun prepare() {
        // Calls the application's Configuration.Provider; this must precede MobileAds.initialize.
        manager = WorkManager.getInstance(context)
    }
    fun cancel() { manager?.cancelAllWork() }
}

/** A persisted offline ping must not bootstrap the ads SDK on a later Pro/unknown cold start. */
class GatedAdWorkerFactory(private val permitted: () -> Boolean) : WorkerFactory() {
    override fun createWorker(context: Context, workerClassName: String, parameters: WorkerParameters): ListenableWorker? =
        if (permitted()) null else SuppressedAdWorker(context, parameters)
}

class SuppressedAdWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = Result.success()
}
