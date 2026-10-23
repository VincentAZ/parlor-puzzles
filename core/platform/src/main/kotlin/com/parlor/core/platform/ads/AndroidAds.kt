package com.parlor.core.platform.ads

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.os.SystemClock
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.banner.*
import com.google.android.libraries.ads.mobile.sdk.common.*
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.interstitial.*
import com.google.android.libraries.ads.mobile.sdk.rewarded.*
import com.parlor.core.platform.billing.ProState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.lang.ref.WeakReference
import java.time.Instant
import java.time.ZoneId

data class AdUnits(val app: String, val banner: String, val interstitial: String, val rewarded: String) {
    val configured get() = app.matches(Regex("ca-app-pub-[0-9]{16}~[0-9]{10}")) &&
        listOf(banner, interstitial, rewarded).all { it.matches(Regex("ca-app-pub-[0-9]{16}/[0-9]{10}")) }
}

/** One application-owned coordinator. Constructing it neither initializes GMA nor requests an ad. */
class AndroidAds(context: Context, private val units: AdUnits, private val billing: StateFlow<ProState>, private val work: AdsWork,
                 consentGateway: ConsentGateway? = null) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var host = WeakReference<Activity>(null)
    @Volatile private var foreground = false
    private var placement = AdPlacement.ONBOARDING
    private val mutablePlacement = MutableStateFlow(placement)
    val currentPlacement = mutablePlacement.asStateFlow()
    private val coordinator = ConsentCoordinator(consentGateway ?: UmpConsentGateway(context) { host.get() }, scope)
    val consent = coordinator.state
    private val banners = mutableSetOf<BannerHandle>()
    private var preloadLease: AdGate.Lease? = null
    private var levelAttempt: String? = null
    private var takenForAttempt: String? = null
    private val fullscreen = mutableSetOf<InterstitialHandle>()
    private val rewarded = mutableSetOf<RewardedHandle>()
    private var lastFullscreenClosed: Long? = null
    val launchedElapsedMs = SystemClock.elapsedRealtime()
    @Volatile var bootMarker: String? = null
        private set
    private val gate = AdGate(scope, initialize = { permitted ->
        withContext(Dispatchers.IO) {
            if (!units.configured || !permitted()) return@withContext false
            bootMarker = DeviceAdClock(this@AndroidAds.context).bootMarker()
            work.prepare()
            if (!permitted()) return@withContext false
            val requests = RequestConfiguration.Builder()
                .setMaxAdContentRating(RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_T).build()
            MobileAds.initialize(this@AndroidAds.context, InitializationConfig.Builder(units.app)
                .setRequestConfiguration(requests).disableMediationAdapterInitialization().build())
            true
        }
    }, livePermission = { units.configured && foreground && billing.value.permitsAds && consent.value.permitsAds },
        cancelBackgroundWork = { work.cancel() })
    val enabled = gate.enabled
    val permitsBackgroundWork get() = gate.permitsBackgroundWork
    val mayPresentResultsAd get() = foreground && placement == AdPlacement.RESULTS &&
        billing.value.permitsAds && consent.value.permitsAds && rewarded.isEmpty() &&
        (lastFullscreenClosed?.let { SystemClock.elapsedRealtime() - it >= 120_000 } != false)
    val interstitialReady get() = preloadLease?.valid == true && InterstitialAdPreloader.isAdAvailable(units.interstitial)
    val mayPresentReward get() = enabled.value && foreground && billing.value.permitsAds && consent.value.permitsAds &&
        placement in setOf(AdPlacement.PLAY, AdPlacement.LEVEL_SELECT, AdPlacement.DAILY_ENTRY) && fullscreen.isEmpty()

    init {
        scope.launch { billing.collect {
            if (!it.permitsAds) {
                fullscreen.toList().forEach { ad -> ad.close() }
                rewarded.toList().forEach { ad -> ad.close() }
            }
            update()
        } }
        scope.launch { consent.collect { updateGate() } }
        scope.launch { enabled.collect { if (it) preload() } }
    }

    fun foreground(activity: Activity, active: Boolean) {
        if (active) host = WeakReference(activity)
        else if (host.get() !== activity) return
        foreground = active
        update()
    }
    fun detach(activity: Activity) {
        if (host.get() !== activity) return
        foreground = false; host.clear(); coordinator.presentationLost(); update()
        fullscreen.toList().forEach { it.close() }
        rewarded.toList().forEach { it.close() }
    }
    fun close() {
        foreground = false; host.clear()
        gate.update(false, false, false, false)
        fullscreen.toList().forEach { it.close() }
        rewarded.toList().forEach { it.close() }
        scope.cancel()
    }
    fun placement(value: AdPlacement) {
        if (placement != value) {
            banners.toList().forEach { it.close() }
            rewarded.toList().forEach { it.close() }
        }
        placement = value
        mutablePlacement.value = value
        if (value != AdPlacement.PLAY && value != AdPlacement.RESULTS) { preloadLease?.close(); levelAttempt = null }
        update()
    }
    fun privacyOptions() { coordinator.privacyOptions(); updateGate() }
    fun retryConsent() { coordinator.retry(); updateGate() }
    private fun update() {
        coordinator.update(units.configured && billing.value.permitsAds, foreground, placement)
        updateGate()
    }
    private fun updateGate() {
        gate.update(billing.value.permitsAds, consent.value.permitsAds, foreground, consent.value.showing)
        if (!consent.value.permitsAds) {
            fullscreen.toList().forEach { it.close() }
            rewarded.toList().forEach { it.close() }
        }
    }

    /** One request per explicit opt-in, with no automatic preload/refill/retry. */
    fun loadRewarded(onLoaded: (RewardAd?) -> Unit): RewardLoad {
        if (!mayPresentReward || rewarded.isNotEmpty()) { onLoaded(null); return RewardLoad {} }
        var handle: RewardedHandle? = null
        val lease = gate.acquire { handle?.invalidate() }
        if (lease == null) { onLoaded(null); return RewardLoad {} }
        val request = RewardedHandle(lease, onLoaded).also { handle = it; rewarded += it }
        request.load()
        return RewardLoad { scope.launch { request.close() } }
    }

    private inner class RewardedHandle(private val lease: AdGate.Lease, onLoaded: (RewardAd?) -> Unit) : RewardAd {
        private var ad: RewardedAd? = null
        private var loadedCallback: ((RewardAd?) -> Unit)? = onLoaded
        private var finished: (() -> Unit)? = null
        private var requestedShow = false
        private var earned = false
        private var failed = false
        private var closed = false
        fun load() {
            try {
                RewardedAd.load(AdRequest.Builder(units.rewarded).build(), object : AdLoadCallback<RewardedAd> {
                    override fun onAdLoaded(ad: RewardedAd) { scope.launch {
                        if (closed || !lease.valid || !mayPresentReward) { ad.destroy(); close(); return@launch }
                        this@RewardedHandle.ad = ad
                        val callback = loadedCallback; loadedCallback = null; callback?.invoke(this@RewardedHandle)
                    } }
                    override fun onAdFailedToLoad(adError: LoadAdError) { scope.launch { close() } }
                })
            } catch (_: Exception) { close() }
        }
        override fun show(onShown: () -> Unit, onEarned: (RewardMoment) -> Unit, onClosed: () -> Unit) {
            val activity = host.get()
            val loaded = ad
            if (closed || requestedShow || !lease.valid || !mayPresentReward || loaded == null ||
                activity == null || activity.isFinishing || activity.isDestroyed) {
                close(); onClosed(); return
            }
            finished = onClosed
            requestedShow = true
            loaded.adEventCallback = object : RewardedAdEventCallback {
                override fun onAdShowedFullScreenContent() { scope.launch { if (!closed) onShown() } }
                override fun onAdDismissedFullScreenContent() { scope.launch { close() } }
                override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                    scope.launch { failed = true; close() }
                }
            }
            try {
                loaded.show(activity) {
                    // Capture the callback's clock before queueing it on Main, including a midnight boundary.
                    val at = System.currentTimeMillis()
                    val moment = RewardMoment(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate().toString(), at)
                    scope.launch {
                        // A real earned callback already queued before close/Pro remains owed to the player.
                        if (!earned && !failed) { earned = true; onEarned(moment) }
                    }
                }
            } catch (_: Exception) { failed = true; close() }
        }
        fun invalidate() {
            if (!requestedShow || !billing.value.permitsAds || !consent.value.permitsAds) close()
        }
        override fun close() {
            if (closed) return
            closed = true
            lease.close(); runCatching { ad?.destroy() }; ad = null; rewarded -= this
            val loading = loadedCallback; loadedCallback = null; loading?.invoke(null)
            val callback = finished; finished = null; callback?.invoke()
        }
    }

    fun levelStarted(attemptId: String) {
        if (levelAttempt != attemptId) { preloadLease?.close(); levelAttempt = attemptId }
        preload()
    }
    private fun preload() {
        if (placement != AdPlacement.PLAY || levelAttempt == null || levelAttempt == takenForAttempt || preloadLease?.valid == true) return
        val lease = gate.acquire {
            InterstitialAdPreloader.destroy(units.interstitial)
            preloadLease = null
        } ?: return
        preloadLease = lease
        try {
            if (!InterstitialAdPreloader.start(units.interstitial, PreloadConfiguration(AdRequest.Builder(units.interstitial).build(), 1))) lease.close()
        } catch (_: Exception) { lease.close() }
    }
    fun takeInterstitial(): ResultsAd? {
        takenForAttempt = levelAttempt
        if (!mayPresentResultsAd || preloadLease?.valid != true) { preloadLease?.close(); return null }
        val ad = try { InterstitialAdPreloader.pollAd(units.interstitial) } catch (_: Exception) { null }
        // Polling may refill the buffer; stop it immediately. The next level starts a fresh preload.
        preloadLease?.close()
        if (ad == null) return null
        var handle: InterstitialHandle? = null
        val lease = gate.acquire { handle?.invalidate() }
        if (lease == null) { ad.destroy(); return null }
        return InterstitialHandle(ad, lease).also { handle = it; fullscreen += it }
    }

    private inner class InterstitialHandle(private val ad: InterstitialAd, private val lease: AdGate.Lease) : ResultsAd {
        private var showing = false
        private var closed = false
        private var finished: (() -> Unit)? = null
        override fun show(onShown: () -> Unit, onClosed: () -> Unit) {
            val activity = host.get()
            if (!lease.valid || !mayPresentResultsAd || activity == null || activity.isFinishing || activity.isDestroyed) {
                close(); onClosed(); return
            }
            finished = onClosed
            showing = true
            ad.adEventCallback = object : InterstitialAdEventCallback {
                override fun onAdShowedFullScreenContent() { scope.launch { if (!closed) onShown() } }
                override fun onAdDismissedFullScreenContent() { scope.launch { close() } }
                override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) { scope.launch { close() } }
            }
            try { ad.show(activity) } catch (_: Exception) { close() }
        }
        fun invalidate() {
            // AdActivity pauses MainActivity. A started fullscreen ad owns its lifecycle until close.
            if (!showing || !billing.value.permitsAds || !consent.value.permitsAds) close()
        }
        override fun close() {
            if (closed) return
            closed = true
            if (showing) lastFullscreenClosed = SystemClock.elapsedRealtime()
            lease.close(); runCatching { ad.destroy() }; fullscreen -= this
            val callback = finished; finished = null; callback?.invoke()
        }
    }

    fun banner(activity: Activity, requestedPlacement: AdPlacement, widthDp: Int): BannerHandle? {
        if (!requestedPlacement.banner || requestedPlacement != placement || widthDp < 1 || host.get() !== activity) return null
        // Instantiate no SDK view until the live ownership/consent check succeeds.
        var handle: BannerHandle? = null
        val lease = gate.acquire { handle?.dispose() } ?: return null
        return try {
            BannerHandle(activity, widthDp, lease).also { handle = it; banners += it; it.load() }
        } catch (_: Exception) { lease.close(); null }
    }

    inner class BannerHandle internal constructor(activity: Activity, widthDp: Int, private val lease: AdGate.Lease) {
        val view = FrameLayout(activity)
        private val adView = AdView(activity)
        // Standard anchored banners leave room for 200% text on compact screens.
        @Suppress("DEPRECATION")
        private val size = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp)
        private val mutableHeight = MutableStateFlow(0)
        val height = mutableHeight.asStateFlow()
        private var disposed = false
        init { view.addView(adView, FrameLayout.LayoutParams(size.getWidthInPixels(activity), size.getHeightInPixels(activity))) }
        internal fun load() {
            if (!lease.valid) { close(); return }
            adView.loadAd(BannerAdRequest.Builder(units.banner, size).build(), object : AdLoadCallback<BannerAd> {
                override fun onAdLoaded(ad: BannerAd) { scope.launch {
                    if (!lease.valid) { ad.destroy(); close() } else mutableHeight.value = size.height
                } }
                override fun onAdFailedToLoad(adError: LoadAdError) { scope.launch { close() } }
            })
        }
        internal fun dispose() {
            if (disposed) return
            disposed = true
            mutableHeight.value = 0
            view.visibility = View.GONE
            view.removeAllViews()
            runCatching { adView.destroy() }
            banners -= this
        }
        fun close() { lease.close() }
    }
}
