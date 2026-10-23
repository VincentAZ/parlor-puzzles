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

class WaveBPersistenceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="wave-b-persistence-test.db"
    private lateinit var database:ParlorDatabase
    private lateinit var repository:ProgressRepository
    private lateinit var catalog:Catalog
    private lateinit var schedule:DailySchedule
    private var pro=true
    private val date="2026-10-04"
    private fun open() {
        database=Room.databaseBuilder(context,ParlorDatabase::class.java,name).build()
        repository=ProgressRepository(database,today={date},isPro={pro});repository.configure(catalog,schedule)
    }
    @Before fun before()=runBlocking {context.deleteDatabase(name);catalog=ContentRepository(context).load();schedule=ContentRepository(context).dailySchedule();open()}
    @After fun after() {database.close();context.deleteDatabase(name)}

    @Test fun newBoardsRestoreUndoRedoHintFlagsWalkingAndPicturePreferences()=runBlocking {
        for(id in listOf("tiles-046","blocks-039","boxes-046")) {
            val level=catalog.levels.single {it.id==id}
            val attempt=repository.startOrResume(level)
            repository.move(level,attempt.id,level.reference.first(),300)
            repository.useHint(level,attempt.id,200)
            repository.undo(level,attempt.id,100)
            repository.saveViewport(level,attempt.id,Viewport(2,3,2,"A",showLabels=false))
            val expected=repository.observeAttempt(attempt.id).first()!!
            database.close();open()
            assertEquals(expected,repository.startOrResume(level))
            assertEquals(600L,expected.activeTimeMs)
            assertTrue(expected.hintsUsed);assertFalse(expected.viewport.showLabels)
            val redo=repository.redo(level,attempt.id,0)!!
            assertEquals(2*engineFor(level.puzzle).cost(level.initial,level.reference.first())+1,redo.scoredMoves)
            assertEquals(2*engineFor(level.puzzle).walkingCost(level.initial,level.reference.first()),redo.walkingSteps)
            assertTrue(hintFor(level,redo) is Hint.Next)
            level.reference.drop(1).forEach {repository.move(level,attempt.id,it,0)}
            val completed=repository.observeAttempt(attempt.id).first()!!
            assertTrue(completed.completed);assertTrue(completed.hintsUsed)
            assertEquals(2,repository.bests.first().single {it.levelId==id}.stars)
            assertNull(repository.move(level,attempt.id,level.reference.last(),0))
        }
        assertEquals(3,repository.completionCount.first())
    }

    @Test fun freeDailyBundledPictureKeepsArtAndControlsWithoutCampaignOwnership()=runBlocking {
        pro=false
        val level=catalog.levels.single {it.id=="tiles-046"}
        schedule=DailySchedule(listOf(ScheduleEpoch(99,date,listOf(LevelRef(level.id,level.revision)))))
        repository.configure(catalog,schedule)
        try {repository.startOrResume(level);fail("Image campaign sample is Pro")} catch(_:AccessDenied) {}
        val daily=repository.startDaily()
        assertEquals(TileArt.GARDEN,(daily.attempt.board as TilesBoard).art)
        repository.saveViewport(level,daily.attempt.id,Viewport(showLabels=false))
        repository.move(level,daily.attempt.id,level.reference.first(),500)
        val expected=repository.observeAttempt(daily.attempt.id).first()!!
        database.close();open()
        assertEquals(expected,repository.startDaily().attempt)
        level.reference.drop(1).forEach {repository.move(level,daily.attempt.id,it,0)}
        assertEquals(3,repository.dailies.first().single().record.best!!.stars)
        assertTrue(repository.bests.first().isEmpty())
        try {repository.startOrResume(level);fail("Daily cannot unlock campaign")} catch(_:AccessDenied) {}
    }

    @Test fun thirdEpochAddsEightFamiliesWithoutChangingEitherEarlierPool() {
        val previous=DailySchedule(schedule.epochs.filter {it.version<3})
        for(date in listOf("2026-09-28","2026-09-30","2026-10-01","2026-10-03")) assertEquals(DailyRules.assignment(date,previous),DailyRules.assignment(date,schedule))
        val next=schedule.epochs.single {it.version==3}
        assertEquals(40,next.pool.size);assertEquals("2026-10-04",next.effectiveFrom)
        assertEquals(8,next.pool.map {ref -> catalog.levels.single {it.id==ref.id}.puzzle}.distinct().size)
    }
}
