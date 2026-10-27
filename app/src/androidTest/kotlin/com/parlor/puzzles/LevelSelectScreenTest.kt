package com.parlor.puzzles

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.data.ContentRepository
import com.parlor.core.model.*
import com.parlor.feature.library.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.RuleChain
import java.io.File

class LevelSelectScreenTest {
    private val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    @get:Rule val rules: RuleChain = RuleChain.outerRule(NativeCompactDisplayRule()).around(compose)
    private val source by lazy { runBlocking { ContentRepository(context).load() } }
    // Layout/load fixture only. These repeated boards never enter the shipped content catalog.
    private val fixture by lazy {
        val sample = source.levels.first { it.puzzle == Puzzle.TRAFFIC_JAM }
        Catalog(levels = Tier.entries.flatMap { tier -> (1..20).map { order -> sample.copy(id = "layout-${tier.name.lowercase()}-$order", tier = tier, order = order) } })
    }
    private fun reveal(id: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("level-list").performScrollToNode(hasTestTag("level-$id"))
        return compose.onNodeWithTag("level-$id")
    }
    private fun jump(tier: Tier) {
        compose.onNodeWithTag("level-difficulty").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("level-jump-$tier").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("level-tier-$tier").assertIsDisplayed()
    }
    private fun capture(name: String) {
        compose.waitForIdle(); SystemClock.sleep(400)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }

    @Test fun eightyCardsStayLazyAndLastLevelSurvivesRecreationWithoutCoveringNavigationOrAds() {
        var locked: LevelDefinition? = null
        var plays = 0
        var backs = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent { ParlorTheme { Surface {
            LevelSelectScreen(fixture, Puzzle.TRAFFIC_JAM, emptyList(), emptyMap(), false, { plays++ }, {}, {}, { backs++ },
                onLockedLevel = { locked = it }, banner = { Box(Modifier.fillMaxWidth().height(50.dp).testTag("fixture-banner")) })
        } } }
        val cards = SemanticsMatcher("level card") { it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith("level-layout-") }
        assertTrue("Offscreen cards must not all be composed", compose.onAllNodes(cards).fetchSemanticsNodes().size < 10)
        compose.onNodeWithText("80 levels").assertExists()
        jump(Tier.EXPERT)
        val last = fixture.levels.last()
        reveal(last.id).assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        assertNull(locked); assertEquals(1, plays)
        val cardBounds = compose.onNodeWithTag("level-${last.id}").fetchSemanticsNode().boundsInRoot
        val bannerBounds = compose.onNodeWithTag("fixture-banner").fetchSemanticsNode().boundsInRoot
        assertTrue(cardBounds.bottom <= bannerBounds.top + 1)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("level-${last.id}").assertIsDisplayed()
        assertTrue(compose.onAllNodes(cards).fetchSemanticsNodes().size < 10)
        compose.onNodeWithText("Back").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        capture("levels-80-last-compact-200.png")
        jump(Tier.EASY)
        compose.onNodeWithTag("level-tier-EASY").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick(); assertEquals(1, backs)
    }

    @Test fun allTiersStayPlayableWithNoProgressOwnershipOrTemporaryGrant() {
        var pro by mutableStateOf(false)
        var now = 1_000L
        val played = mutableListOf<String>()
        val locked = mutableListOf<String>()
        compose.setContent { ParlorTheme { Surface {
            LevelSelectScreen(fixture, Puzzle.TRAFFIC_JAM, emptyList(), emptyMap(), pro,
                { played += it.id }, {}, {}, {}, onLockedLevel = { locked += it.id }, wallTime = { now })
        } } }
        for (tier in Tier.entries) {
            jump(tier)
            val id = fixture.levels.last { it.tier == tier }.id
            reveal(id).performClick()
            assertEquals(id, played.last())
        }
        compose.runOnIdle { now = Long.MAX_VALUE; pro = true }
        reveal("layout-expert-20").performClick()
        compose.runOnIdle { pro = false }
        reveal("layout-expert-20").performClick()
        assertEquals("layout-expert-20", played.last())
        assertTrue(locked.isEmpty())
    }

    @Test fun everyBestUnitAndEarnedStarsRemainReadableWithTheLockAfterProRevocationInRtl() {
        val samples = ScoringUnit.entries.map { unit -> source.levels.first { it.scoringUnit == unit }.copy(tier = Tier.HARD, order = 20) }
        val catalog = Catalog(levels = samples)
        var level by mutableStateOf(samples.first())
        var pro by mutableStateOf(true)
        val bests = samples.map { LevelBest(it.id, 3, 350, 45_000, 17, "2026-09-28") }
        compose.setContent { ParlorTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface { key(level.id) { LevelSelectScreen(catalog, level.puzzle, bests, emptyMap(), pro, {}, {}, {}, {}) } }
        } } }
        samples.forEach { sample ->
            compose.runOnIdle { level = sample; pro = true }
            jump(Tier.HARD)
            reveal(sample.id).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Ready when you are"))
            compose.runOnIdle { pro = false }
            reveal(sample.id).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Ready when you are"))
                .assertContentDescriptionContains("3 of 3 stars")
            val best = when (sample.scoringUnit) {
                ScoringUnit.MOVES -> "Best: 17 moves"
                ScoringUnit.PUSHES -> "Best: 17 pushes"
                ScoringUnit.SCENARIO_MINUTES -> "Best: 17 crossing minutes"
                ScoringUnit.TIME, ScoringUnit.TIME_AND_BACKTRACKS -> "Best time: 45 seconds"
            }
            compose.onNodeWithText(best, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("Level 20", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            capture("levels-${sample.scoringUnit.name.lowercase()}-rtl-compact-200.png")
            val layout = layouts.single()
            assertFalse("Level label overflow: width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}, size=${layout.size}, lines=${layout.lineCount}", layout.hasVisualOverflow)
        }
    }

    @Test fun emptyTiersAndDifficultyDialogRemainReachableAcrossRecreation() {
        var tutorial: Puzzle? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent { ParlorTheme { Surface {
            LevelSelectScreen(Catalog(levels = emptyList()), Puzzle.HANOI, emptyList(), emptyMap(), false, {}, { tutorial = it }, {}, {})
        } } }
        compose.onNodeWithTag("level-list").performScrollToNode(hasText("How to play Tower of Hanoi"))
        compose.onNodeWithText("How to play Tower of Hanoi").performClick(); assertEquals(Puzzle.HANOI, tutorial)
        compose.onNodeWithTag("level-difficulty").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Choose a difficulty").assertIsDisplayed()
        capture("levels-difficulty-compact-200.png")
        compose.onNodeWithTag("level-jump-EXPERT").performScrollTo().performClick()
        compose.onNodeWithTag("level-tier-EXPERT").assertIsDisplayed()
        compose.onAllNodesWithText("No levels are available in this difficulty yet.").onLast().assertIsDisplayed()
    }
}
