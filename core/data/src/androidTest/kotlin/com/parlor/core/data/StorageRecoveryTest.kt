package com.parlor.core.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class StorageRecoveryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun anUnreadableDatabaseIsNotReportedAsACommittedImport() = runBlocking {
        val name = "storage-unreadable-backup.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        file.writeBytes(ByteArray(4096) { 42 })
        val db = ParlorDatabase.open(context, name)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val health = StorageHealth()
        val preferences = object : BackupSettingsStore {
            override val settings = flowOf(PlayerSettings())
            override suspend fun replace(value: PlayerSettings) = error("Settings must not be written")
        }
        try {
            val backup = ManualBackup(context, ProgressRepository(db), preferences, scope, health)
            assertTrue(runCatching { backup.finishPendingSettings() }.isFailure)
            assertEquals(BackupPhase.READY, backup.state.value.phase)
            assertEquals(StorageIssue(StorageArea.PROGRESS, StorageProblem.DAMAGED), health.state.value.issue)
            assertArrayEquals(ByteArray(4096) { 42 }, file.readBytes())
        } finally { scope.cancel(); db.close(); context.deleteDatabase(name) }
    }

    @Test fun corruptDatabaseSurvivesRepeatedOpenAttemptsWithoutReplacement() = runBlocking {
        val name = "storage-corrupt.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val original = ByteArray(4096) { ((it * 31 + 17) % 256).toByte() }
        file.writeBytes(original)
        val db = ParlorDatabase.open(context, name)
        val health = StorageHealth()
        try {
            val repo = ProgressRepository(db, storageHealth = health)
            repeat(2) {
                assertTrue(withTimeout(10_000) { repo.bests.toList() }.isEmpty())
                assertEquals(StorageIssue(StorageArea.PROGRESS, StorageProblem.DAMAGED), health.state.value.issue)
                assertArrayEquals(original, file.readBytes())
                health.retry()
            }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun newerDatabaseKeepsVersionAndRowsWhenThisAppCannotOpenIt() = runBlocking {
        val name = "storage-future.db"
        context.deleteDatabase(name)
        ParlorDatabase.open(context, name).let { db ->
            db.progress().recordCompletion(CompletionRow("future-receipt", "2026-09-28")); db.close()
        }
        withContext(Dispatchers.IO) {
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = 5 }
        }
        val db = ParlorDatabase.open(context, name)
        val health = StorageHealth()
        try {
            val repo = ProgressRepository(db, storageHealth = health)
            repeat(2) {
                assertTrue(withTimeout(10_000) { repo.completionCount.toList() }.isEmpty())
                assertEquals(StorageIssue(StorageArea.PROGRESS, StorageProblem.NEWER_VERSION), health.state.value.issue)
                health.retry()
            }
            withContext(Dispatchers.IO) {
                SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
                    assertEquals(5, raw.version)
                    raw.rawQuery("SELECT attemptId FROM completions", null).use {
                        assertTrue(it.moveToFirst()); assertEquals("future-receipt", it.getString(0)); assertFalse(it.moveToNext())
                    }
                }
            }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun malformedProgressRowStopsObservationAndCanBeRetriedAfterRepair() = runBlocking {
        val name = "storage-row.db"
        context.deleteDatabase(name)
        val db = ParlorDatabase.open(context, name)
        val health = StorageHealth()
        try {
            db.progress().saveBest(BestRow("saved-level", "{truncated"))
            val repo = ProgressRepository(db, storageHealth = health)
            assertTrue(repo.bests.toList().isEmpty())
            assertEquals(StorageProblem.DAMAGED, health.state.value.issue?.problem)
            assertEquals("{truncated", db.progress().allBests().single().json)
            val best = LevelBest("saved-level", 2, 250, 1000, 3, "2026-09-28")
            db.progress().saveBest(BestRow(best.levelId, ParlorJson.encodeToString(best)))
            health.retry()
            assertEquals(listOf(best), repo.bests.first()); assertNull(health.state.value.issue)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    private suspend fun withSettings(block: suspend (File, File, SettingsDisk, Job) -> Unit) {
        val directory = File(context.cacheDir, "storage-settings-test").apply { deleteRecursively(); mkdirs() }
        val file = File(directory, "player.preferences_pb")
        val preserved = File(directory, "preserved/previous.preferences_pb")
        val job = SupervisorJob()
        try { block(file, preserved, SettingsDisk(file, preserved, CoroutineScope(job + Dispatchers.IO)), job) }
        finally { job.cancelAndJoin(); directory.deleteRecursively() }
    }

    @Test fun corruptPreferencesStayUntouchedUntilResetAndTheirExactFileIsPreserved() = runBlocking {
        withSettings { file, preserved, disk, job ->
            val original = byteArrayOf(0xff.toByte(), 0xff.toByte(), 0x00, 0x03)
            file.writeBytes(original)
            val health = StorageHealth()
            val settings = SettingsRepository(disk.store, health)
            repeat(2) {
                assertTrue(settings.settings.toList().isEmpty())
                assertEquals(StorageIssue(StorageArea.SETTINGS, StorageProblem.DAMAGED), health.state.value.issue)
                assertFalse(preserved.exists()); assertArrayEquals(original, file.readBytes()); health.retry()
            }
            disk.reset()
            assertArrayEquals(original, preserved.readBytes())
            assertEquals(PlayerSettings(onboarded = true), settings.settings.first())
            job.cancelAndJoin()
            val reopenedJob = SupervisorJob()
            try {
                val reopened = SettingsDisk(file, preserved, CoroutineScope(reopenedJob + Dispatchers.IO))
                assertEquals(PlayerSettings(onboarded = true), SettingsRepository(reopened.store).settings.first())
                assertArrayEquals(original, preserved.readBytes())
            } finally { reopenedJob.cancelAndJoin() }
        }
    }

    @Test fun validPreferencesWithUnreadableJsonAlsoRequireResetAndKeepTheirOriginalBytes() = runBlocking {
        withSettings { file, preserved, disk, _ ->
            disk.edit { it[SettingsDisk.SETTINGS_KEY] = "{truncated" }
            val original = file.readBytes()
            val health = StorageHealth()
            val settings = SettingsRepository(disk.store, health)
            assertTrue(settings.settings.toList().isEmpty())
            assertEquals(StorageProblem.DAMAGED, health.state.value.issue?.problem)
            assertArrayEquals(original, file.readBytes()); assertFalse(preserved.exists())
            disk.reset(); health.retry()
            assertEquals(PlayerSettings(onboarded = true), settings.settings.first())
            assertArrayEquals(original, preserved.readBytes())
        }
    }

    @Test fun failedPreservationNeverAuthorizesReplacementAndResetCanBeRetried() = runBlocking {
        withSettings { file, preserved, disk, _ ->
            val original = byteArrayOf(0xff.toByte(), 0xff.toByte())
            file.writeBytes(original)
            preserved.parentFile!!.writeText("cannot create a directory here")
            assertTrue(runCatching { disk.reset() }.isFailure)
            assertArrayEquals(original, file.readBytes())
            assertTrue(runCatching { disk.store.data.first() }.isFailure)
            assertArrayEquals(original, file.readBytes())
            preserved.parentFile!!.delete()
            disk.reset()
            assertArrayEquals(original, preserved.readBytes())
            assertEquals(PlayerSettings(onboarded = true), SettingsRepository(disk.store).settings.first())
        }
    }

    @Test fun aFailedArchiveRenameAlsoPreventsSettingsReplacement() = runBlocking {
        withSettings { file, preserved, disk, _ ->
            val original = byteArrayOf(0xff.toByte(), 0xff.toByte())
            file.writeBytes(original)
            // Android AtomicFile logs some rename failures instead of throwing them.
            preserved.mkdirs()
            File(preserved, "occupied").writeText("prevents replacement with a file")
            assertTrue(runCatching { disk.reset() }.isFailure)
            assertArrayEquals(original, file.readBytes())
            assertTrue(runCatching { disk.store.data.first() }.isFailure)
            assertArrayEquals(original, file.readBytes())
            preserved.deleteRecursively()
            disk.reset()
            assertArrayEquals(original, preserved.readBytes())
            assertEquals(PlayerSettings(onboarded = true), SettingsRepository(disk.store).settings.first())
        }
    }
}
