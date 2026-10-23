package com.parlor.puzzles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
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
import org.junit.rules.RuleChain

/** Native 320 x 640 dp, 200% fonts, RTL chrome and canonical puzzle coordinates. */
class RtlCompactBoardTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(NativeCompactDisplayRule()).around(compose)

    @Test fun everyExpertFamilyKeepsLargeTargetsAndItsSpatialOrder() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        val levels = runBlocking { app.content.load() }.levels.groupBy { it.puzzle }.values.map { it.last { level -> level.tier == Tier.EXPERT } }
        var level by mutableStateOf(levels.first())
        var viewport by mutableStateOf(Viewport())
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            ParlorTheme { Surface { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                PuzzleBoard(level.initial, viewport, true, { viewport = it }, {})
            } } }
        } }
        for (item in levels) {
            compose.runOnIdle { level = item; viewport = Viewport() }
            val pair = when (item.puzzle) {
                Puzzle.TRAFFIC_JAM -> "piece-A" to null
                Puzzle.HANOI -> "hanoi-peg-0" to "hanoi-peg-1"
                Puzzle.SWITCH_GRID -> "switch-cell-0" to "switch-cell-1"
                Puzzle.WATER_JUGS -> "jug-0" to "jug-1"
                Puzzle.PANCAKE_FLIP -> "flip-confirm" to null
                Puzzle.SLIDING_TILES -> (item.initial as TilesBoard).let { "tile-${it.tiles[0]}" to "tile-${it.tiles[1]}" }
                Puzzle.BLOCK_ESCAPE -> "block-cell-0" to "block-cell-1"
                Puzzle.BOX_PUSHER -> "worker-UP" to null
                Puzzle.PEG_SOLITAIRE -> {
                    val board = item.initial as PegBoard
                    val holes = pegGeometry(board.layout).holes.sorted()
                    val width = pegGeometry(board.layout).width
                    val first = holes.first { it + 1 in holes && it / width == (it + 1) / width && it % width < 2 }
                    compose.runOnIdle { viewport = Viewport(row = first / width) }
                    "peg-$first" to "peg-${first + 1}"
                }
                Puzzle.QUEENS -> "queen-0" to "queen-1"
                Puzzle.KNIGHTS_TOUR -> "knight-0" to "knight-1"
                Puzzle.CROSSINGS -> "actor-${(item.initial as CrossingBoard).actors.first().id}" to null
            }
            val target = compose.onNodeWithTag(pair.first).performScrollTo().assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            pair.second?.let {
                val next = compose.onNodeWithTag(it).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                assertTrue("${item.puzzle}: column order matches the model",
                    target.fetchSemanticsNode().boundsInRoot.right <= next.fetchSemanticsNode().boundsInRoot.left + 1f)
            }
            captureTabletScreen(compose, "rtl-${item.puzzle.name.lowercase()}-compact-200.png")
        }
    }

    @Test fun overviewAndPanReachTheSameCellsWithoutChangingTheBoard() {
        var board by mutableStateOf<BoardState>(SwitchBoard(8, List(64) { it == 63 }))
        var viewport by mutableStateOf(Viewport())
        var moves = 0
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            ParlorTheme { Surface { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                PuzzleBoard(board, viewport, true, { viewport = it }, { moves++ })
            } } }
        } }
        for (kind in listOf("switch", "queens")) {
            if (kind == "queens") compose.runOnIdle { board = QueensBoard(8, emptyList()); viewport = Viewport(selectedId = "fixture") }
            val before = board
            compose.onNodeWithContentDescription("View right").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(1, viewport.column); assertEquals(0, viewport.row) }
            val overview = compose.onNodeWithTag("$kind-overview").performScrollTo()
            overview.performTouchInput { click(bottomRight - Offset(2f, 2f)) }
            compose.runOnIdle {
                assertEquals(5, viewport.column)
                assertEquals(if (kind == "switch") 5 else 4, viewport.row)
                assertEquals(before, board); assertEquals(0, moves)
                if (kind == "queens") assertEquals("fixture", viewport.selectedId)
            }
            val prefix = if (kind == "switch") "switch-cell" else "queen"
            val index = viewport.row * 8 + viewport.column
            val left = compose.onNodeWithTag("$prefix-$index").assertIsDisplayed().assertWidthIsAtLeast(48.dp)
            val right = compose.onNodeWithTag("$prefix-${index + 1}").assertIsDisplayed()
            assertTrue(left.fetchSemanticsNode().boundsInRoot.right <= right.fetchSemanticsNode().boundsInRoot.left + 1f)
            compose.onNodeWithContentDescription("View left").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(4, viewport.column); assertEquals(before, board); assertEquals(0, moves) }
            captureTabletScreen(compose, "rtl-$kind-pan-compact-200.png")
        }
    }
}
