package com.parlor.puzzles

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.data.ContentRepository
import com.parlor.core.model.*
import com.parlor.feature.library.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File

class HomeRoomTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(NativeCompactDisplayRule()).around(compose)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val catalog by lazy { runBlocking { ContentRepository(context).load() } }

    @Test fun bothLayoutsKeepAllCollectionsReachableAtNativeLargeTextWithCampaignOnlyProgress() {
        var simple by mutableStateOf(false)
        var rtl by mutableStateOf(false)
        var bests by mutableStateOf(emptyList<LevelBest>())
        val chosen = mutableListOf<Puzzle>()
        var daily = 0; var progress = 0
        compose.setContent { ParlorTheme { Surface {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
                    PuzzleRoom(catalog, bests, simple, { chosen += it }, { daily++ }, { progress++ })
                }
            }
        } } }
        fun assertSummary(tag: String, value: String) {
            compose.onNode(hasText(value) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                .performScrollTo().assertIsDisplayed()
        }
        assertSummary("home-progress", "0 of 56 solved")
        val traffic = catalog.levels.filter { it.puzzle == Puzzle.TRAFFIC_JAM }
        val queen = catalog.levels.first { it.puzzle == Puzzle.QUEENS }
        val one = LevelBest(traffic.first().id, 1, 100, 100_000, 30, "2026-09-28")
        compose.runOnIdle {
            bests = listOf(one, one.copy(stars = 3, points = 350), // Duplicate deliveries still count one level.
                one.copy(levelId = traffic.last().id), one.copy(levelId = queen.id), one.copy(levelId = "uninstalled-content"))
        }
        assertSummary("home-progress", "3 of 56 solved")
        assertSummary("home-progress-TRAFFIC_JAM", "2 of 12 solved")
        assertSummary("home-progress-QUEENS", "1 of 4 solved")
        for (plain in listOf(false, true)) {
            compose.runOnIdle { simple = plain; rtl = plain }
            compose.onNodeWithText("Find your next puzzle").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            assertEquals(Puzzle.TRAFFIC_JAM, chosen.last())
            if (plain) compose.onNodeWithTag("home-room-scene", useUnmergedTree = true).assertDoesNotExist()
            else compose.onNodeWithTag("home-room-scene", useUnmergedTree = true).assertExists()
            catalog.levels.map { it.puzzle }.distinct().filter { it != Puzzle.TRAFFIC_JAM }.forEach { puzzle ->
                val card = compose.onNodeWithTag("home-puzzle-$puzzle")
                card.performScrollTo().assertIsDisplayed().assertWidthIsAtLeast(250.dp).assertHeightIsAtLeast(48.dp).performClick()
                assertEquals(puzzle, chosen.last())
                // All native text is allowed to wrap, including long titles at 200%.
                compose.onAllNodes(hasAnyAncestor(hasTestTag("home-puzzle-$puzzle")) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                    .fetchSemanticsNodes().forEach { node ->
                        val layouts = mutableListOf<TextLayoutResult>()
                        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
                        if (layouts.any { it.hasVisualOverflow }) capture("home-overflow.png")
                        if (rtl) layouts.forEach { assertEquals("English fallback must retain word/number order", ResolvedTextDirection.Ltr, it.getParagraphDirection(0)) }
                        assertTrue("Overflow in $puzzle: " + layouts.map { "${it.layoutInput.text.text} size=${it.size} width=${it.didOverflowWidth} height=${it.didOverflowHeight} lines=${it.lineCount}" }, layouts.none { it.hasVisualOverflow })
                    }
            }
            assertEquals(12, chosen.takeLast(12).toSet().size)
            compose.onNodeWithTag("home-puzzle-QUEENS").performScrollTo()
            capture("home-${if (plain) "simple-rtl" else "room"}-compact-200.png")
            compose.onNodeWithText("Daily Challenge").performScrollTo().performClick()
            compose.onNodeWithText("Progress").performScrollTo().performClick()
        }
        assertEquals(2, daily); assertEquals(2, progress)
        compose.runOnIdle { bests = emptyList() }
        assertSummary("home-progress", "0 of 56 solved")
        assertSummary("home-progress-TRAFFIC_JAM", "0 of 12 solved")
    }

    private fun capture(name: String) {
        compose.waitForIdle(); SystemClock.sleep(250)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
