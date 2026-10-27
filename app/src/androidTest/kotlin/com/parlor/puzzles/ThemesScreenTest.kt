package com.parlor.puzzles

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import com.parlor.feature.library.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File
import java.io.FileInputStream

/** Real native font/display settings cover scroll and radio targets at 200%. */
class ThemesScreenTest {
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

    @Test fun allSixChoicesAndAppearanceRemainReachableAndSavedThemeReturnsWithPro() {
        var settings by mutableStateOf(PlayerSettings())
        var pro by mutableStateOf(false)
        var systemDark by mutableStateOf(false)
        var proOpens = 0
        var fail = false
        compose.setContent {
            ParlorTheme(RoomTheme.displayed(settings.theme, pro), settings.appearance.isDark(systemDark)) {
                Surface {
                    ThemesScreen(settings, pro, systemDark,
                        onTheme = { if (fail) error("settings fixture"); settings = settings.copy(theme = it.id) },
                        onAppearance = { settings = settings.copy(appearance = it) },
                        onPro = { proOpens++ }, onBack = {})
                }
            }
        }
        compose.onNodeWithTag("theme-sage").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.waitUntil { settings.theme == "sage" }
        assertEquals(0, proOpens)
        compose.onNodeWithTag("appearance-DARK").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(Appearance.DARK, settings.appearance)
        compose.runOnIdle { pro = true }
        for (theme in RoomTheme.entries) {
            compose.onNodeWithTag("theme-${theme.id}").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            compose.waitUntil { settings.theme == theme.id }
            compose.onNodeWithTag("theme-${theme.id}").assertIsSelected()
            capture("theme-${theme.id}-dark-compact-200.png")
        }
        compose.runOnIdle { pro = false }
        compose.onAllNodesWithTag("theme-retained").assertCountEquals(0)
        assertEquals("walnut", settings.theme)
        compose.onNodeWithTag("theme-walnut").performScrollTo().assertIsSelected()
        compose.runOnIdle { pro = true }
        compose.onNodeWithTag("theme-walnut").performScrollTo().assertIsSelected()
        compose.runOnIdle { fail = true }
        compose.onNodeWithTag("theme-slate").performScrollTo().performClick()
        compose.onNodeWithTag("theme-error").performScrollTo().assertIsDisplayed()
        assertEquals("walnut", settings.theme)
        compose.runOnIdle { fail = false }
        compose.onNodeWithTag("theme-slate").performScrollTo().performClick()
        compose.waitUntil { settings.theme == "slate" }
        compose.onNodeWithTag("appearance-SYSTEM").performScrollTo().performClick()
        compose.runOnIdle { systemDark = true }
        compose.onNodeWithTag("appearance-SYSTEM").assertIsSelected()
        capture("themes-appearance-compact-200.png")
        compose.runOnIdle { pro = false }
        compose.onNodeWithTag("theme-linen").performScrollTo().performClick()
        assertEquals("linen", settings.theme)
    }

    @Test fun aPendingSettingWriteKeepsBackAvailableAndPreventsOverlappingChoices() {
        val pending = kotlinx.coroutines.CompletableDeferred<Unit>()
        var writes = 0; var back = 0
        compose.setContent { ParlorTheme { Surface {
            ThemesScreen(PlayerSettings(), true, false, onTheme = { writes++; pending.await() },
                onAppearance = {}, onPro = {}, onBack = { back++ })
        } } }
        compose.onNodeWithTag("theme-sage").performScrollTo().performClick()
        compose.onNodeWithTag("theme-rose").performScrollTo().assertIsNotEnabled()
        assertEquals(1, writes)
        compose.onNodeWithText("Back").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, back)
        pending.complete(Unit)
    }
}
