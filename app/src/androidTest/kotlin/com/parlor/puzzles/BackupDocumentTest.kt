package com.parlor.puzzles

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import java.util.regex.Pattern
import com.parlor.core.data.*
import com.parlor.core.model.*
import com.parlor.feature.library.ParlorTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Local system DocumentsUI round trip; ownership is injected, no Play purchase is made. */
class BackupDocumentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val drawer = By.res(Pattern.compile(".*:id/roots_list"))
    private fun clickSystem(selector: BySelector) {
        val control = requireNotNull(device.wait(Until.findObject(selector), 15_000)) {
            "System document control not found: $selector"
        }
        control.click()
        device.waitForIdle()
    }
    private fun downloads() {
        if (!device.hasObject(drawer)) clickSystem(By.desc("Show roots"))
        val options = requireNotNull(device.wait(Until.findObject(drawer), 15_000)) { "DocumentsUI roots did not open" }
        val folder = requireNotNull(options.findObject(By.text("Downloads"))) { "Downloads root was not available" }
        folder.click()
        device.waitForIdle()
        // A selected root can leave the drawer open. Dismiss its scrim without cancelling the picker.
        device.findObject(drawer)?.let { remaining ->
            val bounds = remaining.visibleBounds
            device.click((bounds.right + device.displayWidth) / 2, bounds.top + 80)
        }
        assertTrue("DocumentsUI drawer did not close", device.wait(Until.gone(drawer), 5_000))
    }
    private fun capture(name: String, waitForApp: Boolean = true) {
        if (waitForApp) {
            assertTrue("DocumentsUI has not returned to Parlor", device.wait(Until.hasObject(By.pkg(context.packageName)), 10_000))
            device.waitForIdle(); compose.waitForIdle(); SystemClock.sleep(300)
        }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun systemPickerExportsThenPreviewsAndRestoresTheChosenFile() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ParlorDatabase::class.java).build()
        val app = context.applicationContext as ParlorApplication
        val content = app.content.load()
        val repo = ProgressRepository(db, isPro = { true })
        repo.configure(content, app.content.dailySchedule()); repo.refreshDay()
        val level = content.levels.first { it.puzzle == Puzzle.TRAFFIC_JAM }
        val started = repo.startOrResume(level)
        val preferences = SettingsRepository(context)
        val previousPreferences = preferences.settings.first()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val backup = ManualBackup(context, repo, preferences, scope)
        val name = "parlor-${UUID.randomUUID().toString().take(8)}.json"
        try {
            compose.setContent { ParlorTheme { BackupRoute(backup, true, {}, {}, name) } }
            compose.onNodeWithText("Save a backup file").performScrollTo().performClick()
            SystemClock.sleep(500)
            downloads()
            clickSystem(By.text(Pattern.compile("(?i)save")))
            compose.waitUntil(15_000) { backup.state.value.phase in setOf(BackupPhase.EXPORTED, BackupPhase.ERROR) }
            assertEquals(BackupPhase.EXPORTED, backup.state.value.phase)
            repo.move(level, started.id, level.reference.first(), 123)
            val changed = repo.observeAttempt(started.id).first()!!
            compose.onNodeWithText("Choose a backup file").performScrollTo().performClick()
            SystemClock.sleep(500)
            downloads()
            device.findObject(By.desc("List view"))?.click()
            device.waitForIdle()
            clickSystem(By.text(name))
            compose.waitUntil(15_000) { backup.state.value.phase in setOf(BackupPhase.PREVIEW, BackupPhase.ERROR) }
            assertEquals(BackupPhase.PREVIEW, backup.state.value.phase)
            assertEquals(changed, repo.observeAttempt(started.id).first())
            compose.onNodeWithText("Replace progress with this backup").performScrollTo().assertIsDisplayed()
            capture("backup-system-preview.png")
            compose.onNodeWithText("Replace progress with this backup").performClick()
            compose.waitUntil(15_000) { backup.state.value.phase in setOf(BackupPhase.IMPORTED, BackupPhase.ERROR, BackupPhase.FINISH_REQUIRED) }
            assertEquals(BackupPhase.IMPORTED, backup.state.value.phase)
            assertEquals(started, repo.observeAttempt(started.id).first())
            assertNotNull(repo.recoverySnapshot())
            compose.onNodeWithText("Your progress and settings are restored.").performScrollTo().assertIsDisplayed()
            capture("backup-system-restored.png")
        } catch (failure: Throwable) {
            capture("backup-picker-failed.png", waitForApp = false)
            device.dumpWindowHierarchy(File(context.filesDir, "backup-picker-failed.xml"))
            throw failure
        } finally {
            scope.cancel(); preferences.replace(previousPreferences); db.close()
            instrumentation.uiAutomation.executeShellCommand("rm -f /sdcard/Download/$name").close()
        }
    }

    @Test fun systemPickerPreviewsAndRestoresAFileWithTheOriginalDatabaseUnreadable() = runBlocking {
        val app = context.applicationContext as ParlorApplication
        val content = app.content.load(); val schedule = app.content.dailySchedule()
        val directory = File(context.cacheDir, "recovery-document-test").apply { mkdirs() }
        val names = listOf("document-original-test.db", "document-candidate-test.db")
        names.forEach(context::deleteDatabase)
        val slots = DatabaseSlots(context, names, File(directory, "active.json"))
        val owner = ProgressDatabase(slots::openActive)
        val protection = RecoveryProtectionFile(File(directory, "protection.json"))
        val repo = ProgressRepository(owner, isPro = { true }, protection = protection)
        repo.configure(content, schedule); repo.refreshDay()
        val level = content.levels.first { it.puzzle == Puzzle.TRAFFIC_JAM }
        val started = repo.startOrResume(level)
        repo.move(level, started.id, level.reference.first(), 321)
        val expected = repo.observeAttempt(started.id).first()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val preferences = object : BackupSettingsStore {
            override val settings = MutableStateFlow(PlayerSettings(showTimer = true))
            override suspend fun replace(value: PlayerSettings) { settings.value = value }
        }
        val mutex = Mutex()
        val backup = ManualBackup(context, repo, preferences, scope, mutex = mutex)
        val restored = CompletableDeferred<Unit>()
        val recovery = DamagedDatabaseRecovery(context, owner, slots, protection, scope, mutex,
            loadContent = { content to schedule }, isPro = { true }, isRecoverable = { true },
            beforeSwitch = { backup.invalidatePreview() }, onRestored = { restored.complete(Unit) },
            previewFile = File(directory, "preview.json"))
        val name = "parlor-recovery-${UUID.randomUUID().toString().take(8)}.json"
        var recovering by androidx.compose.runtime.mutableStateOf(false)
        val damaged = ByteArray(4096) { 42 }
        try {
            compose.setContent { ParlorTheme {
                if (recovering) DatabaseRecoveryRoute(recovery, true, {}, {})
                else BackupRoute(backup, true, {}, {}, name)
            } }
            compose.onNodeWithText("Save a backup file").performScrollTo().performClick()
            SystemClock.sleep(500); downloads(); clickSystem(By.text(Pattern.compile("(?i)save")))
            compose.waitUntil(15_000) { backup.state.value.phase in setOf(BackupPhase.EXPORTED, BackupPhase.ERROR) }
            assertEquals(BackupPhase.EXPORTED, backup.state.value.phase)
            owner.replace { context.getDatabasePath(names[0]).writeBytes(damaged) }
            compose.runOnIdle { recovering = true }
            compose.onNodeWithTag("database-restore-choose").performScrollTo().performClick()
            SystemClock.sleep(500); downloads()
            device.findObject(By.desc("List view"))?.click(); device.waitForIdle(); clickSystem(By.text(name))
            compose.waitUntil(15_000) { recovery.state.value.phase in setOf(DatabaseRecoveryPhase.PREVIEW, DatabaseRecoveryPhase.ERROR) }
            assertEquals(DatabaseRecoveryPhase.PREVIEW, recovery.state.value.phase)
            assertFalse(context.getDatabasePath(names[1]).exists())
            assertArrayEquals(damaged, context.getDatabasePath(names[0]).readBytes())
            compose.onNodeWithTag("database-restore-confirm").performScrollTo().assertIsDisplayed()
            capture("database-system-preview.png")
            compose.onNodeWithTag("database-restore-confirm").performClick()
            withTimeout(15_000) { restored.await() }
            assertEquals(1, slots.activeSlot()); assertEquals(expected, repo.observeAttempt(started.id).first())
            assertArrayEquals(damaged, context.getDatabasePath(names[0]).readBytes())
            assertNotNull(repo.pendingBackupSettings())
            backup.finishPendingSettings()
            assertNull(repo.pendingBackupSettings()); assertTrue(preferences.settings.value.showTimer)
            assertTrue(preferences.settings.value.onboarded)
        } catch (failure: Throwable) {
            capture("database-picker-failed.png", waitForApp = false)
            device.dumpWindowHierarchy(File(context.filesDir, "database-picker-failed.xml"))
            throw failure
        } finally {
            scope.cancel(); owner.replace { }; names.forEach(context::deleteDatabase); directory.deleteRecursively()
            instrumentation.uiAutomation.executeShellCommand("rm -f /sdcard/Download/$name").close()
        }
    }
}
