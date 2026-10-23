package com.parlor.core.platform.ads

import com.parlor.core.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RewardAdsTest {
    private class Ad : RewardAd {
        var shows = 0; var disposed = false
        lateinit var shown: () -> Unit
        lateinit var earned: (RewardMoment) -> Unit
        lateinit var closed: () -> Unit
        override fun show(onShown: () -> Unit, onEarned: (RewardMoment) -> Unit, onClosed: () -> Unit) {
            shows++; shown = onShown; earned = onEarned; closed = onClosed
        }
        override fun close() { disposed = true }
    }
    private class Store : EarnedRewardStore {
        var receipt: EarnedReward? = null
        var failSave = false
        override suspend fun load() = receipt
        override suspend fun save(receipt: EarnedReward) { check(!failSave); this.receipt = receipt }
        override suspend fun clear(id: String) { receipt = null }
    }
    private class Fixture(val scope: TestScope) {
        val store = Store()
        val applied = mutableListOf<EarnedReward>()
        var mayShow = true
        var status = RewardAvailability.AVAILABLE
        var storageDelay = 0L
        var now = RewardMoment("2026-09-28", 100)
        var requests = 0; var cancellations = 0; var ids = 0
        var callback: ((RewardAd?) -> Unit)? = null
        val delivery = RewardDelivery(scope.backgroundScope, store) { applied += it; RewardApplication.APPLIED }
        val ads = RewardAds(scope.backgroundScope, delivery, load = {
            requests++; callback = it; RewardLoad { cancellations++ }
        }, mayPresent = { mayShow }, availability = { delay(storageDelay); status }, moment = { now }, newId = { "reward-${ids++}" })
        fun ready() { delivery.retry(); scope.runCurrent() }
        fun start(reward: Reward = Reward.Hints): Ad {
            assertTrue(ads.offer(reward)); ads.watch(); scope.runCurrent()
            val ad = Ad(); callback!!(ad); scope.runCurrent()
            assertEquals(1, ad.shows)
            return ad
        }
    }

    @Test fun offeringDoesNotLoadAndRepeatedWatchTapsStartOnlyOneRequest() = runTest {
        val f = Fixture(this); f.ready()
        f.ads.offer(Reward.Hints); runCurrent(); assertEquals(0, f.requests)
        f.ads.watch(); f.ads.watch(); runCurrent(); assertEquals(1, f.requests)
        assertTrue(f.applied.isEmpty())
    }

    @Test fun onlyTheEarnedCallbackGrantsAndDuplicatesDoNotRepeatTheReward() = runTest {
        val f = Fixture(this); f.ready(); val ad = f.start()
        ad.shown(); runCurrent(); assertTrue(f.applied.isEmpty())
        ad.earned(f.now); ad.earned(f.now); runCurrent()
        assertEquals(1, f.applied.size)
        assertEquals(RewardStage.SHOWING, f.ads.state.value?.stage)
        ad.closed(); ad.closed(); runCurrent()
        assertEquals(1, f.applied.size); assertEquals(RewardStage.EARNED, f.ads.state.value?.stage)
        assertTrue(ad.disposed)
    }

    @Test fun failedLoadFailedShowAndEarlyCloseDoNotGrantAnything() = runTest {
        val f = Fixture(this); f.ready()
        f.ads.offer(Reward.Hints); f.ads.watch(); runCurrent(); f.callback!!(null); runCurrent()
        assertEquals(RewardStage.UNAVAILABLE, f.ads.state.value?.stage)
        f.ads.cancel(); val failed = f.start(); failed.closed(); runCurrent()
        assertEquals(RewardStage.UNAVAILABLE, f.ads.state.value?.stage)
        f.ads.cancel(); val dismissed = f.start(); dismissed.shown(); dismissed.closed(); runCurrent()
        assertEquals(RewardStage.CANCELED, f.ads.state.value?.stage)
        assertTrue(f.applied.isEmpty()); assertNull(f.store.receipt)
    }

    @Test fun cancelAndTimeoutDiscardLateLoadsWithoutADeferredInterruption() = runTest {
        val f = Fixture(this); f.ready()
        f.ads.offer(Reward.Hints); f.ads.watch(); runCurrent(); val old = f.callback!!
        f.ads.cancel(); runCurrent(); val canceled = Ad(); old(canceled); runCurrent()
        assertEquals(0, canceled.shows); assertTrue(canceled.disposed); assertNull(f.ads.state.value)
        f.ads.offer(Reward.Hints); f.ads.watch(); runCurrent()
        advanceTimeBy(20_001); runCurrent()
        val late = Ad(); f.callback!!(late); runCurrent()
        assertEquals(2, f.cancellations); assertEquals(0, late.shows); assertTrue(late.disposed)
        assertEquals(RewardStage.UNAVAILABLE, f.ads.state.value?.stage)
    }

    @Test fun proOrConsentLossBeforeShowDisposesTheLoadedAd() = runTest {
        val f = Fixture(this); f.ready()
        f.ads.offer(Reward.Hints); f.ads.watch(); runCurrent()
        f.mayShow = false; val ad = Ad(); f.callback!!(ad); runCurrent()
        assertEquals(0, ad.shows); assertTrue(ad.disposed); assertTrue(f.applied.isEmpty())
    }

    @Test fun capIsCheckedBeforeTheRequestAndAgainBeforePresentation() = runTest {
        val f = Fixture(this); f.ready(); f.status = RewardAvailability.DAILY_LIMIT
        f.ads.offer(Reward.Hints); f.ads.watch(); runCurrent()
        assertEquals(0, f.requests); assertEquals(RewardStage.LIMIT_REACHED, f.ads.state.value?.stage)
        f.ads.cancel(); f.status = RewardAvailability.AVAILABLE
        f.ads.offer(Reward.Hints); f.ads.watch(); runCurrent()
        f.status = RewardAvailability.DAILY_LIMIT; val ad = Ad(); f.callback!!(ad); runCurrent()
        assertEquals(0, ad.shows); assertTrue(ad.disposed); assertEquals(RewardStage.LIMIT_REACHED, f.ads.state.value?.stage)
    }

    @Test fun anEarnedRewardSurvivesClosingTheOfferAndRetryingAStorageFailure() = runTest {
        val f = Fixture(this); f.ready(); val ad = f.start()
        f.store.failSave = true; ad.shown(); ad.earned(f.now); ad.closed(); runCurrent()
        assertEquals(RewardStage.SAVE_ERROR, f.ads.state.value?.stage)
        f.ads.cancel(); f.store.failSave = false; f.delivery.retry(); runCurrent()
        assertEquals(1, f.applied.size); assertNull(f.ads.state.value)
        assertTrue(f.delivery.state.value.canStart)
    }

    @Test fun actualEarnedCallbackAfterUiDisposalStillDeliversTheAuthorizedReward() = runTest {
        val f = Fixture(this); f.ready(); val ad = f.start()
        ad.shown(); f.ads.cancel(); ad.earned(f.now); ad.closed(); runCurrent()
        assertTrue(ad.disposed); assertEquals(1, f.applied.size); assertNull(f.ads.state.value)
    }

    @Test fun dailyOfferKeepsItsOriginalDateWhenTheAdCompletesAfterMidnight() = runTest {
        val f = Fixture(this); f.ready(); val original = f.now.date
        val ad = f.start(Reward.DailyRetry(original)); ad.shown()
        f.now = RewardMoment("2026-09-29", 200)
        ad.earned(f.now); ad.closed(); runCurrent()
        val receipt = f.applied.single()
        assertEquals(original, receipt.offeredDate)
        assertEquals(f.now.date, receipt.earnedDate)
        assertEquals(Reward.DailyRetry(original), receipt.reward)
    }

    @Test fun aDailyOfferThatExpiresWhileLoadingNeverShows() = runTest {
        val f = Fixture(this); f.ready()
        f.ads.offer(Reward.DailyRetry(f.now.date)); f.ads.watch(); runCurrent()
        f.now = RewardMoment("2026-09-29", 200)
        val ad = Ad(); f.callback!!(ad); runCurrent()
        assertTrue(ad.disposed); assertEquals(0, ad.shows); assertTrue(f.applied.isEmpty())
    }

    @Test fun slowStorageStopsTheRequestWithoutLoadingAnAd() = runTest {
        val f = Fixture(this); f.ready(); f.storageDelay = 5_000
        f.ads.offer(Reward.Hints); f.ads.watch(); advanceTimeBy(2_001); runCurrent()
        assertEquals(0, f.requests); assertEquals(RewardStage.UNAVAILABLE, f.ads.state.value?.stage)
    }
}
