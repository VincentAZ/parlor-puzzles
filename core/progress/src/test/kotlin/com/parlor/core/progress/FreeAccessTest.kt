package com.parlor.core.progress

import com.parlor.core.model.*
import kotlin.test.*

class FreeAccessTest {
    @Test fun noPuzzleNeedsPaymentProgressOrAnUnexpiredReward() {
        for (tier in Tier.entries) for (variant in listOf(false, true)) {
            val level = LevelDefinition("free", puzzle = Puzzle.TRAFFIC_JAM, tier = tier, order = 99,
                proVariant = variant,
                initial = TrafficJamBoard(pieces = listOf(TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL))),
                reference = listOf(Translate("A", 6)), par = 1, parKind = ParKind.REFERENCE,
                targetTimeMs = 10_000, provenance = Provenance("Parlor", "test", 0, "original"))
            assertEquals(Access.AVAILABLE, Progression.access(level, listOf(level), emptyList(), false, 0, Long.MAX_VALUE))
        }
    }
    @Test fun exhaustedLegacyAllowancesCannotBlockHintsOrScoredRetries() {
        val day = "2026-10-27"
        var ledger = RewardLedger(latestResetDate = day, dailyHints = 0, earnedHints = 0)
        repeat(100) { ledger = assertNotNull(DailyRules.spendHint(ledger, day, false)) }
        assertEquals(0, ledger.dailyHints)
        assertEquals(0L, ledger.earnedHintsSpent)
        assertEquals(DailyEligibility.PRO, DailyRules.eligibility(DailyClaim(true), false))
        RoomTheme.entries.forEach { assertEquals(it, RoomTheme.displayed(it.id, false)) }
    }
}
