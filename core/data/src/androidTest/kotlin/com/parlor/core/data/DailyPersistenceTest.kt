package com.parlor.core.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import com.parlor.core.progress.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class DailyPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: ParlorDatabase
    private lateinit var repo: ProgressRepository
    private var date = "2026-09-28"
    private var now = 1_000L
    private var pro = false
    private var ids = 0
    private val level = LevelDefinition("premium-daily", puzzle = Puzzle.TRAFFIC_JAM, tier = Tier.EXPERT, order = 1,
        initial = TrafficJamBoard(pieces = listOf(TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL), TrafficPiece("B", 2, 0, 3, Axis.VERTICAL))),
        reference = listOf(Translate("B", 3), Translate("A", 6)), par = 2, parKind = ParKind.PROVEN_OPTIMAL,
        targetTimeMs = 60_000, provenance = Provenance("Parlor", "fixture", 1, "original"))
    private val easy = level.copy(id = "easy-campaign", tier = Tier.EASY)
    private val catalog = Catalog(levels = listOf(easy, level))
    private val schedule = DailySchedule(listOf(ScheduleEpoch(1, "2026-09-28", listOf(LevelRef(level.id, 1)))))
    private fun open() {
        db = Room.databaseBuilder(context, ParlorDatabase::class.java, "daily-test.db").addMigrations(ParlorDatabase.MIGRATION_1_2).build()
        repo = ProgressRepository(db, { date }, { now }, { "daily-test-${ids++}" }, { pro })
        repo.configure(catalog, schedule)
    }
    @Before fun setup() { context.deleteDatabase("daily-test.db"); open() }
    @After fun teardown() { db.close(); context.deleteDatabase("daily-test.db") }
    private suspend fun solve(session: DailySession) {
        for (move in session.level.reference) repo.move(session.level, session.attempt.id, move, 100)
    }

    @Test fun freePremiumDailyScoresOnceAndPracticeStaysSeparateFromCampaign() = runBlocking {
        assertTrue(runCatching { repo.startOrResume(level) }.exceptionOrNull() is AccessDenied)
        val first = repo.startDaily()
        assertEquals(level, first.level)
        assertEquals(DailyEligibility.FIRST_COMPLETION, first.attempt.dailyEligibility)
        repo.useHint(level, first.attempt.id, 0)
        solve(first)
        assertEquals(2, repo.dailyEntry().record.best!!.stars)
        assertTrue(repo.bests.first().isEmpty())
        val practice = repo.startDaily()
        assertEquals(DailyEligibility.PRACTICE, practice.attempt.dailyEligibility)
        solve(practice)
        assertEquals(1, repo.dailyEntry().record.scoredCompletions)
        assertEquals(2, repo.dailyEntry().record.completions)
        assertEquals(250L, repo.player.first().profile.dailyTotals.getValue(Puzzle.TRAFFIC_JAM).points)
        assertTrue(runCatching { repo.startOrResume(level) }.exceptionOrNull() is AccessDenied)
    }

    @Test fun concurrentOpenReturnsOneAttemptAndDuplicateFinishIsIdempotent() = runBlocking {
        val sessions = coroutineScope { List(4) { async { repo.startDaily() } }.awaitAll() }
        assertEquals(1, sessions.map { it.attempt.id }.toSet().size)
        val first = sessions.first()
        repo.move(level, first.attempt.id, level.reference.first(), 10)
        coroutineScope { List(4) { async { repo.move(level, first.attempt.id, level.reference.last(), 10) } }.awaitAll() }
        assertEquals(1, repo.completionCount.first())
        assertEquals(1, repo.dailyEntry().record.scoredCompletions)
        assertEquals(350L, repo.player.first().profile.dailyTotals.getValue(Puzzle.TRAFFIC_JAM).points)
    }

    @Test fun rewardedRetrySurvivesMidnightAndReopenWithoutChangingItsDate() = runBlocking {
        val first = repo.startDaily()
        repo.useHint(level, first.attempt.id, 0)
        solve(first)
        assertTrue(repo.grantReward("reward-1", Reward.DailyRetry(date)))
        assertFalse(repo.grantReward("reward-1", Reward.DailyRetry(date)))
        val retry = repo.startDaily()
        assertEquals(DailyEligibility.REWARDED, retry.attempt.dailyEligibility)
        repo.move(level, retry.attempt.id, level.reference.first(), 500)
        val originalDate = date
        date = "2026-09-29"
        repo.refreshDay()
        db.close(); open()
        val restored = repo.startDaily(originalDate)
        assertEquals(retry.attempt.id, restored.attempt.id)
        assertEquals(originalDate, restored.attempt.challengeDate)
        assertEquals(DailyEligibility.REWARDED, restored.attempt.dailyEligibility)
        assertFalse(repo.grantReward("late", Reward.DailyRetry(originalDate)))
        repo.move(level, restored.attempt.id, level.reference.last(), 500)
        val ledger = repo.player.first().ledger
        assertEquals(setOf(originalDate, date), ledger.creditedDates)
        assertEquals(1, ledger.dailyClaims.getValue(originalDate).consumedRetries)
        assertEquals(350L, repo.player.first().profile.dailyTotals.getValue(Puzzle.TRAFFIC_JAM).points)
    }

    @Test fun proRetriesPreserveEarnedCreditsAndUnfinishedEligibilityAfterRevocation() = runBlocking {
        solve(repo.startDaily())
        assertTrue(repo.grantReward("retry", Reward.DailyRetry(date)))
        pro = true
        val proAttempt = repo.startDaily()
        assertEquals(DailyEligibility.PRO, proAttempt.attempt.dailyEligibility)
        pro = false
        assertEquals(DailyEligibility.PRO, repo.startDaily().attempt.dailyEligibility)
        solve(proAttempt)
        assertEquals(0, repo.player.first().ledger.dailyClaims.getValue(date).consumedRetries)
        assertEquals(DailyEligibility.REWARDED, repo.startDaily().attempt.dailyEligibility)
    }

    @Test fun pruningAndResetKeepClaimsHintsStreaksAndEarnedAchievements() = runBlocking {
        solve(repo.startDaily())
        assertTrue(repo.grantReward("hints", Reward.Hints))
        val before = repo.player.first()
        repo.resetProgress()
        val after = repo.player.first()
        assertEquals(before.ledger, after.ledger)
        assertEquals(before.profile.achievements, after.profile.achievements)
        assertTrue(after.profile.dailyTotals.isEmpty())
        assertEquals(DailyEligibility.PRACTICE, repo.startDaily().attempt.dailyEligibility)
        date = "2026-10-28"
        repo.refreshDay()
        assertTrue(repo.dailies.first().isEmpty())
        assertTrue(repo.player.first().ledger.dailyClaims.getValue("2026-09-28").firstCompletionClaimed)
        date = "2026-09-28"
        repo.refreshDay()
        assertTrue(runCatching { repo.startDaily() }.exceptionOrNull() is DailyUnavailable)
        assertFalse(repo.grantReward("hints", Reward.Hints))
        assertEquals(2, repo.player.first().ledger.earnedHints)
    }

    @Test fun temporaryCampaignAccessExpiresOnReturnButAnActiveBoardCanFinish() = runBlocking {
        assertTrue(repo.grantReward("access", Reward.LevelAccess(level.id)))
        val attempt = repo.startOrResume(level)
        now += 86_400_000
        assertTrue(runCatching { repo.startOrResume(level) }.exceptionOrNull() is AccessDenied)
        assertTrue(runCatching { repo.checkReturnAccess(level, attempt.id) }.exceptionOrNull() is AccessDenied)
        for (move in level.reference) repo.move(level, attempt.id, move, 0)
        assertEquals(3, repo.bests.first().single().stars)
        assertTrue(runCatching { repo.restart(level, attempt.id) }.exceptionOrNull() is AccessDenied)
    }

    @Test fun openedDailyRetainsOriginalRevisionAcrossScheduleUpdates() = runBlocking {
        val original = repo.dailyEntry()
        val replacement = easy.copy(revision = 2)
        repo.configure(Catalog(levels = listOf(replacement)), DailySchedule(listOf(ScheduleEpoch(2, date, listOf(LevelRef(replacement.id, 2))))))
        assertEquals(original, repo.dailyEntry())
        solve(repo.startDaily())
        assertEquals(3, repo.dailyEntry().record.best!!.stars)
    }

    @Test fun activeDailyCanFinishAcrossHistoryExpiryAndIsPrunedAfterLeaving() = runBlocking {
        val session = repo.startDaily()
        repo.move(level, session.attempt.id, level.reference.first(), 100)
        repo.retainActiveDaily(session.attempt.id, date)
        date = "2026-10-28"
        repo.refreshDay()
        assertTrue(runCatching { repo.startDaily("2026-09-28") }.exceptionOrNull() is DailyUnavailable)
        assertTrue(repo.move(level, session.attempt.id, level.reference.last(), 100)!!.completed)
        assertEquals(350L, repo.player.first().profile.dailyTotals.getValue(Puzzle.TRAFFIC_JAM).points)
        repo.retainActiveDaily(session.attempt.id, null)
        repo.refreshDay()
        assertTrue(repo.dailies.first().isEmpty())
        assertNull(repo.observeAttempt(session.attempt.id).first())
        assertEquals(setOf(date), repo.player.first().ledger.creditedDates)
    }

    @Test fun newlyEarnedRetryNeedsExplicitRestartToChangeAnActivePracticeAttempt() = runBlocking {
        solve(repo.startDaily())
        val practice = repo.startDaily()
        repo.move(level, practice.attempt.id, level.reference.first(), 100)
        assertTrue(repo.grantReward("new-retry", Reward.DailyRetry(date)))
        assertEquals(practice.attempt.id, repo.startDaily().attempt.id)
        assertEquals(DailyEligibility.PRACTICE, repo.startDaily().attempt.dailyEligibility)
        val restarted = repo.restart(level, practice.attempt.id)
        assertEquals(DailyEligibility.REWARDED, restarted.dailyEligibility)
        assertEquals(level.initial, restarted.board)
        solve(DailySession(level, restarted))
        assertEquals(1, repo.player.first().ledger.dailyClaims.getValue(date).consumedRetries)
    }

    @Test fun failedDailyCompletionRollsBackClaimScoreReceiptAndBoard() = runBlocking {
        val session = repo.startDaily()
        val before = repo.move(level, session.attempt.id, level.reference.first(), 100)!!
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_daily BEFORE UPDATE ON daily BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        assertTrue(runCatching { repo.move(level, before.id, level.reference.last(), 100) }.isFailure)
        assertEquals(before, repo.observeAttempt(before.id).first())
        assertEquals(0, repo.completionCount.first())
        assertNull(repo.dailyEntry().record.best)
        assertTrue(repo.player.first().ledger.dailyClaims.isEmpty())
    }
}
