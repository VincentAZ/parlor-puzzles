package com.parlor.puzzles

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature as Fold
import androidx.window.layout.WindowLayoutInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.window.layout.WindowMetricsCalculator
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import com.parlor.core.design.*
import com.parlor.core.model.*
import com.parlor.feature.library.ParlorTheme
import com.parlor.feature.play.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.RuleChain

class FoldLayoutTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val publisher = WindowLayoutInfoPublisherRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(NativeTabletDisplayRule(2f)).around(publisher).around(compose)
    private fun publish(vertical: Boolean, size: Int = 40, flat: Boolean = false, center: Int = -1): Fold {
        val window = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(compose.activity).bounds
        val insets = requireNotNull(ViewCompat.getRootWindowInsets(compose.activity.window.decorView))
            .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val usableCenter = if (vertical) (insets.left + window.width() - insets.right) / 2
            else (insets.top + window.height() - insets.bottom) / 2
        val fold = FoldingFeature(compose.activity, size = size, center = if (center == -1) usableCenter else center,
            state = if (flat) Fold.State.FLAT else Fold.State.HALF_OPENED,
            orientation = if (vertical) Fold.Orientation.VERTICAL else Fold.Orientation.HORIZONTAL)
        publisher.overrideWindowLayoutInfo(WindowLayoutInfo(listOf(fold)))
        compose.waitForIdle()
        return fold
    }
    private fun clearOf(node: SemanticsNodeInteraction, fold: Fold) {
        val b = node.assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val f = fold.bounds
        assertTrue("$b must not overlap hinge $f", b.right <= f.left || b.left >= f.right || b.bottom <= f.top || b.top >= f.bottom)
    }
    @Test fun allTwelveBoardsKeepTargetsAndIndependentControlsInBookAndTabletopAtTwoHundredPercent() {
        val app = compose.activity.application as ParlorApplication
        val levels = runBlocking { app.content.load() }.levels.groupBy { it.puzzle }.values.map { group -> group.last { it.tier == Tier.EXPERT } }
        var level by mutableStateOf(levels.first())
        var viewport by mutableStateOf(Viewport())
        var showingBoard by mutableStateOf(true)
        var clicks = 0
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        compose.setContent { ProvideWindowFolds(compose.activity) { ParlorTheme { Surface {
            AdaptivePlayLayout(header = { Text("Fold layout fixture", style = MaterialTheme.typography.titleLarge) },
                information = { Text(puzzleTitle(level.puzzle)) },
                board = if (showingBoard) { { PuzzleBoard(level.initial, viewport, true, { viewport = it }, {}) } } else null,
                controls = {
                    repeat(12) { Text("Explanation ${it + 1}") }
                    Button(onClick = { clicks++ }, modifier = Modifier.testTag("fold-last-control")) { Text("Continue") }
                }, footer = { if (!showingBoard) Box(Modifier.fillMaxWidth().height(50.dp).testTag("fold-footer")) })
        } } } }
        compose.onNodeWithTag("play-wide-layout").assertExists()
        publish(true, size = 0, flat = true)
        compose.onNodeWithTag("play-wide-layout").assertExists()
        for (vertical in listOf(true, false)) {
            val fold = publish(vertical)
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("play-fold-layout").fetchSemanticsNodes().isNotEmpty() }
            for (item in levels) {
                compose.runOnIdle { level = item; viewport = Viewport() }
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
                val target = compose.onNodeWithTag(tag).performScrollTo().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                clearOf(target, fold)
                val before = target.fetchSemanticsNode().boundsInWindow
                val control = compose.onNodeWithTag("fold-last-control").performScrollTo().assertHeightIsAtLeast(48.dp)
                clearOf(control, fold)
                control.performClick()
                assertEquals(before, target.fetchSemanticsNode().boundsInWindow)
                captureTabletScreen(compose, "fold-${if (vertical) "book" else "tabletop"}-${item.puzzle.name.lowercase()}-200.png")
            }
        }
        assertEquals(24, clicks)
        val smallSide = publish(true, center = 200)
        compose.onNodeWithTag("play-fold-layout").assertDoesNotExist()
        clearOf(compose.onNodeWithTag("fold-last-control").performScrollTo(), smallSide)
        compose.runOnIdle { showingBoard = false }
        clearOf(compose.onNodeWithTag("fold-footer"), smallSide)
        publisher.overrideWindowLayoutInfo(WindowLayoutInfo(emptyList()))
        compose.onNodeWithTag("play-single-layout").assertExists()
    }

    @Test fun singlePaneAndMaterialDialogAvoidHingesWithRtlOutsideTapBackAndLivePostureChanges() {
        var rtl by mutableStateOf(false)
        var open by mutableStateOf(false)
        var confirmed = 0
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        compose.setContent { ProvideWindowFolds(compose.activity) { ParlorTheme {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                FoldSafePane { Surface(Modifier.fillMaxSize().testTag("fold-screen")) {
                    Column(Modifier.padding(24.dp)) {
                        Text("Settings")
                        Button(onClick = { open = true }, modifier = Modifier.testTag("open-fold-dialog")) { Text("Open") }
                    }
                } }
                if (open) FoldAwareAlertDialog(onDismissRequest = { open = false },
                    title = { Text("Keep your saved progress") },
                    text = { Column(Modifier.verticalScroll(rememberScrollState())) { repeat(15) { Text("Saved progress stays on this device.") } } },
                    confirmButton = { TextButton(onClick = { confirmed++; open = false }, modifier = Modifier.testTag("fold-confirm")) { Text("Continue") } },
                    dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } })
            }
        } } }
        compose.onNodeWithTag("open-fold-dialog").assertExists()
        val full = compose.onNodeWithTag("fold-screen").fetchSemanticsNode().boundsInWindow
        var fold = publish(true, flat = true, center = ((full.left + full.right) / 2).toInt())
        clearOf(compose.onNodeWithTag("fold-screen"), fold)
        assertTrue(compose.onNodeWithTag("fold-screen").fetchSemanticsNode().boundsInWindow.right <= fold.bounds.left)
        compose.runOnIdle { rtl = true }
        clearOf(compose.onNodeWithTag("fold-screen"), fold)
        assertTrue("RTL pane: ${compose.onNodeWithTag("fold-screen").fetchSemanticsNode().boundsInWindow}; fold: ${fold.bounds}", compose.onNodeWithTag("fold-screen").fetchSemanticsNode().boundsInWindow.left >= fold.bounds.right)
        compose.onNodeWithTag("open-fold-dialog").performClick()
        clearOf(compose.onNodeWithTag("fold-confirm").performScrollTo(), fold)
        captureTabletScreen(compose, "fold-dialog-book-rtl-200.png")
        fold = publish(false)
        clearOf(compose.onNodeWithTag("fold-confirm").performScrollTo(), fold)
        captureTabletScreen(compose, "fold-dialog-tabletop-rtl-200.png")
        compose.onNodeWithTag("fold-confirm").performClick()
        assertEquals(1, confirmed)
        compose.onNodeWithTag("open-fold-dialog").performClick()
        compose.onNode(isDialog()).performTouchInput { click(androidx.compose.ui.geometry.Offset(20f, height * .75f)) }
        compose.onNodeWithTag("fold-confirm").assertDoesNotExist()
        compose.onNodeWithTag("open-fold-dialog").performClick()
        compose.onNode(isDialog()).assertExists()
        compose.waitUntil(10_000) { !compose.activity.hasWindowFocus() }
        val device = androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
        device.waitForIdle()
        device.pressBack()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("fold-confirm").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("fold-confirm").assertDoesNotExist()
        compose.onNodeWithTag("open-fold-dialog").performClick()
        publisher.overrideWindowLayoutInfo(WindowLayoutInfo(emptyList()))
        compose.onNodeWithTag("fold-dialog-viewport").assertDoesNotExist()
        compose.onNodeWithTag("fold-confirm").performClick()
        assertEquals(2, confirmed)
    }
}
