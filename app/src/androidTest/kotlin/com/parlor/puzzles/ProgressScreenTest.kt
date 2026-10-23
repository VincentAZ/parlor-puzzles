package com.parlor.puzzles

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.parlor.core.data.*
import com.parlor.core.model.*
import com.parlor.core.progress.Scoring
import com.parlor.feature.library.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.RuleChain
import java.io.File

/** Native 320 x 640 dp / 200% font settings exercise actual scrolling and reachable actions. */
class ProgressScreenTest {
    private val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val compact = NativeCompactDisplayRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(compact).around(compose)
    private val catalog by lazy { runBlocking { ContentRepository(context).load() } }
    private val traffic get() = catalog.levels.first { it.puzzle == Puzzle.TRAFFIC_JAM }
    private fun entry(level: LevelDefinition = traffic, id: String = "sample", mode: AttemptMode = AttemptMode.CAMPAIGN,
                      practice: Boolean = false): CompletedAttempt {
        val attempt = Attempt.start(id, level, mode, if (mode == AttemptMode.DAILY) "2026-09-28" else null,
            if (practice) DailyEligibility.PRACTICE else DailyEligibility.FIRST_COMPLETION)
            .copy(completed = true, scoredMoves = level.par, activeTimeMs = 12_000, undoCount = 2, walkingSteps = 5)
        return CompletedAttempt(attempt, level.puzzle, "2026-09-29", level, Scoring.result(level, attempt))
    }
    private fun reveal(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        compose.onNodeWithTag("progress-list").performScrollToNode(matcher)
        return compose.onNode(matcher)
    }
    private fun tab(name: String) { reveal(hasTestTag("progress-tab-$name")).assertHeightIsAtLeast(48.dp).performClick() }
    private fun capture(name: String) {
        compose.waitForIdle(); SystemClock.sleep(400)
        val image = instrumentation.uiAutomation.takeScreenshot()
        File(context.filesDir, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
    }

    @Test fun filtersPagesRecreationAndProRevocationKeepDetailsBoundedAndPrivate() {
        var pro by mutableStateOf(false)
        var subscriptions = 0
        var active = 0
        var proOpens = 0
        val queries = mutableListOf<HistoryQuery>()
        val provider: (HistoryQuery) -> Flow<HistoryPage> = { query -> flow {
            subscriptions++; active++; queries += query
            try {
                val level = catalog.levels.first { it.puzzle == (query.puzzle ?: Puzzle.TRAFFIC_JAM) }
                emit(HistoryPage(listOf(entry(level, if (query.before == null) "page-new" else "page-old")),
                    if (query.before == null) HistoryCursor(10, "cursor") else null))
                awaitCancellation()
            } finally { active-- }
        } }
        val restoration = StateRestorationTester(compose)
        val owner = object : LifecycleOwner { override val lifecycle = LifecycleRegistry(this) }
        instrumentation.runOnMainSync { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        restoration.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            ParlorTheme { Surface { ProgressScreen(catalog, emptyList(), PlayerSummary(), pro, provider, { proOpens++ }, {}) } }
        } }
        assertEquals(0, subscriptions)
        tab("history")
        reveal(hasText("More room to explore.")).performClick(); assertEquals(1, proOpens)
        assertEquals(0, subscriptions)
        compose.runOnIdle { pro = true }
        compose.waitUntil { active == 1 }
        compose.onNodeWithTag("history-newer").assertDoesNotExist()
        reveal(hasTestTag("history-older")).assertHeightIsAtLeast(48.dp).performClick()
        compose.waitUntil { queries.lastOrNull()?.before != null }
        compose.onNodeWithTag("history-older").assertDoesNotExist()
        reveal(hasTestTag("history-newer")).assertHeightIsAtLeast(48.dp).performClick()
        reveal(hasTestTag("progress-puzzle-filter")).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("progress-filter-WATER_JUGS").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.waitUntil { queries.lastOrNull()?.puzzle == Puzzle.WATER_JUGS }
        reveal(hasTestTag("progress-kind-filter")).performClick()
        compose.onNodeWithTag("progress-filter-DAILY").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.waitUntil { queries.lastOrNull()?.kind == HistoryKind.DAILY }
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil { queries.lastOrNull() == HistoryQuery(Puzzle.WATER_JUGS, HistoryKind.DAILY) && active == 1 }
        reveal(hasTestTag("progress-puzzle-filter")).assertTextContains("Puzzle: Water Jugs")
        capture("history-filters-compact-200.png")
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        compose.waitUntil { active == 0 }
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.waitUntil { active == 1 }
        reveal(hasTestTag("history-page-new"))
        compose.runOnIdle { pro = false }
        compose.waitUntil { active == 0 }
        reveal(hasText("More room to explore.")).assertIsDisplayed()
        compose.onAllNodes(hasTestTag("history-page-new")).assertCountEquals(0)
        capture("history-free-compact-200.png")
    }

    @Test fun errorsRetryAndChangingFiltersCancelsAnOlderDelayedPage() {
        var fail = true
        val waiting = CompletableDeferred<HistoryPage>()
        var cancelled = false
        val provider: (HistoryQuery) -> Flow<HistoryPage> = { query -> flow {
            if (fail) error("history fixture")
            if (query.puzzle == null) {
                try { emit(waiting.await()) } finally { cancelled = true }
            } else emit(HistoryPage(listOf(entry(catalog.levels.first { it.puzzle == query.puzzle }, "filtered"))))
        } }
        compose.setContent { ParlorTheme { Surface { ProgressScreen(catalog, emptyList(), PlayerSummary(), true, provider, {}, {}) } } }
        tab("history")
        reveal(hasText("Your history could not be loaded. Your saved progress has not changed. Please try again.")).assertIsDisplayed()
        capture("history-error-compact-200.png")
        fail = false
        reveal(hasText("Try again")).assertHeightIsAtLeast(48.dp).performClick()
        reveal(hasTestTag("history-loading")).assertExists()
        reveal(hasTestTag("progress-puzzle-filter")).performClick()
        compose.onNodeWithTag("progress-filter-WATER_JUGS").performScrollTo().performClick()
        compose.waitUntil { cancelled }
        waiting.complete(HistoryPage(listOf(entry(id = "stale"))))
        reveal(hasTestTag("history-filtered")).assertExists()
        compose.onNodeWithTag("history-stale").assertDoesNotExist()
    }

    @Test fun everyScoringUnitAndDailyPracticeHaveReadableResultsAtTwoHundredPercent() {
        val fixtures = ScoringUnit.entries.map { unit -> entry(catalog.levels.first { it.scoringUnit == unit }, unit.name) } +
            entry(id = "daily-scored", mode = AttemptMode.DAILY) + entry(id = "daily-practice", mode = AttemptMode.DAILY, practice = true)
        val provider: (HistoryQuery) -> Flow<HistoryPage> = { flowOf(HistoryPage(fixtures)) }
        compose.setContent { ParlorTheme { Surface { ProgressScreen(catalog, emptyList(), PlayerSummary(), true, provider, {}, {}) } } }
        tab("history")
        fixtures.forEach { fixture ->
            val card = hasTestTag("history-${fixture.attempt.id}")
            reveal(card).assertExists()
            val outcome = if (fixture.result == null) "Practice completion · No stars or points awarded"
                else "Attempt result: ${fixture.result!!.stars} stars · ${fixture.result!!.points} points"
            reveal(hasText(outcome) and hasAnyAncestor(card)).assertIsDisplayed()
        }
        reveal(hasText("Practice completion · No stars or points awarded"))
        capture("history-practice-compact-200.png")
        reveal(hasText("Walking steps: 5")).assertIsDisplayed()
        capture("history-pushes-compact-200.png")
    }

    @Test fun bestResultsAreProOnlyRetainIndependentBestsAndUseReachableRtlFilters() {
        var pro by mutableStateOf(false)
        var requests = 0
        val provider: (HistoryQuery) -> Flow<HistoryPage> = { requests++; flowOf(HistoryPage()) }
        val best = LevelBest(traffic.id, 3, 325, 12_000, traffic.par, "2026-09-28")
        compose.setContent { ParlorTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface { ProgressScreen(catalog, listOf(best), PlayerSummary(), pro, provider, {}, {}) }
        } } }
        tab("bests")
        reveal(hasText("More room to explore.")).assertIsDisplayed()
        compose.onNodeWithTag("best-${traffic.id}").assertDoesNotExist()
        compose.runOnIdle { pro = true }
        reveal(hasText("Saved best: 3 stars · 325 points")).assertIsDisplayed()
        reveal(hasText("Best time: 12 seconds")).assertIsDisplayed()
        capture("progress-bests-rtl-compact-200.png")
        reveal(hasTestTag("progress-puzzle-filter")).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("progress-filter-WATER_JUGS").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        reveal(hasText("Complete a campaign level in this collection to see its best results here.")).assertIsDisplayed()
        assertEquals(0, requests)
    }
}
