package com.parlor.puzzles

import android.graphics.Bitmap
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader
import com.parlor.core.platform.ads.*
import com.parlor.core.platform.billing.*
import com.parlor.feature.library.ParlorTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AdNetworkQualification

/** Explicit, network-dependent Google demo-unit smoke. Injected consent is NOT evidence of a real UMP form. */
@AdNetworkQualification
class AdsSdkQualificationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun clickLabel(node: AccessibilityNodeInfo?, label: String): Boolean {
        if (node == null) return false
        if ((node.text?.toString().equals(label, ignoreCase = true) || node.contentDescription?.toString()?.endsWith(label, ignoreCase = true) == true) && node.isClickable)
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        for (index in 0 until node.childCount) if (clickLabel(node.getChild(index), label)) return true
        return false
    }
    private fun clickVisibleLabel(label: String): Boolean {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS }
        return ui.windows.any { clickLabel(it.root, label) } || clickLabel(ui.rootInActiveWindow, label)
    }
    @Test fun realTestBannerAndInterstitialLoadAndProDestroysAllAdResources() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(BuildConfig.ADMOB_APP.startsWith("ca-app-pub-3940256099942544"))
        val state = MutableStateFlow(ProState(ProAccess.FREE, PurchaseNotice.READY))
        val consent = object : ConsentGateway {
            override fun snapshot() = ConsentSnapshot(canRequestAds = true)
            override fun refresh(onFinished: (Boolean) -> Unit) = onFinished(true)
            override fun load(onFinished: (PrivacyForm?) -> Unit) = onFinished(null)
            override fun privacy(onDismissed: (Boolean) -> Unit) = onDismissed(true)
        }
        lateinit var ads: AndroidAds
        var banner: AndroidAds.BannerHandle? = null
        compose.runOnIdle {
            ads = AndroidAds(context, AdUnits(BuildConfig.ADMOB_APP, BuildConfig.ADMOB_BANNER, BuildConfig.ADMOB_INTERSTITIAL,
                BuildConfig.ADMOB_REWARDED), state, AdsWork(context) { false }, consent)
            ads.foreground(compose.activity, true); ads.placement(AdPlacement.HOME)
        }
        try {
            compose.waitUntil(30_000) { ads.enabled.value }
            assertTrue(MobileAds.isInitialized)
            compose.runOnIdle { banner = ads.banner(compose.activity, AdPlacement.HOME, 320) }
            compose.setContent { ParlorTheme { Surface(Modifier.fillMaxSize()) {
                Column(Modifier.safeDrawingPadding()) {
                    Text("Google demo banner qualification", Modifier.padding(24.dp))
                    Spacer(Modifier.weight(1f))
                    banner?.let { ad ->
                        val height by ad.height.collectAsState()
                        androidx.compose.ui.viewinterop.AndroidView(factory = { ad.view }, modifier = Modifier.width(320.dp).height(height.dp))
                    }
                }
            } } }
            compose.waitUntil(45_000) { banner?.height?.value?.let { it > 0 } == true }
            File(context.filesDir, "ads-test-banner.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.runOnIdle { state.value = ProState(ProAccess.PRO, PurchaseNotice.READY) }
            compose.waitUntil(5_000) { !ads.enabled.value && banner!!.height.value == 0 }
            compose.runOnIdle { state.value = ProState(ProAccess.FREE, PurchaseNotice.READY) }
            compose.waitUntil(5_000) { ads.enabled.value }
            compose.runOnIdle { ads.placement(AdPlacement.PLAY); ads.levelStarted("sdk-qualification") }
            assertEquals(0, banner!!.height.value)
            compose.waitUntil(45_000) { ads.interstitialReady }
            val shown = AtomicBoolean(); val closed = AtomicBoolean()
            compose.runOnIdle {
                ads.placement(AdPlacement.RESULTS)
                requireNotNull(ads.takeInterstitial()).show({ shown.set(true) }, { closed.set(true) })
            }
            compose.waitUntil(10_000) { shown.get() }
            compose.runOnIdle { ads.foreground(compose.activity, false) }
            assertFalse(closed.get()) // An AdActivity pause must not destroy a presented ad.
            // A freshly booted emulator can put Android's first-immersive-use tip above the ad.
            SystemClock.sleep(1_000)
            clickVisibleLabel("Got it")
            // Only the SDK's close control is clicked; the advertisement's destination is never opened.
            SystemClock.sleep(6_000)
            clickVisibleLabel("Got it")
            SystemClock.sleep(1_000)
            val labels = StringBuilder()
            fun describe(node: AccessibilityNodeInfo?, depth: Int = 0) {
                if (node == null || depth > 40) return
                labels.appendLine("$depth ${node.className} text=${node.text} description=${node.contentDescription} clickable=${node.isClickable}")
                for (index in 0 until node.childCount) describe(node.getChild(index), depth + 1)
            }
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.windows.forEach { describe(it.root) }
            describe(automation.rootInActiveWindow)
            File(context.filesDir, "ads-test-accessibility.txt").writeText(labels.toString())
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { screenshot ->
                File(context.filesDir, "ads-test-interstitial.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                screenshot.recycle()
            }
            if (!clickVisibleLabel("Close button")) {
                // The pinned Google image demo draws its X in the WebView without an exposed node.
                // This is the observed close-circle center, 24 dp from the top/right of that fixture.
                val metrics = context.resources.displayMetrics
                val x = metrics.widthPixels - 24 * metrics.density; val y = 24 * metrics.density
                val downTime = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
                    InstrumentationRegistry.getInstrumentation().sendPointerSync(event); event.recycle()
                }
            }
            compose.waitUntil(10_000) { closed.get() }
            compose.runOnIdle { ads.foreground(compose.activity, true); ads.placement(AdPlacement.PLAY); ads.levelStarted("sdk-second") }
            compose.waitUntil(45_000) { ads.interstitialReady }
            compose.runOnIdle { state.value = ProState(ProAccess.PRO, PurchaseNotice.READY) }
            compose.waitUntil(5_000) { !ads.enabled.value }
            compose.runOnIdle {
                ads.placement(AdPlacement.RESULTS)
                assertNull(ads.takeInterstitial()); assertFalse(ads.interstitialReady)
                assertNull(InterstitialAdPreloader.getConfiguration(BuildConfig.ADMOB_INTERSTITIAL))
                assertFalse(ads.permitsBackgroundWork); assertNull(ads.banner(compose.activity, AdPlacement.RESULTS, 320))
            }
        } finally { compose.runOnIdle { banner?.close(); ads.close() } }
    }
}
