package com.parlor.puzzles

import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProgressNavigationTest {
    @get:Rule val compose = navigationComposeRule()
    private fun reveal(matcher: SemanticsMatcher) = compose.onNodeWithTag("progress-list").performScrollToNode(matcher)
    @Test fun progressTabsSurviveRealProNavigationAndActivityRecreationWithoutChangingSaves() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        val settings = runBlocking { app.settings.settings.first() }
        runBlocking { app.settings.update { it.copy(onboarded = true) } }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Progress").fetchSemanticsNodes().isNotEmpty() }
                val before = runBlocking { Triple(app.progress.bests.first(), app.progress.player.first(), app.progress.history.first()) }
                compose.onNodeWithText("Progress").performScrollTo().performClick()
                reveal(hasTestTag("progress-tab-history")); compose.onNodeWithTag("progress-tab-history").performClick()
                compose.onAllNodesWithText("Restore purchases").assertCountEquals(0)
                compose.onNodeWithTag("progress-tab-history").assertIsSelected()
                scenario.recreate()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("progress-list").fetchSemanticsNodes().isNotEmpty() }
                reveal(hasTestTag("progress-tab-history")); compose.onNodeWithTag("progress-tab-history").assertIsSelected()
                reveal(hasTestTag("progress-tab-achievements")); compose.onNodeWithTag("progress-tab-achievements").performClick()
                reveal(hasText("Complete a puzzle in all twelve collections")); compose.onNodeWithText("Complete a puzzle in all twelve collections").assertIsDisplayed()
                compose.onNodeWithText("Back").performClick()
                compose.onNodeWithText("Find your next puzzle").assertExists()
                assertEquals(before, runBlocking { Triple(app.progress.bests.first(), app.progress.player.first(), app.progress.history.first()) })
            }
        } finally { runBlocking { app.settings.replace(settings) } }
    }
}
