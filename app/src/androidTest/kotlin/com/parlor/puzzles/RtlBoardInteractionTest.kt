package com.parlor.puzzles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.parlor.core.game.*
import com.parlor.core.model.*
import com.parlor.feature.library.ParlorTheme
import com.parlor.feature.play.PuzzleBoard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RtlBoardInteractionTest {
    @get:Rule val compose = createComposeRule()
    private var board by mutableStateOf<BoardState?>(null)
    private var viewport by mutableStateOf(Viewport())
    private fun show(initial: BoardState, puzzle: Puzzle) {
        board = initial
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            ParlorTheme { Surface { Column(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Start", Modifier.testTag("chrome-start"))
                    Text("End", Modifier.testTag("chrome-end"))
                }
                PuzzleBoard(requireNotNull(board), viewport, true, { viewport = it }, {
                    board = requireNotNull(engineFor(puzzle).apply(requireNotNull(board), it))
                })
            } } }
        } }
        assertTrue("Surrounding controls still follow RTL",
            compose.onNodeWithTag("chrome-start").fetchSemanticsNode().boundsInRoot.left >
                compose.onNodeWithTag("chrome-end").fetchSemanticsNode().boundsInRoot.left)
    }

    @Test fun rightwardTrafficDragMovesThePieceRightInAnRtlInterface() {
        show(TrafficJamBoard(pieces = listOf(TrafficPiece("A", 1, 2, 2, Axis.HORIZONTAL))), Puzzle.TRAFFIC_JAM)
        val piece = compose.onNodeWithTag("piece-A").performScrollTo()
        val before = piece.fetchSemanticsNode().boundsInRoot
        piece.performTouchInput { down(center); moveBy(Offset(width * .2f, 0f)); moveBy(Offset(width * .3f, 0f)); up() }
        compose.runOnIdle { assertEquals(2, (board as TrafficJamBoard).pieces.single().x) }
        val after = piece.fetchSemanticsNode().boundsInRoot
        assertEquals("The model and visible horizontal movement agree", before.left + before.width / 2, after.left, 1f)
    }

    @Test fun tileCoordinatesAndHorizontalDragAgreeInAnRtlInterface() {
        show(TilesBoard(3, listOf(1, 2, 3, 4, 5, 6, 7, 0, 8)), Puzzle.SLIDING_TILES)
        val tile = compose.onNodeWithTag("tile-8").performScrollTo()
        val empty = compose.onNodeWithTag("tile-0")
        assertTrue("Column three stays to the right of column two",
            tile.fetchSemanticsNode().boundsInRoot.left > empty.fetchSemanticsNode().boundsInRoot.left)
        tile.performTouchInput { down(center); moveBy(Offset(-width * .3f, 0f)); moveBy(Offset(-width * .7f, 0f)); up() }
        compose.runOnIdle { assertTrue(TilesEngine.isGoal(requireNotNull(board))) }
    }

    @Test fun hanoiPegsKeepTheirSpatialOrderForARealDragInRtl() {
        show(HanoiBoard(listOf(listOf(3, 2, 1), emptyList(), emptyList())), Puzzle.HANOI)
        val source = compose.onNodeWithTag("hanoi-peg-0").performScrollTo()
        val destination = compose.onNodeWithTag("hanoi-peg-1")
        val dx = destination.fetchSemanticsNode().boundsInRoot.center.x - source.fetchSemanticsNode().boundsInRoot.center.x
        assertTrue("Peg two stays to the right of peg one", dx > 0)
        source.performTouchInput { down(center); moveBy(Offset(dx * .3f, 0f)); moveBy(Offset(dx * .7f, 0f)); up() }
        compose.runOnIdle { assertEquals(listOf(1), (board as HanoiBoard).pegs[1]) }
    }

    @Test fun trianglePegJumpUsesPhysicalDiagonalCoordinatesInRtl() {
        show(PegBoard(PegLayout.TRIANGLE, listOf(0, 6), 12), Puzzle.PEG_SOLITAIRE)
        val source = compose.onNodeWithTag("peg-0").performScrollTo()
        val destination = compose.onNodeWithTag("peg-12")
        val distance = destination.fetchSemanticsNode().boundsInRoot.center - source.fetchSemanticsNode().boundsInRoot.center
        assertTrue(distance.x > 0 && distance.y > 0)
        source.performTouchInput { swipe(center, center + distance, durationMillis = 400) }
        compose.runOnIdle { assertTrue(PegEngine.isGoal(requireNotNull(board))) }
    }

    @Test fun riverBanksStayOnTheirNamedSidesWhileActorsCrossInRtl() {
        show(CrossingBoard(CrossingScenario.BRIDGE,
            listOf(CrossingActor("A", CrossingRole.WALKER, minutes = 1), CrossingActor("B", CrossingRole.WALKER, minutes = 2)), 2), Puzzle.CROSSINGS)
        val left = compose.onNodeWithText("Left").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithText("Right").fetchSemanticsNode().boundsInRoot
        assertTrue(left.right < right.left)
        compose.onNodeWithTag("actor-A").performClick()
        compose.onNodeWithTag("actor-B").performClick()
        compose.onNodeWithTag("cross-confirm").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(CrossingEngine.isGoal(requireNotNull(board))) }
        val actor = compose.onNodeWithTag("actor-A").performScrollTo().fetchSemanticsNode().boundsInRoot
        assertTrue(actor.left >= right.left)
    }
}
