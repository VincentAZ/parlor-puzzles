package com.parlor.core.progress

import com.parlor.core.model.*
import kotlin.test.*

class RecoveryProtectionTest {
    private val day = "2026-09-28"
    private val award = Achievements.definitions.first().id
    private fun protection(ledger: RewardLedger = RewardLedger()) = RecoveryProtection(1, ledger, emptyMap(), emptySet())

    @Test fun olderSnapshotsCannotRelaxSpendingCompletionOrRewardSafeguards() {
        val old = protection(RewardLedger(latestResetDate = day, dailyHints = 3, earnedHints = 4,
            rewardedViews = 2, dailyClaims = mapOf(day to DailyClaim(true, 1, 0))))
        val local = old.copy(ledger = old.ledger.copy(dailyHints = 0, earnedHints = 1, earnedHintsSpent = 3,
            rewardedViews = 10, claims = setOf("reward"), hintedAttemptIds = setOf("attempt"),
            dailyClaims = mapOf(day to DailyClaim(true, 1, 1)), creditedDates = setOf(day)),
            completionDates = mapOf("completed" to day), achievements = setOf(award))
        var merged = local
        repeat(3) { merged = BackupRules.combineProtection(merged, old, day) }
        assertEquals(0, merged.ledger.dailyHints); assertEquals(1, merged.ledger.earnedHints)
        assertEquals(3L, merged.ledger.earnedHintsSpent); assertEquals(10, merged.ledger.rewardedViews)
        assertEquals(1, merged.ledger.dailyClaims.getValue(day).consumedRetries)
        assertEquals(setOf("reward"), merged.ledger.claims); assertEquals(setOf("attempt"), merged.ledger.hintedAttemptIds)
        assertEquals(local.completionDates, merged.completionDates); assertEquals(setOf(award), merged.achievements)
    }

    @Test fun aClockRollbackOrNewBalanceCannotEraseEarlierDatedClaims() {
        val first = protection(RewardLedger(latestResetDate = day, dailyHints = 1, rewardedViews = 10,
            earnedHints = 2, earnedHintsSpent = 5, creditedDates = setOf(day)))
        val later = first.copy(ledger = DailyRules.rollover(first.ledger, "2026-09-29").copy(dailyHints = 2))
        val merged = BackupRules.combineProtection(later, first, "2026-09-27")
        assertEquals("2026-09-29", merged.ledger.latestResetDate)
        assertEquals(2, merged.ledger.dailyHints); assertEquals(10, merged.ledger.rewardedViewsByDate[day])
        assertEquals(2, merged.ledger.earnedHints)
        assertEquals(merged, BackupRules.combineProtection(merged, merged, "2026-09-29"))
    }

    @Test fun missingFieldsFutureVersionsAndInconsistentRecordsCannotBecomeEmptyProtection() {
        val good = protection()
        assertEquals(good, ParlorJson.decodeFromString<RecoveryProtection>(ParlorJson.encodeToString(good)))
        assertFails { ParlorJson.decodeFromString<RecoveryProtection>("{}") }
        for (bad in listOf(good.copy(version = 2), good.copy(completionDates = mapOf("a" to day)),
            good.copy(ledger = RewardLedger(latestResetDate = "2026-02-30")),
            good.copy(ledger = RewardLedger(earnedHintsSpent = -1)), good.copy(achievements = setOf("unknown")))) {
            assertFails { BackupRules.validateProtection(bad) }
        }
        val json = ParlorJson.encodeToString(good)
        for (field in listOf("photos", "purchaseToken", "entitlement", "consent", "settings")) assertFalse(json.contains(field))
    }
}
