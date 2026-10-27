package com.parlor.puzzles

import android.app.Application
import android.content.Intent
import com.parlor.core.data.*
import com.parlor.core.platform.Feedback
import com.parlor.core.platform.crashes.AndroidCrashReporting
import com.parlor.core.platform.crashes.CrashReportingStatus
import com.parlor.core.platform.reminders.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

enum class SettingsReset { READY, WORKING, FAILED }

open class ParlorApplication : Application(), RemindersOwner {
    var crashReportingStatus = CrashReportingStatus.DISABLED
        private set
    override fun onCreate() {
        super.onCreate()
        crashReportingStatus = AndroidCrashReporting.start(this, BuildConfig.CRASH_REPORTING_ENABLED)
        PhotoCodec.clearAbandonedInputs(cacheDir)
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val databaseSlots by lazy { DatabaseSlots(this) }
    val databaseOwner by lazy { ProgressDatabase(databaseSlots::openActive) }
    val database get() = databaseOwner.current
    val storageHealth = StorageHealth()
    val recoveryProtection by lazy { RecoveryProtectionFile(this) }
    val progress by lazy { ProgressRepository(databaseOwner,isPro = { true }, storageHealth = storageHealth,
        protection = recoveryProtection) }
    val settings by lazy { SettingsRepository(this, storageHealth) }
    private val mutableSettingsReset = MutableStateFlow(SettingsReset.READY)
    val settingsReset = mutableSettingsReset.asStateFlow()
    fun resetUnreadableSettings() {
        if (storageHealth.state.value.issue != StorageIssue(StorageArea.SETTINGS, StorageProblem.DAMAGED) ||
            mutableSettingsReset.value == SettingsReset.WORKING) return
        mutableSettingsReset.value = SettingsReset.WORKING
        // Confirmation belongs to the application: rotation or Close cannot cancel a half-finished reset.
        serviceScope.launch {
            try {
                settings.resetUnreadableSettings()
                mutableSettingsReset.value = SettingsReset.READY
                storageHealth.retry()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableSettingsReset.value = SettingsReset.FAILED }
        }
    }
    private val backupMutex = Mutex()
    val backups: ManualBackup by lazy { ManualBackup(this, progress, settings, serviceScope, storageHealth, backupMutex,
        deleteOtherPhotoCopies = { databaseRecovery.deletePhotoCopies() }) { true } }
    val databaseRecovery: DamagedDatabaseRecovery by lazy { DamagedDatabaseRecovery(this, databaseOwner, databaseSlots, recoveryProtection,
        serviceScope, backupMutex, loadContent = { content.load() to content.dailySchedule() },
        isPro = { true },
        isRecoverable = { storageHealth.state.value.issue == StorageIssue(StorageArea.PROGRESS, StorageProblem.DAMAGED) },
        beforeSwitch = { backups.invalidatePreview() }, onRestored = storageHealth::retry) }
    val content by lazy { ContentRepository(this) }
    val feedback by lazy { Feedback(this) }
    @Volatile private var reminderForeground = false
    private val visibleActivities = mutableSetOf<MainActivity>()
    private var backgroundTransition: Job? = null
    override val reminders by lazy { AndroidReminders(this, serviceScope,
        ReminderProgress { action -> progress.withCompletionCredits(action) }, { reminderForeground },
        { Intent(this, MainActivity::class.java) }) }

    fun reminderVisibility(activity: MainActivity, visible: Boolean) {
        if (visible) {
            visibleActivities += activity; backgroundTransition?.cancel(); reminderForeground = true
            reminders.refresh()
        } else {
            visibleActivities -= activity
            if (visibleActivities.isEmpty()) backgroundTransition = serviceScope.launch {
                delay(750) // Configuration changes must not briefly permit a notification over the open app.
                if (visibleActivities.isEmpty()) { reminderForeground = false; reminders.refresh() }
            }
        }
    }
}
