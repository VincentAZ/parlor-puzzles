package com.parlor.puzzles

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.data.*
import com.parlor.core.model.*
import com.parlor.core.platform.reminders.*
import com.parlor.feature.library.*
import com.parlor.feature.play.TrafficBoard
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.RuleChain
import java.io.FileInputStream
import java.time.Instant
import java.util.Locale

/** Resource locale deliberately differs from the process default, including a live change. */
class LocalePresentationTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(NativeCompactDisplayRule()).around(compose)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var language by mutableStateOf("ar-EG")
    private lateinit var previousLocale: Locale
    @Before fun processUsesEnglish() { previousLocale = Locale.getDefault(); Locale.setDefault(Locale.US) }
    @After fun restoreLocale() { Locale.setDefault(previousLocale) }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            val original = LocalContext.current
            val configuration = Configuration(LocalConfiguration.current).apply {
                setLocales(LocaleList(Locale.forLanguageTag(language)))
            }
            val localized = remember(original, configuration) { original.createConfigurationContext(configuration) }
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides configuration,
                LocalResources provides localized.resources,
                LocalLayoutDirection provides if (configuration.layoutDirection == 1) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                ParlorTheme { Surface { content() } }
            }
        }
    }
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).bufferedReader().readText().trim()
    }

    @Test fun backupAndRecoveryPreviewsUseResourceDigitsGroupingAndDates() {
        val preview = BackupPreview("locale-preview", Instant.parse("2026-09-28T12:00:00Z").toEpochMilli(), 1234, 12, 3456, false, 5)
        var recovery by mutableStateOf(false)
        show {
            if (recovery) DatabaseRecoveryScreen(DatabaseRecoveryState(DatabaseRecoveryPhase.PREVIEW, preview), true,
                onRetry = {}, onClose = {}, onChoose = {}, onConfirm = {}, onDiscard = {})
            else BackupScreen(BackupUiState(BackupPhase.PREVIEW, preview), true,
                onBack = {}, onPro = {}, onExport = {}, onImport = {}, onRecovery = {}, onConfirm = {}, onDiscard = {})
        }
        for (recovering in listOf(false, true)) {
            compose.runOnIdle { recovery = recovering; language = "ar-EG" }
            compose.onNodeWithText("Completed levels: ١٬٢٣٤\nUnfinished puzzles: ١٢\nCampaign stars: ٣٬٤٥٦")
                .performScrollTo().assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    assertTrue(action(layouts))
                    val layout = layouts.single()
                    for (start in listOf(0, layout.layoutInput.text.text.indexOf("Unfinished"), layout.layoutInput.text.text.indexOf("Campaign"))) {
                        assertEquals("English fallback paragraphs keep their punctuation order", ResolvedTextDirection.Ltr, layout.getParagraphDirection(start))
                    }
                }
            compose.onNode(hasText("Saved ", substring = true) and hasText("٢٠٢٦", substring = true)).performScrollTo().assertIsDisplayed()
            captureTabletScreen(compose, "locale-${if (recovering) "recovery" else "backup"}-arabic-200.png")
            compose.runOnIdle { language = "de-DE" }
            compose.onNodeWithText("Completed levels: 1.234\nUnfinished puzzles: 12\nCampaign stars: 3.456")
                .performScrollTo().assertIsDisplayed()
            compose.onNode(hasText("Saved ", substring = true) and hasText("28.09.2026", substring = true)).performScrollTo().assertIsDisplayed()
        }
        assertEquals("locale-preview", preview.snapshotId)
        assertEquals(1234, preview.levels)
    }

    @Test fun trafficDestinationDigitsRefreshWhileStoredCoordinatesStayCanonical() {
        var viewport by mutableStateOf(Viewport(selectedId = "A"))
        var move: Move? = null
        show { TrafficBoard(TrafficJamBoard(pieces = listOf(TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL))), viewport, true,
            { viewport = it }, { move = it }) }
        compose.onNodeWithTag("destination-A-1").assertTextEquals("٢").assertHeightIsAtLeast(48.dp)
        compose.runOnIdle { language = "de-DE" }
        compose.onNodeWithTag("destination-A-1").assertTextEquals("2").performClick()
        compose.runOnIdle { assertEquals(Translate("A", 1), move); assertEquals("A", viewport.selectedId) }
    }

    @Test fun fractionalProgressUsesTheResourceDecimalSeparator() {
        val catalog = runBlocking { ContentRepository(context).load() }
        val levels = catalog.levels.filter { it.puzzle == Puzzle.TRAFFIC_JAM }.take(2)
        val bests = levels.mapIndexed { i, level -> LevelBest(level.id, 2, 250, 12_000, level.par + i + 1, "2026-09-28") }
        language = "de-DE"
        show { ProgressScreen(catalog, bests, PlayerSummary(), true, { flowOf(HistoryPage()) }, {}, {}) }
        val german = hasText("Average best moves over par: 1,5")
        compose.onNodeWithTag("progress-list").performScrollToNode(german)
        compose.onNode(german).assertIsDisplayed()
        captureTabletScreen(compose, "locale-progress-german-200.png")
        compose.runOnIdle { language = "ar-EG" }
        compose.onNodeWithText("Average best moves over par: ١٫٥").assertIsDisplayed()
    }

    @Test fun reminderTimeHonorsTwelveAndTwentyFourHourSettingsWithLocalDigits() {
        val original = shell("settings get system time_12_24")
        var state by mutableStateOf(ReminderUiState(ready = true, preferences = ReminderPreferences(minuteOfDay = 19 * 60 + 5)))
        try {
            shell("settings put system time_12_24 24")
            show { RemindersScreen(state, onBack = {}, onEnabled = {}, onKind = {}, onTime = {}, onSystemSettings = {}, onRetry = {}) }
            compose.onNodeWithText("Reminder time: ١٩:٠٥").performScrollTo().assertHeightIsAtLeast(48.dp).assertIsDisplayed()
            captureTabletScreen(compose, "locale-reminder-arabic-24-200.png")
            shell("settings put system time_12_24 12")
            compose.runOnIdle { state = state.copy(permitted = true) }
            compose.onNode(hasText("Reminder time: ٧:٠٥", substring = true) and hasText("م", substring = true))
                .performScrollTo().assertIsDisplayed()
            compose.runOnIdle { language = "en-US" }
            compose.onNode(hasText("Reminder time: 7:05", substring = true) and hasText("PM", substring = true))
                .performScrollTo().assertIsDisplayed()
            assertEquals(19 * 60 + 5, state.preferences.minuteOfDay)
        } finally { shell(if (original == "null") "settings delete system time_12_24" else "settings put system time_12_24 $original") }
    }
}
