package com.parlor.core.platform.ads

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AdPlacement(val banner: Boolean, val consentForm: Boolean) {
    HOME(true, true), LEVEL_SELECT(true, true), RESULTS(true, true), STATS(true, true), SETTINGS(true, true),
    PLAY(false, false), DAILY_ENTRY(false, true), PRO(false, false), TUTORIAL(false, false), ONBOARDING(false, true), BACKUP(false, false), REMINDERS(false, false), PHOTOS(false, false), THEMES(false, false), RECOVERY(false, false),
}

/** All calls and resource disposal are serialized on the UI dispatcher. No SDK is touched by construction. */
class AdGate(
    private val scope: CoroutineScope,
    private val initialize: suspend (stillPermitted: () -> Boolean) -> Boolean,
    private val livePermission: () -> Boolean,
    private val cancelBackgroundWork: () -> Unit = {},
) {
    private val mutable = MutableStateFlow(false)
    val enabled = mutable.asStateFlow()
    @Volatile private var permitted = false
    @Volatile private var initialized = false
    private var initialization: Job? = null
    private var epoch = 0L
    private val leases = mutableSetOf<Lease>()
    val permitsBackgroundWork get() = initialized && permitted && livePermission()

    fun update(free: Boolean, consent: Boolean, foreground: Boolean, privacyForm: Boolean) {
        val next = free && consent && foreground && !privacyForm
        if (!next) {
            val wasPermitted = permitted
            permitted = false
            mutable.value = false
            if (wasPermitted) {
                epoch++
                leases.toList().forEach { it.close() }
                try { cancelBackgroundWork() } catch (_: Exception) { }
            }
            return
        }
        permitted = true
        if (initialized) { mutable.value = livePermission(); return }
        if (initialization?.isActive == true) return
        initialization = scope.launch {
            try {
                initialized = initialize { permitted && livePermission() }
                mutable.value = initialized && permitted && livePermission()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = false }
        }
    }

    fun acquire(dispose: () -> Unit): Lease? {
        if (!mutable.value || !permitted || !livePermission()) return null
        return Lease(epoch, dispose).also { leases += it }
    }

    inner class Lease internal constructor(private val issuedEpoch: Long, private val dispose: () -> Unit) {
        private var closed = false
        val valid get() = !closed && issuedEpoch == epoch && mutable.value && permitted && livePermission()
        fun close() {
            if (closed) return
            closed = true
            leases -= this
            try { dispose() } catch (_: Exception) { }
        }
    }
}
