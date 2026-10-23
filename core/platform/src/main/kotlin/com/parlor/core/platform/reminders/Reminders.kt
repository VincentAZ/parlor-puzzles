package com.parlor.core.platform.reminders

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicLong

@Serializable enum class ReminderKind { DAILY, STREAK }
@Serializable data class ReminderPreferences(val enabled: Boolean = false, val kind: ReminderKind = ReminderKind.DAILY,
    val minuteOfDay: Int = 19 * 60) {
    init { require(minuteOfDay in 0 until 24 * 60) }
}
/** Device-owned opt-in and a date high-water mark; neither is restored from a progress file. */
@Serializable data class ReminderRecord(val version: Int = 1, val preferences: ReminderPreferences = ReminderPreferences(),
    val lastClaimedDate: String? = null) {
    init {
        require(version == 1)
        lastClaimedDate?.let { require(it.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))); LocalDate.parse(it) }
    }
}
data class ReminderAlarm(val date: String, val atMillis: Long)
data class ReminderUiState(val ready: Boolean = false, val preferences: ReminderPreferences = ReminderPreferences(),
    val permitted: Boolean = false, val busy: Boolean = false, val error: Boolean = false, val next: ReminderAlarm? = null)

interface ReminderStore {
    suspend fun load(): ReminderRecord
    suspend fun save(record: ReminderRecord)
}
interface ReminderDevice {
    fun prepare(enabled: Boolean)
    fun permitted(): Boolean
    fun schedule(alarm: ReminderAlarm?)
    fun hide()
    fun show(kind: ReminderKind, date: String, expiresAtMillis: Long)
}
/** The callback runs inside the progress transaction so completion cannot race the final eligibility check. */
fun interface ReminderProgress { suspend fun withCredits(action: suspend (Set<String>) -> Unit) }

object ReminderRules {
    fun target(date: LocalDate, minute: Int, now: ZonedDateTime): ZonedDateTime =
        date.atTime(LocalTime.of(minute / 60, minute % 60)).atZone(now.zone)

    fun next(record: ReminderRecord, credits: Set<String>, now: ZonedDateTime): ReminderAlarm {
        var date = now.toLocalDate()
        record.lastClaimedDate?.let { if (date.toString() <= it) date = LocalDate.parse(it).plusDays(1) }
        if (date.toString() in credits || !target(date, record.preferences.minuteOfDay, now).isAfter(now)) date = date.plusDays(1)
        return ReminderAlarm(date.toString(), target(date, record.preferences.minuteOfDay, now).toInstant().toEpochMilli())
    }

    fun eligible(record: ReminderRecord, dueDate: String, credits: Set<String>, now: ZonedDateTime): Boolean {
        val today = now.toLocalDate()
        return record.preferences.enabled && dueDate == today.toString() &&
            (record.lastClaimedDate == null || dueDate > record.lastClaimedDate) && dueDate !in credits &&
            !now.isBefore(target(today, record.preferences.minuteOfDay, now)) &&
            (record.preferences.kind == ReminderKind.DAILY || today.minusDays(1).toString() in credits)
    }
}

class ReminderCoordinator(private val store: ReminderStore, private val device: ReminderDevice,
    private val progress: ReminderProgress, private val now: () -> ZonedDateTime = { ZonedDateTime.now() },
    private val foreground: () -> Boolean) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(ReminderUiState())
    val state = mutable.asStateFlow()
    private val generation = AtomicLong()

    suspend fun refresh() = guarded {
        val record = store.load()
        progress.withCredits { apply(record, it) }
    }

    suspend fun update(change: (ReminderPreferences) -> ReminderPreferences) {
        generation.incrementAndGet() // Invalidate a delivery waiting on storage before waiting for its mutex.
        guarded {
            val before = store.load()
            val desired = change(before.preferences)
            device.prepare(desired.enabled)
            if (desired.enabled && !before.preferences.enabled && !device.permitted()) {
                progress.withCredits { apply(before, it) }
                return@guarded
            }
            if (!desired.enabled) { device.schedule(null); device.hide() }
            val record = before.copy(preferences = desired)
            store.save(record)
            progress.withCredits { apply(record, it) }
        }
    }

    suspend fun deliver(dueDate: String) = guarded {
        val revision = generation.get()
        var record = store.load()
        device.prepare(record.preferences.enabled)
        progress.withCredits { credits ->
            val time = now()
            if (device.permitted() && !foreground() && ReminderRules.eligible(record, dueDate, credits, time)) {
                // Claim before posting. A kill after this write may miss an alert, but cannot duplicate it.
                record = record.copy(lastClaimedDate = dueDate)
                store.save(record)
                apply(record, credits) // Install tomorrow's alarm before posting today's notification.
                val atPost = now()
                if (revision == generation.get() && device.permitted() && !foreground() && atPost.toLocalDate().toString() == dueDate &&
                    !atPost.isBefore(ReminderRules.target(atPost.toLocalDate(), record.preferences.minuteOfDay, atPost))) {
                    device.show(record.preferences.kind, dueDate, atPost.toLocalDate().plusDays(1)
                        .atStartOfDay(atPost.zone).toInstant().toEpochMilli())
                }
            } else apply(record, credits)
        }
    }

    private fun apply(record: ReminderRecord, credits: Set<String>) {
        device.prepare(record.preferences.enabled)
        val allowed = device.permitted()
        val time = now()
        if (!record.preferences.enabled || !allowed || foreground() || time.toLocalDate().toString() in credits) device.hide()
        val next = if (record.preferences.enabled && allowed) ReminderRules.next(record, credits, time) else null
        device.schedule(next)
        mutable.value = ReminderUiState(ready = true, preferences = record.preferences, permitted = allowed, next = next)
    }

    private suspend fun guarded(action: suspend () -> Unit) = mutex.withLock {
        mutable.value = mutable.value.copy(busy = true, error = false)
        try { action() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            runCatching { device.schedule(null) }; runCatching { device.hide() }
            mutable.value = mutable.value.copy(ready = true, busy = false, error = true, next = null)
        } finally { mutable.value = mutable.value.copy(busy = false) }
    }
}
