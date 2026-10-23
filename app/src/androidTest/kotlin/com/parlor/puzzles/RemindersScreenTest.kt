package com.parlor.puzzles

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.platform.reminders.*
import com.parlor.feature.library.ParlorTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File
import java.io.FileInputStream

class RemindersScreenTest {
    private val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val compact = object : ExternalResource() {
        private var originalScale: Float? = null
        private var originalSize: String? = null
        private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
            FileInputStream(it.fileDescriptor).bufferedReader().readText().trim()
        }
        private fun awaitScale(value: Float) {
            val end = SystemClock.elapsedRealtime() + 5_000
            while (kotlin.math.abs(context.resources.configuration.fontScale - value) > 0.01f && SystemClock.elapsedRealtime() < end)
                SystemClock.sleep(50)
            assertEquals(value, context.resources.configuration.fontScale, 0.01f)
        }
        override fun before() {
            originalScale = shell("settings get system font_scale").toFloatOrNull()
            originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
            val density = context.resources.displayMetrics.density
            shell("wm size ${(320 * density).toInt()}x${(640 * density).toInt()}")
            shell("settings put system font_scale 2.0"); awaitScale(2f)
        }
        override fun after() {
            shell("wm size ${originalSize ?: "reset"}")
            shell(originalScale?.let { "settings put system font_scale $it" } ?: "settings delete system font_scale")
            awaitScale(originalScale ?: 1f)
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(compact).around(compose)

    private fun capture(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun choicesAndPermissionRecoveryRemainReachableAt200Percent() {
        var state by mutableStateOf(ReminderUiState(ready = true, permitted = false))
        var denied by mutableStateOf(false)
        var enabled: Boolean? = null; var pickedTime = 0; var settings = 0; var back = 0
        compose.setContent { ParlorTheme {
            RemindersScreen(state, denied, onBack = { back++ }, onEnabled = { enabled = it },
                onKind = { state = state.copy(preferences = state.preferences.copy(kind = it)) },
                onTime = { pickedTime++ }, onSystemSettings = { settings++ }, onRetry = {})
        } }
        compose.onNodeWithText("Android notification settings").assertDoesNotExist()
        compose.onNodeWithText("Remind me").performScrollTo().assertHeightIsAtLeast(48.dp).assertIsOff()
        compose.onNodeWithText("Keep a streak going").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Keep a streak going").assertIsSelected()
        compose.onNodeWithText("Remind me").assertIsOff()
        compose.onNodeWithText("Daily puzzle").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Daily puzzle").assertIsSelected()
        compose.onNode(hasText("Reminder time:", substring = true)).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, pickedTime); assertNull(enabled)
        capture("reminders-compact-200-choices.png")
        compose.onNodeWithText("Remind me").performScrollTo().performClick()
        assertEquals(true, enabled)
        // A denied permission callback leaves the model off and offers the system controls.
        compose.runOnIdle { denied = true }
        compose.onNodeWithText("Remind me").assertIsOff()
        compose.onNodeWithText("Android notification settings").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, settings)
        capture("reminders-compact-200-permission.png")
        compose.onNodeWithText("Back").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, back)
    }

    @Test fun loadingAndFailedStorageDisableEditingAndKeepRetryReachable() {
        var state by mutableStateOf(ReminderUiState())
        var retries = 0
        compose.setContent { ParlorTheme {
            RemindersScreen(state, onBack = {}, onEnabled = {}, onKind = {}, onTime = {}, onSystemSettings = {}, onRetry = { retries++ })
        } }
        compose.onNodeWithText("Remind me").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(ready = true, error = true) }
        compose.onNodeWithText("Daily puzzle").performScrollTo().assertIsNotEnabled()
        compose.onNode(hasText("Reminder time:", substring = true)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Try again").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, retries)
        capture("reminders-compact-200-error.png")
        compose.onNodeWithText("Android notification settings").performScrollTo().assertIsEnabled()
        compose.runOnIdle { state = state.copy(preferences = state.preferences.copy(enabled = true)) }
        compose.onNodeWithText("Remind me").performScrollTo().assertIsEnabled()
    }
}
