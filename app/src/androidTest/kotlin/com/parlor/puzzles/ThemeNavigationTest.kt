package com.parlor.puzzles

import android.graphics.Bitmap
import android.os.SystemClock
import android.app.UiModeManager
import android.content.res.Configuration
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.design.roomColors
import com.parlor.core.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class ThemeNavigationTest {
    @get:Rule val compose = navigationComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as ParlorApplication
    private fun capture(name: String) {
        compose.waitForIdle(); SystemClock.sleep(300)
        val image = instrumentation.uiAutomation.takeScreenshot()
        File(context.filesDir, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
    }
    private fun assertBackground(dark: Boolean) {
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("app-surface").captureToImage().toPixelMap()
        assertEquals(roomColors(RoomTheme.LINEN, dark).scheme.background.toArgb(), pixels[2, 2].toArgb())
    }
    private fun assertHomeCard(dark: Boolean) {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("home-traffic-card", useUnmergedTree = true).fetchSemanticsNodes().size == 1
        }
        val pixels = compose.onNodeWithTag("home-traffic-card", useUnmergedTree = true).performScrollTo().captureToImage().toPixelMap()
        assertEquals(roomColors(RoomTheme.LINEN, dark).scheme.surfaceContainer.toArgb(),
            pixels[pixels.width / 2, pixels.height - 4].toArgb())
    }
    private fun visible(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test fun deviceAppearanceTracksRealNightModeWhileExplicitLightStaysLight() {
        val previous = runBlocking { app.settings.settings.first() }
        val manager = context.getSystemService(UiModeManager::class.java)
        val previousMode = when (manager.nightMode) {
            UiModeManager.MODE_NIGHT_AUTO -> "auto"
            UiModeManager.MODE_NIGHT_YES -> "yes"
            UiModeManager.MODE_NIGHT_NO -> "no"
            else -> "custom"
        }
        fun night(mode: String) {
            instrumentation.uiAutomation.executeShellCommand("cmd uimode night $mode").use {
                FileInputStream(it.fileDescriptor).bufferedReader().readText()
            }
            val expected = if (mode == "yes") Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            compose.waitUntil(10_000) { context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == expected }
        }
        runBlocking { app.settings.replace(PlayerSettings(onboarded = true, appearance = Appearance.SYSTEM)) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(15_000) { visible("Settings") }
                night("yes"); compose.waitForIdle(); assertBackground(true); assertHomeCard(true); capture("themes-device-dark.png")
                night("no"); compose.waitForIdle(); assertBackground(false); assertHomeCard(false); capture("themes-device-light.png")
                runBlocking { app.settings.update { it.copy(appearance = Appearance.LIGHT) } }
                night("yes"); compose.waitForIdle(); assertBackground(false); assertHomeCard(false)
            }
        } finally {
            instrumentation.uiAutomation.executeShellCommand("cmd uimode night $previousMode").use {
                FileInputStream(it.fileDescriptor).bufferedReader().readText()
            }
            runBlocking { app.settings.replace(previous) }
        }
    }

    @Test fun actualSettingsApplyDarkPersistThroughRecreationAndRetainPremiumChoiceForFree() {
        val previous = runBlocking { app.settings.settings.first() }
        runBlocking { app.settings.replace(PlayerSettings(onboarded = true, theme = "walnut")) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitUntil(15_000) { visible("Settings") }
                assertFalse(app.billing.hasPro); assertBackground(false)
                compose.onNodeWithText("Settings").performClick()
                compose.onNodeWithText("Themes and appearance").performScrollTo().performClick()
                compose.onNodeWithTag("theme-retained").performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("theme-linen").performScrollTo().assertIsSelected()
                compose.onNodeWithTag("appearance-DARK").performScrollTo().performClick()
                compose.waitUntil(5_000) { runBlocking { app.settings.settings.first().appearance } == Appearance.DARK }
                assertBackground(true)
                scenario.onActivity { assertFalse(WindowCompat.getInsetsController(it.window, it.window.decorView).isAppearanceLightStatusBars) }
                scenario.recreate()
                compose.waitUntil(15_000) { visible("Themes and appearance") }
                compose.onNodeWithTag("appearance-DARK").assertIsSelected(); assertBackground(true)
                capture("themes-native-dark.png")
                compose.onNodeWithTag("theme-sage").performScrollTo().performClick()
                compose.waitUntil { visible("Restore purchases") }
                compose.onNodeWithText("Back").performClick()
                compose.onNodeWithTag("theme-retained").performScrollTo().assertIsDisplayed()
                assertEquals("walnut", runBlocking { app.settings.settings.first().theme })
                compose.onNodeWithTag("theme-linen").performScrollTo().performClick()
                compose.waitUntil { runBlocking { app.settings.settings.first().theme } == "linen" }
                compose.onNodeWithTag("appearance-LIGHT").performScrollTo().performClick()
                compose.waitUntil { runBlocking { app.settings.settings.first().appearance } == Appearance.LIGHT }
                assertBackground(false)
                scenario.onActivity { assertTrue(WindowCompat.getInsetsController(it.window, it.window.decorView).isAppearanceLightStatusBars) }
                capture("themes-native-light.png")
                compose.onNodeWithText("Back").performScrollTo().performClick()
                compose.onNodeWithText("Back").performScrollTo().performClick()
                compose.onNodeWithText("Find your next puzzle").assertExists()
            }
        } finally { runBlocking { app.settings.replace(previous) } }
    }
}
