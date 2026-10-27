package com.parlor.core.progress

import com.parlor.core.model.*
import kotlin.test.*
import kotlinx.datetime.LocalDate

class DailyAndAchievementTest {
    private val level = LevelDefinition("daily-test", puzzle = Puzzle.TRAFFIC_JAM, tier = Tier.EXPERT, order = 1,
        initial = TrafficJamBoard(pieces = listOf(TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL))),
        reference = listOf(Translate("A", 6)), par = 1, parKind = ParKind.PROVEN_OPTIMAL, targetTimeMs = 60_000,
        provenance = Provenance("Parlor", "test fixture", 0, "original"))

    @Test fun scheduleVisitsEveryPoolEntryAndDoesNotDependOnCatalogOrder() {
        val refs = (1..12).map { LevelRef("level-$it", 1) }
        val schedule = DailySchedule(listOf(ScheduleEpoch(1, "2026-09-28", refs)))
        val dates = (0..11).map { LocalDate.fromEpochDays(LocalDate.parse("2026-09-28").toEpochDays() + it).toString() }
        assertEquals(refs.toSet(), dates.map { DailyRules.assignment(it, schedule).second }.toSet())
        // Golden assignments are part of the frozen schedule contract, including DST dates.
        for ((date, expected) in mapOf("1900-01-01" to 5, "2099-12-31" to 1, "2026-03-08" to 10, "2026-11-01" to 12)) {
            assertEquals(1 to LevelRef("level-$expected", 1), DailyRules.assignment(date, schedule))
        }
        val future = schedule.copy(epochs = schedule.epochs + ScheduleEpoch(2, "2027-01-01", refs.asReversed()))
        assertEquals(DailyRules.assignment("2026-12-31", schedule), DailyRules.assignment("2026-12-31", future))
        assertEquals(2, DailyRules.assignment("2027-01-01", future).first)
        assertFailsWith<IllegalArgumentException> {
            DailyRules.assignment("2026-09-28", schedule.copy(epochs = schedule.epochs + ScheduleEpoch(2, "2026-09-28", refs)))
        }
    }

    @Test fun unlimitedRetriesDoNotDependOnPurchasesOrEarnedCredits() {
        assertEquals(DailyEligibility.FIRST_COMPLETION, DailyRules.eligibility(DailyClaim(), false))
        val claim = DailyClaim(true, earnedRetries = 2, consumedRetries = 1)
        assertEquals(DailyEligibility.PRO, DailyRules.eligibility(claim, false))
        assertEquals(DailyEligibility.PRO, DailyRules.eligibility(claim, true))
        assertEquals(DailyEligibility.PRO, DailyRules.eligibility(claim.copy(consumedRetries = 2), false))
        val attempt = Attempt.start("a", level, AttemptMode.DAILY, "2026-09-28").copy(completed = true)
        assertNull(Scoring.result(level, attempt))
        assertEquals(350, Scoring.result(level, attempt.copy(dailyEligibility = DailyEligibility.FIRST_COMPLETION))!!.points)
    }

    @Test fun rewardKindsShareCapAndRejectDuplicateLateOrInvalidTargets() {
        var ledger = RewardLedger(dailyClaims = mapOf("2026-09-28" to DailyClaim(true)))
        repeat(8) { ledger = RewardRules.grant(ledger, "2026-09-28", "h-$it", Reward.Hints, 100)!! }
        ledger = RewardRules.grant(ledger, "2026-09-28", "unlock", Reward.LevelAccess(level.id), 100, level)!!
        ledger = RewardRules.grant(ledger, "2026-09-28", "retry", Reward.DailyRetry("2026-09-28"), 100)!!
        assertEquals(10, ledger.rewardedViews)
        assertEquals(16, ledger.earnedHints)
        assertEquals(86_400_100L, ledger.temporaryUnlocks[level.id])
        assertEquals(1, ledger.dailyClaims.getValue("2026-09-28").earnedRetries)
        assertNull(RewardRules.grant(ledger, "2026-09-28", "eleventh", Reward.Hints, 100))
        assertNull(RewardRules.grant(ledger, "2026-09-29", "retry", Reward.Hints, 100))
        assertNull(RewardRules.grant(ledger, "2026-09-29", "late", Reward.DailyRetry("2026-09-28"), 100))
        assertNull(RewardRules.grant(RewardLedger(), "2026-09-28", "easy", Reward.LevelAccess(level.id), 100, level.copy(tier = Tier.EASY)))
        assertNull(RewardRules.grant(RewardLedger(), "2026-09-28", "first", Reward.DailyRetry("2026-09-28"), 100))
    }

    @Test fun historyUsesHighWaterDateWithoutDiscardingTimezoneProgress() {
        assertTrue(DailyRules.withinHistory("2026-09-01", "2026-09-30", null))
        assertFalse(DailyRules.withinHistory("2026-08-31", "2026-09-30", null))
        assertFalse(DailyRules.withinHistory("2026-08-31", "2026-09-01", "2026-09-30"))
        assertTrue(DailyRules.withinHistory("2026-09-30", "2026-09-29", "2026-09-30"))
    }

    @Test fun achievementCatalogHasExactlyFortyStableUniqueIdsAndExcludesDemonstrations() {
        assertEquals(40, Achievements.definitions.size)
        assertEquals(40, Achievements.definitions.map { it.id }.toSet().size)
        val demo = Attempt.start("demo", level, AttemptMode.DEMONSTRATION).copy(completed = true)
        assertTrue(Achievements.evaluate(level, demo, null, listOf(level), emptyList(), RewardLedger(), PlayerProfile()).isEmpty())
        val completed = Attempt.start("a", level).copy(completed = true)
        val result = Scoring.result(level, completed)!!
        val awards = Achievements.evaluate(level, completed, result, listOf(level), listOf(Scoring.improve(level.id, null, result, "2026-09-28")), RewardLedger(), PlayerProfile())
        assertTrue("first-solution" in awards)
        assertTrue("three-stars-traffic_jam" in awards)
        assertFalse("perfect-tier-traffic_jam" in awards)
        assertTrue(Achievements.evaluate(level, completed, result, listOf(level), emptyList(), RewardLedger(), PlayerProfile(achievements = awards)).isEmpty())
    }

    @Test fun specialAchievementsRequireTheirActualCompletionFactsAndUndoCount() {
        val hanoi = level.copy(puzzle = Puzzle.HANOI)
        val a = Attempt.start("a", hanoi).copy(completed = true)
        fun earned(l: LevelDefinition, attempt: Attempt, facts: CompletionFacts = CompletionFacts()) =
            Achievements.evaluate(l, attempt, null, emptyList(), emptyList(), RewardLedger(), PlayerProfile(), facts)
        assertFalse("hanoi-ten" in earned(hanoi, a))
        assertTrue("hanoi-ten" in earned(hanoi, a, CompletionFacts(hanoiDisks = 10)))
        assertTrue("knight-closed-eight" in earned(level.copy(puzzle = Puzzle.KNIGHTS_TOUR), a, CompletionFacts(closedKnightSide = 8)))
        assertFalse("box-no-undo" in earned(level.copy(puzzle = Puzzle.BOX_PUSHER), a.copy(undoCount = 1)))
        assertTrue("box-no-undo" in earned(level.copy(puzzle = Puzzle.BOX_PUSHER), a))
    }
}
