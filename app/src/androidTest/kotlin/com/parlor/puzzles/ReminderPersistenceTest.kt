package com.parlor.puzzles

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.data.*
import com.parlor.core.model.*
import com.parlor.core.platform.reminders.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.ZonedDateTime
import java.util.Collections

class ReminderPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "reminders-instrumentation.json"
    private val file get() = File(context.noBackupFilesDir, name)
    private fun clear() { listOf("", ".bak", ".new").forEach { File(file.path + it).delete() } }
    private suspend fun rejected(action: suspend () -> Unit) {
        try { action(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
    }

    @Test fun privateRecordSurvivesReopenAndCannotLoseOrMoveItsClaimBackwards() = runBlocking {
        clear()
        try {
            assertEquals(ReminderRecord(), DeviceReminderStore(context, name).load())
            val record = ReminderRecord(preferences = ReminderPreferences(true, ReminderKind.STREAK, 1234), lastClaimedDate = "2026-09-28")
            DeviceReminderStore(context, name).save(record)
            assertEquals(context.noBackupFilesDir, file.parentFile)
            val reopened = DeviceReminderStore(context, name)
            assertEquals(record, reopened.load())
            rejected { reopened.save(record.copy(lastClaimedDate = null)) }
            rejected { reopened.save(record.copy(lastClaimedDate = "2026-09-27")) }
            assertEquals(record, reopened.load())
            val off = record.copy(preferences = record.preferences.copy(enabled = false))
            reopened.save(off)
            assertEquals(off, DeviceReminderStore(context, name).load())
        } finally { clear() }
    }

    @Test fun interruptedAtomicWriteRestoresThePreviousOptInAndClaim() = runBlocking {
        clear()
        try {
            val record = ReminderRecord(preferences = ReminderPreferences(true), lastClaimedDate = "2026-09-28")
            DeviceReminderStore(context, name).save(record)
            // AtomicFile's legacy recovery path is also present on current Android.
            assertTrue(file.renameTo(File(file.path + ".bak")))
            file.writeText("{")
            assertEquals(record, DeviceReminderStore(context, name).load())
            assertFalse(File(file.path + ".bak").exists())
        } finally { clear() }
    }

    @Test fun malformedOversizedAndFutureFilesStayUntouchedAndFailClosed() = runBlocking {
        clear()
        try {
            for (bytes in listOf("{".toByteArray(), """{"version":2}""".toByteArray(), ByteArray(4097), byteArrayOf(0xc3.toByte(), 0x28))) {
                file.writeBytes(bytes)
                var posted = false
                var scheduled: ReminderAlarm? = ReminderAlarm("2026-09-28", 1)
                val device = object : ReminderDevice {
                    override fun prepare(enabled: Boolean) {}
                    override fun permitted() = true
                    override fun schedule(alarm: ReminderAlarm?) { scheduled = alarm }
                    override fun hide() {}
                    override fun show(kind: ReminderKind, date: String, expiresAtMillis: Long) { posted = true }
                }
                val coordinator = ReminderCoordinator(DeviceReminderStore(context, name), device, ReminderProgress { it(emptySet()) },
                    { ZonedDateTime.parse("2026-09-28T20:00:00Z") }, { false })
                coordinator.deliver("2026-09-28")
                coordinator.update { it.copy(enabled = true) }
                assertTrue(coordinator.state.value.error); assertNull(scheduled); assertFalse(posted)
                assertArrayEquals(bytes, file.readBytes())
            }
        } finally { clear() }
    }

    @Test fun progressImportAndResetCannotEnableRemindersOrEraseTheirDailyClaim() = runBlocking {
        clear()
        val db = Room.inMemoryDatabaseBuilder(context, ParlorDatabase::class.java).build()
        try {
            val content = ContentRepository(context)
            val repo = ProgressRepository(db, today = { "2026-09-28" }, isPro = { true })
            repo.configure(content.load(), content.dailySchedule())
            val store = DeviceReminderStore(context, name)
            val backup = repo.exportProgress(PlayerSettings(notifications = true))
            val bytes = BackupFile.bytes(backup).toString(Charsets.UTF_8)
            assertFalse(bytes.contains("minuteOfDay")); assertFalse(bytes.contains("lastClaimedDate"))
            repo.importProgress(backup, PlayerSettings())
            repo.finishBackupSettings(requireNotNull(repo.pendingBackupSettings()))
            assertFalse(store.load().preferences.enabled)
            val record = ReminderRecord(preferences = ReminderPreferences(true, ReminderKind.STREAK, 1200), lastClaimedDate = "2026-09-28")
            store.save(record)
            repo.importProgress(backup, PlayerSettings())
            repo.finishBackupSettings(requireNotNull(repo.pendingBackupSettings()))
            repo.resetProgress()
            assertEquals(record, DeviceReminderStore(context, name).load())
        } finally { db.close(); clear() }
    }

    @Test fun completionAndDeliveryAreOrderedByTheSameRoomTransaction() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ParlorDatabase::class.java).build()
        try {
            val content = ContentRepository(context); val catalog = content.load()
            val repo = ProgressRepository(db, today = { "2026-09-28" }, isPro = { true })
            repo.configure(catalog, content.dailySchedule())
            val level = catalog.levels.first { it.puzzle == Puzzle.TRAFFIC_JAM }
            val attempt = repo.startOrResume(level)
            level.reference.dropLast(1).forEach { repo.move(level, attempt.id, it, 1) }
            val claimed = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            var saved = ReminderRecord(preferences = ReminderPreferences(enabled = true))
            val store = object : ReminderStore {
                override suspend fun load() = saved
                override suspend fun save(record: ReminderRecord) { claimed.complete(Unit); release.await(); saved = record }
            }
            val events = Collections.synchronizedList(mutableListOf<String>())
            var visible = false
            val device = object : ReminderDevice {
                override fun prepare(enabled: Boolean) {}
                override fun permitted() = true
                override fun schedule(alarm: ReminderAlarm?) {}
                override fun hide() { visible = false }
                override fun show(kind: ReminderKind, date: String, expiresAtMillis: Long) { events += "post"; visible = true }
            }
            val progress = ReminderProgress { repo.withCompletionCredits(it) }
            val now = { ZonedDateTime.parse("2026-09-28T20:00:00Z") }
            val coordinator = ReminderCoordinator(store, device, progress, now, { false })
            withTimeout(10_000) {
                val delivery = async(Dispatchers.Default) { coordinator.deliver("2026-09-28") }
                claimed.await()
                val started = CompletableDeferred<Unit>()
                val completion = async(Dispatchers.Default) {
                    started.complete(Unit)
                    assertTrue(repo.move(level, attempt.id, level.reference.last(), 1)!!.completed)
                    events += "complete"
                }
                started.await(); assertFalse(completion.isCompleted)
                release.complete(Unit); delivery.await(); completion.await()
            }
            assertEquals(listOf("post", "complete"), events)
            assertTrue("2026-09-28" in repo.player.first().ledger.creditedDates)
            coordinator.refresh(); assertFalse(visible)
            // An unclaimed fresh coordinator still suppresses a date already completed in Room.
            saved = saved.copy(lastClaimedDate = null)
            ReminderCoordinator(store, device, progress, now, { false }).deliver("2026-09-28")
            assertEquals(listOf("post", "complete"), events)
        } finally { db.close() }
    }
}
