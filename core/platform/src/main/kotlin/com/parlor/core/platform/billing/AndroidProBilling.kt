package com.parlor.core.platform.billing

import android.app.Activity
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.*

interface ProBillingOwner { val billing: AndroidProBilling }

class AndroidProBilling(context: Context, publicKey: String, productId: String) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val verifier = PlayPurchaseVerifier(publicKey,context.packageName,productId)
    private val gateway: PlayBillingGateway = PlayBillingGateway(context,productId,
        updated = { code, receipts -> scope.launch {
            ready.await(); coordinator.purchasesUpdated(code,receipts)
            if(code == StoreCode.ALREADY_OWNED) coordinator.refresh(restoring = true)
        } }, disconnected = { requestRefresh() })
    private val coordinator: ProBilling = ProBilling(gateway,DevicePurchaseStore(context),verifier,verifier.configured,::scheduleRetry)
    private val ready: Deferred<Unit> = scope.async { coordinator.loadLocal() }
    val state = coordinator.state
    val hasPro get() = coordinator.hasPro
    val configured get() = verifier.configured
    suspend fun awaitLocalState() { ready.await() }
    private var refreshJob: Job? = null
    private var foregroundJob: Job? = null
    private var registered = false
    private val network = context.getSystemService(ConnectivityManager::class.java)
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { requestRefresh() }
    }
    @Synchronized fun requestRefresh(restoring: Boolean = false) {
        if(refreshJob?.isActive == true) return
        refreshJob = scope.launch { ready.await();coordinator.refresh(restoring) }
    }
    fun foreground(active: Boolean) {
        foregroundJob?.cancel();foregroundJob = null
        if(!active) {
            if(registered) { network.unregisterNetworkCallback(callback);registered = false }
            return
        }
        requestRefresh()
        if(configured && !registered) {
            try { network.registerDefaultNetworkCallback(callback);registered = true } catch(_: SecurityException) { /* Manual retry and foreground retry remain available. */ }
        }
        foregroundJob = scope.launch {
            while(isActive) {
                delay(30_000)
                if(coordinator.needsRetry) requestRefresh()
            }
        }
    }
    fun buy(activity: Activity) { scope.launch { ready.await();coordinator.buy { gateway.launch(activity,it) } } }
    suspend fun retryInBackground(): Boolean { ready.await();coordinator.refresh();return coordinator.needsRetry }
    private fun scheduleRetry() {
        if(!configured) return
        // A platform scheduling quota must not block Home or discard the durable receipt.
        // Foreground/network reconciliation retries even when scheduling is unavailable.
        try {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if(scheduler.getPendingJob(RETRY_JOB) != null) return
            val job = JobInfo.Builder(RETRY_JOB,ComponentName(context,BillingRetryJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setMinimumLatency(30_000)
                .setBackoffCriteria(30_000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build()
            scheduler.schedule(job)
        } catch(_: RuntimeException) { }
    }
    companion object { const val RETRY_JOB = 1977068420 }
}

/** Platform-managed background retries; the app acquires no wake lock. */
class BillingRetryJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var job: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        job = scope.launch {
            val owner = application as? ProBillingOwner
            val retry = try { owner?.billing?.retryInBackground() ?: false }
                catch(cancelled: CancellationException) { throw cancelled }
                catch(_: Exception) { true }
            jobFinished(params,retry)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { job?.cancel();return true }
    override fun onDestroy() { scope.cancel();super.onDestroy() }
}
