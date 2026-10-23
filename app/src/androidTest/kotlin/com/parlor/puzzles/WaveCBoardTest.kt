package com.parlor.puzzles

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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

class WaveCBoardTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var level:LevelDefinition
    private var attempt by mutableStateOf<Attempt?>(null)
    private fun show(id:String) {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        level=runBlocking {app.content.load()}.levels.single {it.id==id}
        attempt=Attempt.start("compact-c",level)
        compose.setContent {ParlorTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                Surface {Column(Modifier.width(320.dp).height(640.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    val saved=requireNotNull(attempt)
                    PuzzleBoard(saved.board,saved.viewport,!saved.completed,{attempt=saved.copy(viewport=it)},
                        {attempt=requireNotNull(Session.move(engineFor(level.puzzle),saved,it))})
                }}
            }
        }}
    }
    private fun target(tag:String)=compose.onNodeWithTag(tag).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    private fun capture(name:String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir,"wave-c-$name.png").outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun reveal(cell:Int,width:Int) {
        val row=cell/width;val column=cell%width
        fun pan(label:String)=compose.onNodeWithContentDescription(label).performScrollTo().performClick()
        while(attempt!!.viewport.row>row) pan("View up")
        while(attempt!!.viewport.row+3<row) pan("View down")
        while(attempt!!.viewport.column>column) pan("View left")
        while(attempt!!.viewport.column+2<column) pan("View right")
    }

    @Test fun diamondPegsCompleteWithOverviewAndAccessibleJumps() {
        show("pegs-031")
        val geometry=pegGeometry((level.initial as PegBoard).layout)
        capture("pegs-compact-200")
        val initial=attempt!!.board
        compose.onNodeWithTag("pegs-overview").performTouchInput {click(bottomRight-Offset(2f,2f))}
        assertEquals(initial,attempt!!.board)
        for(move in level.reference.filterIsInstance<JumpPeg>()) {
            reveal(move.from,geometry.width)
            val source=target("peg-${move.from}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("pegs-overview").assertIsDisplayed()
            val label="Jump from row ${move.from/geometry.width+1}, column ${move.from%geometry.width+1} to row ${move.to/geometry.width+1}, column ${move.to%geometry.width+1}."
            val actions=source.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            compose.runOnIdle {assertTrue(actions.single {it.label==label}.action())}
        }
        assertTrue(attempt!!.completed);assertEquals(level.par,attempt!!.scoredMoves)
    }

    @Test fun triangularPegJumpsUseBothDragAxesAtLargeTextSize() {
        show("pegs-011");capture("triangle-compact-200")
        for(move in level.reference.filterIsInstance<JumpPeg>()) {
            val rows=move.to/5-move.from/5
            val columns=move.to%5-move.from%5
            target("peg-${move.from}").performScrollTo().performTouchInput {
                swipe(center,center+Offset((columns-rows/2f)*width,rows*height.toFloat()),durationMillis=400)
            }
        }
        assertTrue(attempt!!.completed);assertEquals(level.par,attempt!!.scoredMoves)
    }

    @Test fun constrainedEightQueensKeepFixedPiecesAndReachEveryPlacement() {
        show("queens-024");capture("queens-compact-200")
        val initial=attempt!!.board as QueensBoard
        val fixed=initial.fixed.first();reveal(fixed,8)
        target("queen-$fixed").performScrollTo().performClick()
        assertEquals(initial,attempt!!.board)
        for(move in level.reference.filterIsInstance<PlaceQueen>()) {
            reveal(move.cell,8)
            target("queen-${move.cell}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("queens-overview").assertIsDisplayed()
            compose.onNodeWithTag("queen-${move.cell}").performClick()
        }
        assertTrue(attempt!!.completed);assertEquals(initial.fixed,(attempt!!.board as QueensBoard).fixed)
    }

    @Test fun closedEightTourFollowsTheKnightWithoutLosingTheFullOverview() {
        show("knight-024");capture("knight-compact-200")
        for(move in level.reference.filterIsInstance<KnightStep>()) {
            val board=attempt!!.board as KnightBoard
            val source=target("knight-${board.path.last()}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("knight-overview").assertIsDisplayed()
            val actions=source.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            val label="Go to row ${move.cell/8+1}, column ${move.cell%8+1}."
            compose.runOnIdle {assertTrue(actions.single {it.label==label}.action())}
        }
        assertTrue(attempt!!.completed);assertEquals(64,(attempt!!.board as KnightBoard).path.size)
        assertEquals(8,KnightEngine.completionFacts(attempt!!.board).closedKnightSide)
    }

    @Test fun bridgeTravellersAndConfirmRemainVisibleAtTwoHundredPercent()=crossings("crossings-031","bridge")
    @Test fun missionaryPartiesUseDistinctTokensAndSafeCrossings()=crossings("crossings-011","missionaries")
    @Test fun pairedTravellersUseDistinctTokensAndSafeCrossings()=crossings("crossings-021","couples")
    private fun crossings(id:String,name:String) {
        show(id)
        target("cross-confirm").performScrollTo()
        capture("$name-compact-200")
        for(move in level.reference.filterIsInstance<CrossRiver>()) {
            for(actor in move.passengers) target("actor-$actor").performScrollTo().performClick()
            target("cross-confirm").performScrollTo().assertIsEnabled()
            // All travellers remain available for planning while confirming the crossing.
            (attempt!!.board as CrossingBoard).actors.forEach {target("actor-${it.id}").assertIsDisplayed()}
            compose.onNodeWithTag("cross-confirm").performClick()
        }
        assertTrue(attempt!!.completed);assertEquals(level.par,attempt!!.scoredMoves)
        if((attempt!!.board as CrossingBoard).scenario==CrossingScenario.BRIDGE) assertEquals(level.par,(attempt!!.board as CrossingBoard).elapsedMinutes)
    }
}
