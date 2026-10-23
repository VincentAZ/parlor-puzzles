package com.parlor.puzzles

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import com.parlor.core.platform.ads.AdPlacement
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class CompletionMotionTest {
    @get:Rule val compose = navigationComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication

    @Test fun completionIsDurableBeforeTheLastSlideAndRecreationDoesNotRepeatTheCelebration() = withAttempt(7, false) { level, attempt, before, scenario ->
        select(level.reference.last() as Translate)
        val start = compose.onNodeWithTag("piece-A").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("destination-A-6").performClick()
        val saved = read { app.progress.observeAttempt(attempt.id).first { it?.completed == true }!! }
        assertEquals(8, saved.scoredMoves)
        // Completed attempts intentionally discard undo history after recording the result.
        assertTrue(saved.history.isEmpty())
        assertEquals(6, (saved.board as TrafficJamBoard).pieces.single { it.id == "A" }.x)
        advanceUntil { compose.onAllNodesWithText("8 moves").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("A clear way through.").assertDoesNotExist()
        val middle = compose.onNodeWithTag("piece-A").fetchSemanticsNode().boundsInRoot
        assertTrue("The saved final move is still visibly travelling", middle.left > start.left)
        compose.onNodeWithTag("piece-A").assertIsNotEnabled()
        assertEquals(AdPlacement.PLAY, app.ads.currentPlacement.value)
        capture("completion-last-slide")
        compose.mainClock.advanceTimeBy(240)
        compose.onNodeWithText("A clear way through.").assertExists()
        compose.onNodeWithContentDescription("3 out of 3 stars").assertExists()
        compose.mainClock.advanceTimeBy(32)
        capture("completion-confetti")
        compose.onNodeWithTag("completion-confetti", useUnmergedTree = true).assertExists()
        assertEquals(AdPlacement.RESULTS, app.ads.currentPlacement.value)
        scenario.recreate()
        advanceUntil { compose.onAllNodesWithText("A clear way through.").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("completion-confetti", useUnmergedTree = true).assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        assertEquals(before + 1, read { app.progress.completionCount.first() })
        assertEquals(saved, read { app.progress.observeAttempt(attempt.id).first() })
        val best = read { app.progress.bests.first().single { it.levelId == level.id } }
        assertEquals(3, best.stars)
        assertEquals(8, best.moves)
    }

    @Test fun reducedMotionShowsTheSavedResultWithoutASlideDelayOrConfetti() = withAttempt(7, true) { _, attempt, before, _ ->
        select(Translate("A", 6))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("destination-A-6").performClick()
        val saved = read { app.progress.observeAttempt(attempt.id).first { it?.completed == true }!! }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("A clear way through.").assertExists()
        compose.onNodeWithTag("completion-confetti", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("piece-A").assertDoesNotExist()
        assertEquals(8, saved.scoredMoves)
        assertEquals(AdPlacement.RESULTS, app.ads.currentPlacement.value)
        capture("completion-reduced")
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("completion-confetti", useUnmergedTree = true).assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        assertEquals(before + 1, read { app.progress.completionCount.first() })
    }

    @Test fun recreationDuringAnOrdinarySlideOpensAtTheCommittedPositionWithHistoryAndSelection() = withAttempt(1, false) { _, attempt, before, scenario ->
        select(Translate("C", 3))
        val origin = compose.onNodeWithTag("piece-C").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("destination-C-3").performClick()
        val saved = read { app.progress.observeAttempt(attempt.id).first { it?.history?.size == 2 }!! }
        advanceUntil { compose.onAllNodesWithText("2 moves").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(64)
        val moving = compose.onNodeWithTag("piece-C").fetchSemanticsNode().boundsInRoot
        val destination = origin.left + origin.width / 2
        assertTrue(moving.left > origin.left && moving.left < destination)
        assertEquals(3, (saved.board as TrafficJamBoard).pieces.single { it.id == "C" }.x)
        scenario.recreate()
        advanceUntil { compose.onAllNodesWithTag("piece-C").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(destination, compose.onNodeWithTag("piece-C").fetchSemanticsNode().boundsInRoot.left, 1f)
        compose.mainClock.autoAdvance = true
        val restored = read { app.progress.observeAttempt(attempt.id).first()!! }
        assertEquals(saved.board, restored.board)
        assertEquals(saved.history, restored.history)
        assertEquals(saved.viewport, restored.viewport)
        assertEquals("C", restored.viewport.selectedId)
        assertEquals(2, restored.scoredMoves)
        assertTrue(restored.activeTimeMs >= saved.activeTimeMs)
        assertEquals(before, read { app.progress.completionCount.first() })
    }

    private fun withAttempt(steps: Int, reduced: Boolean, check: (LevelDefinition, Attempt, Int, ActivityScenario<MainActivity>) -> Unit) {
        val preferences = runBlocking { app.settings.settings.first() }
        val catalog = runBlocking { app.content.load() }
        val level = catalog.levels.first { it.puzzle == Puzzle.TRAFFIC_JAM && it.tier == Tier.EASY }
        val attempt = runBlocking {
            app.progress.configure(catalog, app.content.dailySchedule())
            val fresh = app.progress.restart(level, app.progress.startOrResume(level).id)
            level.reference.take(steps).forEach { app.progress.move(level, fresh.id, it, 10) }
            app.settings.update { it.copy(onboarded = true, tutorialsSeen = Puzzle.entries.toSet(), reduceMotion = reduced, sfxVolume = 0f, haptics = false) }
            fresh
        }
        val before = runBlocking { app.progress.completionCount.first() }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Pick up where you left off").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Pick up where you left off").performScrollTo().performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
                check(level, attempt, before, scenario)
            }
        } finally {
            compose.mainClock.autoAdvance = true
            runBlocking { app.settings.replace(preferences) }
        }
    }

    private fun select(move: Translate) {
        compose.onNodeWithTag("piece-${move.pieceId}").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("destination-${move.pieceId}-${move.to}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("destination-${move.pieceId}-${move.to}").performScrollTo().assertIsEnabled()
        compose.waitForIdle()
    }

    /** Observe a real transaction without advancing the animation clock past the frame under test. */
    private fun <T> read(block: suspend () -> T): T {
        val scope = CoroutineScope(Dispatchers.IO)
        val pending = scope.async { withTimeout(10_000) { block() } }
        try {
            compose.waitUntil(12_000) { pending.isCompleted }
            return runBlocking { pending.await() }
        } finally { scope.cancel() }
    }

    private fun advanceUntil(predicate: () -> Boolean) {
        compose.waitUntil(12_000) {
            compose.mainClock.advanceTimeByFrame()
            predicate()
        }
    }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(app.filesDir, "motion-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
