package com.parlor.puzzles

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.parlor.core.data.PlayerRow
import com.parlor.core.model.*
import com.parlor.core.platform.reminders.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class ReminderNavigationTest {
    @get:Rule val compose = navigationComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as ParlorApplication
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private fun visible(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private fun awaitDaily() { compose.waitUntil(15_000) { visible("Daily Challenge ·") } }
    private fun capture(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).bufferedReader().readText()
    }
    private fun clickNotification(): Boolean {
        val roots = listOfNotNull(instrumentation.uiAutomation.rootInActiveWindow) +
            instrumentation.uiAutomation.windows.mapNotNull { it.root }
        return roots.any { click(it, "Today’s puzzle is ready") }
    }
    private fun click(node: AccessibilityNodeInfo?, label: String): Boolean {
        if (node == null) return false
        if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
            capture("reminder-notification.png")
            var target: AccessibilityNodeInfo? = node
            while (target != null) {
                if (target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                target = target.parent
            }
        }
        for (i in 0 until node.childCount) if (click(node.getChild(i), label)) return true
        return false
    }

    @Test fun notificationIntentWaitsForOnboardingAndIsConsumedOnceAcrossRecreation() {
        val previous = runBlocking { app.settings.settings.first() }
        runBlocking { app.settings.update { it.copy(onboarded = false, tutorialsSeen = Puzzle.entries.toSet()) } }
        try {
            val intent = Intent(context, MainActivity::class.java).setAction(AndroidReminderDevice.OPEN_DAILY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                compose.waitUntil(15_000) { visible("A quiet place for a puzzle.") }
                assertFalse(visible("Daily Challenge ·"))
                scenario.recreate()
                compose.waitUntil(15_000) { visible("A quiet place for a puzzle.") }
                compose.onNodeWithText("Continue").performScrollTo().performClick()
                compose.onNodeWithText("Continue").performScrollTo().performClick()
                compose.onNodeWithText("Step inside").performScrollTo().performClick()
                awaitDaily()
                scenario.recreate(); awaitDaily()
                compose.onNodeWithText("Back").performScrollTo().performClick()
                compose.waitUntil(10_000) { visible("Traffic Jam") && visible("Settings") }
                assertFalse(visible("Daily Challenge ·"))
            }
        } finally { runBlocking { app.settings.replace(previous) } }
    }

    @Test fun realReceiverSuppressesForegroundPostsOnceInBackgroundAndNotificationOpensDaily() = runBlocking {
        if (Build.VERSION.SDK_INT >= 33)
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val previousSettings = app.settings.settings.first()
        val previousPlayer = app.database.progress().player()
        val file = File(context.noBackupFilesDir, "reminders-v1.json")
        app.reminders.update { it.copy(enabled = false) }.join()
        val previousRecord = if (file.exists()) file.readBytes() else null
        val device = AndroidReminderDevice(context) { Intent(context, MainActivity::class.java) }
        val today = app.progress.localDate()
        val automation = instrumentation.uiAutomation
        val previousFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        suspend fun sendDue() {
            val finished = CompletableDeferred<Unit>()
            val intent = Intent(context, ReminderReceiver::class.java).setAction(AndroidReminderDevice.DUE)
                .putExtra(AndroidReminderDevice.DATE, today)
            val pending = PendingIntent.getBroadcast(context, AndroidReminderDevice.ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            pending.send(context, 0, null, { _, _, _, _, _ -> finished.complete(Unit) }, Handler(Looper.getMainLooper()))
            withTimeout(10_000) { finished.await() }
        }
        try {
            // Isolated debug fixture: remove prior test claims; ordinary UI never clears this file.
            file.delete()
            val row = previousPlayer ?: PlayerRow(ledgerJson = ParlorJson.encodeToString(RewardLedger()))
            val ledger = ParlorJson.decodeFromString<RewardLedger>(row.ledgerJson)
            app.database.progress().savePlayer(row.copy(ledgerJson = ParlorJson.encodeToString(ledger.copy(creditedDates = ledger.creditedDates - today))))
            app.settings.update { it.copy(onboarded = true, tutorialsSeen = Puzzle.entries.toSet()) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
              try {
                compose.waitUntil(15_000) { visible("Settings") }
                app.reminders.update { ReminderPreferences(enabled = true, minuteOfDay = 0) }.join()
                assertFalse(app.reminders.state.value.error); assertTrue(app.reminders.state.value.permitted)
                sendDue()
                assertNull(DeviceReminderStore(context).load().lastClaimedDate)
                assertTrue(manager.activeNotifications.none { it.tag == AndroidReminderDevice.TAG })
                scenario.moveToState(Lifecycle.State.CREATED)
                // Allow the production rotation/departure debounce to finish.
                delay(1_000)
                sendDue()
                withTimeout(5_000) {
                    while (manager.activeNotifications.none { it.tag == AndroidReminderDevice.TAG }) delay(50)
                }
                val first = manager.activeNotifications.single { it.tag == AndroidReminderDevice.TAG }
                assertEquals(today, DeviceReminderStore(context).load().lastClaimedDate)
                assertEquals(AndroidReminderDevice.CHANNEL, first.notification.channelId)
                assertTrue(first.notification.timeoutAfter > 0)
                assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(AndroidReminderDevice.CHANNEL).importance)
                sendDue()
                assertEquals(first.postTime, manager.activeNotifications.single { it.tag == AndroidReminderDevice.TAG }.postTime)
                assertFalse(MobileAds.isInitialized)
                shell("cmd statusbar expand-notifications")
                delay(1_000)
                val clicked = withTimeoutOrNull(5_000) {
                    while (!clickNotification()) delay(100)
                    true
                }
                if (clicked != true) {
                    capture("reminder-shade-failed.png")
                    val labels = mutableListOf<String>()
                    fun collect(node: AccessibilityNodeInfo?) {
                        if (node == null) return
                        labels += "${node.text} | ${node.contentDescription} | ${node.viewIdResourceName}"
                        for (i in 0 until node.childCount) collect(node.getChild(i))
                    }
                    collect(automation.rootInActiveWindow); automation.windows.forEach { collect(it.root) }
                    File(context.filesDir, "reminder-shade-failed.txt").writeText(labels.joinToString("\n"))
                    fail("The notification shade did not expose the reminder as a tappable control")
                }
                awaitDaily()
                capture("reminder-opened-daily.png")
                compose.waitUntil(5_000) { manager.activeNotifications.none { it.tag == AndroidReminderDevice.TAG } }
                scenario.recreate(); awaitDaily()
                compose.onNodeWithText("Back").performScrollTo().performClick()
                compose.waitUntil(10_000) { visible("Settings") }
                compose.onNodeWithText("Settings").performClick()
                compose.onNodeWithText("Reminders").performScrollTo().performClick()
                compose.onNodeWithText("Remind me").performScrollTo().assertIsOn().performClick()
                compose.waitUntil(5_000) { !app.reminders.state.value.preferences.enabled && !app.reminders.state.value.busy }
                assertNull(PendingIntent.getBroadcast(context, AndroidReminderDevice.ID,
                    Intent(context, ReminderReceiver::class.java).setAction(AndroidReminderDevice.DUE),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
                assertTrue(manager.activeNotifications.none { it.tag == AndroidReminderDevice.TAG })
              } finally { shell("cmd statusbar collapse") }
            }
        } finally {
            app.reminders.update { it.copy(enabled = false) }.join(); device.schedule(null); device.hide()
            if (previousRecord == null) file.delete() else file.writeBytes(previousRecord)
            app.settings.replace(previousSettings)
            previousPlayer?.let { app.database.progress().savePlayer(it) }
            app.reminders.refresh().join()
            automation.serviceInfo = automation.serviceInfo.apply { flags = previousFlags }
            shell("cmd statusbar collapse")
        }
    }

    @Test fun manifestKeepsReceiverPrivateAndRequiresNeitherWakeLockNorExactAlarms() {
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in permissions)
        assertTrue(Manifest.permission.RECEIVE_BOOT_COMPLETED in permissions)
        assertFalse(Manifest.permission.WAKE_LOCK in permissions)
        assertFalse("android.permission.SCHEDULE_EXACT_ALARM" in permissions)
        assertFalse("android.permission.USE_EXACT_ALARM" in permissions)
        val info = context.packageManager.getReceiverInfo(ComponentName(context, ReminderReceiver::class.java), PackageManager.MATCH_DISABLED_COMPONENTS)
        assertFalse(info.exported)
    }
}
