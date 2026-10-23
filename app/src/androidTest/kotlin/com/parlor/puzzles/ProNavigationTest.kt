package com.parlor.puzzles

import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class ProNavigationTest {
    @get:Rule val compose = navigationComposeRule()
    @Test fun unconfiguredBuildCanVisitProAndReturnToFreePlay() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        assumeTrue(!app.billing.configured)
        runBlocking { app.settings.update { it.copy(onboarded = true) } }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithText("More room to explore.").performScrollTo().performClick()
            compose.onNodeWithText("Purchasing is not available in this build. You can keep playing the free puzzles.").assertExists()
            compose.onNodeWithText("Price currently unavailable").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("Restore purchases").assertIsNotEnabled()
            compose.onNodeWithText("Back").performScrollTo().performClick()
            compose.onNodeWithText("Make yourself comfortable").assertExists()
            compose.onNodeWithText("Back").performScrollTo().performClick()
            compose.onNodeWithText("Traffic Jam").assertExists()
        }
    }
}
