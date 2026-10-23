package com.parlor.puzzles

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import com.parlor.feature.library.ParlorTheme
import com.parlor.feature.play.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class TabletPlayLayoutTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(NativeTabletDisplayRule(2f)).around(compose)
    @Test fun everyFamilyKeepsLargeTargetsAndIndependentControlsWithNarrowWindowFallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as ParlorApplication
        val levels = runBlocking { app.content.load() }.levels.groupBy { it.puzzle }.values.map { group -> group.last { it.tier == Tier.EXPERT } }
        var level by mutableStateOf(levels.first())
        var viewport by mutableStateOf(Viewport())
        var width by mutableStateOf(960.dp)
        var showingBoard by mutableStateOf(true)
        var clicks = 0
        compose.setContent { ParlorTheme { Surface { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.width(width).fillMaxHeight()) {
                AdaptivePlayLayout(header = { Text("Tablet layout fixture", style = MaterialTheme.typography.titleLarge) },
                    information = { Text(puzzleTitle(level.puzzle)); Text("Keep the board visible while reading the controls.") },
                    board = if (showingBoard) { { PuzzleBoard(level.initial, viewport, true, { viewport = it }, {}) } } else null,
                    controls = {
                        repeat(12) { Text("Control explanation ${it + 1}") }
                        Button(onClick = { clicks++ }, modifier = Modifier.testTag("tablet-last-control").heightIn(min = 48.dp)) { Text("Continue") }
                    }, footer = { if (!showingBoard) Box(Modifier.fillMaxWidth().height(50.dp).testTag("tablet-results-banner")) })
            }
        } } } }
        for (item in levels) {
            compose.runOnIdle { level = item; viewport = Viewport() }
            compose.onNodeWithTag("play-wide-layout").assertExists()
            val tag = when (item.puzzle) {
                Puzzle.TRAFFIC_JAM -> "piece-A"
                Puzzle.HANOI -> "hanoi-peg-0"
                Puzzle.SWITCH_GRID -> "switch-cell-0"
                Puzzle.WATER_JUGS -> "jug-0"
                Puzzle.PANCAKE_FLIP -> "flip-confirm"
                Puzzle.SLIDING_TILES -> "tile-0"
                Puzzle.BLOCK_ESCAPE -> "block-cell-0"
                Puzzle.BOX_PUSHER -> "worker-UP"
                Puzzle.PEG_SOLITAIRE -> "peg-${(item.initial as PegBoard).pegs.first()}"
                Puzzle.QUEENS -> "queen-0"
                Puzzle.KNIGHTS_TOUR -> "knight-0"
                Puzzle.CROSSINGS -> "actor-${(item.initial as CrossingBoard).actors.first().id}"
            }
            val target = compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            val before = target.fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("tablet-last-control").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            assertEquals(before, target.fetchSemanticsNode().boundsInRoot)
            val boardPane = compose.onNodeWithTag("play-board-pane").fetchSemanticsNode().boundsInRoot
            val controlsPane = compose.onNodeWithTag("play-controls-pane").fetchSemanticsNode().boundsInRoot
            assertTrue(boardPane.right < controlsPane.left)
            assertTrue(before.left >= boardPane.left && before.right <= boardPane.right)
            captureTabletScreen(compose, "tablet-${item.puzzle.name.lowercase()}-200.png")
            if (item.initial is QueensBoard) {
                val fixed = (item.initial as QueensBoard).fixed.first()
                val pixels = compose.onNodeWithTag("queen-$fixed").performScrollTo().captureToImage().toPixelMap()
                // The fixed-piece underline must remain continuous below the enlarged glyph.
                // Exclude the cell border and scan the lower interior so a border cannot pass.
                val underlineVisible = ((pixels.height * .72f).toInt()..(pixels.height * .93f).toInt()).any { y ->
                    ((pixels.width * .25f).toInt()..(pixels.width * .75f).toInt()).all { x -> pixels[x, y].luminance() < .2f }
                }
                assertTrue("Fixed queen underline remains visible at 200% text", underlineVisible)
            }
        }
        assertEquals(12, clicks)
        compose.runOnIdle { level = levels.first { it.puzzle == Puzzle.TRAFFIC_JAM }; viewport = Viewport(1, 0, 2, "A") }
        compose.onNodeWithContentDescription("Board overview. Tap to move the magnified view.").performScrollTo().assertIsDisplayed()
        val overview = compose.onNodeWithContentDescription("Board overview. Tap to move the magnified view.").fetchSemanticsNode().boundsInRoot
        val board = compose.onNodeWithContentDescription("Traffic Jam board, six rows and six columns. Exit on the right of row three.").fetchSemanticsNode().boundsInRoot
        assertTrue("Overview sits beside the magnified interaction", overview.right < board.left)
        val saved = viewport
        compose.runOnIdle { width = 600.dp }
        compose.onNodeWithTag("play-single-layout").assertExists()
        assertEquals(saved, viewport)
        compose.runOnIdle { width = 960.dp }
        compose.onNodeWithTag("play-wide-layout").assertExists()
        assertEquals(saved, viewport)
        compose.runOnIdle { showingBoard = false }
        compose.onNodeWithTag("play-single-layout").assertExists()
        compose.onNodeWithTag("tablet-last-control").performScrollTo().assertIsDisplayed()
        val action = compose.onNodeWithTag("tablet-last-control").fetchSemanticsNode().boundsInRoot
        val banner = compose.onNodeWithTag("tablet-results-banner").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(action.bottom <= banner.top)
    }
}
