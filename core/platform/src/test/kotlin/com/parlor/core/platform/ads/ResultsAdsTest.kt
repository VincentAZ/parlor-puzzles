package com.parlor.core.platform.ads

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ResultsAdsTest {
    private class Ad : ResultsAd {
        var shows = 0; var closes = 0
        var shown: (() -> Unit)? = null; var dismissed: (() -> Unit)? = null
        override fun show(onShown: () -> Unit, onClosed: () -> Unit) { shows++; shown = onShown; dismissed = onClosed }
        override fun close() { closes++ }
    }
    @Test fun missingAdNavigatesWithoutQueuingAnInterruption() = runTest {
        var navigated = 0; var claims = 0; var takes = 0
        val ads = ResultsAds(backgroundScope, { takes++; null }, { true }, { _, ready -> assertFalse(ready); claims++; false }, { false })
        ads.leave("one", { navigated++ }, { fail("No upsell") }); runCurrent()
        assertEquals(1, navigated); assertEquals(1, claims); assertEquals(1, takes)
        advanceTimeBy(60_000); runCurrent(); assertEquals(1, navigated)
    }
    @Test fun slowOrBrokenStorageCannotStallNavigation() = runTest {
        val ad = Ad(); var navigated = 0
        val ads = ResultsAds(backgroundScope, { ad }, { true }, { _, _ -> delay(1_000); true }, { false })
        ads.leave("one", { navigated++ }, { fail("No upsell") })
        advanceTimeBy(251); runCurrent()
        assertEquals(1, navigated); assertEquals(0, ad.shows); assertEquals(1, ad.closes)
    }
    @Test fun liveUpgradeBeforePresentationDiscardsTheReadyAd() = runTest {
        val ad = Ad(); val saved = CompletableDeferred<Boolean>(); var mayShow = true; var navigated = 0
        val ads = ResultsAds(backgroundScope, { ad }, { mayShow }, { _, _ -> saved.await() }, { false })
        ads.leave("one", { navigated++ }, { fail("No upsell") }); runCurrent()
        mayShow = false; saved.complete(true); runCurrent()
        assertEquals(0, ad.shows); assertEquals(1, ad.closes); assertEquals(1, navigated)
    }
    @Test fun repeatedTapsAndCallbacksDoNotRepeatAdsNavigationOrDismissalCounts() = runTest {
        val ad = Ad(); var navigated = 0; var recorded = 0
        val ads = ResultsAds(backgroundScope, { ad }, { true }, { _, _ -> true }, { recorded++; false })
        repeat(2) { ads.leave("one", { navigated++ }, { fail("No upsell") }) }; runCurrent()
        assertEquals(1, ad.shows); assertEquals(0, navigated)
        ad.shown!!(); repeat(2) { ad.dismissed!!() }; runCurrent()
        assertEquals(1, navigated); assertEquals(1, recorded)
    }
    @Test fun failedShowDoesNotCountAsADismissedInterstitial() = runTest {
        val ad = Ad(); var navigated = 0
        val ads = ResultsAds(backgroundScope, { ad }, { true }, { _, _ -> true }, { fail("An unshown ad cannot count") })
        ads.leave("one", { navigated++ }, { fail("No upsell") }); runCurrent()
        ad.dismissed!!(); runCurrent(); assertEquals(1, navigated)
    }
    @Test fun upsellWaitsOnResultsAndProChoiceDoesNotStartTheNextBoard() = runTest {
        val ad = Ad(); var navigated = 0; var prompt: ((Boolean) -> Unit)? = null
        val ads = ResultsAds(backgroundScope, { ad }, { true }, { _, _ -> true }, { true })
        ads.leave("one", { navigated++ }, { prompt = it }); runCurrent()
        ad.shown!!(); ad.dismissed!!(); runCurrent()
        assertNotNull(prompt); assertEquals(0, navigated)
        prompt!!(false); assertEquals(0, navigated)
        ads.leave("two", { navigated++ }, { prompt = it }); runCurrent(); assertEquals(2, ad.shows)
        ad.shown!!(); ad.dismissed!!(); runCurrent(); prompt!!(true); prompt!!(true)
        assertEquals(1, navigated)
    }
}
