package com.parlor.puzzles

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.game.*
import com.parlor.core.model.*
import com.parlor.feature.library.ParlorTheme
import com.parlor.feature.play.PuzzleBoard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class WaveBBoardTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var level:LevelDefinition
    private var attempt by mutableStateOf<Attempt?>(null)
    private fun show(id:String) {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        level=runBlocking {app.content.load()}.levels.single {it.id==id}
        attempt=Attempt.start("compact",level)
        compose.setContent {ParlorTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                Surface {Column(Modifier.width(320.dp).height(640.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    val saved=requireNotNull(attempt)
                    PuzzleBoard(saved.board,saved.viewport,!saved.completed,{attempt=saved.copy(viewport=it)},{attempt=requireNotNull(Session.move(engineFor(level.puzzle),saved,it))})
                }}
            }
        }}
    }
    private fun target(tag:String)=compose.onNodeWithTag(tag).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    private fun capture(name:String) {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        File(app.filesDir,"wave-b-$name.png").outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }

    @Test fun tileDragSurvivesACommittedTimerCheckpointWhileHeld() {
        show("tiles-001")
        val source=target("tile-4")
        source.performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(0f,-height*.3f)) }
        compose.runOnIdle { attempt=attempt!!.copy(activeTimeMs=1_000) }
        source.performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(0f,-height*.7f)); up() }
        compose.runOnIdle {
            assertEquals(TilesEngine.apply(level.initial,SlideTile(4)),attempt!!.board)
            assertEquals(1,attempt!!.scoredMoves)
            assertEquals(1_000L,attempt!!.activeTimeMs)
        }
    }

    @Test fun blockDragCommitsOneWholeTranslationAcrossAClockUpdate() {
        show("blocks-001")
        val source=target("block-cell-2")
        source.performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(-width*.3f,0f)) }
        compose.runOnIdle { attempt=attempt!!.copy(activeTimeMs=1_000) }
        source.performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(-width*.7f,0f)); up() }
        compose.runOnIdle {
            assertEquals(BlockEscapeEngine.apply(level.initial,SlideBlock("J",1,0)),attempt!!.board)
            assertEquals(1,attempt!!.scoredMoves)
            assertEquals(1_000L,attempt!!.activeTimeMs)
        }
    }

    @Test fun fiveByFiveBundledPictureSolvesWithLargeTargetsAndOptionalNumberLabels() {
        show("tiles-046")
        capture("tiles-compact-200")
        compose.onNodeWithText("Hide numbers").performScrollTo().performClick()
        compose.onNodeWithText("Show complete picture").performScrollTo().performClick()
        compose.onNodeWithTag("tile-picture").performScrollTo().assertIsDisplayed()
        capture("picture-compact-200")
        compose.onNodeWithText("Hide complete picture").performScrollTo().performClick()
        assertFalse(attempt!!.viewport.showLabels)
        for(move in level.reference.filterIsInstance<SlideTile>()) {
            target("tile-${move.tile}").performScrollTo().performClick()
            target("tile-0").performScrollTo().performClick()
        }
        compose.runOnIdle {assertTrue(attempt!!.completed);assertEquals(level.par,attempt!!.scoredMoves);assertFalse(attempt!!.viewport.showLabels)}
    }

    @Test fun expertBlocksRemainDistinctAndAccessibleForEveryTranslation() {
        show("blocks-039")
        capture("blocks-compact-200")
        for(move in level.reference.filterIsInstance<SlideBlock>()) {
            val board=attempt!!.board as BlockEscapeBoard
            val block=board.blocks.single {it.id==move.id}
            val source=target("block-cell-${block.y*board.width+block.x}")
            val actions=source.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            val label=if(move.y==board.height) "Slide ${move.id} through the opening." else "Move ${move.id} to row ${move.y+1}, column ${move.x+1}."
            compose.runOnIdle {assertTrue(actions.single {it.label==label}.action())}
        }
        compose.runOnIdle {assertTrue(attempt!!.completed);assertEquals(level.par,attempt!!.scoredMoves)}
    }

    @Test fun sixBoxExpertSolvesWithOverviewPanAndExplicitPushControls() {
        show("boxes-046")
        capture("boxes-compact-200")
        compose.onNodeWithTag("boxes-overview").assertIsDisplayed()
        val before=attempt!!.board
        compose.onNodeWithTag("boxes-overview").performTouchInput {click(bottomRight-androidx.compose.ui.geometry.Offset(2f,2f))}
        compose.runOnIdle {assertEquals(before,attempt!!.board);assertEquals(0,attempt!!.scoredMoves)}
        for(move in level.reference) when(move) {
            is WalkTo -> {
                val board=attempt!!.board as BoxPusherBoard
                val row=move.cell/board.width;val column=move.cell%board.width
                while(attempt!!.viewport.row>row) pan("View up")
                while(attempt!!.viewport.row+3<row) pan("View down")
                while(attempt!!.viewport.column>column) pan("View left")
                while(attempt!!.viewport.column+2<column) pan("View right")
                target("box-cell-${move.cell}").performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("boxes-overview").assertIsDisplayed()
                compose.onNodeWithTag("box-cell-${move.cell}").performClick()
            }
            is WorkerStep -> {
                target("worker-${move.direction.name}").performScrollTo().performClick()
                compose.onNodeWithTag("boxes-overview").assertIsDisplayed()
            }
            else -> error("box route")
        }
        compose.runOnIdle {assertTrue(attempt!!.completed);assertEquals(level.par,attempt!!.scoredMoves);assertTrue(attempt!!.walkingSteps>0)}
    }
    private fun pan(label:String) {compose.onNodeWithContentDescription(label).performScrollTo().performClick()}
}
