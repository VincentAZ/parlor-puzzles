package com.parlor.puzzles

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import com.parlor.core.platform.ads.*
import com.parlor.feature.library.ParlorTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File
import java.io.FileInputStream

class RewardLayoutTest {
    private val compose = createComposeRule()
    private val fontScale = object : ExternalResource() {
        private var original: Float? = null
        private var originalSize: String? = null
        private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command).use { descriptor -> FileInputStream(descriptor.fileDescriptor).bufferedReader().readText().trim() }
        private fun awaitScale(value: Float) {
            val deadline = SystemClock.elapsedRealtime() + 5_000
            val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
            while (kotlin.math.abs(resources.configuration.fontScale - value) > 0.01f && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
            assertEquals(value, resources.configuration.fontScale, 0.01f)
        }
        override fun before() {
            original = shell("settings get system font_scale").toFloatOrNull()
            originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
            val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
            shell("wm size ${(320 * density).toInt()}x${(640 * density).toInt()}")
            shell("settings put system font_scale 2.0"); awaitScale(2f)
        }
        override fun after() {
            shell("wm size ${originalSize ?: "reset"}")
            shell(original?.let { "settings put system font_scale $it" } ?: "settings delete system font_scale")
            awaitScale(original ?: 1f)
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fontScale).around(compose)
    @Test fun largeTextOffersKeepWatchCancelAndRecoveryControlsReachable() {
        var state by mutableStateOf(RewardAdState(Reward.Hints))
        var delivery by mutableStateOf(RewardDeliveryState(checking = false))
        var watches = 0; var closes = 0; var retries = 0
        compose.setContent { ParlorTheme {
            RewardDialog(state, delivery, true, 9, onWatch = { watches++ }, onClose = { closes++ },
                onRetrySave = { retries++ }, onContinue = {})
        } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun capture(name: String) {
            File(context.filesDir, name).outputStream().use {
                val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                screenshot.recycle()
            }
        }
        for ((name, reward) in listOf("hints" to Reward.Hints, "level" to Reward.LevelAccess("example"), "daily" to Reward.DailyRetry("2026-09-28"))) {
            compose.runOnIdle { state = RewardAdState(reward) }
            compose.onAllNodes(hasScrollAction()).onFirst().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -10_000f) }
            compose.onNodeWithText("Watch an ad").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithText("Close").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
            capture("reward-$name-compact-200.png")
            compose.onNodeWithText("Completed reward ads today: 9 of 10.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Watch an ad").assertIsDisplayed()
            compose.onNodeWithText("Close").assertIsDisplayed()
            capture("reward-$name-compact-200-scrolled.png")
        }
        assertEquals(3, watches); assertEquals(3, closes)
        compose.runOnIdle { state = state.copy(stage = RewardStage.LOADING) }
        compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        compose.runOnIdle {
            state = state.copy(stage = RewardStage.SAVE_ERROR)
            delivery = RewardDeliveryState(checking = false, pending = EarnedReward("one", Reward.Hints, "2026-09-28", 0), durable = true, error = true)
        }
        compose.onNodeWithText("Retry saving").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, retries)
    }
}
