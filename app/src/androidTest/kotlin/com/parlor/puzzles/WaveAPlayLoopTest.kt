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

/** Real navigation, tutorial, accepted controls and durable completion for each new family. */
class WaveAPlayLoopTest {
    @get:Rule val compose = navigationComposeRule()
    private var flipCount = 2

    @Test fun hanoiFirstVisitTeachesThenScoresARealBoard() = play(Puzzle.HANOI, "Tower of Hanoi")
    @Test fun switchesFirstVisitTeachesThenScoresARealBoard() = play(Puzzle.SWITCH_GRID, "Switch Grid")
    @Test fun jugsFirstVisitTeachesThenScoresARealBoard() = play(Puzzle.WATER_JUGS, "Water Jugs")
    @Test fun pancakesFirstVisitTeachesThenScoresARealBoard() = play(Puzzle.PANCAKE_FLIP, "Pancake Flip")

    private fun play(puzzle: Puzzle, title: String) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        val catalog = runBlocking { app.content.load() }
        val level = catalog.levels.single { it.puzzle == puzzle && it.tier == Tier.EASY }
        runBlocking {
            app.progress.configure(catalog, app.content.dailySchedule())
            app.progress.resetProgress(puzzle)
            app.settings.update { it.copy(onboarded = true, tutorialsSeen = Puzzle.entries.toSet() - puzzle, reduceMotion = true) }
        }
        val before = runBlocking { app.progress.completionCount.first() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(title).performScrollTo().performClick()
            compose.onAllNodesWithText("Level 1")[0].performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("How to play $title").fetchSemanticsNodes().isNotEmpty() }
            when (puzzle) {
                Puzzle.HANOI -> {
                    compose.onNodeWithTag("hanoi-peg-0").performScrollTo().performClick()
                    compose.onNodeWithTag("hanoi-destination-2").performScrollTo().performClick()
                    move(TransferDisk(0, 1)); move(TransferDisk(2, 1))
                }
                Puzzle.SWITCH_GRID -> listOf(0, 4, 8).forEach { move(Toggle(it)) }
                Puzzle.WATER_JUGS -> { move(FillJug(0)); move(PourJug(0, 1)) }
                Puzzle.PANCAKE_FLIP -> listOf(3, 4, 2).forEach { move(FlipPrefix(it)) }
                else -> error("Unexpected family")
            }
            compose.onNodeWithText("Ready for your first puzzle").assertExists()
            assertEquals(before, runBlocking { app.progress.completionCount.first() })
            assertTrue(runBlocking { app.progress.bests.first().none { it.levelId == level.id } })
            compose.onNodeWithText("Begin a puzzle").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("0 moves").fetchSemanticsNodes().isNotEmpty() }
            flipCount = 2
            level.reference.forEachIndexed { index, action ->
                move(action)
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithText(if (index == level.reference.lastIndex) "A puzzle, settled."
                        else if (index == 0) "1 move" else "${index + 1} moves").fetchSemanticsNodes().isNotEmpty()
                }
                if (index == 0) {
                    // Recreate through the real activity/navigation/viewmodel stack during play.
                    scenario.recreate()
                    compose.waitUntil(10_000) { compose.onAllNodesWithText("1 move").fetchSemanticsNodes().isNotEmpty() }
                }
            }
            compose.onNodeWithContentDescription("3 out of 3 stars").assertExists()
            assertEquals(before + 1, runBlocking { app.progress.completionCount.first() })
            assertEquals(3, runBlocking { app.progress.bests.first().single { it.levelId == level.id }.stars })
        }
    }

    private fun move(move: Move) {
        when (move) {
            is TransferDisk -> {
                compose.onNodeWithTag("hanoi-peg-${move.from}").performScrollTo().performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("hanoi-destination-${move.to}").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("hanoi-destination-${move.to}").performScrollTo().performClick()
            }
            is Toggle -> compose.onNodeWithTag("switch-cell-${move.index}").performScrollTo().performClick()
            is FillJug -> { selectJug(move.jug); compose.onNodeWithTag("jug-fill").performScrollTo().performClick() }
            is EmptyJug -> { selectJug(move.jug); compose.onNodeWithTag("jug-empty").performScrollTo().performClick() }
            is PourJug -> { selectJug(move.from); compose.onNodeWithTag("jug-pour-${move.to}").performScrollTo().performClick() }
            is FlipPrefix -> {
                repeat(kotlin.math.abs(move.count - flipCount)) {
                    compose.onNodeWithTag(if (move.count > flipCount) "flip-more" else "flip-less").performScrollTo().performClick()
                }
                flipCount = move.count
                compose.onNodeWithTag("flip-confirm").performScrollTo().performClick()
            }
            else -> error("Unexpected action")
        }
    }
    private fun selectJug(jug: Int) {
        compose.onNodeWithTag("jug-$jug").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("jug-fill").fetchSemanticsNodes().isNotEmpty() }
    }
}
