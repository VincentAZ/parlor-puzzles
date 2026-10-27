package com.parlor.puzzles

import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.PlayerSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FreeEditionTest {
    @get:Rule val compose = navigationComposeRule()
    @Test fun everyPaletteIsAvailableWithoutAStoreAndNoPaymentOrAdPermissionShips() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as ParlorApplication
        val previous = runBlocking { app.settings.settings.first() }
        runBlocking { app.settings.replace(PlayerSettings(onboarded = true)) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Settings").performClick()
                compose.onAllNodesWithText("More room to explore.").assertCountEquals(0)
                compose.onNodeWithText("Themes and appearance").performScrollTo().performClick()
                for (theme in listOf("amber", "rose", "sage", "slate", "walnut", "linen")) {
                    compose.onNodeWithTag("theme-$theme").performScrollTo().performClick()
                    compose.waitUntil { runBlocking { app.settings.settings.first().theme } == theme }
                    compose.onNodeWithTag("theme-$theme").assertIsSelected()
                    compose.onAllNodesWithText("Restore purchases").assertCountEquals(0)
                }
                scenario.recreate()
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("theme-linen").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("theme-linen").performScrollTo().assertIsSelected()
            }
            @Suppress("DEPRECATION")
            val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions.orEmpty().toSet()
            assertFalse("com.android.vending.BILLING" in permissions)
            assertFalse("com.google.android.gms.permission.AD_ID" in permissions)
            assertFalse(permissions.any { it.startsWith("android.permission.ACCESS_ADSERVICES") })
        } finally { runBlocking { app.settings.replace(previous) } }
    }
}
