package com.parlor.core.data

import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.game.AttemptValidation
import com.parlor.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerializationException
import org.junit.*
import org.junit.Assert.*

class SavedBestValidationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "saved-best-validation.db"
    private lateinit var db: ParlorDatabase
    private lateinit var repo: ProgressRepository
    private val health = StorageHealth()
    private val day = "2026-09-28"
    private val level = LevelDefinition("saved-best-traffic", puzzle = Puzzle.TRAFFIC_JAM, tier = Tier.EASY, order = 1,
        initial = TrafficJamBoard(pieces = listOf(TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL))),
        reference = listOf(Translate("A", 6)), par = 1, parKind = ParKind.PROVEN_OPTIMAL,
        targetTimeMs = 60_000, provenance = Provenance("Parlor", "saved best fixture", 0, "original"))
    private val good = LevelBest(level.id, 2, 250, 10_000, 2, day)
    private fun row(best: LevelBest) = BestRow(level.id, ParlorJson.encodeToString(best))
    private fun open() {
        db = ParlorDatabase.open(context, name)
        repo = ProgressRepository(db, today = { day }, storageHealth = health)
        repo.configure(Catalog(levels = listOf(level)), DailySchedule(listOf(ScheduleEpoch(1, day, listOf(LevelRef(level.id, level.revision))))))
    }
    @Before fun before() { context.deleteDatabase(name); open() }
    @After fun after() { db.close(); context.deleteDatabase(name) }

    @Test fun readableJsonWithInvalidScoresDatesOrRowIdentityIsPreservedAndNeverEmitted() = runBlocking {
        val invalid = listOf(good.copy(stars = 4), good.copy(stars = 0), good.copy(stars = -1), good.copy(stars = Int.MAX_VALUE),
            good.copy(points = 199), good.copy(points = 251), good.copy(points = Int.MAX_VALUE),
            good.copy(timeMs = -1), good.copy(timeMs = AttemptValidation.MAX_TIME + 1),
            good.copy(moves = -1), good.copy(moves = 100_000_001),
            good.copy(firstCompletedDate = "2026-02-30"), good.copy(firstCompletedDate = "2026-9-28"),
            good.copy(levelId = "other-level"), good.copy(levelId = ""))
        invalid.forEach { best ->
            health.retry()
            val saved = row(best); db.progress().saveBest(saved)
            assertTrue("Invalid best escaped: $best", withTimeout(5_000) { repo.bests.take(1).toList() }.isEmpty())
            assertEquals(StorageIssue(StorageArea.PROGRESS, StorageProblem.DAMAGED), health.state.value.issue)
            assertEquals(saved, db.progress().allBests().single())
        }
    }

    @Test fun corruptionEndsObservationWithoutAnEmptyReplacementAndRetryNeedsValidData() = runBlocking {
        db.progress().saveBest(row(good))
        val seen = mutableListOf<List<LevelBest>>()
        val first = CompletableDeferred<Unit>()
        val observer = launch { repo.bests.collect { seen += it; first.complete(Unit) } }
        withTimeout(5_000) { first.await() }
        val broken = row(good.copy(stars = 4)); db.progress().saveBest(broken)
        withTimeout(5_000) { observer.join() }
        assertEquals(listOf(listOf(good)), seen)
        assertEquals(StorageProblem.DAMAGED, health.state.value.issue?.problem)
        db.close(); open(); health.retry()
        assertTrue(withTimeout(5_000) { repo.bests.take(1).toList() }.isEmpty())
        assertEquals(broken, db.progress().allBests().single())
        db.progress().saveBest(row(good)); health.retry()
        assertEquals(listOf(good), repo.bests.first()); assertNull(health.state.value.issue)
    }

    @Test fun aMalformedBestRollsBackCompletionAndRepairAllowsExactlyOneAward() = runBlocking {
        db.progress().saveBest(row(good))
        val attempt = repo.startOrResume(level)
        val player = repo.player.first()
        val broken = row(good.copy(stars = 4)); db.progress().saveBest(broken)
        val failure = runCatching { repo.move(level, attempt.id, level.reference.single(), 50) }.exceptionOrNull()
        assertTrue("Invalid stored best must reject completion", failure is SerializationException)
        assertEquals(attempt, repo.observeAttempt(attempt.id).first())
        assertEquals(player, repo.player.first()); assertEquals(0, repo.completionCount.first())
        assertEquals(broken, db.progress().allBests().single())
        db.close(); open()
        assertEquals(attempt, repo.observeAttempt(attempt.id).first())
        assertEquals(player, repo.player.first()); assertEquals(0, repo.completionCount.first())
        db.progress().saveBest(row(good)); health.retry()
        assertTrue(repo.move(level, attempt.id, level.reference.single(), 50)!!.completed)
        assertEquals(1, repo.completionCount.first()); assertEquals(350, repo.bests.first().single().points)
        assertNull(repo.move(level, attempt.id, level.reference.single(), 50))
        assertEquals(1, repo.completionCount.first()); assertEquals(setOf(day), repo.player.first().ledger.creditedDates)
    }
}
