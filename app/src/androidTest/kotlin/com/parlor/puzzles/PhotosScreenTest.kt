package com.parlor.puzzles

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.feature.library.ParlorTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File
import java.io.FileInputStream

class PhotosScreenTest {
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
        compose.waitForIdle(); SystemClock.sleep(500)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun explicitDeletionAndFailureRecoveryStayReachableAt200Percent() {
        var deletes = 0
        var fail = true
        compose.setContent { ParlorTheme {
            PhotosScreen(onDelete = { deletes++; if (fail) error("storage fixture") }, onBack = {})
        } }
        compose.onNodeWithTag("photos-delete").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Keep photos").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(0, deletes)
        compose.onNodeWithTag("photos-delete").performClick()
        compose.onNodeWithTag("photos-confirm").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("photos-outcome").performScrollTo().assertTextContains("could not be deleted", substring = true)
        assertEquals(1, deletes)
        capture("photos-compact-200-error.png")
        compose.runOnIdle { fail = false }
        compose.onNodeWithTag("photos-delete").performScrollTo().performClick()
        compose.onNodeWithTag("photos-delete-detail").performTouchInput { swipeUp() }
        capture("photos-compact-200-confirm.png")
        compose.onNodeWithTag("photos-confirm").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Saved photo copies deleted.").performScrollTo().assertIsDisplayed()
        assertEquals(2, deletes)
        capture("photos-compact-200-deleted.png")
    }

    @Test fun waitingForStorageDoesNotBlockBackNavigation() {
        val pending = kotlinx.coroutines.CompletableDeferred<Unit>()
        var back = 0
        compose.setContent { ParlorTheme { PhotosScreen(onDelete = { pending.await() }, onBack = { back++ }) } }
        compose.onNodeWithTag("photos-delete").performScrollTo().performClick()
        compose.onNodeWithTag("photos-confirm").performClick()
        compose.onNodeWithText("Deleting saved photos…").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Back").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, back)
        pending.complete(Unit)
        compose.onNodeWithText("Saved photo copies deleted.").performScrollTo().assertIsDisplayed()
    }
}
