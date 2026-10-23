package com.parlor.puzzles

import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.data.PlayerRow
import com.parlor.core.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DailyLoopTest {
    @get:Rule val compose = navigationComposeRule()
    private var moves = 0

    @Test fun dailyResumeResultsPracticeAndAchievementsStaySeparateFromCampaign() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        val level = runBlocking {
            app.progress.configure(app.content.load(), app.content.dailySchedule())
            app.progress.resetProgress()
            // Isolate allowances from earlier debug installs; production reset deliberately retains them.
            app.database.progress().savePlayer(PlayerRow(ledgerJson = ParlorJson.encodeToString(RewardLedger())))
            app.progress.refreshDay()
            app.settings.update { it.copy(onboarded = true, tutorialsSeen = setOf(Puzzle.TRAFFIC_JAM)) }
            app.progress.dailyEntry().level
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Daily Challenge").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Daily Challenge").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Play for stars").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Play for stars").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("piece-A").fetchSemanticsNodes().isNotEmpty() }
            play(level.reference.first() as Translate)
            compose.onNodeWithText("Back").performScrollTo().performClick()
            compose.onNodeWithText("Save and leave").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Continue saved daily").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Back").performScrollTo().performClick()
            compose.onNodeWithText("Pick up where you left off").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("1 move").fetchSemanticsNodes().isNotEmpty() }
            level.reference.drop(1).forEach { play(it as Translate) }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("A clear way through.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("3 out of 3 stars").assertExists()
            assertTrue(runBlocking { app.progress.bests.first().isEmpty() })
            val before = runBlocking { app.progress.player.first().profile.dailyTotals }
            compose.onNodeWithText("Try another approach").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Practice · Your saved daily best stays as it is.").fetchSemanticsNodes().isNotEmpty() }
            moves = 0
            level.reference.forEach { play(it as Translate) }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Practice complete. No stars or points added.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("3 out of 3 stars").assertDoesNotExist()
            assertEquals(before, runBlocking { app.progress.player.first().profile.dailyTotals })
            assertTrue(runBlocking { app.progress.bests.first().isEmpty() })
            compose.onNodeWithText("Back to Daily Challenge").performScrollTo().performClick()
            compose.onNodeWithText("Your recent challenges stay here for 30 days. Pick up where you left off, with the same date and scoring.")
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Back").performScrollTo().performClick()
            compose.onNodeWithText("Progress").performScrollTo().performClick()
            compose.onAllNodesWithText("0 levels completed")[0].assertExists()
            compose.onNodeWithText("Achievements").performScrollTo().performClick()
            compose.onNodeWithTag("progress-list").performScrollToNode(hasText("Complete your first puzzle"))
            compose.onNodeWithText("Complete your first puzzle").assertIsDisplayed()
        }
    }

    private fun play(move: Translate) {
        compose.onNodeWithTag("piece-${move.pieceId}").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("destination-${move.pieceId}-${move.to}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("destination-${move.pieceId}-${move.to}").performScrollTo().performClick()
        moves++
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(if (moves == 1) "1 move" else "$moves moves").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("A clear way through.").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
