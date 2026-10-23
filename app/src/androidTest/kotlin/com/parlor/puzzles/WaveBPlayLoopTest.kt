package com.parlor.puzzles

import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.game.engineFor
import com.parlor.core.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WaveBPlayLoopTest {
    @get:Rule val compose=navigationComposeRule()
    @Test fun tilesTutorialAndCampaignKeepTileIdentityThroughRecreation() = play(Puzzle.SLIDING_TILES,"Sliding Tiles")
    @Test fun blocksTutorialAndCampaignKeepWholeSlideCostsThroughRecreation() = play(Puzzle.BLOCK_ESCAPE,"Block Escape")
    @Test fun boxesTutorialAndCampaignKeepPushAndWalkingCountsThroughRecreation() = play(Puzzle.BOX_PUSHER,"Box Pusher")

    private fun play(puzzle:Puzzle,title:String) {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        val catalog=runBlocking {app.content.load()}
        val level=catalog.levels.single {it.puzzle==puzzle&&it.tier==Tier.EASY}
        runBlocking {
            app.progress.configure(catalog,app.content.dailySchedule());app.progress.resetProgress(puzzle)
            app.settings.update {it.copy(onboarded=true,tutorialsSeen=Puzzle.entries.toSet()-puzzle,reduceMotion=true)}
        }
        val before=runBlocking {app.progress.completionCount.first()}
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(10_000) {compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText(title).performScrollTo().performClick()
            compose.onAllNodesWithText("Level 1")[0].performScrollTo().performClick()
            compose.waitUntil(10_000) {compose.onAllNodesWithText("How to play $title").fetchSemanticsNodes().isNotEmpty()}
            when(puzzle) {
                Puzzle.SLIDING_TILES -> listOf(4,5,8).forEach {tile -> tile(tile)}
                Puzzle.BLOCK_ESCAPE -> {
                    compose.onNodeWithTag("block-cell-10").performScrollTo().performClick();destination(SlideBlock("B",0,2))
                    compose.onNodeWithTag("block-cell-0").performScrollTo().performClick();destination(SlideBlock("A",1,0))
                    compose.onNodeWithTag("block-cell-1").performScrollTo().performClick();destination(SlideBlock("A",1,5))
                }
                Puzzle.BOX_PUSHER -> {
                    compose.onNodeWithTag("box-cell-13").performScrollTo().performClick()
                    repeat(2) {compose.onNodeWithTag("worker-RIGHT").performScrollTo().performClick()}
                }
                else -> error("Wave B")
            }
            compose.onNodeWithText("Ready for your first puzzle").assertExists()
            assertEquals(before,runBlocking {app.progress.completionCount.first()})
            compose.onNodeWithText("Begin a puzzle").performScrollTo().performClick()
            compose.waitUntil(10_000) {runBlocking {app.progress.resume.first()?.levelId==level.id}}
            var board=level.initial
            val id=runBlocking {app.progress.resume.first()!!.id}
            level.reference.forEachIndexed { index,action ->
                val saved=runBlocking {app.progress.observeAttempt(id).first()!!}
                when(action) {
                    is SlideTile -> tile(action.tile)
                    is SlideBlock -> {
                        board as BlockEscapeBoard
                        val block=board.blocks.single {it.id==action.id}
                        if(saved.viewport.selectedId!=action.id) compose.onNodeWithTag("block-cell-${block.y*board.width+block.x}").performScrollTo().performClick()
                        destination(action)
                    }
                    is WalkTo -> compose.onNodeWithTag("box-cell-${action.cell}").performScrollTo().performClick()
                    is WorkerStep -> compose.onNodeWithTag("worker-${action.direction.name}").performScrollTo().performClick()
                    else -> error("Unexpected move")
                }
                board=requireNotNull(engineFor(puzzle).apply(board,action))
                compose.waitUntil(10_000) {runBlocking {app.progress.observeAttempt(id).first()?.let { if(index==level.reference.lastIndex) it.completed else it.history.size==index+1 }==true}}
                if(index==0) {
                    scenario.recreate()
                    compose.waitUntil(10_000) {compose.onAllNodesWithText(if(puzzle==Puzzle.BOX_PUSHER) "0 pushes" else "1 move").fetchSemanticsNodes().isNotEmpty()}
                    assertEquals(board,runBlocking {app.progress.observeAttempt(id).first()!!.board})
                }
            }
            compose.waitUntil(10_000) {compose.onAllNodesWithText("A puzzle, settled.").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithContentDescription("3 out of 3 stars").assertExists()
            assertEquals(before+1,runBlocking {app.progress.completionCount.first()})
            val best=runBlocking {app.progress.bests.first().single {it.levelId==level.id}}
            assertEquals(level.par,best.moves)
            if(puzzle==Puzzle.BOX_PUSHER) {
                assertTrue(runBlocking {app.progress.observeAttempt(id).first()!!.walkingSteps}>0)
                compose.onNodeWithText("${level.par} pushes · Target: ${level.par} pushes").assertExists()
                assertTrue("box-no-undo" in runBlocking {app.progress.player.first().profile.achievements})
            }
        }
    }
    private fun tile(value:Int) {
        compose.onNodeWithTag("tile-$value").performScrollTo().performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("tile-slide").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("tile-slide").performScrollTo().performClick()
    }
    private fun destination(move:SlideBlock) {
        val tag="block-to-${move.id}-${move.x}-${move.y}"
        compose.waitUntil(10_000) {compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }
}
