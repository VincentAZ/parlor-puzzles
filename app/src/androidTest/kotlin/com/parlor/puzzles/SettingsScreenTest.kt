package com.parlor.puzzles

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.parlor.core.data.*
import com.parlor.core.model.*
import com.parlor.feature.library.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.RuleChain
import java.io.File

/** Confirmation and accessibility actions on a native 320 x 640 dp / 200% text device. */
class SettingsScreenTest {
    private val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val compact = NativeCompactDisplayRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(compact).around(compose)
    private val catalog by lazy { runBlocking { ContentRepository(context).load() } }
    private val confirm = hasText("Reset progress") and hasClickAction()
    private val allowance = "Daily scoring allowances are not renewed."
    private val failure = "Progress could not be reset. Nothing was changed. Please try again."

    @Composable private fun Screen(settings: PlayerSettings = PlayerSettings(onboarded = true),
                                   update: ((PlayerSettings) -> PlayerSettings) -> Unit = {},
                                   reset: suspend (Puzzle?) -> Unit = {}) {
        ParlorTheme { Surface {
            LibraryScreen(catalog, emptyList(), null, PlayerSummary(), settings, emptyList(),
                { flowOf(HistoryPage()) }, "2026-09-28", false, false, {}, {}, reset, update, {}, {}, {})
        } }
    }
    private fun openSettings() = compose.onNodeWithText("Settings").performScrollTo().performClick()
    private fun openReset(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
    private fun capture(name: String) {
        compose.waitForIdle(); SystemClock.sleep(400)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun completeExplanationRemainsReachableAndBothCancellationPathsLeaveProgressAlone() {
        var calls = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Screen(reset = { calls++ }) }
        openSettings(); openReset("Reset all progress")
        compose.onNodeWithText("Reset all progress?").assertIsDisplayed()
        compose.onNode(confirm).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Cancel").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        capture("settings-reset-top-compact-200.png")
        compose.onNodeWithText(allowance).performScrollTo().assertIsDisplayed()
        capture("settings-reset-bottom-compact-200.png")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Reset all progress?").assertIsDisplayed()
        compose.onNodeWithText(allowance).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Reset all progress?").assertDoesNotExist()
        openReset("Reset Traffic Jam progress")
        compose.onNodeWithText("Reset Traffic Jam?").assertIsDisplayed()
        UiDevice.getInstance(instrumentation).pressBack()
        compose.waitUntil { compose.onAllNodesWithText("Reset Traffic Jam?").fetchSemanticsNodes().isEmpty() }
        assertEquals(0, calls)
    }

    @Test fun resetFailureRollsBackAndRetryClearsOnlyConfirmedScopeWhileKeepingClaims() {
        val db = Room.inMemoryDatabaseBuilder(context, ParlorDatabase::class.java).build()
        var ids = 0
        val repo = ProgressRepository(db, { "2026-09-28" }, { 1_000L }, { "settings-${ids++}" })
        val traffic = catalog.levels.first { it.puzzle == Puzzle.TRAFFIC_JAM && it.tier == Tier.EASY }
        val hanoi = catalog.levels.first { it.puzzle == Puzzle.HANOI && it.tier == Tier.EASY }
        repo.configure(catalog, DailySchedule(listOf(ScheduleEpoch(1, "2026-09-28", listOf(LevelRef(traffic.id, traffic.revision))))))
        try {
            runBlocking {
                for (level in listOf(traffic, hanoi)) {
                    val a = repo.startOrResume(level)
                    level.reference.forEach { repo.move(level, a.id, it, 100) }
                }
                val daily = repo.startDaily()
                daily.level.reference.forEach { repo.move(daily.level, daily.attempt.id, it, 100) }
                assertTrue(repo.grantReward("settings-hints", Reward.Hints))
                assertTrue(repo.grantReward("settings-retry", Reward.DailyRetry("2026-09-28")))
                assertTrue(repo.grantReward("settings-unlock", Reward.LevelAccess(catalog.levels.first { it.tier == Tier.HARD }.id)))
            }
            val playerBefore = runBlocking { repo.player.first() }
            val bestsBefore = runBlocking { repo.bests.first() }
            val historyBefore = runBlocking { repo.history.first() }
            val dailyBefore = runBlocking { repo.dailies.first() }
            val countBefore = runBlocking { repo.completionCount.first() }
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_settings_reset BEFORE UPDATE ON player BEGIN SELECT RAISE(ABORT, 'reset fixture'); END")
            var gate = CompletableDeferred<Unit>()
            val scopes = mutableListOf<Puzzle?>()
            compose.setContent { Screen(reset = { puzzle -> scopes += puzzle; gate.await(); repo.resetProgress(puzzle) }) }
            openSettings(); openReset("Reset Traffic Jam progress")
            compose.onNode(confirm).performClick()
            compose.onNodeWithText("Resetting progress…").assertIsDisplayed()
            compose.onNode(confirm).assertIsNotEnabled()
            compose.onNodeWithText("Cancel").assertIsNotEnabled()
            UiDevice.getInstance(instrumentation).pressBack()
            compose.onNodeWithText("Reset Traffic Jam?").assertIsDisplayed()
            assertEquals(listOf(Puzzle.TRAFFIC_JAM), scopes)
            gate.complete(Unit)
            compose.waitUntil { compose.onAllNodesWithText(failure).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(failure).assertIsDisplayed()
            compose.onNode(confirm).assertIsEnabled()
            capture("settings-reset-failure-compact-200.png")
            runBlocking {
                assertEquals(playerBefore, repo.player.first()); assertEquals(bestsBefore, repo.bests.first())
                assertEquals(historyBefore, repo.history.first()); assertEquals(dailyBefore, repo.dailies.first())
                assertEquals(countBefore, repo.completionCount.first())
            }
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_settings_reset")
            gate = CompletableDeferred()
            compose.onNode(confirm).performClick()
            compose.onNodeWithText(failure).assertDoesNotExist()
            compose.onNodeWithText("Resetting progress…").assertIsDisplayed()
            gate.complete(Unit)
            compose.waitUntil { compose.onAllNodesWithText("Reset Traffic Jam?").fetchSemanticsNodes().isEmpty() }
            runBlocking {
                val player = repo.player.first()
                assertEquals(playerBefore.ledger, player.ledger)
                assertEquals(playerBefore.profile.achievements, player.profile.achievements)
                assertEquals(playerBefore.profile.playTimeByPuzzle - Puzzle.TRAFFIC_JAM, player.profile.playTimeByPuzzle)
                assertEquals(player.profile.playTimeByPuzzle.values.sum(), player.totalPlayTimeMs)
                assertTrue(player.profile.dailyTotals.isEmpty())
                assertEquals(bestsBefore.filter { it.levelId == hanoi.id }, repo.bests.first())
                assertEquals(historyBefore.filter { it.levelId == hanoi.id }, repo.history.first())
                assertNull(repo.dailies.first().single().record.best)
                assertEquals(DailyEligibility.REWARDED, repo.startDaily().attempt.dailyEligibility)
            }
            openReset("Reset all progress"); compose.onNode(confirm).performClick()
            compose.waitUntil { compose.onAllNodesWithText("Reset all progress?").fetchSemanticsNodes().isEmpty() }
            assertEquals(listOf(Puzzle.TRAFFIC_JAM, Puzzle.TRAFFIC_JAM, null), scopes)
            runBlocking {
                assertTrue(repo.bests.first().isEmpty()); assertTrue(repo.history.first().isEmpty()); assertNull(repo.resume.first())
                val player = repo.player.first()
                assertEquals(0L, player.totalPlayTimeMs); assertTrue(player.profile.playTimeByPuzzle.isEmpty())
                assertEquals(playerBefore.ledger, player.ledger)
                assertEquals(playerBefore.profile.achievements, player.profile.achievements)
                assertEquals(countBefore, repo.completionCount.first())
                assertFalse(repo.grantReward("settings-hints", Reward.Hints))
            }
        } finally { db.close() }
    }

    @Test fun labelledRtlAudioControlsPersistIndependentVolumeAndExplainDisabledMusic() {
        val app = context.applicationContext as ParlorApplication
        val saved = runBlocking { app.settings.settings.first() }
        val initial = saved.copy(onboarded = true, sfxVolume = .65f, musicVolume = 0f)
        runBlocking { app.settings.replace(initial) }
        try {
            compose.setContent {
                val settings by app.settings.settings.collectAsState(initial)
                val scope = rememberCoroutineScope()
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Screen(settings, { transform -> scope.launch { app.settings.update(transform) } })
                }
            }
            openSettings()
            val sfx = compose.onNodeWithContentDescription("Sound effects")
            sfx.performScrollTo().assertIsDisplayed().assertIsEnabled().assertHeightIsAtLeast(48.dp)
            sfx.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(.25f)) }
            compose.waitUntil { runBlocking { app.settings.settings.first().sfxVolume } == .25f }
            sfx.assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(.25f, 0f..1f))
            val music = compose.onNodeWithContentDescription("Music")
            music.performScrollTo().assertIsDisplayed().assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "No music included. Enjoy a quiet room."))
            compose.onNodeWithText("No music included. Enjoy a quiet room.").performScrollTo().assertIsDisplayed()
            capture("settings-audio-rtl-compact-200.png")
            assertEquals(initial.copy(sfxVolume = .25f), runBlocking { app.settings.settings.first() })
            compose.onNodeWithText("Back").performScrollTo().performClick(); openSettings()
            sfx.performScrollTo().assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(.25f, 0f..1f))
        } finally { runBlocking { app.settings.replace(saved) } }
    }
}
