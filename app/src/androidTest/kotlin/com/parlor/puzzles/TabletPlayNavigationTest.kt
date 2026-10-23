package com.parlor.puzzles

import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class TabletPlayNavigationTest {
    private val compose = navigationComposeRule()
    private val display = NativeTabletDisplayRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(display).around(compose)

    @Test fun rotationRetainsTheDurableBoardHistoryAndMagnifiedSelectionThenAllowsCompletion() {
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
            compose.onNodeWithTag("destination-${action.pieceId}-${action.to}").performScrollTo().performClick()
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Find your next puzzle").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Find your next puzzle").performScrollTo().performClick()
                compose.onNodeWithTag("level-list").performScrollToNode(hasTestTag("level-${level.id}"))
                compose.onNodeWithTag("level-${level.id}").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("play-wide-layout").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Undo").assertIsDisplayed()
                captureTabletScreen(compose, "tablet-play-landscape.png")
                move(level.reference.first() as Translate)
                compose.waitUntil { saved().history.size == 1 }
                compose.onNodeWithTag("piece-A").performScrollTo().performClick()
                compose.onNodeWithText("Magnify board").performScrollTo().performClick()
                compose.onNodeWithText("View right").performScrollTo().performClick()
                compose.waitUntil { saved().viewport == Viewport(1, 0, 2, "A") }
                val before = saved()
                assertTrue(before.activeTimeMs > 0)
                compose.onNodeWithContentDescription("Board overview. Tap to move the magnified view.").performScrollTo().assertIsDisplayed()
                captureTabletScreen(compose, "tablet-play-magnified.png")
                display.orientation(true)
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("play-single-layout").fetchSemanticsNodes().isNotEmpty() }
                val portrait = saved()
                assertEquals(before.board, portrait.board); assertEquals(before.history, portrait.history)
                assertEquals(before.viewport, portrait.viewport); assertEquals(before.hintsUsed, portrait.hintsUsed)
                assertTrue(portrait.activeTimeMs >= before.activeTimeMs)
                captureTabletScreen(compose, "tablet-play-portrait.png")
                display.orientation(false)
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("play-wide-layout").fetchSemanticsNodes().isNotEmpty() }
                scenario.recreate()
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("play-wide-layout").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(before.viewport, saved().viewport); assertEquals(before.history, saved().history)
                compose.onNodeWithText("Undo").assertIsDisplayed().performClick()
                compose.waitUntil { saved().history.isEmpty() }
                assertEquals(level.initial, saved().board)
                compose.onNodeWithText("Redo").performClick()
                compose.waitUntil { saved().history.size == 1 }
                assertEquals(before.board, saved().board)
                compose.onNodeWithText("Restart").performScrollTo().performClick()
                compose.onAllNodesWithText("Restart").onLast().performClick()
                compose.waitUntil { saved().history.isEmpty() && saved().scoredMoves == 0 }
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
                captureTabletScreen(compose, "tablet-play-results.png")
            }
        } finally { runBlocking { app.settings.replace(settings) } }
    }
}
