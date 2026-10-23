package com.parlor.puzzles

import androidx.compose.ui.test.*
import androidx.window.layout.FoldingFeature as Fold
import androidx.window.layout.WindowLayoutInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.window.layout.WindowMetricsCalculator
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class FoldNavigationTest {
    private val compose = navigationComposeRule()
    private val display = NativeTabletDisplayRule()
    private val publisher = WindowLayoutInfoPublisherRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(display).around(publisher).around(compose)

    @Test fun foldUnfoldAndRecreationKeepTheAttemptAndAllowOneCompletion() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        val catalog = runBlocking { app.content.load() }
        val level = catalog.levels.first { it.puzzle == Puzzle.TRAFFIC_JAM && it.tier == Tier.EASY }
        val settings = runBlocking { app.settings.settings.first() }
        runBlocking {
            app.progress.configure(catalog, app.content.dailySchedule())
            val old = app.progress.startOrResume(level)
            app.progress.restart(level, old.id)
            app.settings.update { it.copy(onboarded = true, tutorialsSeen = it.tutorialsSeen + Puzzle.TRAFFIC_JAM) }
        }
        fun saved() = runBlocking { app.progress.startOrResume(level) }
        fun move(action: Translate) {
            compose.onNodeWithTag("piece-${action.pieceId}").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("destination-${action.pieceId}-${action.to}").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("destination-${action.pieceId}-${action.to}").performScrollTo().assertIsEnabled().performClick()
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Find your next puzzle").fetchSemanticsNodes().isNotEmpty() }
                fun fold(vertical: Boolean) {
                    scenario.onActivity { activity ->
                        val window = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds
                        val insets = requireNotNull(ViewCompat.getRootWindowInsets(activity.window.decorView))
                            .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                        val center = if (vertical) (insets.left + window.width() - insets.right) / 2
                            else (insets.top + window.height() - insets.bottom) / 2
                        publisher.overrideWindowLayoutInfo(WindowLayoutInfo(listOf(FoldingFeature(activity, size = 40, center = center,
                            orientation = if (vertical) Fold.Orientation.VERTICAL else Fold.Orientation.HORIZONTAL))))
                    }
                    compose.waitForIdle()
                }
                fold(true)
                captureTabletScreen(compose, "fold-home-book.png")
                compose.onNodeWithText("Find your next puzzle").performScrollTo().performClick()
                captureTabletScreen(compose, "fold-levels-book.png")
                compose.onNodeWithTag("level-list").performScrollToNode(hasTestTag("level-${level.id}"))
                compose.onNodeWithTag("level-${level.id}").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("play-fold-layout").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Undo").performScrollTo().assertIsDisplayed()
                captureTabletScreen(compose, "fold-play-landscape.png")
                move(level.reference.first() as Translate)
                compose.waitUntil(10_000) { saved().history.size == 1 }
                compose.onNodeWithTag("piece-A").performScrollTo().performClick()
                compose.onNodeWithText("Magnify board").performScrollTo().performClick()
                compose.onNodeWithText("View right").performScrollTo().performClick()
                compose.waitUntil(10_000) { saved().viewport == Viewport(1, 0, 2, "A") }
                val before = saved()
                assertTrue(before.activeTimeMs > 0)
                compose.onNodeWithContentDescription("Board overview. Tap to move the magnified view.").performScrollTo().assertIsDisplayed()
                captureTabletScreen(compose, "fold-play-magnified.png")
                fold(false)
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("play-fold-layout").fetchSemanticsNodes().isNotEmpty() }
                val tabletop = saved()
                assertEquals(before.board, tabletop.board); assertEquals(before.history, tabletop.history)
                assertEquals(before.viewport, tabletop.viewport); assertEquals(before.hintsUsed, tabletop.hintsUsed)
                assertTrue(tabletop.activeTimeMs >= before.activeTimeMs)
                captureTabletScreen(compose, "fold-play-tabletop.png")
                publisher.overrideWindowLayoutInfo(WindowLayoutInfo(emptyList()))
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("play-wide-layout").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(before.viewport, saved().viewport)
                display.orientation(true)
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("play-single-layout").fetchSemanticsNodes().isNotEmpty() }
                display.orientation(false)
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("play-wide-layout").fetchSemanticsNodes().isNotEmpty() }
                scenario.recreate()
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("play-wide-layout").fetchSemanticsNodes().isNotEmpty() }
                fold(true)
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("play-fold-layout").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(before.viewport, saved().viewport); assertEquals(before.history, saved().history)
                compose.onNodeWithText("Undo").performScrollTo().assertIsDisplayed().performClick()
                compose.waitUntil(10_000) { saved().history.isEmpty() }
                assertEquals(level.initial, saved().board)
                compose.onNodeWithText("Redo").performClick()
                compose.waitUntil(10_000) { saved().history.size == 1 }
                assertEquals(before.board, saved().board)
                compose.onNodeWithText("Restart").performScrollTo().performClick()
                compose.onAllNodesWithText("Restart").onLast().performScrollTo().performClick()
                compose.waitUntil(10_000) { saved().history.isEmpty() && saved().scoredMoves == 0 }
                val id = saved().id
                val completions = runBlocking { app.progress.completionCount.first() }
                level.reference.forEachIndexed { index, action ->
                    move(action as Translate)
                    compose.waitUntil(10_000) {
                        val durable = runBlocking { app.progress.observeAttempt(id).first() }
                        if (index == level.reference.lastIndex) durable?.completed == true && durable.scoredMoves == level.reference.size
                        else durable?.history?.size == index + 1
                    }
                }
                compose.onNodeWithContentDescription("3 out of 3 stars").assertExists()
                compose.onNodeWithText("Next level").performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("play-single-layout").assertExists()
                assertEquals(completions + 1, runBlocking { app.progress.completionCount.first() })
                captureTabletScreen(compose, "fold-play-results.png")
                compose.onNodeWithText("Back to levels").performScrollTo().performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("level-list").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(completions + 1, runBlocking { app.progress.completionCount.first() })
                captureTabletScreen(compose, "fold-results-return.png")
            }
        } finally { runBlocking { app.settings.replace(settings) } }
    }
}
