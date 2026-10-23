package com.parlor.puzzles

import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ResultsReturnNavigationTest {
    @get:Rule val compose = navigationComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication

    @Test fun resumedCampaignReturnsToItsOwnCollectionAndConsumesTheDestinationOnce() {
        val preferences = runBlocking { app.settings.settings.first() }
        val catalog = runBlocking { app.content.load() }
        val level = catalog.levels.first { it.puzzle == Puzzle.HANOI && it.tier == Tier.EASY }
        val attempt = runBlocking {
            app.progress.configure(catalog, app.content.dailySchedule())
            val fresh = app.progress.restart(level, app.progress.startOrResume(level).id)
            app.progress.move(level, fresh.id, level.reference.first(), 10)
            app.settings.update { it.copy(onboarded = true, tutorialsSeen = Puzzle.entries.toSet(), reduceMotion = true) }
            fresh
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                resume()
                compose.waitUntil { compose.onAllNodesWithTag("hanoi-peg-0").fetchSemanticsNodes().isNotEmpty() }
                for ((index, action) in level.reference.drop(1).withIndex()) {
                    val move = action as TransferDisk
                    compose.onNodeWithTag("hanoi-peg-${move.from}").performScrollTo().performClick()
                    compose.onNodeWithTag("hanoi-destination-${move.to}").performScrollTo().performClick()
                    compose.waitUntil { compose.onAllNodesWithText(
                        if (index == level.reference.size - 2) "A puzzle, settled." else "${index + 2} moves"
                    ).fetchSemanticsNodes().isNotEmpty() }
                }
                compose.onNodeWithText("Back to levels").performScrollTo().performClick()
                compose.onNodeWithTag("level-list").assertExists()
                compose.onNodeWithText("Tower of Hanoi").assertExists()
                compose.onNodeWithTag("level-list").performScrollToNode(hasTestTag("level-${level.id}"))
                compose.onNodeWithTag("level-${level.id}").assertExists()
                scenario.recreate()
                compose.waitUntil { compose.onAllNodesWithTag("level-list").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Tower of Hanoi").assertExists()
                compose.onNodeWithText("Back").performClick()
                compose.onNodeWithText("Settings").performScrollTo().performClick()
                scenario.recreate()
                compose.waitUntil { compose.onAllNodesWithText("Reminders").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("level-list").assertDoesNotExist()
            }
        } finally { runBlocking { app.settings.replace(preferences) } }
    }

    @Test fun resumedDailyReturnsToDailyHistoryInsteadOfHome() {
        val preferences = runBlocking { app.settings.settings.first() }
        val daily = runBlocking {
            app.progress.configure(app.content.load(), app.content.dailySchedule())
            val session = app.progress.startDaily()
            val fresh = app.progress.restart(session.level, session.attempt.id)
            app.settings.update { it.copy(onboarded = true, tutorialsSeen = Puzzle.entries.toSet(), reduceMotion = true) }
            session.copy(attempt = fresh)
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                resume()
                compose.waitUntil { compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
                // The repository delivers a real durable daily completion while this route observes it.
                runBlocking { daily.level.reference.forEach { app.progress.move(daily.level, daily.attempt.id, it, 10) } }
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Back to Daily Challenge").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Back to Daily Challenge").performScrollTo().performClick()
                compose.onNodeWithText("Your recent challenges stay here for 30 days. Pick up where you left off, with the same date and scoring.")
                    .performScrollTo().assertIsDisplayed()
                scenario.recreate()
                compose.waitUntil { compose.onAllNodesWithText("Your recent challenges stay here for 30 days. Pick up where you left off, with the same date and scoring.").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(daily.attempt.challengeDate, readWhileAdvancingUi { app.progress.observeAttempt(daily.attempt.id).first() }?.challengeDate)
            }
        } finally { runBlocking { app.settings.replace(preferences) } }
    }

    private fun resume() {
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Pick up where you left off").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Pick up where you left off").performScrollTo().performClick()
    }

    /** Startup work may own the database lease until the controlled Compose dispatcher advances. */
    private fun <T> readWhileAdvancingUi(read: suspend () -> T): T {
        val scope = CoroutineScope(Dispatchers.IO)
        val pending = scope.async { withTimeout(10_000) { read() } }
        try {
            compose.waitUntil(12_000) { pending.isCompleted }
            return runBlocking { pending.await() }
        } finally { scope.cancel() }
    }
}
