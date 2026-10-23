package com.parlor.core.platform.reminders

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReminderCoordinatorTest {
    private class Store : ReminderStore {
        var record = ReminderRecord()
        var readFails = false; var writeFails = false
        var beforeSave: suspend () -> Unit = {}
        override suspend fun load(): ReminderRecord { check(!readFails); return record }
        override suspend fun save(record: ReminderRecord) { beforeSave(); check(!writeFails); this.record = record }
    }
    private class Device : ReminderDevice {
        var allowed = true
        var enabled = false
        var alarm: ReminderAlarm? = null
        var postFails = false; var scheduleFails = false
        val posted = mutableListOf<Pair<ReminderKind, String>>()
        var hides = 0
        override fun prepare(enabled: Boolean) { this.enabled = enabled }
        override fun permitted() = allowed
        override fun schedule(alarm: ReminderAlarm?) { check(!scheduleFails); this.alarm = alarm }
        override fun hide() { hides++ }
        override fun show(kind: ReminderKind, date: String, expiresAtMillis: Long) { check(!postFails); posted += kind to date }
    }
    private class Rig {
        val store = Store(); val device = Device()
        var time = ZonedDateTime.parse("2026-09-28T18:00:00-07:00[America/Phoenix]")
        var foreground = false
        var credits = emptySet<String>()
        fun coordinator() = ReminderCoordinator(store, device, ReminderProgress { it(credits) }, { time }, { foreground })
        val coordinator = coordinator()
        suspend fun enable() { coordinator.update { it.copy(enabled = true) } }
        fun atDue() { time = time.withHour(19) }
        suspend fun deliver(date: String = time.toLocalDate().toString()) { coordinator.deliver(date) }
    }

    @Test fun defaultAndDeniedPermissionCannotOptInOrSchedule() = runTest {
        val r = Rig(); r.coordinator.refresh(); r.atDue(); r.deliver()
        assertFalse(r.store.record.preferences.enabled); assertNull(r.device.alarm); assertTrue(r.device.posted.isEmpty())
        r.device.allowed = false; r.enable()
        assertFalse(r.store.record.preferences.enabled); assertFalse(r.coordinator.state.value.permitted)
        r.device.allowed = true; r.coordinator.refresh()
        assertFalse(r.store.record.preferences.enabled); assertNull(r.device.alarm)
        r.enable(); assertTrue(r.store.record.preferences.enabled); assertNotNull(r.device.alarm)
    }

    @Test fun deliveryClaimsOnceBeforePostingAndSchedulesTheNextLocalDay() = runTest {
        val r = Rig(); r.enable()
        assertEquals(r.time.withHour(19).toInstant().toEpochMilli(), r.device.alarm!!.atMillis)
        r.atDue(); repeat(10) { launch { r.deliver() } }; runCurrent()
        assertEquals(listOf(ReminderKind.DAILY to "2026-09-28"), r.device.posted)
        assertEquals("2026-09-28", r.store.record.lastClaimedDate)
        assertEquals("2026-09-29", r.device.alarm!!.date)
        assertEquals(r.time.plusDays(1).toInstant().toEpochMilli(), r.device.alarm!!.atMillis)
    }

    @Test fun changingKindTimeAndOptInCannotReopenTheSameDate() = runTest {
        val r = Rig(); r.enable(); r.atDue(); r.deliver()
        r.coordinator.update { it.copy(kind = ReminderKind.STREAK, minuteOfDay = 10) }
        r.credits = setOf("2026-09-27")
        r.coordinator.update { it.copy(enabled = false) }; assertNull(r.device.alarm)
        r.enable(); r.deliver()
        assertEquals(1, r.device.posted.size); assertEquals("2026-09-28", r.store.record.lastClaimedDate)
    }

    @Test fun streakRemindersNeedYesterdayAndSkipAfterAnyCompletionToday() = runTest {
        val r = Rig(); r.coordinator.update { it.copy(enabled = true, kind = ReminderKind.STREAK) }; r.atDue()
        r.credits = setOf("2026-09-26"); r.deliver(); assertTrue(r.device.posted.isEmpty())
        r.credits = setOf("2026-09-27", "2026-09-28"); r.deliver(); assertTrue(r.device.posted.isEmpty())
        r.credits = setOf("2026-09-27"); r.deliver()
        assertEquals(listOf(ReminderKind.STREAK to "2026-09-28"), r.device.posted)
    }

    @Test fun anOpenAppOrCompletionSuppressesDeliveryAndClearsVisibleReminders() = runTest {
        val r = Rig(); r.enable(); r.atDue(); r.foreground = true; r.deliver()
        assertTrue(r.device.posted.isEmpty()); assertTrue(r.device.hides > 0)
        r.foreground = false; r.credits = setOf("2026-09-28"); r.deliver()
        assertTrue(r.device.posted.isEmpty()); assertEquals("2026-09-29", r.device.alarm!!.date)
    }

    @Test fun earlyStaleAndMalformedAlarmDatesNeverPost() = runTest {
        val r = Rig(); r.enable()
        r.deliver(); assertTrue(r.device.posted.isEmpty())
        r.atDue(); for (date in listOf("2026-09-27", "2026-09-29", "bad", "2026-02-30")) r.deliver(date)
        assertTrue(r.device.posted.isEmpty()); assertNull(r.store.record.lastClaimedDate)
    }

    @Test fun clockAndTimezoneRollbackCannotRepeatOrForgetAClaim() = runTest {
        val r = Rig(); r.enable(); r.atDue(); r.deliver()
        r.time = r.time.minusDays(1); r.deliver()
        r.time = ZonedDateTime.parse("2026-09-28T23:00:00+09:00[Asia/Tokyo]"); r.deliver()
        assertEquals(1, r.device.posted.size); assertEquals("2026-09-29", r.device.alarm!!.date)
        r.time = r.time.plusDays(1); r.deliver(); assertEquals(2, r.device.posted.size)
    }

    @Test fun daySchedulingHandlesDstGapsOverlapAndChangedZones() {
        val zone = ZoneId.of("America/New_York")
        val record = ReminderRecord(preferences = ReminderPreferences(enabled = true), lastClaimedDate = "2027-03-13")
        val before = ZonedDateTime.of(2027, 3, 13, 19, 0, 0, 0, zone)
        val next = ReminderRules.next(record, emptySet(), before)
        assertEquals(23, Duration.between(before.toInstant(), java.time.Instant.ofEpochMilli(next.atMillis)).toHours())
        assertEquals(3, ReminderRules.target(LocalDate.of(2027, 3, 14), 150, before).hour)
        val fall = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, zone)
        val first = ReminderRules.target(fall.toLocalDate(), 90, fall)
        val second = first.withLaterOffsetAtOverlap()
        assertEquals(1, Duration.between(first.toInstant(), second.toInstant()).toHours())
        val claimed = ReminderRecord(preferences = ReminderPreferences(true, minuteOfDay = 90), lastClaimedDate = "2026-11-01")
        assertFalse(ReminderRules.eligible(claimed, "2026-11-01", emptySet(), second))
        assertEquals("2026-11-02", ReminderRules.next(claimed, emptySet(), second).date)
        val tokyo = before.withZoneSameInstant(ZoneId.of("Asia/Tokyo"))
        val shifted = ReminderRules.next(ReminderRecord(preferences = ReminderPreferences(true)), emptySet(), tokyo)
        assertEquals(19, java.time.Instant.ofEpochMilli(shifted.atMillis).atZone(tokyo.zone).hour)
    }

    @Test fun failedLoadOrClaimWriteFailsClosedWithoutDiscardingTheRecord() = runTest {
        val r = Rig(); r.enable(); r.atDue(); r.store.writeFails = true; r.deliver()
        assertTrue(r.device.posted.isEmpty()); assertNull(r.store.record.lastClaimedDate); assertTrue(r.coordinator.state.value.error)
        r.store.writeFails = false; r.store.readFails = true; r.coordinator.update { it.copy(enabled = false) }
        assertTrue(r.store.record.preferences.enabled); assertTrue(r.coordinator.state.value.error); assertNull(r.device.alarm)
        r.store.readFails = false; r.coordinator.refresh(); assertFalse(r.coordinator.state.value.error)
    }

    @Test fun aCrashBoundaryAfterTheClaimCannotPostAgainInANewCoordinator() = runTest {
        val r = Rig(); r.enable(); r.atDue(); r.device.postFails = true; r.deliver()
        assertEquals("2026-09-28", r.store.record.lastClaimedDate)
        assertTrue(r.device.posted.isEmpty()); assertTrue(r.coordinator.state.value.error)
        r.device.postFails = false; r.coordinator().deliver("2026-09-28")
        assertTrue(r.device.posted.isEmpty()); assertEquals("2026-09-29", r.device.alarm!!.date)
    }

    @Test fun disablingDuringClaimStoragePreventsTheWaitingNotification() = runTest {
        val r = Rig(); r.enable(); r.atDue()
        val commit = CompletableDeferred<Unit>(); r.store.beforeSave = { commit.await() }
        launch { r.deliver() }; runCurrent()
        launch { r.coordinator.update { it.copy(enabled = false) } }; runCurrent()
        commit.complete(Unit); runCurrent()
        assertTrue(r.device.posted.isEmpty()); assertFalse(r.store.record.preferences.enabled); assertNull(r.device.alarm)
    }

    @Test fun permissionForegroundMidnightAndClockChangesAreRecheckedAfterStorage() = runTest {
        for (change in listOf<(Rig) -> Unit>({ it.device.allowed = false }, { it.foreground = true },
            { it.time = it.time.plusDays(1) }, { it.time = it.time.minusHours(1) })) {
            val r = Rig(); r.enable(); r.atDue()
            r.store.beforeSave = { change(r) }
            r.deliver()
            assertTrue(r.device.posted.isEmpty()); assertEquals("2026-09-28", r.store.record.lastClaimedDate)
        }
    }

    @Test fun scheduleFailureIsVisibleAndRetryRestoresOneFutureAlarm() = runTest {
        val r = Rig(); r.device.scheduleFails = true; r.enable()
        assertTrue(r.coordinator.state.value.error); assertTrue(r.store.record.preferences.enabled)
        r.device.scheduleFails = false; r.coordinator.refresh()
        assertFalse(r.coordinator.state.value.error); assertEquals("2026-09-28", r.device.alarm!!.date)
    }

    @Test fun privateSchemaRejectsUnknownVersionsDatesTimesAndFields() {
        val json = Json { encodeDefaults = true }
        val text = json.encodeToString(ReminderRecord(lastClaimedDate = "2026-09-28"))
        assertEquals("2026-09-28", json.decodeFromString<ReminderRecord>(text).lastClaimedDate)
        for (bad in listOf(text.replace("\"version\":1", "\"version\":2"), text.replace("1140", "1440"),
            text.replace("2026-09-28", "2026-02-30"), text.dropLast(1) + ",\"purchased\":true}"))
            assertFails { json.decodeFromString<ReminderRecord>(bad) }
    }
}
