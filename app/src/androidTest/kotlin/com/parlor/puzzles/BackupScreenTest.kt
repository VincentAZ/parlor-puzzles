package com.parlor.puzzles

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.core.data.*
import com.parlor.feature.library.ParlorTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackupScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun largeTextPreviewRequiresExplicitReplacementAndCancelKeepsCurrentProgress() {
        var confirms = 0; var cancels = 0
        val preview = BackupPreview("preview", 1_790_620_800_000, 12, 4, 29, false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                ParlorTheme { Box(Modifier.requiredSize(320.dp, 640.dp)) {
                    BackupScreen(BackupUiState(BackupPhase.PREVIEW, preview), true, onBack = {}, onPro = {}, onExport = {}, onImport = {},
                        onRecovery = {}, onConfirm = { confirms++ }, onDiscard = { cancels++ })
                } }
            }
        }
        assertEquals(0, confirms)
        compose.onNodeWithText("Replace progress with this backup").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Keep current progress").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, confirms); assertEquals(1, cancels)
    }

    @Test fun freeOwnershipGatesNewFilesButKeepsExistingRecoveryAvailable() {
        var pro = 0; var recovery = 0
        compose.setContent { ParlorTheme {
            BackupScreen(BackupUiState(canRecover = true), false, onBack = {}, onPro = { pro++ }, onExport = {}, onImport = {},
                onRecovery = { recovery++ }, onConfirm = {}, onDiscard = {})
        } }
        compose.onNodeWithText("Save a backup file").assertDoesNotExist()
        compose.onNodeWithText("Choose a backup file").assertDoesNotExist()
        compose.onNodeWithText("Explore Pro").performScrollTo().performClick()
        compose.onNodeWithText("Review previous progress").performScrollTo().performClick()
        assertEquals(1, pro); assertEquals(1, recovery)
    }
}
