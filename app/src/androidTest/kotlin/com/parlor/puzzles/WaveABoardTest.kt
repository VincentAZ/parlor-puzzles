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

/** 320-dp phone content width (288 after margins), 200% type and actual semantic controls. */
class WaveABoardTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var level: LevelDefinition
    private var board by mutableStateOf<BoardState?>(null)
    private var viewport by mutableStateOf(Viewport())
    private var actions = 0

    private fun show(id: String) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        level = runBlocking { app.content.load() }.levels.single { it.id == id }
        board = level.initial
        compose.setContent {
            ParlorTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Surface {
                        Column(Modifier.width(320.dp).height(640.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                            PuzzleBoard(requireNotNull(board), viewport, true, { viewport = it }, {
                                board = requireNotNull(engineFor(level.puzzle).apply(requireNotNull(board), it))
                                if (it !is FlipPrefix) viewport = viewport.copy(selectedId = null)
                                actions++
                            })
                        }
                    }
                }
            }
        }
    }
    private fun target(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag)
        .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    private fun save(name: String) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(app.filesDir, "wave-a-$name.png")
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun eightByEightSwitchesKeepAnOverviewAndSolveWithLargeTargets() {
        show("switch-046")
        compose.onNodeWithTag("switch-overview").assertIsDisplayed()
        save("switch-compact-200")
        val before = board
        compose.onNodeWithTag("switch-overview").performTouchInput { click(bottomRight - androidx.compose.ui.geometry.Offset(2f, 2f)) }
        compose.runOnIdle { assertEquals(before, board); assertEquals(0, actions); assertTrue(viewport.row > 0) }
        for (action in level.reference.filterIsInstance<Toggle>()) {
            val row = action.index / 8
            val column = action.index % 8
            // Three 60-dp interaction cells fit beside the persistent 96-dp overview.
            while (viewport.row > row) compose.onNodeWithContentDescription("View up").performScrollTo().performClick()
            while (viewport.row + 2 < row) compose.onNodeWithContentDescription("View down").performScrollTo().performClick()
            while (viewport.column > column) compose.onNodeWithContentDescription("View left").performScrollTo().performClick()
            while (viewport.column + 2 < column) compose.onNodeWithContentDescription("View right").performScrollTo().performClick()
            val cell = target("switch-cell-${action.index}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("switch-overview").assertIsDisplayed()
            compose.onNodeWithContentDescription("Row ${row + 1}, column ${column + 1}").assertExists()
            cell.performClick()
        }
        compose.runOnIdle { assertTrue(SwitchEngine.isGoal(requireNotNull(board))); assertEquals(level.par, actions) }
    }

    @Test fun fourPegHanoiCanBeCompletedThroughAccessibleActions() {
        show("hanoi-011")
        save("hanoi-compact-200")
        for (move in level.reference.filterIsInstance<TransferDisk>()) {
            val source = target("hanoi-peg-${move.from}")
            val options = source.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            compose.runOnIdle { assertTrue(options.single { it.label.endsWith("peg ${move.to + 1}.") }.action()) }
        }
        compose.runOnIdle { assertTrue(HanoiEngine.isGoal(requireNotNull(board))); assertEquals(level.par, actions) }
    }

    @Test fun threeJugsHaveDistinctAccessibleVolumesAndPourActions() {
        show("jugs-039")
        save("jugs-compact-200")
        for (move in level.reference) {
            val from = when (move) { is FillJug -> move.jug; is EmptyJug -> move.jug; is PourJug -> move.from; else -> error("jug action") }
            val options = target("jug-$from").fetchSemanticsNode().config[SemanticsActions.CustomActions]
            val label = when (move) { is FillJug -> "Fill jug ${from + 1}."; is EmptyJug -> "Empty jug ${from + 1}."; is PourJug -> "Pour from jug ${from + 1} into jug ${move.to + 1}."; else -> error("jug action") }
            compose.runOnIdle { assertTrue(options.single { it.label == label }.action()) }
        }
        compose.runOnIdle { assertTrue(JugsEngine.isGoal(requireNotNull(board))); assertEquals(level.par, actions) }
    }

    @Test fun twelvePancakesCanBePlannedAndFlippedUsingLargeControls() {
        show("pancake-031")
        save("pancake-compact-200")
        var count = 2
        for (move in level.reference.filterIsInstance<FlipPrefix>()) {
            repeat(kotlin.math.abs(move.count - count)) {
                target(if (move.count > count) "flip-more" else "flip-less").performScrollTo().performClick()
            }
            count = move.count
            target("flip-confirm").performScrollTo().performClick()
        }
        compose.runOnIdle { assertTrue(PancakeEngine.isGoal(requireNotNull(board))); assertEquals(level.par, actions) }
    }
}
