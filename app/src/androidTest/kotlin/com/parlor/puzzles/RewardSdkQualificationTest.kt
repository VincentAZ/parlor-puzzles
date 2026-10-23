package com.parlor.puzzles

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.data.*
import com.parlor.core.model.*
import com.parlor.core.platform.ads.*
import com.parlor.core.platform.billing.*
import com.parlor.feature.library.ParlorTheme
import com.parlor.feature.play.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import kotlin.math.abs

/** Real Google rewarded demo; known ownership/consent are injected only in this qualification fixture. */
@AdNetworkQualification
class RewardSdkQualificationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun clickLabel(node: AccessibilityNodeInfo?, label: String): Boolean {
        if (node == null) return false
        if ((node.text?.toString().equals(label, true) || node.contentDescription?.toString().equals(label, true)) && node.isClickable)
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        for (index in 0 until node.childCount) if (clickLabel(node.getChild(index), label)) return true
        return false
    }
    private fun clickVisibleLabel(label: String): Boolean {
        val ui = instrumentation.uiAutomation
        ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS }
        return ui.windows.filter { it.isActive || it.isFocused }.any { clickLabel(it.root, label) } || clickLabel(ui.rootInActiveWindow, label)
    }
    private fun capture(name: String) {
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        File(context.filesDir, name).outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }

    /** Locate the observed white X in the demo's upper-right SDK chrome; never tap an absent close. */
    private fun visibleDemoClose(): Pair<Float, Float>? {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            val density = context.resources.displayMetrics.density
            val left = (bitmap.width - 56 * density).toInt().coerceAtLeast(0)
            val width = bitmap.width - left
            val height = (64 * density).toInt().coerceAtMost(bitmap.height)
            val white = BooleanArray(width * height) { index ->
                val color = bitmap.getPixel(left + index % width, index / width)
                android.graphics.Color.red(color) > 225 && android.graphics.Color.green(color) > 225 && android.graphics.Color.blue(color) > 225
            }
            val visited = BooleanArray(white.size)
            for (start in white.indices) {
                if (!white[start] || visited[start]) continue
                val points = mutableListOf(start); visited[start] = true
                var cursor = 0
                while (cursor < points.size) {
                    val point = points[cursor++]; val x = point % width; val y = point / width
                    for (dy in -1..1) for (dx in -1..1) {
                        val nx = x + dx; val ny = y + dy
                        if (nx !in 0 until width || ny !in 0 until height) continue
                        val next = ny * width + nx
                        if (white[next] && !visited[next]) { visited[next] = true; points += next }
                    }
                }
                val x1 = points.minOf { it % width }; val x2 = points.maxOf { it % width }
                val y1 = points.minOf { it / width }; val y2 = points.maxOf { it / width }
                val w = (x2 - x1).toFloat(); val h = (y2 - y1).toFloat()
                if (w !in 8 * density..24 * density || h !in 8 * density..24 * density || w / h !in 0.75f..1.33f) continue
                var diagonal = 0; var reverse = 0; var either = 0
                for (point in points) {
                    val x = (point % width - x1) / w; val y = (point / width - y1) / h
                    val a = abs(x - y) < 0.25f; val b = abs(x + y - 1) < 0.25f
                    if (a) diagonal++; if (b) reverse++; if (a || b) either++
                }
                if (either >= points.size * 0.85 && diagonal >= points.size * 0.3 && reverse >= points.size * 0.3)
                    return (left + (x1 + x2) / 2f) to ((y1 + y2) / 2f)
            }
            return null
        } finally { bitmap.recycle() }
    }

    @Test fun realRewardedCallbackAddsTwoHintsOnceWhileBoardAndTimerStayPaused() {
        assertTrue(BuildConfig.ADMOB_REWARDED.startsWith("ca-app-pub-3940256099942544"))
        val app = context.applicationContext as ParlorApplication
        val billing = MutableStateFlow(ProState(ProAccess.FREE, PurchaseNotice.READY))
        val db = Room.inMemoryDatabaseBuilder(context, ParlorDatabase::class.java).build()
        val repo = ProgressRepository(db, isPro = { billing.value.isPro })
        val content = runBlocking { app.content.load() }
        val level = content.levels.first { it.puzzle == Puzzle.TRAFFIC_JAM && it.tier == Tier.EASY }
        repo.configure(content, runBlocking { app.content.dailySchedule() })
        runBlocking { repo.refreshDay() }
        val consent = object : ConsentGateway {
            override fun snapshot() = ConsentSnapshot(canRequestAds = true)
            override fun refresh(onFinished: (Boolean) -> Unit) = onFinished(true)
            override fun load(onFinished: (PrivacyForm?) -> Unit) = onFinished(null)
            override fun privacy(onDismissed: (Boolean) -> Unit) = onDismissed(true)
        }
        val storeName = "earned-reward-sdk-test.json"
        listOf("", ".bak", ".new").forEach { File(context.noBackupFilesDir, storeName + it).delete() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var ads: AndroidAds
        lateinit var delivery: RewardDelivery
        lateinit var rewards: RewardAds
        lateinit var vm: PlayViewModel
        val visible = mutableStateOf(true)
        compose.runOnIdle {
            ads = AndroidAds(context, AdUnits(BuildConfig.ADMOB_APP, BuildConfig.ADMOB_BANNER,
                BuildConfig.ADMOB_INTERSTITIAL, BuildConfig.ADMOB_REWARDED), billing, AdsWork(context) { false }, consent)
            ads.foreground(compose.activity, true); ads.placement(AdPlacement.PLAY)
            delivery = RewardDelivery(scope, DeviceRewardStore(context, storeName), repo::applyEarnedReward)
            delivery.retry()
            rewards = RewardAds(scope, delivery, ads::loadRewarded, { ads.mayPresentReward }, repo::rewardAvailability,
                { RewardMoment(LocalDate.now().toString(), System.currentTimeMillis()) }, { "sdk-earned-reward" })
        }
        compose.setContent { if (visible.value) ParlorTheme {
            vm = viewModel(factory = PlayViewModel.factory(level, repo))
            val offered by rewards.state.collectAsState()
            val delivered by delivery.state.collectAsState()
            val enabled by ads.enabled.collectAsState()
            val ownership by billing.collectAsState()
            val player by repo.player.collectAsState(PlayerSummary())
            PlayScreen(vm, PlayerSettings(reduceMotion = true), player.ledger.dailyHints + player.ledger.earnedHints, false,
                app.feedback, {}, null, pro = ownership.isPro, externalPause = offered != null, onPlacement = ads::placement,
                onRewardHints = { rewards.offer(Reward.Hints) })
            offered?.let { RewardDialog(it, delivered, enabled && ads.mayPresentReward, player.ledger.rewardedViews,
                onWatch = rewards::watch, onClose = rewards::cancel, onRetrySave = rewards::retryDelivery, onContinue = rewards::cancel) }
        } }
        try {
            compose.waitUntil(30_000) { ads.enabled.value && delivery.state.value.canStart && vm.state.value.attempt != null }
            val move = level.reference.first() as Translate
            compose.onNodeWithTag("piece-${move.pieceId}").performScrollTo().performClick()
            compose.onNodeWithTag("destination-${move.pieceId}-${move.to}").performScrollTo().performClick()
            compose.waitUntil(10_000) { vm.state.value.attempt?.scoredMoves == 1 }
            compose.onNodeWithText("Get 2 hints with an ad").performScrollTo().performClick()
            compose.onNodeWithText("Watch an ad").assertIsEnabled()
            val paused = runBlocking { repo.observeAttempt(vm.state.value.attempt!!.id).first()!! }
            compose.onNodeWithText("Watch an ad").performClick()
            compose.waitUntil(45_000) { rewards.state.value?.stage == RewardStage.SHOWING }
            compose.runOnIdle { ads.foreground(compose.activity, false) }
            SystemClock.sleep(6_000); clickVisibleLabel("Got it")
            capture("reward-sdk-presented.png")
            // The actual SDK earned callback, rather than a close/load or a test grant, must deliver the reward.
            compose.waitUntil(90_000) { delivery.state.value.lastApplied?.id == "sdk-earned-reward" }
            var close: Pair<Float, Float>? = null
            // The earned callback can precede the end-card animation. Wait for the actual visible X.
            compose.waitUntil(15_000) { visibleDemoClose().also { close = it } != null }
            capture("reward-sdk-earned.png")
            val closedThroughSemantics = listOf("Close button", "Close ad").any(::clickVisibleLabel)
            if (!closedThroughSemantics) {
                val (x, y) = requireNotNull(close)
                val downTime = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
                    instrumentation.sendPointerSync(event); event.recycle()
                }
            }
            compose.waitUntil(45_000) { rewards.state.value?.stage == RewardStage.EARNED }
            compose.runOnIdle { ads.foreground(compose.activity, true) }
            assertEquals(paused, runBlocking { repo.observeAttempt(paused.id).first() })
            val ledger = runBlocking { repo.player.first().ledger }
            assertEquals(2, ledger.earnedHints); assertEquals(1, ledger.rewardedViews)
            assertEquals(setOf("sdk-earned-reward"), ledger.claims)
            assertFalse(paused.hintsUsed)
            compose.onNodeWithText("Keep playing").performClick()
            compose.onNodeWithText("5 hints").performScrollTo().assertExists()
            compose.runOnIdle { billing.value = ProState(ProAccess.PRO, PurchaseNotice.READY) }
            compose.waitUntil(5_000) { !ads.enabled.value }
            compose.onNodeWithText("Get 2 hints with an ad").assertDoesNotExist()
            compose.runOnIdle { ads.loadRewarded { assertNull(it) } }
            assertNull(runBlocking { DeviceRewardStore(context, storeName).load() })
            capture("reward-sdk-returned.png")
        } finally {
            compose.runOnIdle { rewards.cancel(); ads.close(); vm.lifecycle(false); visible.value = false }
            compose.waitForIdle(); scope.cancel(); db.close()
            listOf("", ".bak", ".new").forEach { File(context.noBackupFilesDir, storeName + it).delete() }
        }
    }
}
