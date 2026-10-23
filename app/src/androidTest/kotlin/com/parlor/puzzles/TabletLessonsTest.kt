package com.parlor.puzzles

import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import com.parlor.feature.library.ParlorTheme
import com.parlor.feature.play.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class TabletLessonsTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(NativeTabletDisplayRule(2f)).around(compose)
    @Test fun tutorialAndDemonstrationKeepTheBoardBesideScrollableInstructionsWithoutRewards() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as ParlorApplication
        val level = runBlocking { app.content.load() }.levels.first { it.puzzle == Puzzle.TRAFFIC_JAM }
        val before = runBlocking { Triple(app.progress.player.first(), app.progress.bests.first(), app.progress.history.first()) }
        var demo by mutableStateOf(false)
        var finishes = 0
        val settings = PlayerSettings(reduceMotion = true, sfxVolume = 0f, haptics = false)
        compose.setContent { ParlorTheme { Surface {
            if (demo) DemonstrationScreen(level, settings, {})
            else PuzzleTutorial(Puzzle.QUEENS, settings, app.feedback, { finishes++ }, {})
        } } }
        compose.onNodeWithTag("play-wide-layout").assertExists()
        captureTabletScreen(compose, "tablet-tutorial-200.png")
        for (cell in listOf(7, 8, 14)) compose.onNodeWithTag("queen-$cell").performScrollTo().performClick()
        compose.onNodeWithText("Begin a puzzle").performScrollTo().performClick()
        assertEquals(1, finishes)
        compose.runOnIdle { demo = true }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("play-wide-layout").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("piece-A").assertIsNotEnabled()
        val boardBounds = compose.onNodeWithTag("piece-A").fetchSemanticsNode().boundsInRoot
        repeat(3) { compose.onNodeWithText("Next step").performScrollTo().performClick() }
        compose.onNodeWithText("Step 3 of ${level.reference.size}").assertExists()
        assertEquals(boardBounds, compose.onNodeWithTag("piece-A").fetchSemanticsNode().boundsInRoot)
        captureTabletScreen(compose, "tablet-demonstration-200.png")
        assertEquals(before, runBlocking { Triple(app.progress.player.first(), app.progress.bests.first(), app.progress.history.first()) })
    }

}
