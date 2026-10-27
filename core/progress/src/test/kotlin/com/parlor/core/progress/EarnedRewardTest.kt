package com.parlor.core.progress

import com.parlor.core.model.*
import kotlin.test.*

class EarnedRewardTest {
    private val day = "2026-09-28"
    private val next = "2026-09-29"
    private val level = LevelDefinition("reward-level", puzzle = Puzzle.TRAFFIC_JAM, tier = Tier.HARD, order = 1,
        initial = TrafficJamBoard(pieces = listOf(TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL))),
        reference = listOf(Translate("A", 6)), par = 1, parKind = ParKind.PROVEN_OPTIMAL, targetTimeMs = 60_000,
        provenance = Provenance("Parlor", "reward fixture", 0, "original"))

    @Test fun offersUseOneCapAndRequireAnEligibleTargetOnTheCurrentDate() {
        val ledger = RewardLedger(latestResetDate = day, rewardedViews = 9, dailyClaims = mapOf(day to DailyClaim(true)))
        assertTrue(RewardRules.canOffer(ledger, day, Reward.Hints))
        assertTrue(RewardRules.canOffer(ledger, day, Reward.LevelAccess(level.id), level))
        assertTrue(RewardRules.canOffer(ledger, day, Reward.DailyRetry(day)))
        assertFalse(RewardRules.canOffer(ledger, day, Reward.LevelAccess(level.id), level.copy(tier = Tier.MEDIUM)))
        assertFalse(RewardRules.canOffer(ledger, next, Reward.DailyRetry(day)))
        assertFalse(RewardRules.canOffer(ledger, day, Reward.DailyRetry(next)))
        assertFalse(RewardRules.canOffer(ledger.copy(dailyClaims = emptyMap()), day, Reward.DailyRetry(day)))
        assertFalse(RewardRules.canOffer(ledger.copy(rewardedViews = 10), day, Reward.Hints))
        assertFalse(RewardRules.canOffer(DailyRules.rollover(ledger, next), day, Reward.Hints))
    }

    @Test fun anEarnedCallbackRecoveredAfterMidnightUsesItsOriginalCapAndDoesNotResetHints() {
        val oldPreview = RewardLedger(latestResetDate = day, dailyHints = 0, earnedHints = 5, rewardedViews = 9)
        var ledger = DailyRules.rollover(oldPreview, next)
        ledger = RewardRules.grant(ledger, next, "new-day", Reward.Hints, 200)!!
        ledger = DailyRules.spendHint(ledger, next, false)!!
        val pending = EarnedReward("late-write", Reward.Hints, day, 100)
        ledger = RewardRules.grantEarned(ledger, next, pending)!!
        assertEquals(next, ledger.latestResetDate)
        assertEquals(3, ledger.dailyHints)
        assertEquals(9, ledger.earnedHints)
        assertEquals(1, ledger.rewardedViews)
        assertEquals(mapOf(day to 10, next to 1), ledger.rewardedViewsByDate)
        assertNull(RewardRules.grantEarned(ledger, next, pending))
        assertNull(RewardRules.grantEarned(ledger, next, pending.copy(id = "eleventh")))
        assertEquals(ledger, DailyRules.rollover(ledger, day))
    }

    @Test fun temporaryAccessExpiresFromEarningTimeAndNeverShortensAnExistingGrant() {
        val receipt = EarnedReward("level", Reward.LevelAccess(level.id), day, 100)
        val recovered = RewardRules.grantEarned(RewardLedger(), next, receipt, level)!!
        assertEquals(86_400_100L, recovered.temporaryUnlocks[level.id])
        val longer = recovered.copy(temporaryUnlocks = mapOf(level.id to 200_000_000L))
        assertEquals(200_000_000L, RewardRules.grantEarned(longer, next, receipt.copy(id = "another"), level)!!.temporaryUnlocks[level.id])
        assertNull(RewardRules.grantEarned(RewardLedger(), next, receipt, level.copy(id = "wrong")))
    }

    @Test fun dailyRecoveryKeepsTheEarnedChallengeDateAndNeverCreatesAnotherDaysRetry() {
        val ledger = RewardLedger(dailyClaims = mapOf(day to DailyClaim(true), next to DailyClaim(true)))
        val receipt = EarnedReward("daily", Reward.DailyRetry(day), day, 100)
        val recovered = RewardRules.grantEarned(ledger, next, receipt)!!
        assertEquals(1, recovered.dailyClaims.getValue(day).earnedRetries)
        assertEquals(0, recovered.dailyClaims.getValue(next).earnedRetries)
        assertEquals(0, recovered.rewardedViews)
        // A new request for an old challenge remains ineligible.
        assertFalse(RewardRules.canOffer(recovered, next, Reward.DailyRetry(day)))
        assertNull(RewardRules.grantEarned(recovered, next, receipt.copy(id = "different-target", reward = Reward.DailyRetry(next))))
    }

    @Test fun earnedDatesPreserveHighWaterProtectionAcrossClockRollback() {
        val future = RewardLedger(latestResetDate = next, dailyHints = 1, rewardedViews = 10)
        val earned = RewardRules.grantEarned(future, day, EarnedReward("earlier", Reward.Hints, day, 100))!!
        assertEquals(next, earned.latestResetDate)
        assertEquals(1, earned.dailyHints)
        assertEquals(10, earned.rewardedViews)
        assertEquals(mapOf(next to 10, day to 1), earned.rewardedViewsByDate)
        assertFalse(RewardRules.canOffer(earned, day, Reward.Hints))
        assertNull(RewardRules.grantEarned(earned, day, EarnedReward("over-cap", Reward.Hints, next, 200)))
    }

    @Test fun aDailyAdSpanningMidnightKeepsItsOfferAndCountsTheCompletionDay() {
        val ledger = RewardLedger(latestResetDate = day, rewardedViews = 9, dailyClaims = mapOf(day to DailyClaim(true)))
        assertTrue(RewardRules.canOffer(ledger, day, Reward.DailyRetry(day)))
        val receipt = EarnedReward("midnight", Reward.DailyRetry(day), next, 100, offeredDate = day)
        val earned = RewardRules.grantEarned(ledger, next, receipt)!!
        assertEquals(1, earned.dailyClaims.getValue(day).earnedRetries)
        assertNull(earned.dailyClaims[next])
        assertEquals(1, earned.rewardedViews)
        assertEquals(mapOf(day to 9, next to 1), earned.rewardedViewsByDate)
        assertFalse(RewardRules.canOffer(earned, next, Reward.DailyRetry(day)))
    }

    @Test fun receiptSerializationRetainsTheOriginalRewardAndRejectsUnsupportedVersions() {
        for (reward in listOf(Reward.Hints, Reward.LevelAccess(level.id), Reward.DailyRetry(day))) {
            val receipt = EarnedReward("receipt", reward, day, 123)
            assertEquals(receipt, ParlorJson.decodeFromString<EarnedReward>(ParlorJson.encodeToString(receipt)))
        }
        assertFailsWith<IllegalArgumentException> { EarnedReward("id", Reward.Hints, day, 0, schemaVersion = 2) }
        assertFailsWith<IllegalArgumentException> { EarnedReward("id", Reward.Hints, day, Long.MAX_VALUE) }
    }
}
