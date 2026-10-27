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

class LevelSelectionNavigationTest {
    @get:Rule val compose = navigationComposeRule()
    @Test fun levelPositionSurvivesProReturnAndActivityRecreationWithoutChangingProgress() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        val settings = runBlocking { app.settings.settings.first() }
        val catalog = runBlocking { app.content.load() }
        val last = catalog.levels.last { it.puzzle == Puzzle.TRAFFIC_JAM && it.tier == Tier.EXPERT }
        runBlocking { app.settings.update { it.copy(onboarded = true) } }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Find your next puzzle").fetchSemanticsNodes().isNotEmpty() }
                val before = runBlocking { Triple(app.progress.bests.first(), app.progress.player.first(), app.progress.history.first()) }
                compose.onNodeWithText("Find your next puzzle").performScrollTo().performClick()
                compose.onNodeWithTag("level-difficulty").performClick()
                compose.onNodeWithTag("level-jump-EXPERT").performScrollTo().performClick()
                compose.onNodeWithTag("level-list").performScrollToNode(hasTestTag("level-${last.id}"))
                compose.onNodeWithTag("level-${last.id}").assertIsDisplayed()
                compose.onAllNodesWithText("Explore Pro").assertCountEquals(0)
                scenario.recreate()
                compose.waitUntil { compose.onAllNodesWithTag("level-${last.id}").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("level-${last.id}").assertIsDisplayed()
                compose.onNodeWithText("Back").performClick()
                compose.onNodeWithText("Find your next puzzle").assertExists()
                assertEquals(before, runBlocking { Triple(app.progress.bests.first(), app.progress.player.first(), app.progress.history.first()) })
            }
        } finally { runBlocking { app.settings.replace(settings) } }
    }
}
