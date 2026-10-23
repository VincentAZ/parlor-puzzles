package com.parlor.puzzles

import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.Translate
import com.parlor.core.model.Puzzle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PlayLoopTest {
    @get:Rule val compose = navigationComposeRule()

    @Before fun startWithAFreshBoard() {
        // Instrumentation may install over a manually exercised debug build.
        // Preserve bests but reset this test's attempt so the reference starts at move zero.
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        runBlocking {
            val catalog = app.content.load()
            app.progress.configure(catalog, app.content.dailySchedule())
            val level = catalog.levels.first()
            val attempt = app.progress.startOrResume(level)
            app.progress.restart(level, attempt.id)
        }
    }

    @Test fun timerCheckpointDoesNotCancelARealTouch() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        runBlocking { app.settings.update { it.copy(onboarded = true, tutorialsSeen = setOf(Puzzle.TRAFFIC_JAM)) } }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Find your next puzzle").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Find your next puzzle").performScrollTo().performClick()
            compose.onAllNodesWithText("Level 1")[0].performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("piece-C").fetchSemanticsNodes().isNotEmpty() }
            val piece = compose.onNodeWithTag("piece-C")
            piece.performScrollTo().performTouchInput { down(center) }
            // Hold across a real one-second durable timer checkpoint.
            Thread.sleep(1_300)
            piece.performTouchInput { up() }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Piece C · choose a destination").fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test fun tutorialTeachesSelectionMovementAndExitWithoutGrantingProgress() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        runBlocking { app.settings.update { it.copy(onboarded = true) } }
        val before = runBlocking { app.progress.completionCount.first() }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Find your next puzzle").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Find your next puzzle").performScrollTo().performClick()
            compose.onNodeWithText("How to play Traffic Jam").performScrollTo().performClick()
            compose.onNodeWithTag("piece-A").performScrollTo().performClick()
            compose.onNodeWithTag("piece-B").performScrollTo().performClick()
            compose.onNodeWithTag("destination-B-3").performScrollTo().performClick()
            compose.onNodeWithTag("piece-A").performScrollTo().performClick()
            compose.onNodeWithTag("destination-A-6").performScrollTo().performClick()
            compose.onNodeWithText("Ready for your first puzzle").assertExists()
            compose.onNodeWithText("Begin a puzzle").performScrollTo().performClick()
            compose.onNodeWithText("How to play Traffic Jam").assertExists()
            assertEquals(before, runBlocking { app.progress.completionCount.first() })
        }
    }

    @Test fun firstBoardCanBeSolvedWithAccessibleTapControls() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        val catalog = runBlocking { app.content.load() }
        val level = catalog.levels.first()
        runBlocking { app.settings.update { it.copy(onboarded = true, tutorialsSeen = setOf(Puzzle.TRAFFIC_JAM)) } }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Find your next puzzle").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Find your next puzzle").performScrollTo().performClick()
            compose.onAllNodesWithText("Level 1")[0].performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("piece-A").fetchSemanticsNodes().isNotEmpty() }
            level.reference.forEachIndexed { index, raw ->
                val move = raw as Translate
                compose.onNodeWithTag("piece-${move.pieceId}").performScrollTo().performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("destination-${move.pieceId}-${move.to}").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("destination-${move.pieceId}-${move.to}").performScrollTo().performClick()
                compose.waitUntil(10_000) {
                    if (index == level.reference.lastIndex) compose.onAllNodesWithText("A clear way through.").fetchSemanticsNodes().isNotEmpty()
                    else compose.onAllNodesWithText(if (index == 0) "1 move" else "${index + 1} moves").fetchSemanticsNodes().isNotEmpty()
                }
            }
            compose.onNodeWithText("A clear way through.").assertExists()
            compose.onNodeWithContentDescription("3 out of 3 stars").assertExists()
        }
    }
}
