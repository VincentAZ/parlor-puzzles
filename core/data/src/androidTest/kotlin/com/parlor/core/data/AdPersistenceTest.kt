package com.parlor.core.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AdPersistenceTest {
    @Test fun legacyAdCallsNeverOfferAdsOrChangeProgress() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, ParlorDatabase::class.java).build()
        try {
            val repo = ProgressRepository(db, isPro = { false })
            val before = repo.player.first()
            assertFalse(repo.claimInterstitial("old-attempt", true, Long.MAX_VALUE, 0, "boot"))
            assertFalse(repo.interstitialDismissed("old-attempt", Long.MAX_VALUE, "boot"))
            assertFalse(repo.claimTenCompletionPrompt())
            assertEquals(before, repo.player.first())
        } finally { db.close() }
    }
}
