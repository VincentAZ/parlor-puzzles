package com.parlor.core.data

import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.*
import org.junit.Assert.*
import java.io.File

class BackupPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: ParlorDatabase
    private lateinit var repo: ProgressRepository
    private var pro = true
    private val day = "2026-09-28"
    private val level = LevelDefinition("backup-traffic", puzzle = Puzzle.TRAFFIC_JAM, tier = Tier.EASY, order = 1,
        initial = TrafficJamBoard(pieces = listOf(TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL), TrafficPiece("B", 2, 0, 3, Axis.VERTICAL))),
        reference = listOf(Translate("B", 3), Translate("A", 6)), par = 2, parKind = ParKind.PROVEN_OPTIMAL,
        targetTimeMs = 60_000, provenance = Provenance("Parlor", "backup fixture", 0, "original"))
    private val settings = PlayerSettings(showTimer = true, sfxVolume = 0.4f, onboarded = true)
    private fun open() {
        db = Room.databaseBuilder(context, ParlorDatabase::class.java, "backup-persistence-test.db").build()
        repo = ProgressRepository(db, today = { day }, wallTime = { 1_000 }, isPro = { pro })
        repo.configure(Catalog(levels = listOf(level)), DailySchedule(listOf(ScheduleEpoch(1, day, listOf(LevelRef(level.id, 1))))))
    }
    @Before fun setup() { context.deleteDatabase("backup-persistence-test.db"); open() }
    @After fun teardown() { db.close(); context.deleteDatabase("backup-persistence-test.db") }
    private suspend fun finishSettings() { repo.finishBackupSettings(requireNotNull(repo.pendingBackupSettings())) }

    @Test fun replaceRestoresHistoryViewportAndBestsWithARecoverablePreviousSnapshot() = runBlocking {
        repo.refreshDay()
        val first = repo.startOrResume(level)
        repo.move(level, first.id, level.reference.first(), 123)
        repo.useHint(level, first.id, 10)
        repo.saveViewport(level, first.id, Viewport(1, 1, 2, "B"), 0)
        val before = repo.undo(level, first.id, 20)!!
        val backup = repo.exportProgress(settings)
        repo.redo(level, first.id, 10); repo.move(level, first.id, level.reference.last(), 30)
        val best = repo.bests.first().single()
        val next = repo.startOrResume(level); repo.move(level, next.id, level.reference.first(), 90)
        // Import a different unfinished attempt; an already completed ID must never be made eligible again.
        val imported = backup.copy(attempts = listOf(before.copy(id = "restored-attempt")), attemptUpdatedAt = mapOf("restored-attempt" to 500L))
        repo.importProgress(imported, PlayerSettings())
        val restored = repo.startOrResume(level)
        assertEquals(before.copy(id = "restored-attempt"), restored)
        assertTrue(repo.bests.first().isEmpty())
        assertEquals(1, repo.completionCount.first())
        assertEquals(settings, repo.pendingBackupSettings())
        val recovery = repo.recoverySnapshot()!!
        assertEquals(best, recovery.bests.single()); assertTrue(recovery.attempts.any { it.id == next.id })
        finishSettings(); pro = false
        repo.importProgress(recovery, settings, recovery = true); finishSettings()
        assertEquals(best, repo.bests.first().single()); assertEquals(next.id, repo.startOrResume(level).id)
        assertFalse(repo.hasPro())
    }

    @Test fun repeatedOldImportsRetainSpentHintBalancesFlagsAndRewardCap() = runBlocking {
        repo.refreshDay(); val a = repo.startOrResume(level)
        repo.grantReward("one", Reward.Hints); repo.grantReward("two", Reward.Hints)
        val backup = repo.exportProgress(settings)
        pro = false
        repeat(5) { repo.useHint(level, a.id, 0) }
        assertEquals(2, repo.player.first().ledger.earnedHints)
        pro = true
        repeat(3) { repo.importProgress(backup, settings); finishSettings() }
        val restored = repo.observeAttempt(a.id).first()!!
        assertTrue(restored.hintsUsed)
        val ledger = repo.player.first().ledger
        assertEquals(0, ledger.dailyHints); assertEquals(2, ledger.earnedHints); assertEquals(2L, ledger.earnedHintsSpent)
        assertEquals(2, ledger.rewardedViews)
        assertFalse(repo.grantReward("one", Reward.Hints))
    }

    @Test fun firstCompletionAndUsedScoredRetriesCannotBeReopenedByAnOlderFile() = runBlocking {
        pro = false; repo.refreshDay()
        val first = repo.startDaily(); level.reference.forEach { repo.move(level, first.attempt.id, it, 0) }
        repo.grantReward("retry", Reward.DailyRetry(day))
        val retry = repo.startDaily(); assertEquals(DailyEligibility.REWARDED, retry.attempt.dailyEligibility)
        pro = true; val backup = repo.exportProgress(settings); pro = false
        level.reference.forEach { repo.move(level, retry.attempt.id, it, 0) }
        pro = true; repo.importProgress(backup, settings); finishSettings(); pro = false
        assertEquals(1, repo.player.first().ledger.dailyClaims.getValue(day).consumedRetries)
        assertEquals(DailyEligibility.PRACTICE, repo.startDaily().attempt.dailyEligibility)
        assertEquals(2, repo.completionCount.first())
    }

    @Test fun failedTransactionRollsBackRowsAndRecoveryWhileInvalidFilesNeverStartReplacement() = runBlocking {
        repo.refreshDay(); val a = repo.startOrResume(level)
        val backup = repo.exportProgress(settings)
        repo.move(level, a.id, level.reference.first(), 123)
        val current = repo.observeAttempt(a.id).first()
        for (bad in listOf(backup.copy(schemaVersion = 200), backup.copy(contentVersion = 200),
            backup.copy(ledger = backup.ledger.copy(claims = (0..10_000).map { "claim-$it-" + "x".repeat(180) }.toSet())),
            backup.copy(attempts = backup.attempts.map { it.copy(levelId = "unknown-level") }))) {
            assertTrue(runCatching { repo.importProgress(bad, settings) }.isFailure)
            assertEquals(current, repo.observeAttempt(a.id).first())
        }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_backup BEFORE INSERT ON backup_state BEGIN SELECT RAISE(ABORT, 'simulated full disk'); END")
        assertTrue(runCatching { repo.importProgress(backup, settings) }.isFailure)
        assertEquals(current, repo.observeAttempt(a.id).first()); assertNull(repo.recoverySnapshot()); assertNull(repo.pendingBackupSettings())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_backup")
        pro = false
        assertTrue(runCatching { repo.importProgress(backup, settings) }.isFailure)
        assertTrue(runCatching { repo.exportProgress(settings) }.isFailure)
        assertEquals(current, repo.observeAttempt(a.id).first())
    }

    @Test fun largeRecoverySnapshotSurvivesCursorWindowLimitsAndSettingsCompletion() = runBlocking {
        repo.refreshDay(); val attempt = repo.startOrResume(level)
        level.reference.forEach { repo.move(level, attempt.id, it, 0) }
        val small = repo.exportProgress(settings)
        val completed = small.attempts.single()
        val many = (0 until 5_000).map { completed.copy(id = "large-backup-$it") }
        val large = small.copy(snapshotId = "large-snapshot", attempts = many,
            attemptUpdatedAt = many.associate { it.id to 1_000L },
            completedAttemptIds = small.completedAttemptIds + many.map { it.id },
            completionDates = small.completionDates + many.associate { it.id to day })
        assertTrue(BackupFile.bytes(large).size > 2 * 1024 * 1024)
        repo.importProgress(large, settings); finishSettings()
        repo.importProgress(small, settings); finishSettings()
        db.close(); open()
        val recovery = requireNotNull(repo.recoverySnapshot())
        assertEquals(5_000, recovery.attempts.size)
        pro = false
        repo.importProgress(recovery, settings, recovery = true); finishSettings()
        assertEquals(5_000, db.progress().allAttempts().size)
        assertNull(repo.pendingBackupSettings())
    }

    @Test fun aKillAfterProgressCommitFinishesSettingsOnReopenWithoutGrantingProOrNotificationOptIn() = runBlocking {
        repo.refreshDay(); repo.startOrResume(level)
        val backup = repo.exportProgress(settings.copy(notifications = true))
        repo.importProgress(backup, PlayerSettings(notifications = false))
        db.close(); pro = false; open()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val store = SettingsRepository(context)
        try {
            store.replace(PlayerSettings())
            val coordinator = ManualBackup(context, repo, store, scope)
            coordinator.finishPendingSettings()
            assertEquals(settings.copy(notifications = false), store.settings.first())
            assertNull(repo.pendingBackupSettings()); assertTrue(repo.hasRecoverySnapshot()); assertFalse(repo.hasPro())
            coordinator.finishPendingSettings()
            assertEquals(1, db.progress().allAttempts().size)
        } finally { scope.cancel(); store.replace(PlayerSettings()) }
    }

    @Test fun documentReadCreatesOnlyAPreviewAndConfirmationImportsTheStagedBytes() = runBlocking {
        repo.refreshDay(); val attempt = repo.startOrResume(level)
        val incoming = repo.exportProgress(settings)
        val file = File(context.cacheDir, "chosen-backup.json").apply { writeBytes(BackupFile.bytes(incoming)) }
        repo.move(level, attempt.id, level.reference.first(), 50)
        val before = repo.observeAttempt(attempt.id).first()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val store = SettingsRepository(context)
        try {
            val coordinator = ManualBackup(context, repo, store, scope)
            coordinator.select(Uri.fromFile(file))!!.join()
            assertEquals(BackupPhase.PREVIEW, coordinator.state.value.phase)
            assertEquals(before, repo.observeAttempt(attempt.id).first())
            file.writeText("truncated") // The preview describes private staged bytes, not a subsequently changed provider.
            coordinator.confirm()
            withTimeout(10_000) { coordinator.state.first { it.phase == BackupPhase.IMPORTED || it.phase == BackupPhase.ERROR } }
            assertEquals(BackupPhase.IMPORTED, coordinator.state.value.phase)
            assertEquals(incoming.attempts.single(), repo.observeAttempt(attempt.id).first())
            assertEquals(settings, store.settings.first())
        } finally { scope.cancel(); store.replace(PlayerSettings()); file.delete() }
    }

    @Test fun failedSettingsWriteKeepsTheRecoveryMarkerAndBlocksAnotherImportUntilRetry() = runBlocking {
        repo.refreshDay(); repo.startOrResume(level)
        val file = File(context.cacheDir, "settings-failure-backup.json").apply { writeBytes(BackupFile.bytes(repo.exportProgress(settings))) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var fail = true
        val store = object : BackupSettingsStore {
            override val settings = MutableStateFlow(PlayerSettings())
            override suspend fun replace(value: PlayerSettings) {
                if (fail) throw java.io.IOException("Simulated preferences write failure")
                settings.value = value
            }
        }
        val coordinator = ManualBackup(context, repo, store, scope)
        try {
            coordinator.select(Uri.fromFile(file))!!.join(); coordinator.confirm()
            withTimeout(10_000) { coordinator.state.first { it.phase == BackupPhase.FINISH_REQUIRED || it.phase == BackupPhase.ERROR } }
            assertEquals(BackupPhase.FINISH_REQUIRED, coordinator.state.value.phase)
            assertNotNull(repo.pendingBackupSettings()); assertTrue(repo.hasRecoverySnapshot())
            assertNull(coordinator.select(Uri.fromFile(file)))
            assertTrue(runCatching { coordinator.finishPendingSettings() }.isFailure)
            assertNotNull(repo.pendingBackupSettings())
            fail = false; coordinator.finishPendingSettings()
            assertNull(repo.pendingBackupSettings()); assertEquals(settings, store.settings.value)
            assertEquals(BackupPhase.IMPORTED, coordinator.state.value.phase)
        } finally { scope.cancel(); file.delete() }
    }

    @Test fun olderAndReorderedPendingSettingsFinishAfterReopenWithoutChangingImportedProgress() = runBlocking {
        repo.refreshDay(); val attempt = repo.startOrResume(level)
        repo.move(level, attempt.id, level.reference.first(), 300)
        val file = repo.exportProgress(settings)
        repo.importProgress(file, PlayerSettings())
        val recovery = repo.recoverySnapshot()
        val canonical = ParlorJson.parseToJsonElement(ParlorJson.encodeToString(settings)).jsonObject
        val encodings = listOf(
            JsonObject(canonical - "appearance").toString(),
            " \n" + JsonObject(canonical.entries.reversed().associate { it.key to it.value }).toString() + "\n ",
            """{ "onboarded": true, "sfxVolume": 0.4, "showTimer": true }""",
        )
        val store = SettingsRepository(context)
        val original = store.settings.first()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            for (json in encodings) {
                assertEquals(1, db.progress().restoreBackupSettings(json, file.snapshotId))
                db.close(); pro = false; open()
                store.replace(PlayerSettings())
                val coordinator = ManualBackup(context, repo, store, scope)
                assertEquals(settings, repo.pendingBackupSettings())
                coordinator.finishPendingSettings()
                assertEquals(settings, store.settings.first()); assertNull(db.progress().pendingSettings())
                assertEquals(file.attempts.single(), repo.observeAttempt(attempt.id).first())
                assertEquals(recovery, repo.recoverySnapshot()); assertFalse(repo.hasPro())
                coordinator.finishPendingSettings()
                assertEquals(1, db.progress().allAttempts().size)
            }
        } finally { scope.cancel(); store.replace(original) }
    }

    @Test fun changedSettingsBetweenApplicationAndAcknowledgementKeepTheNewPendingMarker() = runBlocking {
        repo.refreshDay(); repo.startOrResume(level)
        val file = repo.exportProgress(settings); repo.importProgress(file, PlayerSettings())
        val changed = settings.copy(reduceMotion = true, sfxVolume = .2f)
        val changedJson = ParlorJson.encodeToString(changed)
        var replaceMarker = true
        val store = object : BackupSettingsStore {
            override val settings = MutableStateFlow(PlayerSettings())
            override suspend fun replace(value: PlayerSettings) {
                settings.value = value
                if (replaceMarker) db.progress().restoreBackupSettings(changedJson, "changed-marker")
            }
        }
        val health = StorageHealth()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val coordinator = ManualBackup(context, repo, store, scope, health)
            assertTrue(runCatching { coordinator.finishPendingSettings() }.isFailure)
            assertEquals(settings, store.settings.value)
            assertEquals(changedJson, db.progress().pendingSettings())
            assertEquals(BackupPhase.FINISH_REQUIRED, coordinator.state.value.phase)
            assertEquals(StorageArea.PROGRESS, health.state.value.issue?.area)
            replaceMarker = false; health.retry(); coordinator.finishPendingSettings()
            assertEquals(changed, store.settings.value); assertNull(db.progress().pendingSettings())
            assertEquals(BackupPhase.IMPORTED, coordinator.state.value.phase)
        } finally { scope.cancel() }
    }

    @Test fun malformedAndUnknownPendingSettingsStayIntactAndCannotClearUsingOldPreferences() = runBlocking {
        repo.refreshDay(); repo.startOrResume(level)
        val file = repo.exportProgress(settings); repo.importProgress(file, PlayerSettings())
        val recovery = repo.recoverySnapshot()
        val unchanged = PlayerSettings(haptics = false)
        val store = object : BackupSettingsStore {
            override val settings = MutableStateFlow(unchanged)
            override suspend fun replace(value: PlayerSettings) { fail("Invalid settings must not reach DataStore") }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            for (json in listOf("{", """{"appearance":"FUTURE"}""", """{"futureField":true}""")) {
                db.progress().restoreBackupSettings(json, file.snapshotId)
                assertTrue(runCatching { repo.finishBackupSettings(settings) }.isFailure)
                val coordinator = ManualBackup(context, repo, store, scope)
                assertTrue(runCatching { coordinator.finishPendingSettings() }.isFailure)
                assertEquals(json, db.progress().pendingSettings()); assertEquals(unchanged, store.settings.value)
                assertEquals(recovery, repo.recoverySnapshot())
            }
        } finally { scope.cancel() }
    }

    @Test fun aFailedPendingMarkerCommitCanRetryWithoutLosingTheStoredEncoding() = runBlocking {
        repo.refreshDay(); repo.startOrResume(level)
        val file = repo.exportProgress(settings); repo.importProgress(file, PlayerSettings())
        val legacy = """{ "showTimer": true, "onboarded": true, "sfxVolume": 0.4 }"""
        db.progress().restoreBackupSettings(legacy, file.snapshotId)
        withContext(Dispatchers.IO) {
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_finish BEFORE UPDATE OF pendingSettingsJson ON backup_state WHEN NEW.pendingSettingsJson IS NULL BEGIN SELECT RAISE(ABORT, 'injected marker failure'); END")
        }
        assertTrue(runCatching { repo.finishBackupSettings(settings) }.isFailure)
        assertEquals(legacy, db.progress().pendingSettings())
        withContext(Dispatchers.IO) { db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_finish") }
        repo.finishBackupSettings(settings)
        assertNull(db.progress().pendingSettings()); assertTrue(repo.hasRecoverySnapshot())
        assertTrue(runCatching { repo.finishBackupSettings(settings) }.isFailure)
        assertNull(db.progress().pendingSettings())
    }
}
