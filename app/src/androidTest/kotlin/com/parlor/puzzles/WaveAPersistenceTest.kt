package com.parlor.puzzles

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.data.*
import com.parlor.core.game.*
import com.parlor.core.model.*
import com.parlor.core.progress.DailyRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WaveAPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "wave-a-persistence-test.db"
    private lateinit var database: ParlorDatabase
    private lateinit var repository: ProgressRepository
    private lateinit var catalog: Catalog
    private lateinit var schedule: DailySchedule
    private var pro = true
    private var date = "2026-10-01"

    private fun open() {
        database = Room.databaseBuilder(context, ParlorDatabase::class.java, name).build()
        repository = ProgressRepository(database, today = { date }, isPro = { pro })
        repository.configure(catalog, schedule)
    }
    @Before fun before() = runBlocking {
        context.deleteDatabase(name)
        catalog = ContentRepository(context).load()
        schedule = ContentRepository(context).dailySchedule()
        open()
    }
    @After fun after() { database.close(); context.deleteDatabase(name) }

    @Test fun allNewFamiliesRestoreHintHistorySelectionAndScoringFromDisk() = runBlocking {
        val ids = listOf("hanoi-011", "switch-046", "jugs-039", "pancake-031")
        for (level in catalog.levels.filter { it.id in ids }) {
            val attempt = repository.startOrResume(level)
            repository.move(level, attempt.id, level.reference.first(), 700)
            repository.useHint(level, attempt.id, 100)
            repository.undo(level, attempt.id, 200)
            val viewport = when (level.puzzle) {
                Puzzle.SWITCH_GRID -> Viewport(5, 4, 2)
                Puzzle.PANCAKE_FLIP -> Viewport(selectedId = "9")
                else -> Viewport(selectedId = "0")
            }
            repository.saveViewport(level, attempt.id, viewport)
            val durable = repository.observeAttempt(attempt.id).first()!!
            database.close(); open()
            val restored = repository.startOrResume(level)
            assertEquals(durable, restored)
            assertEquals(level.initial, restored.board)
            assertEquals(1, restored.redo.size)
            assertTrue(restored.hintsUsed)
            assertEquals(1_000L, restored.activeTimeMs)
            val replayed = repository.redo(level, attempt.id, 0)!!
            assertEquals(3, replayed.scoredMoves)
            val hint = hintFor(level, replayed)
            assertTrue(hint is Hint.Next)
            level.reference.drop(1).forEach { repository.move(level, attempt.id, it, 0) }
            val result = repository.observeAttempt(attempt.id).first()!!
            assertTrue(result.completed)
            assertTrue(result.hintsUsed)
            assertEquals(level.par + 2, result.scoredMoves)
            assertEquals(2, repository.bests.first().single { it.levelId == level.id }.stars)
            val before = repository.player.first()
            assertNull(repository.move(level, attempt.id, level.reference.last(), 0))
            assertEquals(before, repository.player.first())
        }
        assertEquals(4, repository.completionCount.first())
    }

    @Test fun fourPegDailyIsFreeAndItsProgressDoesNotOpenTheCampaignVariant() = runBlocking {
        pro = false
        val level = catalog.levels.single { it.id == "hanoi-011" }
        repository.configure(catalog, DailySchedule(listOf(ScheduleEpoch(9, date, listOf(LevelRef(level.id, level.revision))))))
        try { repository.startOrResume(level); fail("Campaign variant requires Pro") } catch (_: AccessDenied) { }
        val daily = repository.startDaily()
        assertEquals(DailyEligibility.FIRST_COMPLETION, daily.attempt.dailyEligibility)
        level.reference.forEach { assertNotNull(repository.move(level, daily.attempt.id, it, 0)) }
        assertEquals(3, repository.dailies.first().single().record.best!!.stars)
        assertTrue(repository.bests.first().isEmpty())
        assertEquals(DailyEligibility.PRACTICE, repository.startDaily().attempt.dailyEligibility)
        try { repository.startOrResume(level); fail("Daily must not unlock campaign") } catch (_: AccessDenied) { }
        pro = true
        assertEquals(level.initial, repository.startOrResume(level).board)
    }

    @Test fun contentUpgradePreservesTheOldDailyEpochAndAddsAllFiveFamilies() {
        val old = DailySchedule(listOf(schedule.epochs.first()))
        for (day in 28..30) assertEquals(DailyRules.assignment("2026-09-$day", old), DailyRules.assignment("2026-09-$day", schedule))
        val new = schedule.epochs.single { it.version == 2 }
        assertEquals("2026-10-01", new.effectiveFrom)
        assertEquals(28, new.pool.size)
        assertEquals(5, new.pool.map { ref -> catalog.levels.single { it.id == ref.id }.puzzle }.distinct().size)
    }
}
