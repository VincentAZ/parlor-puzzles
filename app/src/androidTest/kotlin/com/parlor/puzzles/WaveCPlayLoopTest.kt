package com.parlor.puzzles

import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import com.parlor.core.game.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WaveCPlayLoopTest {
    @get:Rule val compose=navigationComposeRule()
    @Test fun pegTutorialCampaignAndRecreation()=play(Puzzle.PEG_SOLITAIRE,"Peg Solitaire")
    @Test fun queensTutorialCampaignAndRecreation()=play(Puzzle.QUEENS,"Queens")
    @Test fun knightTutorialCampaignAndRecreation()=play(Puzzle.KNIGHTS_TOUR,"Knight’s Tour")
    @Test fun crossingTutorialCampaignAndRecreation()=play(Puzzle.CROSSINGS,"Crossings")
    private fun play(puzzle:Puzzle,title:String) {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        val catalog=runBlocking {app.content.load()};val level=catalog.levels.single {it.puzzle==puzzle&&it.tier==Tier.EASY}
        runBlocking {
            app.progress.configure(catalog,app.content.dailySchedule());app.progress.resetProgress(puzzle)
            app.settings.update {it.copy(onboarded=true,tutorialsSeen=Puzzle.entries.toSet()-puzzle,reduceMotion=true)}
        }
        val before=runBlocking {app.progress.completionCount.first()}
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            compose.waitUntil(10_000) {compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText(title).performScrollTo().performClick()
            compose.onAllNodesWithText("Level 1")[0].performScrollTo().performClick()
            compose.waitUntil(10_000) {compose.onAllNodesWithText("How to play $title").fetchSemanticsNodes().isNotEmpty()}
            when(puzzle) {
                Puzzle.PEG_SOLITAIRE -> listOf(JumpPeg(12,10),JumpPeg(15,5),JumpPeg(0,10)).forEach {move(it)}
                Puzzle.QUEENS -> listOf(7,8,14).map(::PlaceQueen).forEach {move(it)}
                Puzzle.KNIGHTS_TOUR -> listOf(7,4,13).map(::KnightStep).forEach {move(it)}
                Puzzle.CROSSINGS -> listOf(CrossRiver(listOf("G")),CrossRiver(emptyList()),CrossRiver(listOf("C"))).forEach {move(it)}
                else -> error("Wave C")
            }
            compose.onNodeWithText("Ready for your first puzzle").assertExists()
            assertEquals(before,runBlocking {app.progress.completionCount.first()})
            compose.onNodeWithText("Begin a puzzle").performScrollTo().performClick()
            compose.waitUntil(10_000) {runBlocking {app.progress.resume.first()?.levelId==level.id}}
            val id=runBlocking {app.progress.resume.first()!!.id}
            var board=level.initial
            level.reference.forEachIndexed {index,action ->
                if(board is PegBoard && compose.onAllNodesWithTag("pegs-overview").fetchSemanticsNodes().isNotEmpty()) {
                    val jump=action as JumpPeg
                    val width=pegGeometry((board as PegBoard).layout).width
                    fun saved()=runBlocking {app.progress.observeAttempt(id).first()!!.viewport}
                    fun pan(label:String)=compose.onNodeWithContentDescription(label).performScrollTo().performClick()
                    while(saved().row>jump.from/width) pan("View up")
                    while(saved().row+3<jump.from/width) pan("View down")
                    while(saved().column>jump.from%width) pan("View left")
                    // At the normal emulator width four 60-dp columns fit alongside the overview.
                    while(saved().column+3<jump.from%width) pan("View right")
                }
                move(action)
                board=requireNotNull(engineFor(puzzle).apply(board,action))
                compose.waitUntil(10_000) {runBlocking {app.progress.observeAttempt(id).first()?.let {if(index==level.reference.lastIndex) it.completed else it.history.size==index+1}==true}}
                if(index==0) {
                    scenario.recreate()
                    compose.waitUntil(10_000) {compose.onAllNodesWithText(if(puzzle in listOf(Puzzle.QUEENS,Puzzle.KNIGHTS_TOUR)) "Actions: 1" else "1 move").fetchSemanticsNodes().isNotEmpty()}
                    assertEquals(board,runBlocking {app.progress.observeAttempt(id).first()!!.board})
                }
            }
            compose.waitUntil(10_000) {compose.onAllNodesWithText("A puzzle, settled.").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithContentDescription("3 out of 3 stars").assertExists()
            assertEquals(before+1,runBlocking {app.progress.completionCount.first()})
        }
    }
    private fun move(move:Move) {
        when(move) {
            is JumpPeg -> {
                compose.onNodeWithTag("peg-${move.from}").performScrollTo().performClick()
                compose.waitUntil(10_000) {compose.onAllNodesWithTag("peg-to-${move.to}").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithTag("peg-to-${move.to}").performScrollTo().performClick()
            }
            is PlaceQueen -> compose.onNodeWithTag("queen-${move.cell}").performScrollTo().performClick()
            is KnightStep -> compose.onNodeWithTag("knight-${move.cell}").performScrollTo().performClick()
            is CrossRiver -> {
                for(id in move.passengers) compose.onNodeWithTag("actor-$id").performScrollTo().performClick()
                compose.onNodeWithTag("cross-confirm").performScrollTo().performClick()
            }
            else -> error("Wave C action")
        }
    }
}
