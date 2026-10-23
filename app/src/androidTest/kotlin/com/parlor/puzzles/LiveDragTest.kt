package com.parlor.puzzles

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.parlor.core.game.*
import com.parlor.core.model.*
import com.parlor.feature.library.ParlorTheme
import com.parlor.feature.play.PuzzleBoard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LiveDragTest {
    @get:Rule val compose = createComposeRule()
    private var board by mutableStateOf<BoardState?>(null)
    private var enabled by mutableStateOf(true)
    private var moves = 0

    private fun show(initial: BoardState, engine: PuzzleEngine) {
        board = initial
        compose.setContent {
            var viewport by remember { mutableStateOf(Viewport()) }
            ParlorTheme { Surface { Column(Modifier.width(360.dp)) {
                PuzzleBoard(requireNotNull(board), viewport, enabled, { viewport = it }, {
                    board = requireNotNull(engine.apply(requireNotNull(board), it)); moves++
                })
            } } }
        }
    }
    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test fun trafficMovesBeforeReleaseAndCommitsOnlyOnce() {
        show(TrafficJamBoard(pieces=listOf(TrafficPiece("A",0,2,2,Axis.HORIZONTAL))), TrafficJamEngine)
        val start = bounds("piece-A")
        compose.onNodeWithTag("piece-A").performTouchInput { down(center); moveBy(Offset(start.width * .65f, 0f)) }
        compose.waitForIdle()
        assertTrue(bounds("piece-A").left > start.left + start.width * .3f)
        compose.runOnIdle { assertEquals(0,moves) }
        compose.onNodeWithTag("piece-A").performTouchInput { up() }
        compose.runOnIdle { assertEquals(1,moves); assertEquals(1,(board as TrafficJamBoard).pieces.single().x) }
    }

    @Test fun trafficStopsAtBlockersAndCancelDoesNotCommit() {
        show(TrafficJamBoard(pieces=listOf(TrafficPiece("A",0,2,2,Axis.HORIZONTAL), TrafficPiece("B",3,1,2,Axis.VERTICAL))), TrafficJamEngine)
        val start = bounds("piece-A")
        compose.onNodeWithTag("piece-A").performTouchInput { down(center); moveBy(Offset(start.width*2,0f)) }
        compose.waitForIdle()
        assertEquals(start.left+start.width/2,bounds("piece-A").left,1f)
        compose.onNodeWithTag("piece-A").performTouchInput { cancel() }
        compose.waitForIdle()
        assertEquals(start.left,bounds("piece-A").left,1f)
        compose.runOnIdle { assertEquals(0,moves) }
    }

    @Test fun tileFollowsFingerAndHighlightsOnlyTheEmptyDestination() {
        show(TilesBoard(3,listOf(1,2,3,4,0,6,7,5,8),TileArt.GARDEN),TilesEngine)
        val start=bounds("tile-5")
        compose.onNodeWithTag("tile-5").performTouchInput { down(center); moveBy(Offset(0f,-start.height*.85f)) }
        compose.waitForIdle()
        assertTrue(bounds("tile-5").top < start.top-start.height*.3f)
        compose.onNodeWithTag("tile-0").assertIsSelected()
        compose.onNodeWithTag("tile-6").assertIsNotSelected()
        compose.runOnIdle { assertEquals(0,moves) }
        compose.onNodeWithTag("tile-5").performTouchInput { up() }
        compose.runOnIdle { assertEquals(1,moves); assertEquals(5,(board as TilesBoard).tiles[4]) }
        compose.onNodeWithTag("tile-0").assertIsNotSelected()
    }

    @Test fun draggingAnyBlockCellMovesTheWholeBlock() {
        show(BlockEscapeBoard(blocks=listOf(EscapeBlock("A",1,0,2,2))),BlockEscapeEngine)
        val start=bounds("block-cell-1")
        compose.onNodeWithTag("block-cell-2").performTouchInput { down(center); moveBy(Offset(0f,start.height*.85f)) }
        compose.waitForIdle()
        assertTrue(bounds("block-cell-1").top > start.top+start.height*.3f)
        assertEquals(bounds("block-cell-1").top,bounds("block-cell-2").top,1f)
        compose.onNodeWithTag("block-cell-2").performTouchInput { up() }
        compose.runOnIdle { assertEquals(1,moves); assertEquals(1,(board as BlockEscapeBoard).blocks.single().y) }
    }

    @Test fun aTrafficExitKeepsTheGestureAliveOutsideTheBoard() {
        show(TrafficJamBoard(pieces=listOf(TrafficPiece("A",4,2,2,Axis.HORIZONTAL))),TrafficJamEngine)
        val start=bounds("piece-A")
        compose.onNodeWithTag("piece-A").performTouchInput { down(Offset(4f,center.y)); moveBy(Offset(start.width*1.4f,0f)) }
        compose.waitForIdle()
        compose.onNodeWithTag("piece-A").performTouchInput { up() }
        compose.runOnIdle { assertEquals(1,moves); assertTrue(TrafficJamEngine.isGoal(requireNotNull(board))) }
    }

    @Test fun severalDragEventsContinueTrackingWithoutAJumpOrExtraMove() {
        show(TrafficJamBoard(pieces=listOf(TrafficPiece("A",0,2,2,Axis.HORIZONTAL))),TrafficJamEngine)
        val start=bounds("piece-A")
        compose.onNodeWithTag("piece-A").performTouchInput { down(center); moveBy(Offset(start.width*.3f,0f)) }
        compose.waitForIdle()
        val first=bounds("piece-A")
        compose.onNodeWithTag("piece-A").performTouchInput { moveBy(Offset(start.width*.3f,0f)) }
        compose.waitForIdle()
        assertEquals(first.left+start.width*.3f,bounds("piece-A").left,2f)
        compose.runOnIdle { assertEquals(0,moves) }
        compose.onNodeWithTag("piece-A").performTouchInput { up() }
        compose.runOnIdle { assertEquals(1,moves) }
    }

    @Test fun hanoiHighlightsOnlyLegalPegsAndPauseCancelsTheDrag() {
        show(HanoiBoard(listOf(listOf(3,2),listOf(1),emptyList()),2),HanoiEngine)
        val start=bounds("hanoi-peg-0")
        compose.onNodeWithTag("hanoi-peg-0").performTouchInput { down(center); moveBy(Offset(start.width*.7f,0f)) }
        compose.waitForIdle()
        compose.onNodeWithTag("hanoi-peg-1").assertIsNotSelected()
        compose.onNodeWithTag("hanoi-peg-2").assertIsSelected()
        compose.runOnIdle { enabled=false }
        compose.onNodeWithTag("hanoi-peg-0").performTouchInput { up() }
        compose.runOnIdle { assertEquals(0,moves) }
        compose.onNodeWithTag("hanoi-peg-2").assertIsNotSelected()
    }
}
