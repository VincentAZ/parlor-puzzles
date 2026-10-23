package com.parlor.puzzles

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.data.ProgressRepository
import com.parlor.core.model.*
import com.parlor.feature.library.ParlorTheme
import com.parlor.feature.play.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DemonstrationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun demonstrationPreservesPausedBoardTimeHintsAndAllRewards() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        val catalog = runBlocking { app.content.load() }
        val level = catalog.levels.first()
        // An injected provider exercises Pro behavior without a fake purchase path in the app.
        val repo = ProgressRepository(app.database, isPro = { true })
        repo.configure(catalog, runBlocking { app.content.dailySchedule() })
        val original = runBlocking {
            repo.resetProgress()
            val attempt = repo.startOrResume(level)
            repo.move(level, attempt.id, level.reference.first(), 1234)
            repo.useHint(level, attempt.id, 0)!!
        }
        compose.setContent {
            ParlorTheme {
                val vm: PlayViewModel = viewModel(factory = PlayViewModel.factory(level, repo))
                var demonstration by remember { mutableStateOf(false) }
                val settings = PlayerSettings(reduceMotion = true)
                if (demonstration) DemonstrationScreen(level, settings, onBack = { demonstration = false })
                else PlayScreen(vm, settings, 3, false, app.feedback, onLeave = {}, onNext = null,
                    pro = true, onDemonstrate = { demonstration = true })
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Solve for me").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Solve for me").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Step 0 of ${level.reference.size}").fetchSemanticsNodes().isNotEmpty() }
        val paused = runBlocking { repo.observeAttempt(original.id).first()!! }
        val player = runBlocking { repo.player.first() }
        val completions = runBlocking { repo.completionCount.first() }
        repeat(level.reference.size) {
            compose.onNodeWithText("Next step").performScrollTo().performClick()
        }
        compose.onNodeWithText("The way is clear. Return to try your own approach.").assertExists()
        assertEquals(paused, runBlocking { repo.observeAttempt(original.id).first() })
        assertEquals(player, runBlocking { repo.player.first() })
        assertEquals(completions, runBlocking { repo.completionCount.first() })
        assertTrue(runBlocking { repo.bests.first().isEmpty() })
        compose.onNodeWithText("Return to your puzzle").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("1 move").fetchSemanticsNodes().isNotEmpty() }
        val restored = runBlocking { repo.observeAttempt(original.id).first()!! }
        assertEquals(original.board, restored.board)
        assertEquals(original.history, restored.history)
        assertTrue(restored.hintsUsed)
        assertEquals(original.viewport, restored.viewport)
    }
}
