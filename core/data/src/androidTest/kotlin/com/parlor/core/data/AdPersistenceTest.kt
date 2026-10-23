package com.parlor.core.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class AdPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: ParlorDatabase
    private lateinit var repo: ProgressRepository
    private var pro = false
    private fun open() {
        db = Room.databaseBuilder(context, ParlorDatabase::class.java, "ads-rules-test.db").build()
        repo = ProgressRepository(db, isPro = { pro })
    }
    @Before fun setup() { context.deleteDatabase("ads-rules-test.db"); open() }
    @After fun teardown() { db.close(); context.deleteDatabase("ads-rules-test.db") }
    private suspend fun completions(count: Int) {
        repeat(count) { db.progress().recordCompletion(CompletionRow("attempt-$it", "2026-09-28")) }
    }
    @Test fun completionAndSpacingClaimsSurviveRestartResetAndDuplicateCallbacks() = runBlocking {
        completions(4)
        assertFalse(repo.claimInterstitial("unknown", true, 60_000, 0, "device:1"))
        val claims = coroutineScope { List(4) { async { repo.claimInterstitial("attempt-3", true, 60_000, 0, "device:1") } }.awaitAll() }
        assertEquals(1, claims.count { it })
        assertFalse(repo.interstitialDismissed("attempt-3", 60_010, "device:1")); assertFalse(repo.interstitialDismissed("attempt-3", 70_000, "device:1"))
        assertEquals(1, repo.player.first().ledger.ads.dismissedInterstitials)
        repo.resetProgress(); db.close(); open(); completions(7)
        assertFalse(repo.claimInterstitial("attempt-4", true, 179_999, 100_000, "device:1"))
        assertTrue(repo.claimInterstitial("attempt-6", true, 180_010, 100_000, "device:1"))
        assertEquals(7, repo.completionCount.first())
        assertEquals(7, repo.player.first().ledger.ads.lastInterstitialCompletion)
    }
    @Test fun missedAdsAndProDeparturesCannotBecomeDeferredAds() = runBlocking {
        completions(10)
        assertFalse(repo.claimInterstitial("attempt-0", false, 60_000, 0, "device:1"))
        assertFalse(repo.claimInterstitial("attempt-0", true, 80_000, 0, "device:1"))
        pro = true; assertFalse(repo.claimInterstitial("attempt-1", true, 80_000, 0, "device:1"))
        pro = false; assertFalse(repo.claimInterstitial("attempt-1", true, 100_000, 0, "device:1"))
        assertTrue(repo.claimTenCompletionPrompt()); repo.resetProgress(); db.close(); open()
        assertFalse(repo.claimTenCompletionPrompt())
    }
    @Test fun everyFifthActualDismissalOffersProOnce() = runBlocking {
        repeat(5) { index ->
            completions(4 + index * 3)
            val id = "attempt-${3 + index * 3}"
            assertTrue(repo.claimInterstitial(id, true, 60_000L + index * 120_000L, 0, "device:1"))
            assertEquals(index == 4, repo.interstitialDismissed(id, 60_000L + index * 120_000L, "device:1"))
            assertFalse(repo.interstitialDismissed(id, 60_000L + index * 120_000L, "device:1"))
        }
        assertEquals(5, repo.player.first().ledger.ads.dismissedInterstitials)
    }
}
