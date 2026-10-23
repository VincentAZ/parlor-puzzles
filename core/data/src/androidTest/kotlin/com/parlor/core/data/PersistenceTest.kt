package com.parlor.core.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: ParlorDatabase
    private lateinit var repository: ProgressRepository
    private var ids = 0
    private val name = "persistence-test.db"
    private val level = LevelDefinition("traffic-test", puzzle = Puzzle.TRAFFIC_JAM, tier = Tier.EASY, order = 1,
        initial = TrafficJamBoard(pieces = listOf(TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL), TrafficPiece("B", 2, 0, 3, Axis.VERTICAL))),
        reference = listOf(Translate("B", 3), Translate("A", 6)), par = 2, parKind = ParKind.PROVEN_OPTIMAL,
        targetTimeMs = 60_000, provenance = Provenance("Parlor", "fixture", 0, "original"))

    private fun open() {
        database = Room.databaseBuilder(context, ParlorDatabase::class.java, name).build()
        repository = ProgressRepository(database, today = { "2026-09-28" }, newId = { "test-${ids++}" })
        repository.configure(Catalog(levels = listOf(level)), DailySchedule(listOf(ScheduleEpoch(1, "2026-09-28", listOf(LevelRef(level.id, level.revision))))))
    }
    @Before fun before() { context.deleteDatabase(name); open() }
    @After fun after() { database.close(); context.deleteDatabase(name) }

    @Test fun boardUndoHistoryTimerSelectionAndHintFlagSurviveDatabaseReopen() = runBlocking {
        val attempt = repository.startOrResume(level)
        repository.move(level, attempt.id, Translate("B", 3), 700)
        repository.useHint(level, attempt.id, 100)
        repository.undo(level, attempt.id, 200)
        repository.saveViewport(level, attempt.id, Viewport(1, 2, 2, "B"))
        database.close()
        open()
        val restored = repository.startOrResume(level)
        assertEquals(attempt.id, restored.id)
        assertEquals(level.initial, restored.board)
        assertEquals(2, restored.scoredMoves)
        assertEquals(1_000L, restored.activeTimeMs)
        assertTrue(restored.hintsUsed)
        assertEquals(1, restored.redo.size)
        assertEquals(Viewport(1, 2, 2, "B"), restored.viewport)
        assertEquals(2, repository.player.first().ledger.dailyHints)
        repository.redo(level, restored.id, 100)
        assertEquals(3, repository.observeAttempt(restored.id).first()!!.scoredMoves)
    }

    @Test fun duplicateCompletionAndReplaysNeverFarmBests() = runBlocking {
        val a = repository.startOrResume(level)
        repository.move(level, a.id, Translate("B", 3), 0)
        coroutineScope {
            repeat(5) { launch { repository.move(level, a.id, Translate("A", 6), 0) } }
        }
        assertEquals(1, repository.completionCount.first())
        val best = repository.bests.first().single()
        assertEquals(3, best.stars)
        assertEquals(350, best.points)
        val replay = repository.startOrResume(level)
        assertNotEquals(a.id, replay.id)
        repository.move(level, replay.id, Translate("B", 3), 0)
        repository.move(level, replay.id, Translate("A", 6), 0)
        assertEquals(2, repository.completionCount.first())
        assertEquals(listOf(best), repository.bests.first())
        assertEquals(setOf("2026-09-28"), repository.player.first().ledger.creditedDates)
    }

    @Test fun failedCompletionTransactionLeavesLastDurableBoardAndNoReward() = runBlocking {
        val a = repository.startOrResume(level)
        val before = repository.move(level, a.id, Translate("B", 3), 50)!!
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_best BEFORE INSERT ON bests BEGIN SELECT RAISE(ABORT, 'simulated full disk'); END")
        var failed = false
        try { repository.move(level, a.id, Translate("A", 6), 50) } catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertEquals(before, repository.observeAttempt(a.id).first())
        assertEquals(0, repository.completionCount.first())
        assertTrue(repository.bests.first().isEmpty())
        assertEquals(50L, repository.player.first().totalPlayTimeMs)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_best")
        assertTrue(repository.move(level, a.id, Translate("A", 6), 50)!!.completed)
        assertEquals(1, repository.completionCount.first())
    }

    @Test fun restartKeepsSpentHintsAndRejectsInvalidMovesWithoutLosingTime() = runBlocking {
        val a = repository.startOrResume(level)
        assertNull(repository.move(level, a.id, Translate("A", 4), 100))
        val after = repository.observeAttempt(a.id).first()!!
        assertEquals(level.initial, after.board)
        assertEquals(100L, after.activeTimeMs)
        repository.useHint(level, a.id, 0)
        val restarted = repository.restart(level, a.id)
        assertEquals(0L, restarted.activeTimeMs)
        assertFalse(restarted.hintsUsed)
        assertEquals(2, repository.player.first().ledger.dailyHints)
        assertEquals(100L, repository.player.first().totalPlayTimeMs)
    }
}
