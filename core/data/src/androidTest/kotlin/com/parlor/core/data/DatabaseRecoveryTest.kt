package com.parlor.core.data

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.channels.Channel
import org.junit.*
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class DatabaseRecoveryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = listOf("recovery-original-test.db", "recovery-candidate-test.db")
    private val directory = File(context.cacheDir, "database-recovery-test")
    private val selection = File(directory, "active.json")
    private val safeguards = File(directory, "protection.json")
    private val preview = File(directory, "preview.json")
    private val source = File(directory, "source.json")
    private val day = "2026-09-28"
    private var pro = true
    private var recoverable = true
    private var failSwitch = false
    private var failAfterSwitch = false
    private var failProtection = false
    private var restored = 0
    private val damaged = ByteArray(4096) { 42 }
    private lateinit var slots: DatabaseSlots
    private lateinit var owner: ProgressDatabase
    private lateinit var repo: ProgressRepository
    private lateinit var protection: RecoveryProtectionStore
    private lateinit var scope: CoroutineScope
    private val mutex = Mutex()
    private val level = LevelDefinition("recovery-tiles", puzzle = Puzzle.SLIDING_TILES, tier = Tier.EASY, order = 1,
        initial = TilesBoard(3, listOf(1, 2, 3, 4, 5, 6, 0, 7, 8)), reference = listOf(SlideTile(7), SlideTile(8)),
        par = 2, parKind = ParKind.PROVEN_OPTIMAL, targetTimeMs = 60_000, provenance = Provenance("Parlor", "recovery fixture", 0, "original"))
    private val catalog = Catalog(levels = listOf(level))
    private val schedule = DailySchedule(listOf(ScheduleEpoch(1, day, listOf(LevelRef(level.id, 1)))))
    private val preferences = PlayerSettings(showTimer = true, sfxVolume = .4f, onboarded = false, notifications = true)

    private fun repository(database: ProgressDatabase) = ProgressRepository(database, today = { day }, isPro = { pro }, protection = protection)
        .also { it.configure(catalog, schedule) }
    private fun recovery(before: suspend () -> Unit = {}) = DamagedDatabaseRecovery(context, owner, slots, protection, scope, mutex,
        loadContent = { catalog to schedule }, isPro = { pro }, isRecoverable = { recoverable }, beforeSwitch = before,
        onRestored = { restored++ }, today = { day }, previewFile = preview)

    @Before fun setup() {
        names.forEach(context::deleteDatabase); directory.deleteRecursively(); directory.mkdirs()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val disk = RecoveryProtectionFile(safeguards)
        protection = object : RecoveryProtectionStore {
            override suspend fun read() = disk.read()
            override suspend fun preserve(value: RecoveryProtection, today: String) {
                if (failProtection) throw IOException("Injected safeguard write failure")
                disk.preserve(value, today)
            }
        }
        slots = DatabaseSlots(context, names, selection) { file, bytes ->
            if (failSwitch) throw IOException("Injected selector write failure")
            DurableFiles.write(file, bytes)
            if (failAfterSwitch) throw IOException("Injected directory sync failure after rename")
        }
        owner = ProgressDatabase(slots::openActive); repo = repository(owner)
    }
    @After fun cleanup() = runBlocking {
        scope.cancel(); owner.replace { }; names.forEach(context::deleteDatabase); directory.deleteRecursively(); Unit
    }
    private suspend fun seed(photo: Boolean = false): ProgressEnvelope {
        repo.refreshDay()
        val first = repo.startOrResume(level)
        level.reference.forEach { repo.move(level, first.id, it, 500) }
        val active = repo.startOrResume(level)
        repo.move(level, active.id, level.reference.first(), 120); repo.undo(level, active.id, 30)
        if (photo) {
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            val bytes = try { bitmap.eraseColor(Color.BLUE); ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }
                finally { bitmap.recycle() }
            val image = bytes.inputStream().use { PhotoCodec.normalize(it, context.cacheDir) }
            repo.setPhoto(level, active.id, image)
        }
        return repo.exportProgress(preferences)
    }
    private suspend fun corrupt() {
        val current = slots.activeSlot()
        owner.replace { context.getDatabasePath(names[current]).writeBytes(damaged) }
    }
    private suspend fun select(recovery: DamagedDatabaseRecovery, file: ProgressEnvelope) {
        source.writeBytes(BackupFile.bytes(file))
        requireNotNull(recovery.select(Uri.fromFile(source))).join()
        assertEquals(DatabaseRecoveryPhase.PREVIEW, recovery.state.value.phase)
    }
    private suspend fun finish() { repo.finishBackupSettings(requireNotNull(repo.pendingBackupSettings())) }

    @Test fun confirmedRestoreKeepsDamagedBytesAndReopensMovesPhotosBestsAndSettingsMarker() = runBlocking {
        val file = seed(photo = true); val guard = protection.read(); corrupt()
        // Keep the same failed Room owner that the real recovery screen receives after startup.
        assertTrue(runCatching { repo.refreshDay() }.isFailure)
        val recovery = recovery(); select(recovery, file)
        assertFalse(context.getDatabasePath(names[1]).exists()); assertFalse(selection.exists())
        assertArrayEquals(damaged, context.getDatabasePath(names[0]).readBytes())
        requireNotNull(recovery.confirm()).join()
        assertEquals(DatabaseRecoveryPhase.READY, recovery.state.value.phase); assertEquals(1, restored)
        assertEquals(1, slots.activeSlot()); assertArrayEquals(damaged, context.getDatabasePath(names[0]).readBytes())
        assertEquals(file.bests, repo.bests.first())
        for (attempt in file.attempts) assertEquals(attempt, repo.observeAttempt(attempt.id).first())
        assertArrayEquals(PhotoCodec.bytes(file.photos.single()), repo.photo(file.photos.single().id))
        assertEquals(preferences.copy(onboarded = true, notifications = false), repo.pendingBackupSettings())
        assertEquals(guard.completionDates, protection.read().completionDates); assertNull(repo.recoverySnapshot())
        owner.replace { }; owner = ProgressDatabase(DatabaseSlots(context, names, selection)::openActive); repo = repository(owner)
        assertNotNull(repo.pendingBackupSettings()); finish(); pro = false
        assertEquals(file.bests, repo.bests.first()); assertFalse(repo.hasPro())
    }

    @Test fun repeatedOlderFilesCannotRefillSpentHintsOrReopenConsumedDailyRetries() = runBlocking {
        pro = false; repo.refreshDay()
        val daily = repo.startDaily().attempt
        level.reference.forEach { repo.move(level, daily.id, it, 0) }
        repo.grantReward("daily-retry", Reward.DailyRetry(day))
        val retry = repo.startDaily().attempt
        repo.grantReward("hints", Reward.Hints)
        pro = true; val file = repo.exportProgress(preferences); pro = false
        repeat(4) { repo.useHint(level, retry.id, 0) }
        level.reference.forEach { repo.move(level, retry.id, it, 0) }
        pro = true
        repeat(2) {
            corrupt(); val recovery = recovery(); select(recovery, file); recovery.confirm()!!.join()
            assertEquals(DatabaseRecoveryPhase.READY, recovery.state.value.phase)
            finish(); pro = false
            val ledger = repo.player.first().ledger
            assertEquals(0, ledger.dailyHints); assertEquals(1, ledger.earnedHints); assertEquals(1L, ledger.earnedHintsSpent)
            assertEquals(1, ledger.dailyClaims.getValue(day).consumedRetries)
            assertEquals(DailyEligibility.PRACTICE, repo.startDaily().attempt.dailyEligibility)
            assertFalse(repo.grantReward("hints", Reward.Hints)); assertNull(repo.observeAttempt(retry.id).first())
            pro = true
        }
    }

    @Test fun invalidAndFutureFilesNeverTouchEitherDatabaseOrSelector() = runBlocking {
        val file = seed(); corrupt()
        val previous = context.getDatabasePath(names[1]).apply { writeText("previous copy") }
        val recovery = recovery()
        for (bytes in listOf("{".toByteArray(), BackupFile.bytes(file.copy(schemaVersion = 100)),
            BackupFile.bytes(file.copy(attempts = file.attempts.map { it.copy(levelId = "missing") })))) {
            source.writeBytes(bytes); recovery.select(Uri.fromFile(source))!!.join()
            assertEquals(DatabaseRecoveryProblem.FILE, recovery.state.value.problem)
            assertArrayEquals(damaged, context.getDatabasePath(names[0]).readBytes())
            assertEquals("previous copy", previous.readText()); assertFalse(selection.exists())
        }
    }

    @Test fun selectionAndConfirmationRefuseMissingMalformedAndFutureSafeguards() = runBlocking {
        val file = seed(); val good = safeguards.readBytes(); corrupt()
        for (bytes in listOf(null, "{".toByteArray(), ParlorJson.encodeToString(protection.read().copy(version = 2)).toByteArray())) {
            val recovery = recovery(); select(recovery, file)
            if (bytes == null) safeguards.delete() else safeguards.writeBytes(bytes)
            recovery.confirm()!!.join()
            assertEquals(DatabaseRecoveryProblem.SAFEGUARDS, recovery.state.value.problem)
            assertFalse(context.getDatabasePath(names[1]).exists()); assertFalse(selection.exists())
            recovery.select(Uri.fromFile(source))!!.join()
            assertEquals(DatabaseRecoveryProblem.SAFEGUARDS, recovery.state.value.problem)
            safeguards.writeBytes(good)
        }
    }

    @Test fun ownershipIsCheckedOnSelectionConfirmationAndImmediatelyBeforeSwitch() = runBlocking {
        val file = seed(); corrupt(); val recovery = recovery()
        source.writeBytes(BackupFile.bytes(file)); pro = false
        recovery.select(Uri.fromFile(source))!!.join()
        assertEquals(DatabaseRecoveryProblem.PRO_REQUIRED, recovery.state.value.problem)
        pro = true; select(recovery, file); pro = false; recovery.confirm()!!.join()
        assertEquals(DatabaseRecoveryProblem.PRO_REQUIRED, recovery.state.value.problem)
        assertFalse(context.getDatabasePath(names[1]).exists())
        pro = true; val late = recovery { pro = false }; select(late, file); late.confirm()!!.join()
        assertEquals(DatabaseRecoveryProblem.PRO_REQUIRED, late.state.value.problem)
        assertFalse(selection.exists()); assertArrayEquals(damaged, context.getDatabasePath(names[0]).readBytes()); assertEquals(0, restored)
    }

    @Test fun preparationAndSelectorFailuresKeepOriginalSelectedAndAllowExplicitRetry() = runBlocking {
        val file = seed(); corrupt(); val recovery = recovery()
        select(recovery, file); failProtection = true; recovery.confirm()!!.join()
        assertEquals(DatabaseRecoveryPhase.ERROR, recovery.state.value.phase); assertEquals(0, slots.activeSlot())
        failProtection = false; select(recovery, file); failSwitch = true; recovery.confirm()!!.join()
        assertEquals(DatabaseRecoveryPhase.ERROR, recovery.state.value.phase); assertEquals(0, slots.activeSlot())
        assertArrayEquals(damaged, context.getDatabasePath(names[0]).readBytes())
        // A new process still sees the old selection; the abandoned complete candidate is not auto-imported.
        assertEquals(0, DatabaseSlots(context, names, selection).activeSlot())
        failSwitch = false; select(recovery, file); recovery.confirm()!!.join()
        assertEquals(1, slots.activeSlot()); assertEquals(1, restored)
    }

    @Test fun aFailureAfterSelectorRenameResumesCommittedDatabaseInsteadOfRepeatingTheImport() = runBlocking {
        val file = seed(); corrupt(); val recovery = recovery(); select(recovery, file)
        failAfterSwitch = true; recovery.confirm()!!.join()
        assertEquals(DatabaseRecoveryPhase.READY, recovery.state.value.phase); assertEquals(1, restored)
        assertEquals(1, DatabaseSlots(context, names, selection).activeSlot())
        assertEquals(file.bests, repo.bests.first()); assertNotNull(repo.pendingBackupSettings())
        assertNull(recovery.confirm())
    }

    @Test fun missingOrInvalidSelectorNeverCreatesAnEmptyReplacement() = runBlocking {
        seed(); owner.replace { }
        for (value in listOf("{", "{\"version\":2,\"slot\":0}", "{\"version\":1,\"slot\":8}")) {
            selection.writeText(value)
            assertTrue(runCatching { slots.openActive() }.isFailure)
        }
        selection.writeText("{\"version\":1,\"slot\":1}")
        assertFalse(context.getDatabasePath(names[1]).exists())
        assertTrue(runCatching { slots.openActive() }.isFailure)
        selection.delete(); context.deleteDatabase(names[0]); context.getDatabasePath(names[1]).writeText("candidate")
        assertTrue(runCatching { slots.openActive() }.isFailure)
        assertFalse(context.getDatabasePath(names[0]).exists())
    }

    @Test fun restartAndCancelDiscardPreviewWithoutAuthorizingReplacement() = runBlocking {
        val file = seed(); corrupt(); val recovery = recovery(); select(recovery, file)
        recovery.discard()!!.join(); assertNull(recovery.confirm()); assertFalse(preview.exists())
        select(recovery, file)
        val restarted = recovery(); assertNull(restarted.confirm()); assertFalse(preview.exists())
        assertFalse(selection.exists()); assertFalse(context.getDatabasePath(names[1]).exists())
    }

    @Test fun photoDeletionRemovesPreviousDatabaseAndBothPreviewsWhileKeepingCurrentScores() = runBlocking {
        val file = seed(photo = true); corrupt()
        val recovery = recovery(); select(recovery, file); recovery.confirm()!!.join(); finish()
        val settings = object : BackupSettingsStore {
            override val settings = MutableStateFlow(preferences)
            override suspend fun replace(value: PlayerSettings) { settings.value = value }
        }
        val backups = ManualBackup(context, repo, settings, scope, mutex = mutex,
            deleteOtherPhotoCopies = { recovery.deletePhotoCopies() })
        select(recovery, file)
        backups.select(Uri.fromFile(source))!!.join()
        backups.deletePhotos()
        assertFalse(context.getDatabasePath(names[0]).exists()); assertFalse(preview.exists())
        assertEquals(DatabaseRecoveryPhase.READY, recovery.state.value.phase); assertNull(recovery.confirm())
        assertNull(backups.state.value.preview); assertEquals(file.bests, repo.bests.first())
        assertNull(repo.photo(file.photos.single().id))
        assertTrue(repo.history.first().all { it.photoId == null })
        assertEquals(1, slots.activeSlot())
    }

    @Test fun databaseReplacementDrainsTransactionsSupportsNestedCallsAndRejectsRecursiveReplacement() = runBlocking {
        seed()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val transaction = async(Dispatchers.IO) { owner.transaction {
            owner.transaction { assertTrue(runCatching { owner.replace { } }.isFailure) }
            entered.complete(Unit); release.await(); repo.refreshDay()
        } }
        entered.await(); var switched = false
        val replacement = async(Dispatchers.IO) { owner.replace { switched = true } }
        delay(100); assertFalse(switched); release.complete(Unit)
        withTimeout(5_000) { transaction.await(); replacement.await() }
        assertTrue(switched); assertTrue(repo.bests.first().isNotEmpty())
    }

    @Test fun anUnwritablePreviewOrChangedRecoveryIssueCannotReplaceStorage() = runBlocking {
        val file = seed(); corrupt(); val recovery = recovery()
        preview.mkdir(); File(preview, "retained").writeText("blocks atomic rename")
        source.writeBytes(BackupFile.bytes(file)); recovery.select(Uri.fromFile(source))!!.join()
        assertEquals(DatabaseRecoveryPhase.ERROR, recovery.state.value.phase); assertFalse(selection.exists())
        preview.deleteRecursively(); select(recovery, file); recoverable = false; recovery.confirm()!!.join()
        assertEquals(DatabaseRecoveryPhase.ERROR, recovery.state.value.phase)
        assertFalse(context.getDatabasePath(names[1]).exists()); assertFalse(selection.exists())
    }

    @Test fun existingObserversFollowTheSelectedFileWithoutAnInventedEmptySave() = runBlocking {
        val file = seed()
        val observed = Channel<List<LevelBest>>(Channel.UNLIMITED)
        val observer = launch(Dispatchers.IO) { repo.bests.collect { observed.send(it) } }
        try {
            assertEquals(file.bests, withTimeout(5_000) { observed.receive() })
            owner.replace {
                val candidate = slots.openCandidate(1)
                try {
                    val prepared = ProgressRepository(candidate, today = { day }, isPro = { true }, protection = protection)
                    prepared.configure(catalog, schedule)
                    prepared.restoreIntoEmptyDatabase(file, protection.read())
                } finally { candidate.close() }
                slots.commit(1)
            }
            // Services retain their original repository and owner after a replacement.
            assertNotNull(repo.pendingBackupSettings())
            assertEquals(file.bests, withTimeout(5_000) { observed.receive() })
        } finally { observer.cancelAndJoin(); observed.close() }
    }

    @Test fun aVerificationFailureCannotDeleteAnAlreadyCommittedAtomicFileOnOlderAndroid() {
        val path = File(directory, "atomic.json").apply { writeText("previous") }
        val file = object : AtomicFile(path) {
            override fun openRead(): java.io.FileInputStream = throw IOException("Injected read-back failure")
        }
        assertTrue(runCatching { DurableFiles.write(file, "committed".toByteArray()) }.isFailure)
        assertEquals("committed", path.readText())
    }

    @Test fun cancellingRevokesConfirmationEvenWhenPreviewDeletionFails() = runBlocking {
        val file = seed(); corrupt(); val recovery = recovery(); select(recovery, file)
        preview.delete(); preview.mkdir(); File(preview, "cannot-remove").writeText("injected deletion failure")
        val cancellation = recovery.discard()!!; cancellation.join()
        assertFalse(cancellation.isCancelled)
        assertEquals(DatabaseRecoveryProblem.STORAGE, recovery.state.value.problem)
        assertNull(recovery.state.value.preview); assertNull(recovery.confirm())
        assertFalse(selection.exists()); assertArrayEquals(damaged, context.getDatabasePath(names[0]).readBytes())
        preview.deleteRecursively(); recovery.discard()!!.join()
        assertEquals(DatabaseRecoveryPhase.READY, recovery.state.value.phase)
    }
}
