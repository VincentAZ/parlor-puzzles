package com.parlor.core.platform.ads

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AdGateTest {
    @Test fun everyPermissionMustBeEstablishedBeforeInitialization() = runTest {
        var starts = 0
        val gate = AdGate(backgroundScope, { allowed -> assertTrue(allowed()); starts++; true }, { true })
        for (free in listOf(false, true)) for (consent in listOf(false, true)) for (foreground in listOf(false, true)) {
            if (free && consent && foreground) continue
            gate.update(free, consent, foreground, false); runCurrent()
            assertEquals(0, starts); assertFalse(gate.enabled.value); assertNull(gate.acquire {})
        }
        gate.update(true, true, true, true); runCurrent(); assertEquals(0, starts)
        gate.update(true, true, true, false); runCurrent()
        assertEquals(1, starts); assertTrue(gate.enabled.value)
        repeat(4) { gate.update(true, true, true, false) }; runCurrent(); assertEquals(1, starts)
    }

    @Test fun upgradeDuringInitializationRejectsItsLateResultAndAllResources() = runTest {
        val ready = CompletableDeferred<Unit>()
        var free = true
        val gate = AdGate(backgroundScope, { ready.await(); true }, { free })
        gate.update(true, true, true, false); runCurrent()
        free = false; gate.update(false, true, true, false)
        ready.complete(Unit); runCurrent()
        assertFalse(gate.enabled.value); assertFalse(gate.permitsBackgroundWork); assertNull(gate.acquire {})
    }

    @Test fun downgradeAndForegroundReturnCannotReviveAnOldAd() = runTest {
        var starts = 0; var disposed = 0; var canceled = 0
        val gate = AdGate(backgroundScope, { starts++; true }, { true }, { canceled++ })
        gate.update(true, true, true, false); runCurrent()
        val old = assertNotNull(gate.acquire { disposed++ }); assertTrue(old.valid)
        gate.update(false, true, true, false)
        assertFalse(old.valid); assertEquals(1, disposed); assertEquals(1, canceled)
        gate.update(false, true, true, false); old.close(); assertEquals(1, disposed)
        gate.update(true, true, true, false); assertTrue(gate.enabled.value); assertFalse(old.valid)
        val next = assertNotNull(gate.acquire { disposed++ })
        gate.update(true, true, false, false)
        assertFalse(next.valid); assertEquals(2, disposed); assertEquals(1, starts)
    }

    @Test fun liveOwnershipCheckClosesTheGapBeforeTheStateCollectorRuns() = runTest {
        var live = true
        val gate = AdGate(backgroundScope, { true }, { live })
        gate.update(true, true, true, false); runCurrent()
        val lease = assertNotNull(gate.acquire {})
        live = false
        assertFalse(lease.valid); assertNull(gate.acquire {}); assertFalse(gate.permitsBackgroundWork)
    }

    @Test fun failedInitializationLeavesPlayAvailableAndCanRetryLater() = runTest {
        var starts = 0
        val gate = AdGate(backgroundScope, { if (++starts == 1) error("offline") else true }, { true })
        gate.update(true, true, true, false); runCurrent(); assertFalse(gate.enabled.value)
        gate.update(true, true, false, false)
        gate.update(true, true, true, false); runCurrent(); assertTrue(gate.enabled.value)
    }

    @Test fun placementsExcludeActivePlayDailyEntryTutorialAndPro() {
        assertEquals(setOf(AdPlacement.HOME, AdPlacement.LEVEL_SELECT, AdPlacement.RESULTS, AdPlacement.STATS, AdPlacement.SETTINGS),
            AdPlacement.entries.filter { it.banner }.toSet())
        assertFalse(AdPlacement.PLAY.consentForm); assertFalse(AdPlacement.TUTORIAL.consentForm)
        assertFalse(AdPlacement.PRO.consentForm); assertFalse(AdPlacement.ONBOARDING.banner)
        assertFalse(AdPlacement.RECOVERY.consentForm)
    }
}
