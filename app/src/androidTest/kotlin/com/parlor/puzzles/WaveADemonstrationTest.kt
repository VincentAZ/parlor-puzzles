package com.parlor.puzzles

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import com.parlor.feature.library.ParlorTheme
import com.parlor.feature.play.DemonstrationScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WaveADemonstrationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyAdditionalFamilyReplaysWithoutEnablingMovesOrChangingSavedProgress() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        val levels = runBlocking { app.content.load() }.levels.filter { it.puzzle != Puzzle.TRAFFIC_JAM && it.tier == Tier.EASY }
        val saved = runBlocking { Triple(app.progress.player.first(), app.progress.history.first(), app.progress.bests.first()) }
        val completions = runBlocking { app.progress.completionCount.first() }
        var selected by mutableStateOf(levels.first())
        compose.setContent { ParlorTheme { key(selected.id) { DemonstrationScreen(selected, PlayerSettings(reduceMotion = true), {}) } } }
        for (level in levels) {
            compose.runOnIdle { selected = level }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Step 0 of ${level.reference.size}").fetchSemanticsNodes().isNotEmpty() }
            val tag = when (level.puzzle) {
                Puzzle.SLIDING_TILES -> "tile-0"
                Puzzle.BLOCK_ESCAPE -> "block-cell-0"
                Puzzle.BOX_PUSHER -> "worker-UP"
                Puzzle.PEG_SOLITAIRE -> "peg-${(level.initial as PegBoard).pegs.first()}"
                Puzzle.QUEENS -> "queen-0"
                Puzzle.KNIGHTS_TOUR -> "knight-0"
                Puzzle.CROSSINGS -> "actor-G"
                Puzzle.HANOI -> "hanoi-peg-0"
                Puzzle.SWITCH_GRID -> "switch-cell-0"
                Puzzle.WATER_JUGS -> "jug-0"
                Puzzle.PANCAKE_FLIP -> "flip-confirm"
                else -> error("Wave A")
            }
            compose.onNodeWithTag(tag).assertIsNotEnabled()
            repeat(level.reference.size) { compose.onNodeWithText("Next step").performScrollTo().performClick() }
            compose.onNodeWithText("The demonstration is complete. Return to try your own approach.").assertExists()
        }
        assertEquals(saved, runBlocking { Triple(app.progress.player.first(), app.progress.history.first(), app.progress.bests.first()) })
        assertEquals(completions, runBlocking { app.progress.completionCount.first() })
    }
}
