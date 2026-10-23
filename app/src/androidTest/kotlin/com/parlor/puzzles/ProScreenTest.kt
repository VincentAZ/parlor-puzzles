package com.parlor.puzzles

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.platform.billing.*
import com.parlor.feature.library.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ProScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir,"billing-$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }
    @Test fun localizedPricePendingAndOfflineOwnershipRemainClearAtLargeTextSize() {
        var state by mutableStateOf(ProState(ProAccess.FREE,PurchaseNotice.READY,ProOffer("€3.49","offer")))
        var buys = 0;var restores = 0
        compose.setContent { ParlorTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,2f)) {
                Surface(Modifier.width(320.dp).height(640.dp)) { ProScreen(state,{}, { buys++ },{ restores++ },{}) }
            }
        } }
        compose.onNodeWithText("Unlock Pro · €3.49").performScrollTo().assertIsEnabled().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1,buys)
        capture("price-compact-200")
        compose.runOnIdle { state = state.copy(access = ProAccess.UNKNOWN,notice = PurchaseNotice.PENDING) }
        compose.onNodeWithText("Unlock Pro · €3.49").assertIsNotEnabled()
        compose.onNodeWithText("Your payment is pending in Google Play. Pro will open after payment is confirmed.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Restore purchases").performScrollTo().performClick();assertEquals(1,restores)
        capture("pending-compact-200")
        compose.runOnIdle { state = state.copy(access = ProAccess.PRO,notice = PurchaseNotice.RETRY) }
        compose.onNodeWithText("Unlock Pro · €3.49").assertDoesNotExist()
        compose.onNodeWithText("Parlor Pro is yours.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Google Play is unavailable right now. Your confirmed Pro access is still available.").performScrollTo().assertIsDisplayed()
        capture("offline-pro-compact-200")
    }
}
