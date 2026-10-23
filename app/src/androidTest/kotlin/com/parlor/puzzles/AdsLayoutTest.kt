package com.parlor.puzzles

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.data.*
import com.parlor.core.model.*
import com.parlor.core.platform.ads.AdPlacement
import com.parlor.feature.library.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class AdsLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Test fun privacyControlsRemainReachableAtTwoHundredPercentAndDailyEntryHasNoBanner() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = runBlocking { ContentRepository(context).load() }
        var placement = AdPlacement.HOME
        var requests = 0
        compose.setContent { ParlorTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Surface(Modifier.width(320.dp).height(640.dp)) {
                    LibraryScreen(catalog, emptyList(), null, PlayerSummary(), PlayerSettings(onboarded = true),
                        emptyList(), { kotlinx.coroutines.flow.flowOf(com.parlor.core.data.HistoryPage()) }, "2026-09-28", false, false, {}, {}, {}, {}, {}, {}, {},
                        onPlacement = { placement = it }, banner = {
                            Surface(Modifier.fillMaxWidth().height(50.dp).testTag("banner-slot")) { Text("Ad layout fixture") }
                        }, onPrivacyOptions = { requests++ }, privacyRequired = true)
                }
            }
        } }
        compose.onNodeWithTag("banner-slot").assertIsDisplayed()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Privacy options").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, requests); assertEquals(AdPlacement.SETTINGS, placement)
        compose.onNodeWithTag("banner-slot").assertIsDisplayed()
        File(context.filesDir, "ads-privacy-compact-200.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("Back").performScrollTo().performClick()
        compose.onNodeWithText("Daily Challenge").performScrollTo().performClick()
        compose.onNodeWithTag("banner-slot").assertDoesNotExist()
        assertEquals(AdPlacement.DAILY_ENTRY, placement)
    }
}
