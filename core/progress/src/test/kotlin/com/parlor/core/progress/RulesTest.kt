package com.parlor.core.progress

import com.parlor.core.model.*
import kotlin.test.*

class RulesTest {
    private val level = LevelDefinition("score-test", puzzle = Puzzle.TRAFFIC_JAM, tier = Tier.EASY, order = 1,
        initial = TrafficJamBoard(pieces = listOf(TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL))),
        reference = listOf(Translate("A", 6)), par = 5, parKind = ParKind.REFERENCE, targetTimeMs = 10_000,
        provenance = Provenance("Parlor", "test", 0, "original"))
    private fun result(moves: Int, time: Long = 10_000, hinted: Boolean = false): Result = requireNotNull(Scoring.result(level,
        Attempt.start("a", level).copy(completed = true, scoredMoves = moves, activeTimeMs = time, hintsUsed = hinted)))

    @Test fun starThresholdsRoundDownAndHintsOnlyCap() {
        assertEquals(3, result(5).stars)
        assertEquals(2, result(6).stars)
        assertEquals(2, result(7).stars)
        assertEquals(1, result(8).stars)
        assertEquals(2, result(4, hinted = true).stars)
        assertEquals(1, result(8, hinted = true).stars)
    }

    @Test fun timeBonusIsDeterministicAtEveryBoundary() {
        assertEquals(350, result(5, 0).points)
        assertEquals(350, result(5, 10_000).points)
        assertEquals(349, result(5, 10_001).points)
        assertEquals(325, result(5, 20_000).points)
        assertEquals(300, result(5, 30_000).points)
        assertEquals(300, result(5, Long.MAX_VALUE).points)
    }

    @Test fun timePuzzlesAndBacktracksUseSeparateRules() {
        val timed = level.copy(scoringUnit = ScoringUnit.TIME_AND_BACKTRACKS)
        fun score(time: Long, backtracks: Int) = Scoring.result(timed,
            Attempt.start("a", timed).copy(completed = true, activeTimeMs = time, backtracks = backtracks))!!.stars
        assertEquals(3, score(10_000, 0))
        assertEquals(2, score(10_000, 1))
        assertEquals(2, score(20_000, 0))
        assertEquals(1, score(20_001, 0))
    }

    @Test fun replaysImproveBestsIndependentlyWithoutFarming() {
        val best = Scoring.improve(level.id, null, result(5), "2026-09-28")
        assertEquals(best, Scoring.improve(level.id, best, result(5), "2026-09-29"))
        val faster = Scoring.improve(level.id, best, result(7, 1), "2026-09-29")
        assertEquals(3, faster.stars)
        assertEquals(350, faster.points)
        assertEquals(1L, faster.timeMs)
        assertEquals(5, faster.moves)
        assertNull(Scoring.result(level, Attempt.start("d", level, AttemptMode.DEMONSTRATION).copy(completed = true)))
    }

    @Test fun dateRolloverKeepsEarnedHintsAndRejectsClockRollback() {
        var ledger = RewardLedger(earnedHints = 7)
        ledger = DailyRules.rollover(ledger, "2026-09-28")
        repeat(3) { ledger = DailyRules.spendHint(ledger, "2026-09-28", false)!! }
        assertEquals(0, ledger.dailyHints)
        assertEquals(7, ledger.earnedHints)
        assertEquals(ledger, DailyRules.rollover(ledger, "2026-09-27"))
        assertEquals(ledger, DailyRules.rollover(ledger, "2026-09-28"))
        val next = DailyRules.rollover(ledger, "2026-10-01")
        assertEquals(3, next.dailyHints)
        assertEquals(7, next.earnedHints)
    }

    @Test fun rewardCallbacksAreIdempotentAndShareDailyCap() {
        var ledger = RewardLedger()
        repeat(10) { ledger = DailyRules.earnHints(ledger, "2026-09-28", "reward-$it")!! }
        assertEquals(20, ledger.earnedHints)
        assertNull(DailyRules.earnHints(ledger, "2026-09-28", "extra"))
        assertNull(DailyRules.earnHints(ledger, "2026-09-29", "reward-0"))
        assertNull(DailyRules.earnHints(ledger, "2026-09-27", "another"))
        assertNotNull(DailyRules.earnHints(ledger, "2026-09-29", "new"))
    }

    @Test fun streakDatesAndScheduleHandleHistoricalAndFutureDates() {
        val dates = setOf("2026-09-27", "2026-09-28", "2026-09-29", "2026-10-02")
        assertEquals(3, DailyRules.longestStreak(dates))
        assertEquals(3, DailyRules.currentStreak(dates, "2026-09-30"))
        assertEquals(0, DailyRules.currentStreak(dates, "2026-10-01"))
        for (date in listOf("1900-01-01", "2026-09-28", "2099-12-31")) {
            assertEquals(level, DailyRules.assignment(date, listOf(level)))
        }
    }

    @Test fun progressionUsesCeilingAndExcludesProVariants() {
        val easy = (1..4).map { level.copy(id = "easy-$it", order = it, proVariant = it == 4) }
        val medium = level.copy(id = "medium-1", tier = Tier.MEDIUM)
        val all = easy + medium
        val bests = easy.take(2).map { Scoring.improve(it.id, null, result(5), "2026-09-28") }
        assertEquals(Access.PREVIOUS_TIER, Progression.access(medium, all, bests, false))
        val third = Scoring.improve(easy[2].id, null, result(5), "2026-09-28")
        assertEquals(Access.AVAILABLE, Progression.access(medium, all, bests + third, false))
        assertEquals(Access.PRO, Progression.access(medium.copy(tier = Tier.HARD), all, bests, false))
        assertEquals(Access.AVAILABLE, Progression.access(medium, all, emptyList(), true))
    }
}
