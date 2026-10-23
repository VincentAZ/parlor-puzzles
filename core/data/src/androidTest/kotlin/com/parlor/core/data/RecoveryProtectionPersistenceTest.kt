package com.parlor.core.data

import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.io.IOException

class RecoveryProtectionPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "protection-test.db"
    private val day = "2026-09-28"
    private val directory = File(context.cacheDir, "protection-test")
    private val path = File(directory, "protection.json")
    private lateinit var db: ParlorDatabase
    private lateinit var repo: ProgressRepository
    private lateinit var disk: RecoveryProtectionFile
    private lateinit var health: StorageHealth
    private var failPreservation = false
    private var pro = false
    private val level = LevelDefinition("protection-traffic", puzzle = Puzzle.TRAFFIC_JAM, tier = Tier.EASY, order = 1,
        initial = TrafficJamBoard(pieces = listOf(TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL))),
        reference = listOf(Translate("A", 6)), par = 1, parKind = ParKind.PROVEN_OPTIMAL, targetTimeMs = 60_000,
        provenance = Provenance("Parlor", "recovery protection fixture", 0, "original"))

    private fun open() {
        db = ParlorDatabase.open(context, name)
        health = StorageHealth()
        val store = object : RecoveryProtectionStore {
            override suspend fun read() = disk.read()
            override suspend fun preserve(value: RecoveryProtection, today: String) {
                if (failPreservation) throw IOException("Injected protection failure")
                disk.preserve(value, today)
            }
        }
        repo = ProgressRepository(db, today = { day }, wallTime = { 1_000 }, isPro = { pro },
            storageHealth = health, protection = store)
        repo.configure(Catalog(levels = listOf(level)), DailySchedule(listOf(ScheduleEpoch(1, day, listOf(LevelRef(level.id, 1))))))
    }
    @Before fun setup() {
        context.deleteDatabase(name); directory.deleteRecursively(); directory.mkdirs()
        disk = RecoveryProtectionFile(path); open()
    }
    @After fun teardown() { db.close(); context.deleteDatabase(name); directory.deleteRecursively() }

    @Test fun committedHintsCompletionsAndAchievementsSurviveAnUnreadableMainDatabase() = runBlocking {
        repo.refreshDay()
        val solved = repo.startOrResume(level)
        repo.move(level, solved.id, level.reference.single(), 1_000)
        val next = repo.startOrResume(level)
        repo.grantReward("earned-hints", Reward.Hints)
        repeat(4) { repo.useHint(level, next.id, 0) }
        val row = repo.player.first()
        val protection = disk.read()
        assertEquals(row.ledger, protection.ledger)
        assertEquals(mapOf(solved.id to day), protection.completionDates)
        assertEquals(row.profile.achievements, protection.achievements); assertTrue(protection.achievements.isNotEmpty())
        assertEquals(1L, protection.ledger.earnedHintsSpent); assertTrue(next.id in protection.ledger.hintedAttemptIds)
        db.close()
        val original = ByteArray(4096) { 42 }
        context.getDatabasePath(name).writeBytes(original)
        assertEquals(protection, RecoveryProtectionFile(path).read())
        assertArrayEquals(original, context.getDatabasePath(name).readBytes())
    }

    @Test fun aFailedSafeguardWriteRollsBackHintAndCompletionTransactions() = runBlocking {
        repo.refreshDay()
        val attempt = repo.startOrResume(level)
        val before = repo.player.first()
        failPreservation = true
        assertTrue(runCatching { repo.useHint(level, attempt.id, 500) }.isFailure)
        assertEquals(StorageIssue(StorageArea.PROGRESS, StorageProblem.UNAVAILABLE), health.state.value.issue)
        // Inspect Room directly because presentation is intentionally stopped by the storage issue.
        assertEquals(attempt, ParlorJson.decodeFromString<Attempt>(db.progress().attempt(attempt.id)!!.json))
        assertEquals(before.ledger, ParlorJson.decodeFromString<RewardLedger>(db.progress().player()!!.ledgerJson))
        assertTrue(runCatching { repo.move(level, attempt.id, level.reference.single(), 500) }.isFailure)
        assertTrue(db.progress().allCompletions().isEmpty()); assertTrue(db.progress().allBests().isEmpty())
        assertEquals(attempt, ParlorJson.decodeFromString<Attempt>(db.progress().attempt(attempt.id)!!.json))
        failPreservation = false; health.retry()
        assertTrue(repo.move(level, attempt.id, level.reference.single(), 500)!!.completed)
        assertEquals(setOf(attempt.id), disk.read().completionDates.keys)
    }

    @Test fun aLaterSqlRollbackCannotRemoveAlreadyWrittenRecoverySafeguards() = runBlocking {
        repo.refreshDay(); val attempt = repo.startOrResume(level)
        withContext(Dispatchers.IO) {
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_complete BEFORE UPDATE ON attempts WHEN NEW.completed = 1 BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        }
        assertTrue(runCatching { repo.move(level, attempt.id, level.reference.single(), 0) }.isFailure)
        assertTrue(db.progress().allCompletions().isEmpty()); assertTrue(db.progress().allBests().isEmpty())
        assertFalse(ParlorJson.decodeFromString<Attempt>(db.progress().attempt(attempt.id)!!.json).completed)
        val protection = RecoveryProtectionFile(path).read()
        assertEquals(mapOf(attempt.id to day), protection.completionDates)
        assertTrue(protection.achievements.isNotEmpty())
        repo.refreshDay()
        assertEquals(protection.completionDates, disk.read().completionDates)
    }

    @Test fun anOlderDatabaseAndRepeatedImportsCannotRefillTheProtectedHintBalance() = runBlocking {
        repo.refreshDay(); val attempt = repo.startOrResume(level)
        repo.grantReward("one", Reward.Hints); repo.grantReward("two", Reward.Hints)
        pro = true; val backup = repo.exportProgress(PlayerSettings()); pro = false
        repeat(6) { repo.useHint(level, attempt.id, 0) }
        assertEquals(3L, disk.read().ledger.earnedHintsSpent)
        // Recreate older main storage while retaining the independent, later safeguards.
        db.close(); context.deleteDatabase(name); open(); repo.refreshDay(); pro = true
        repeat(3) {
            repo.importProgress(backup, PlayerSettings())
            repo.finishBackupSettings(requireNotNull(repo.pendingBackupSettings()))
            val restored = repo.player.first().ledger
            assertEquals(0, restored.dailyHints); assertEquals(1, restored.earnedHints); assertEquals(3L, restored.earnedHintsSpent)
            assertTrue(repo.observeAttempt(attempt.id).first()!!.hintsUsed)
            assertFalse(repo.grantReward("one", Reward.Hints))
        }
    }

    @Test fun missingMalformedOrFutureSafeguardsCannotSilentlyBecomeAnEmptyLedger() = runBlocking {
        assertTrue(runCatching { disk.read() }.isFailure)
        repo.refreshDay(); val value = disk.read()
        for (bytes in listOf("{".toByteArray(), "{}".toByteArray(), byteArrayOf(0xc0.toByte(), 0xaf.toByte()),
            ParlorJson.encodeToString(value.copy(version = 2)).toByteArray())) {
            path.writeBytes(bytes)
            val reopened = RecoveryProtectionFile(path)
            assertTrue(runCatching { reopened.read() }.isFailure)
            assertTrue(runCatching { reopened.preserve(value, day) }.isFailure)
            assertArrayEquals(bytes, path.readBytes())
        }
        assertTrue(runCatching { RecoveryProtectionFile(path).read() }.exceptionOrNull() is NewerStorageVersion)
    }

    @Test fun aBlockedDirectoryFailsBeforeProgressCommitsAndCanBeRetriedAfterRepair() = runBlocking {
        directory.deleteRecursively(); directory.writeText("cannot create protection here")
        assertTrue(runCatching { repo.refreshDay() }.isFailure)
        assertNull(db.progress().player())
        directory.delete(); directory.mkdirs(); health.retry(); repo.refreshDay()
        val value = disk.read(); val bytes = path.readBytes()
        assertTrue(path.setLastModified(1_000))
        repo.refreshDay()
        assertEquals(1_000L, path.lastModified()); assertArrayEquals(bytes, path.readBytes())
        assertEquals(value, RecoveryProtectionFile(path).read())
    }

    @Test fun bootstrapRetainsLegacyHintFlagsAndDatesFromIndependentCompletionReceipts() = runBlocking {
        val attempt = repo.startOrResume(level)
        val row = db.progress().attempt(attempt.id)!!
        db.progress().saveAttempt(row.copy(json = ParlorJson.encodeToString(attempt.copy(hintsUsed = true))))
        db.progress().recordCompletion(CompletionRow("legacy-receipt", "2026-09-27"))
        repo.refreshDay()
        val protection = RecoveryProtectionFile(path).read()
        assertTrue(attempt.id in protection.ledger.hintedAttemptIds)
        assertEquals("2026-09-27", protection.completionDates["legacy-receipt"])
        assertTrue("2026-09-27" in protection.ledger.creditedDates)
    }

    @Test fun importedLegacyHintFlagsAreProtectedBeforeAnotherStartupOrMove() = runBlocking {
        repo.refreshDay(); val attempt = repo.startOrResume(level)
        pro = true
        val original = repo.exportProgress(PlayerSettings())
        val legacy = original.copy(attempts = original.attempts.map { it.copy(hintsUsed = true) })
        assertFalse(attempt.id in legacy.ledger.hintedAttemptIds)
        repo.importProgress(legacy, PlayerSettings())
        repo.finishBackupSettings(requireNotNull(repo.pendingBackupSettings()))
        assertTrue(attempt.id in RecoveryProtectionFile(path).read().ledger.hintedAttemptIds)
        repo.importProgress(original, PlayerSettings())
        repo.finishBackupSettings(requireNotNull(repo.pendingBackupSettings()))
        assertTrue(repo.observeAttempt(attempt.id).first()!!.hintsUsed)
    }

    @Test fun aFailedFileCommitInvalidatesCachedProofBeforeRetry() = runBlocking {
        repo.refreshDay()
        val original = path.readBytes()
        // Force AtomicFile's commit/read-back to fail while the previous value is cached.
        path.delete(); path.mkdir(); File(path, "occupied").writeText("cannot replace this directory")
        assertTrue(runCatching { repo.grantReward("not-committed", Reward.Hints) }.isFailure)
        assertFalse("not-committed" in ParlorJson.decodeFromString<RewardLedger>(db.progress().player()!!.ledgerJson).claims)
        path.deleteRecursively(); File(path.path + ".bak").deleteRecursively(); File(path.path + ".new").deleteRecursively()
        path.writeText("{truncated")
        health.retry()
        assertTrue(runCatching { repo.refreshDay() }.isFailure)
        assertEquals("{truncated", path.readText())
        assertEquals(StorageProblem.DAMAGED, health.state.value.issue?.problem)
        // A verified repair can then be retried without restarting the repository.
        path.writeBytes(original); health.retry(); repo.refreshDay()
        assertNull(health.state.value.issue)
        assertFalse("not-committed" in RecoveryProtectionFile(path).read().ledger.claims)
    }
}
