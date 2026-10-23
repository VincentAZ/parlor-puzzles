package com.parlor.core.platform.ads

import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ConsentCoordinatorTest {
    private class Gateway : ConsentGateway {
        var snapshot = ConsentSnapshot()
        var refreshes = 0; var loads = 0; var shows = 0; var privacyShows = 0
        var finishRefresh: ((Boolean) -> Unit)? = null
        var finishLoad: ((PrivacyForm?) -> Unit)? = null
        var dismiss: ((Boolean) -> Unit)? = null
        override fun snapshot() = snapshot
        override fun refresh(onFinished: (Boolean) -> Unit) { refreshes++; finishRefresh = onFinished }
        override fun load(onFinished: (PrivacyForm?) -> Unit) { loads++; finishLoad = onFinished }
        override fun privacy(onDismissed: (Boolean) -> Unit) { privacyShows++; dismiss = onDismissed }
        fun loaded() { finishLoad!!(object : PrivacyForm {
            override fun show(onDismissed: (Boolean) -> Unit) { shows++; dismiss = onDismissed }
        }) }
        fun refreshed(required: Boolean = false, allowed: Boolean = !required, ok: Boolean = true) {
            snapshot = ConsentSnapshot(allowed, required, required); finishRefresh!!(ok)
        }
    }

    @Test fun unknownAndProDoNotRefreshAndFreeRefreshesOncePerSession() = runTest {
        val gateway = Gateway(); val consent = ConsentCoordinator(gateway, backgroundScope)
        repeat(3) { consent.update(false, true, AdPlacement.HOME) }
        assertEquals(0, gateway.refreshes); assertFalse(consent.state.value.permitsAds)
        consent.update(true, true, AdPlacement.HOME)
        assertEquals(1, gateway.refreshes); assertTrue(consent.state.value.refreshing)
        gateway.refreshed(); assertTrue(consent.state.value.permitsAds)
        consent.update(true, false, AdPlacement.HOME); consent.update(true, true, AdPlacement.HOME)
        assertEquals(1, gateway.refreshes)
    }

    @Test fun lateConsentFormWaitsUntilThePlayerLeavesTheBoard() = runTest {
        val gateway = Gateway(); val consent = ConsentCoordinator(gateway, backgroundScope)
        consent.update(true, true, AdPlacement.HOME)
        consent.update(true, true, AdPlacement.PLAY)
        gateway.refreshed(required = true); gateway.loaded()
        assertEquals(0, gateway.shows); assertTrue(consent.state.value.formPending); assertFalse(consent.state.value.permitsAds)
        consent.update(true, true, AdPlacement.TUTORIAL); assertEquals(0, gateway.shows)
        consent.update(true, true, AdPlacement.RESULTS)
        assertEquals(1, gateway.shows); assertTrue(consent.state.value.showing)
        gateway.snapshot = ConsentSnapshot(true, false, true); gateway.dismiss!!(true)
        assertTrue(consent.state.value.permitsAds)
    }

    @Test fun aLoadedFormDoesNotPresentWhileBackgrounded() = runTest {
        val gateway = Gateway(); val consent = ConsentCoordinator(gateway, backgroundScope)
        consent.update(true, true, AdPlacement.ONBOARDING); gateway.refreshed(required = true)
        consent.update(true, false, AdPlacement.ONBOARDING); gateway.loaded()
        assertEquals(0, gateway.shows)
        consent.update(true, true, AdPlacement.ONBOARDING); assertEquals(1, gateway.shows)
    }

    @Test fun networkDeadlinesIgnoreLateRefreshAndLoadCallbacks() = runTest {
        val gateway = Gateway(); val consent = ConsentCoordinator(gateway, backgroundScope)
        consent.update(true, true, AdPlacement.HOME)
        val old = gateway.finishRefresh!!
        advanceTimeBy(10_001); runCurrent()
        assertFalse(consent.state.value.refreshing); assertTrue(consent.state.value.error)
        gateway.snapshot = ConsentSnapshot(true); old(true)
        assertFalse(consent.state.value.permitsAds)
        consent.retry(); gateway.refreshed(required = true)
        advanceTimeBy(10_001); runCurrent(); gateway.loaded()
        assertEquals(0, gateway.shows); assertFalse(consent.state.value.formPending); assertTrue(consent.state.value.error)
    }

    @Test fun upgradingInvalidatesPendingFormsAndRefundCanRefreshAgain() = runTest {
        val gateway = Gateway(); val consent = ConsentCoordinator(gateway, backgroundScope)
        consent.update(true, true, AdPlacement.HOME); gateway.refreshed(required = true)
        consent.update(false, true, AdPlacement.HOME); gateway.loaded()
        assertEquals(0, gateway.shows); assertFalse(consent.state.value.formPending)
        consent.update(true, true, AdPlacement.HOME); assertEquals(2, gateway.refreshes)
    }

    @Test fun failedRefreshUsesOnlyUmpsCurrentPermissionAndDoesNotBlockPlay() = runTest {
        val gateway = Gateway(); val consent = ConsentCoordinator(gateway, backgroundScope)
        consent.update(true, true, AdPlacement.HOME); gateway.refreshed(allowed = true, ok = false)
        assertTrue(consent.state.value.error); assertTrue(consent.state.value.permitsAds)
        consent.retry(); assertFalse(consent.state.value.permitsAds)
        gateway.refreshed(allowed = false, ok = false); assertFalse(consent.state.value.permitsAds)
    }

    @Test fun privacyOptionsStopAdsAndHonorRevocationIncludingAfterProUpgrade() = runTest {
        val gateway = Gateway(); val consent = ConsentCoordinator(gateway, backgroundScope)
        consent.update(true, true, AdPlacement.SETTINGS); gateway.refreshed()
        gateway.snapshot = ConsentSnapshot(true, false, true)
        consent.privacyOptions(); assertEquals(1, gateway.privacyShows); assertFalse(consent.state.value.permitsAds)
        gateway.snapshot = ConsentSnapshot(false, false, true); gateway.dismiss!!(true)
        assertFalse(consent.state.value.permitsAds)
        consent.update(false, true, AdPlacement.SETTINGS); consent.privacyOptions()
        assertEquals(2, gateway.privacyShows)
    }

    @Test fun destroyedActivityCannotCompleteANewerConsentPresentation() = runTest {
        val gateway = Gateway(); val consent = ConsentCoordinator(gateway, backgroundScope)
        consent.update(true, true, AdPlacement.HOME); gateway.refreshed(required = true); gateway.loaded()
        val oldDismiss = gateway.dismiss!!
        consent.presentationLost(); consent.update(true, true, AdPlacement.HOME)
        gateway.refreshed(required = true); gateway.loaded()
        oldDismiss(true); assertTrue(consent.state.value.showing)
        gateway.dismiss!!(true); assertFalse(consent.state.value.showing)
    }

    @Test fun proCanExplicitlyRefreshPrivacyChoicesWithoutAutomaticConsentOrAds() = runTest {
        val gateway = Gateway(); val consent = ConsentCoordinator(gateway, backgroundScope)
        consent.update(false, true, AdPlacement.SETTINGS); assertEquals(0, gateway.refreshes)
        consent.privacyOptions(); assertEquals(1, gateway.refreshes)
        consent.update(false, true, AdPlacement.PLAY)
        gateway.refreshed(required = true)
        assertEquals(0, gateway.privacyShows); assertEquals(0, gateway.loads)
        consent.update(false, true, AdPlacement.SETTINGS)
        assertEquals(1, gateway.privacyShows)
        gateway.dismiss!!(true)
        consent.update(false, false, AdPlacement.SETTINGS); consent.update(false, true, AdPlacement.SETTINGS)
        assertEquals(1, gateway.refreshes)
    }
}
