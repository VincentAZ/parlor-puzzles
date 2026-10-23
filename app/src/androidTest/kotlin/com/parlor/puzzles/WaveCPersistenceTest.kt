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

class WaveCPersistenceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="wave-c-persistence-test.db"
    private lateinit var database:ParlorDatabase
    private lateinit var repository:ProgressRepository
    private lateinit var catalog:Catalog
    private lateinit var schedule:DailySchedule
    private var pro=true
    private val date="2026-10-07"
    private fun open() {
        database=Room.databaseBuilder(context,ParlorDatabase::class.java,name).build()
        repository=ProgressRepository(database,today={date},isPro={pro});repository.configure(catalog,schedule)
    }
    @Before fun before()=runBlocking {context.deleteDatabase(name);catalog=ContentRepository(context).load();schedule=ContentRepository(context).dailySchedule();open()}
    @After fun after() {database.close();context.deleteDatabase(name)}

    @Test fun everyNewExpertBoardRestoresItsHistoryCountersHintsAndViewport()=runBlocking {
        for(id in listOf("pegs-031","queens-024","knight-024","crossings-031")) {
            val level=catalog.levels.single {it.id==id};val engine=engineFor(level.puzzle)
            val attempt=repository.startOrResume(level)
            repository.move(level,attempt.id,level.reference.first(),300)
            repository.useHint(level,attempt.id,200)
            repository.undo(level,attempt.id,100)
            repository.saveViewport(level,attempt.id,Viewport(2,2,2))
            val expected=repository.observeAttempt(attempt.id).first()!!
            database.close();open()
            assertEquals(expected,repository.startOrResume(level))
            assertEquals(600L,expected.activeTimeMs);assertTrue(expected.hintsUsed)
            val redo=repository.redo(level,attempt.id,0)!!
            val cost=engine.cost(level.initial,level.reference.first())
            assertEquals(cost*2+if(level.scoringUnit==ScoringUnit.SCENARIO_MINUTES) cost else 1,redo.scoredMoves)
            assertTrue(hintFor(level,redo) is Hint.Next)
            if(level.puzzle==Puzzle.KNIGHTS_TOUR) assertEquals(1,redo.backtracks)
            level.reference.drop(1).forEach {repository.move(level,attempt.id,it,0)}
            val completed=repository.observeAttempt(attempt.id).first()!!
            assertTrue(completed.completed);assertTrue(completed.hintsUsed)
            assertEquals(2,repository.bests.first().single {it.levelId==id}.stars)
            assertNull(repository.move(level,attempt.id,level.reference.last(),0))
        }
        assertEquals(4,repository.completionCount.first())
        assertTrue("knight-closed-eight" in repository.player.first().profile.achievements)
    }

    @Test fun freeDailyClosedTourPreservesBacktracksAndAwardsOnlyDailyProgress()=runBlocking {
        pro=false
        val level=catalog.levels.single {it.id=="knight-024"}
        schedule=DailySchedule(listOf(ScheduleEpoch(99,date,listOf(LevelRef(level.id,level.revision)))))
        repository.configure(catalog,schedule)
        try {repository.startOrResume(level);fail("Expert campaign is Pro")} catch(_:AccessDenied) {}
        val daily=repository.startDaily()
        repository.move(level,daily.attempt.id,level.reference.first(),300)
        repository.undo(level,daily.attempt.id,100)
        val expected=repository.observeAttempt(daily.attempt.id).first()!!
        database.close();open()
        assertEquals(expected,repository.startDaily().attempt)
        val redo=repository.redo(level,daily.attempt.id,0)!!
        assertEquals(1,redo.backtracks);assertFalse(redo.hintsUsed)
        level.reference.drop(1).forEach {repository.move(level,daily.attempt.id,it,0)}
        assertEquals(2,repository.dailies.first().single().record.best!!.stars)
        assertTrue(repository.bests.first().isEmpty())
        assertTrue("knight-closed-eight" in repository.player.first().profile.achievements)
        try {repository.startOrResume(level);fail("Daily does not grant campaign access")} catch(_:AccessDenied) {}
    }

    @Test fun fourthEpochIncludesAllTwelveFamiliesAndPreservesEarlierDates() {
        val previous=DailySchedule(schedule.epochs.filter {it.version<4})
        for(day in listOf("2026-09-28","2026-10-01","2026-10-04","2026-10-06")) assertEquals(DailyRules.assignment(day,previous),DailyRules.assignment(day,schedule))
        val next=schedule.epochs.single {it.version==4}
        assertEquals("2026-10-07",next.effectiveFrom);assertEquals(56,next.pool.size)
        assertEquals(Puzzle.entries.toSet(),next.pool.map {ref->catalog.levels.single {it.id==ref.id}.puzzle}.toSet())
    }
}
