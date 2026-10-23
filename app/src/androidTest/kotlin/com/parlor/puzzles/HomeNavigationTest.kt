package com.parlor.puzzles

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class HomeNavigationTest {
    @get:Rule val compose = navigationComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as ParlorApplication
    private fun capture(name: String) {
        compose.waitForIdle(); SystemClock.sleep(250)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }

    @Test fun roomAndSimpleHomeKeepProgressNavigationAndPositionAcrossActivityRecreation() {
        val settings = runBlocking { app.settings.settings.first() }
        val catalog = runBlocking { app.content.load() }
        runBlocking { app.settings.replace(PlayerSettings(onboarded = true)) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("home-progress", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                val before = runBlocking { Triple(app.progress.bests.first(), app.progress.player.first(), app.progress.history.first()) }
                val count = catalog.levels.count { level -> before.first.any { it.levelId == level.id } }
                compose.onNode(hasText("$count of ${catalog.levels.size} solved") and hasAnyAncestor(hasTestTag("home-progress")), useUnmergedTree = true).assertExists()
                capture("home-native-room.png")
                compose.onNodeWithTag("home-puzzle-HANOI").performScrollTo()
                capture("home-native-shelves.png")
                listOf("WATER_JUGS", "SLIDING_TILES", "BOX_PUSHER", "QUEENS", "CROSSINGS").forEach { puzzle ->
                    compose.onNodeWithTag("home-puzzle-$puzzle").performScrollTo()
                    capture("home-native-${puzzle.lowercase()}.png")
                }
                val tag = "home-puzzle-KNIGHTS_TOUR"
                compose.onNodeWithTag(tag).performScrollTo().performClick()
                compose.onNodeWithTag("level-difficulty").assertIsDisplayed()
                compose.onNodeWithText("Back").performClick()
                compose.onNodeWithTag(tag).assertIsDisplayed()
                scenario.recreate()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag(tag).assertIsDisplayed()
                compose.onNodeWithText("Settings").performScrollTo().performClick()
                compose.onNodeWithContentDescription("Simple Home layout").performScrollTo().performClick()
                compose.waitUntil { runBlocking { app.settings.settings.first().accessibleHome } }
                compose.onNodeWithText("Back").performScrollTo().performClick()
                compose.onNodeWithTag("home-room-scene", useUnmergedTree = true).assertDoesNotExist()
                compose.onNodeWithTag("home-puzzle-QUEENS").performScrollTo().performClick()
                compose.onNodeWithTag("level-difficulty").assertIsDisplayed()
                compose.onNodeWithText("Back").performClick()
                compose.onNodeWithTag("home-puzzle-QUEENS").assertIsDisplayed()
                scenario.recreate()
                compose.waitUntil { compose.onAllNodesWithTag("home-puzzle-QUEENS").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("home-puzzle-QUEENS").assertIsDisplayed()
                compose.onNodeWithTag("home-room-scene", useUnmergedTree = true).assertDoesNotExist()
                capture("home-native-simple.png")
                assertEquals(before, runBlocking { Triple(app.progress.bests.first(), app.progress.player.first(), app.progress.history.first()) })
                runBlocking { app.settings.update { it.copy(accessibleHome = false, appearance = Appearance.DARK) } }
                compose.onNodeWithTag("home-puzzle-QUEENS").performScrollTo()
                capture("home-native-dark-shelves.png")
            }
        } finally { runBlocking { app.settings.replace(settings) } }
    }
}
