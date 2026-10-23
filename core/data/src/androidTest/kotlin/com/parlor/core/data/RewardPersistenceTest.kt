package com.parlor.core.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class RewardPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: ParlorDatabase
    private lateinit var repo: ProgressRepository
    private var date = "2026-09-28"
    private var now = 1_000L
    private var pro = false
    private val level = LevelDefinition("reward-premium", puzzle = Puzzle.TRAFFIC_JAM, tier = Tier.EXPERT, order = 1,
        initial = TrafficJamBoard(pieces = listOf(TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL))),
        reference = listOf(Translate("A", 6)), par = 1, parKind = ParKind.PROVEN_OPTIMAL,
        targetTimeMs = 60_000, provenance = Provenance("Parlor", "reward persistence fixture", 1, "original"))
    private val catalog = Catalog(levels = listOf(level.copy(id = "reward-easy", tier = Tier.EASY), level))
    private val schedule = DailySchedule(listOf(ScheduleEpoch(1, "2026-09-28", listOf(LevelRef(level.id, 1)))))
    private fun open() {
        db = Room.databaseBuilder(context, ParlorDatabase::class.java, "reward-persistence-test.db").build()
        repo = ProgressRepository(db, { date }, { now }, isPro = { pro })
        repo.configure(catalog, schedule)
    }
    @Before fun setup() { context.deleteDatabase("reward-persistence-test.db"); open() }
    @After fun teardown() { db.close(); context.deleteDatabase("reward-persistence-test.db") }

    @Test fun earnedCallbacksApplyOnceAfterMidnightProUpgradeResetAndDatabaseReopen() = runBlocking {
        val receipt = EarnedReward("reward-recovered", Reward.LevelAccess(level.id), date, now)
        assertTrue(repo.canOfferReward(receipt.reward))
        date = "2026-09-29"; now += 90_000_000; pro = true; repo.refreshDay()
        assertFalse(repo.canOfferReward(Reward.Hints))
        val outcomes = coroutineScope { List(5) { async { repo.applyEarnedReward(receipt) } }.awaitAll() }
        assertEquals(1, outcomes.count { it == RewardApplication.APPLIED })
        assertEquals(4, outcomes.count { it == RewardApplication.ALREADY_APPLIED })
        repo.resetProgress(); db.close(); open()
        assertEquals(RewardApplication.ALREADY_APPLIED, repo.applyEarnedReward(receipt))
        val ledger = repo.player.first().ledger
        assertEquals(86_401_000L, ledger.temporaryUnlocks[level.id])
        assertEquals(mapOf("2026-09-28" to 1), ledger.rewardedViewsByDate)
        assertEquals(0, ledger.rewardedViews)
        assertEquals(3, ledger.dailyHints)
    }

    @Test fun allThreeRewardsShareThePersistedCompletedViewCap() = runBlocking {
        assertFalse(repo.canOfferReward(Reward.DailyRetry(date)))
        val daily = repo.startDaily(); repo.move(level, daily.attempt.id, level.reference.single(), 100)
        assertTrue(repo.canOfferReward(Reward.DailyRetry(date)))
        repeat(8) { assertEquals(RewardApplication.APPLIED, repo.applyEarnedReward(EarnedReward("hint-$it", Reward.Hints, date, now))) }
        assertEquals(RewardApplication.APPLIED, repo.applyEarnedReward(EarnedReward("retry", Reward.DailyRetry(date), date, now)))
        assertEquals(RewardApplication.APPLIED, repo.applyEarnedReward(EarnedReward("unlock", Reward.LevelAccess(level.id), date, now)))
        assertFalse(repo.canOfferReward(Reward.Hints))
        assertEquals(RewardApplication.REJECTED, repo.applyEarnedReward(EarnedReward("eleventh", Reward.Hints, date, now)))
        db.close(); open()
        val ledger = repo.player.first().ledger
        assertEquals(10, ledger.rewardedViews); assertEquals(16, ledger.earnedHints)
        assertEquals(1, ledger.dailyClaims.getValue(date).earnedRetries)
        assertEquals(DailyEligibility.REWARDED, repo.startDaily().attempt.dailyEligibility)
        date = "2026-09-29"
        assertTrue(repo.canOfferReward(Reward.Hints))
        assertFalse(repo.canOfferReward(Reward.DailyRetry("2026-09-28")))
        date = "2026-09-28"
        assertFalse(repo.canOfferReward(Reward.Hints))
    }

    @Test fun dailyCallbackEarnedBeforeMidnightCanRecoverLaterWithoutChangingTheChallenge() = runBlocking {
        val first = repo.startDaily(); repo.move(level, first.attempt.id, level.reference.single(), 100)
        val oldDate = date
        val receipt = EarnedReward("daily-pending", Reward.DailyRetry(date), date, now)
        date = "2026-09-29"; repo.refreshDay(); db.close(); open()
        assertEquals(RewardApplication.APPLIED, repo.applyEarnedReward(receipt))
        val retry = repo.startDaily(oldDate)
        assertEquals(oldDate, retry.attempt.challengeDate)
        assertEquals(DailyEligibility.REWARDED, retry.attempt.dailyEligibility)
        assertEquals(0, repo.player.first().ledger.rewardedViews)
        assertEquals(1, repo.player.first().ledger.rewardedViewsByDate[oldDate])
    }

    @Test fun failedRewardTransactionLeavesTheBalanceCapAndClaimUnchanged() = runBlocking {
        repo.refreshDay()
        val before = repo.player.first().ledger
        val receipt = EarnedReward("retry-after-write-failure", Reward.Hints, date, now)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_reward BEFORE INSERT ON player BEGIN SELECT RAISE(ABORT, 'simulated full disk'); END")
        assertTrue(runCatching { repo.applyEarnedReward(receipt) }.isFailure)
        assertEquals(before, repo.player.first().ledger)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_reward")
        assertEquals(RewardApplication.APPLIED, repo.applyEarnedReward(receipt))
        assertEquals(RewardApplication.ALREADY_APPLIED, repo.applyEarnedReward(receipt))
        assertEquals(2, repo.player.first().ledger.earnedHints)
        assertEquals(1, repo.player.first().ledger.rewardedViews)
    }

    @Test fun earningARetryPreservesPracticeUntilTheExplicitStartAndRepeatedStartKeepsScoredProgress() = runBlocking {
        val first = repo.startDaily(); repo.move(level, first.attempt.id, level.reference.single(), 100)
        val practice = repo.startDaily(); repo.move(level, practice.attempt.id, Translate("A", 1), 100)
        assertEquals(RewardApplication.APPLIED, repo.applyEarnedReward(EarnedReward("earned-retry", Reward.DailyRetry(date), date, now)))
        assertEquals(practice.attempt.id, repo.startDaily().attempt.id)
        assertEquals(1, repo.startDaily().attempt.scoredMoves)
        val retry = repo.startScoredDailyRetry(date)
        assertNotEquals(practice.attempt.id, retry.attempt.id)
        assertEquals(DailyEligibility.REWARDED, retry.attempt.dailyEligibility)
        assertEquals(0, retry.attempt.scoredMoves)
        assertEquals(level.initial, retry.attempt.board)
        repo.move(level, retry.attempt.id, Translate("A", 1), 100)
        assertEquals(retry.attempt.id, repo.startScoredDailyRetry(date).attempt.id)
        assertEquals(1, repo.startScoredDailyRetry(date).attempt.scoredMoves)
        assertEquals(0, repo.player.first().ledger.dailyClaims.getValue(date).consumedRetries)
        repo.move(level, retry.attempt.id, level.reference.single(), 100)
        assertEquals(1, repo.player.first().ledger.dailyClaims.getValue(date).consumedRetries)
    }

    @Test fun startingWithoutARetryDoesNotDiscardAnUnfinishedPracticeBoard() = runBlocking {
        val first = repo.startDaily(); repo.move(level, first.attempt.id, level.reference.single(), 100)
        val practice = repo.startDaily(); repo.move(level, practice.attempt.id, Translate("A", 1), 100)
        val before = repo.startDaily().attempt
        assertTrue(runCatching { repo.startScoredDailyRetry(date) }.isFailure)
        assertEquals(before, repo.startDaily().attempt)
    }
}
