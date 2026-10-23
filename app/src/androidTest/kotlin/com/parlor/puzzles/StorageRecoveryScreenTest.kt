package com.parlor.puzzles

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.data.*
import com.parlor.feature.library.ParlorTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File
import java.io.FileInputStream

class StorageRecoveryScreenTest {
    private val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val compact = object : ExternalResource() {
        private var scale: Float? = null
        private var size: String? = null
        private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
            FileInputStream(it.fileDescriptor).bufferedReader().readText().trim()
        }
        private fun awaitScale(value: Float) {
            val end = SystemClock.elapsedRealtime() + 5_000
            while (kotlin.math.abs(context.resources.configuration.fontScale - value) > .01f && SystemClock.elapsedRealtime() < end)
                SystemClock.sleep(50)
            assertEquals(value, context.resources.configuration.fontScale, .01f)
        }
        override fun before() {
            scale = shell("settings get system font_scale").toFloatOrNull()
            size = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
            val density = context.resources.displayMetrics.density
            shell("wm size ${(320 * density).toInt()}x${(640 * density).toInt()}")
            shell("settings put system font_scale 2.0"); awaitScale(2f)
        }
        override fun after() {
            shell("wm size ${size ?: "reset"}")
            shell(scale?.let { "settings put system font_scale $it" } ?: "settings delete system font_scale")
            awaitScale(scale ?: 1f)
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(compact).around(compose)
    private fun capture(name: String) {
        compose.waitForIdle(); SystemClock.sleep(300)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun settingsResetRequiresConfirmationAndRemainsReachableAtNativeTwoHundredPercent() {
        var resets = 0; var retries = 0; var closes = 0
        var state by mutableStateOf(SettingsReset.READY)
        compose.setContent { ParlorTheme { Surface {
            StorageRecoveryScreen(StorageIssue(StorageArea.SETTINGS, StorageProblem.DAMAGED), state,
                onRetry = { retries++ }, onReset = { resets++; state = SettingsReset.WORKING }, onClose = { closes++ })
        } } }
        compose.onNodeWithTag("storage-retry").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, retries)
        compose.onNodeWithTag("storage-reset").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(0, resets)
        compose.onNodeWithTag("storage-reset-cancel").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(0, resets)
        compose.onNodeWithTag("storage-reset").performScrollTo().performClick()
        compose.onNodeWithTag("storage-reset-confirm").performScrollTo().assertHeightIsAtLeast(48.dp)
        capture("storage-confirm-compact-200.png")
        compose.onNodeWithTag("storage-reset-confirm").performClick()
        assertEquals(1, resets)
        compose.onNodeWithTag("storage-retry").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("storage-reset").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("storage-close").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, closes)
        compose.runOnIdle { state = SettingsReset.FAILED }
        compose.onNodeWithTag("storage-reset-error").performScrollTo().assertIsDisplayed()
        capture("storage-error-compact-200.png")
        compose.onNodeWithTag("storage-reset").performScrollTo().performClick()
        assertEquals(1, resets)
    }

    @Test fun progressAndFutureVersionProblemsOfferNoSettingsOrProgressReset() {
        var issue by mutableStateOf(StorageIssue(StorageArea.PROGRESS, StorageProblem.DAMAGED))
        var retries = 0
        compose.setContent { ParlorTheme { Surface {
            StorageRecoveryScreen(issue, SettingsReset.READY, { retries++ }, { fail("No reset should be offered") }, {})
        } } }
        compose.onNodeWithTag("storage-reset").assertDoesNotExist()
        compose.onNodeWithTag("storage-retry").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, retries)
        capture("storage-damaged-compact-200.png")
        compose.runOnIdle { issue = StorageIssue(StorageArea.PROGRESS, StorageProblem.NEWER_VERSION) }
        compose.onNodeWithText(context.getString(R.string.storage_newer)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("storage-reset").assertDoesNotExist()
        compose.onNodeWithTag("storage-close").performScrollTo().assertHeightIsAtLeast(48.dp)
        capture("storage-newer-compact-200.png")
        compose.runOnIdle { issue = StorageIssue(StorageArea.SETTINGS, StorageProblem.UNAVAILABLE) }
        compose.onNodeWithTag("storage-reset").assertDoesNotExist()
    }

    @Test fun databaseRestorePreviewCanBeCancelledAndConfirmedAtTwoHundredPercent() {
        val preview = BackupPreview("restore", 1_790_582_400_000, 12, 3, 35, false, 2)
        var state by mutableStateOf(DatabaseRecoveryState())
        var confirms = 0; var closes = 0; var pro by mutableStateOf(true)
        compose.setContent { ParlorTheme { Surface {
            DatabaseRecoveryScreen(state, pro, onRetry = {}, onClose = { closes++ },
                onChoose = { state = DatabaseRecoveryState(DatabaseRecoveryPhase.PREVIEW, preview) },
                onConfirm = { confirms++; state = state.copy(phase = DatabaseRecoveryPhase.RESTORING) },
                onDiscard = { state = DatabaseRecoveryState() })
        } } }
        compose.onNodeWithTag("database-restore-choose").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(0, confirms)
        compose.onNodeWithTag("database-restore-cancel").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(0, confirms)
        compose.onNodeWithTag("database-restore-choose").performScrollTo().performClick()
        compose.runOnIdle { pro = false }
        compose.onNodeWithTag("database-restore-confirm").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { pro = true }
        compose.onNodeWithTag("database-restore-confirm").performScrollTo().assertHeightIsAtLeast(48.dp)
        capture("database-confirm-compact-200.png")
        compose.onNodeWithTag("database-restore-confirm").performClick()
        assertEquals(1, confirms)
        compose.onNodeWithTag("storage-retry").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("database-restore-choose").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("storage-close").performScrollTo().assertIsEnabled().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, closes)
        capture("database-working-compact-200.png")
    }

    @Test fun databaseRestoreExplainsMissingSafeguardsAndNeverOffersAFreeImport() {
        var state by mutableStateOf(DatabaseRecoveryState())
        var pro by mutableStateOf(false)
        compose.setContent { ParlorTheme { Surface {
            DatabaseRecoveryScreen(state, pro, onRetry = {}, onClose = {}, onChoose = {}, onConfirm = {}, onDiscard = {})
        } } }
        compose.onNodeWithTag("database-restore-choose").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("storage-retry").performScrollTo().assertIsEnabled()
        compose.runOnIdle { pro = true; state = DatabaseRecoveryState(DatabaseRecoveryPhase.ERROR, problem = DatabaseRecoveryProblem.SAFEGUARDS) }
        compose.onNodeWithTag("database-recovery-error").performScrollTo().assertIsDisplayed()
        capture("database-safeguards-compact-200.png")
        compose.onNodeWithTag("database-restore-choose").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("database-restore-confirm").assertDoesNotExist()
    }
}
