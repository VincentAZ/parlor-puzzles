package com.parlor.puzzles

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.window.layout.WindowLayoutInfo
import androidx.window.layout.FoldingFeature as Fold
import androidx.window.layout.WindowMetricsCalculator
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import com.parlor.core.design.*
import com.parlor.feature.library.ParlorTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.FileInputStream

@SdkSuppress(minSdkVersion = 28)
class FoldCutoutTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val publisher = WindowLayoutInfoPublisherRule()
    private val cutout = object : ExternalResource() {
        private var old = emptyList<String>()
        private val overlay = "com.android.internal.display.cutout.emulation.double"
        fun shell(command: String) = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
            FileInputStream(it.fileDescriptor).bufferedReader().readText()
        }
        override fun before() {
            val list = shell("cmd overlay list")
            check(overlay in list) { "Native double-cutout overlay is required by this emulator qualification fixture" }
            old = list.lines().filter { it.startsWith("[x]") && ".display.cutout.emulation." in it }.map { it.substringAfter("] ").trim() }
            shell("cmd overlay enable-exclusive --category $overlay")
            SystemClock.sleep(300)
        }
        override fun after() {
            shell("cmd overlay disable $overlay")
            old.forEach { shell("cmd overlay enable $it") }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(NativeTabletDisplayRule(2f)).around(cutout).around(publisher).around(compose)
    @Test fun nativeDoubleCutoutAndSeparatingFoldKeepScreenAndDialogInsideSafeInsets() {
        var open by mutableStateOf(false)
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        compose.setContent { ProvideWindowFolds(compose.activity) { ParlorTheme {
            FoldSafePane { Surface(Modifier.fillMaxSize().testTag("cutout-safe-pane")) {
                Column(Modifier.padding(16.dp)) {
                    Text("Cutout layout fixture")
                    Button(onClick = { open = true }, modifier = Modifier.testTag("cutout-open")) { Text("Open") }
                }
            } }
            if (open) FoldAwareAlertDialog(onDismissRequest = { open = false }, title = { Text("Saved progress") },
                text = { Text("Your level is saved on this device.") },
                confirmButton = { TextButton(onClick = { open = false }, modifier = Modifier.testTag("cutout-confirm")) { Text("Continue") } })
        } } }
        compose.waitUntil { ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.displayCutout != null }
        val native = requireNotNull(ViewCompat.getRootWindowInsets(compose.activity.window.decorView))
        val cutout = requireNotNull(native.displayCutout)
        assertTrue(cutout.boundingRects.isNotEmpty())
        val insets = native.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val window = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(compose.activity).bounds
        fun safe(tag: String) {
            val b = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInWindow
            assertTrue("$b inside native safe insets $insets", b.left >= insets.left && b.top >= insets.top &&
                b.right <= window.width() - insets.right && b.bottom <= window.height() - insets.bottom)
        }
        safe("cutout-safe-pane")
        val fold = FoldingFeature(compose.activity, size = 40, orientation = Fold.Orientation.VERTICAL)
        publisher.overrideWindowLayoutInfo(WindowLayoutInfo(listOf(fold)))
        compose.waitForIdle()
        safe("cutout-safe-pane")
        val b = compose.onNodeWithTag("cutout-safe-pane").fetchSemanticsNode().boundsInWindow
        assertTrue(b.right <= fold.bounds.left || b.left >= fold.bounds.right)
        captureTabletScreen(compose, "fold-native-double-cutout.png")
        compose.onNodeWithTag("cutout-open").performClick()
        compose.onNodeWithTag("cutout-confirm").performScrollTo()
        safe("cutout-confirm")
        captureTabletScreen(compose, "fold-native-double-cutout-dialog.png")
        compose.onNodeWithTag("cutout-confirm").performClick()
    }
}
