package com.parlor.core.platform.ads

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConsentSnapshot(val canRequestAds: Boolean = false, val formRequired: Boolean = false, val privacyRequired: Boolean = false)
data class ConsentState(
    val snapshot: ConsentSnapshot = ConsentSnapshot(), val refreshing: Boolean = false,
    val showing: Boolean = false, val formPending: Boolean = false, val error: Boolean = false,
) {
    val permitsAds get() = snapshot.canRequestAds && !refreshing && !showing && !formPending
}
interface PrivacyForm { fun show(onDismissed: (Boolean) -> Unit) }
interface ConsentGateway {
    fun snapshot(): ConsentSnapshot
    fun refresh(onFinished: (Boolean) -> Unit)
    fun load(onFinished: (PrivacyForm?) -> Unit)
    fun privacy(onDismissed: (Boolean) -> Unit)
}

/** UMP owns consent storage. A late network callback can never open a form over a puzzle. */
class ConsentCoordinator(private val gateway: ConsentGateway, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(ConsentState())
    val state = mutable.asStateFlow()
    private var free = false
    private var foreground = false
    private var safe = false
    private var refreshed = false
    private var loading = false
    private var privacyRequested = false
    private var generation = 0L
    private var form: PrivacyForm? = null
    private var timeout: Job? = null

    fun update(free: Boolean, foreground: Boolean, placement: AdPlacement) {
        if (!free && !privacyRequested && (state.value.refreshing || state.value.formPending || loading)) refreshed = false
        this.free = free; this.foreground = foreground; safe = placement.consentForm
        if (!free && !state.value.showing && !privacyRequested) {
            generation++; timeout?.cancel(); timeout = null; form = null; loading = false
            mutable.value = state.value.copy(refreshing = false, formPending = false)
            return
        }
        if (free && foreground && !refreshed) refresh() else presentIfSafe()
    }

    fun retry() { if (foreground && safe && !state.value.showing && !state.value.refreshing) { refreshed = false; refresh() } }

    fun presentationLost() {
        if (!state.value.showing) return
        generation++
        privacyRequested = false
        mutable.value = state.value.copy(showing = false, error = true)
        refreshed = false
    }

    private fun refresh(explicitPrivacy: Boolean = false) {
        if (!foreground || (!free && !explicitPrivacy) || state.value.refreshing) return
        refreshed = true
        val token = ++generation
        mutable.value = state.value.copy(refreshing = true, error = false)
        deadline(token) { finishRefresh(false) }
        try { gateway.refresh { ok -> if (token == generation) { timeout?.cancel(); finishRefresh(ok) } } }
        catch (_: Exception) { if (token == generation) { timeout?.cancel(); finishRefresh(false) } }
    }

    private fun readSnapshot() = try { gateway.snapshot() } catch (_: Exception) { ConsentSnapshot() }
    private fun finishRefresh(ok: Boolean) {
        // Invalidate a response that arrives after the deadline. Only the next explicit retry can revive it.
        generation++
        val snapshot = readSnapshot()
        mutable.value = state.value.copy(snapshot = snapshot, refreshing = false, error = !ok,
            formPending = snapshot.formRequired && free)
        if (privacyRequested) {
            if (snapshot.privacyRequired) { mutable.value = state.value.copy(formPending = true); presentIfSafe(); return }
            privacyRequested = false
        }
        if (snapshot.formRequired && free) load() else presentIfSafe()
    }

    private fun load() {
        if (loading || form != null || !free) return
        loading = true
        val token = ++generation
        deadline(token) { loading = false; mutable.value = state.value.copy(formPending = false, error = true) }
        try { gateway.load { loaded ->
            if (token == generation) {
                timeout?.cancel(); loading = false; form = loaded
                mutable.value = state.value.copy(formPending = loaded != null, error = loaded == null)
                presentIfSafe()
            }
        } } catch (_: Exception) {
            timeout?.cancel(); loading = false
            mutable.value = state.value.copy(formPending = false, error = true)
        }
    }

    private fun presentIfSafe() {
        if (!foreground || !safe || state.value.showing || state.value.refreshing) return
        if (privacyRequested && state.value.snapshot.privacyRequired) {
            privacyRequested = false
            generation++; timeout?.cancel(); loading = false; form = null
            mutable.value = state.value.copy(showing = true, formPending = false, error = false)
            val token = generation
            try { gateway.privacy { ok -> if (token == generation) dismissed(ok) } } catch (_: Exception) { dismissed(false) }
            return
        }
        if (!free) return
        val loaded = form ?: return
        form = null
        mutable.value = state.value.copy(showing = true, formPending = false)
        val token = ++generation
        try { loaded.show { ok -> if (token == generation) dismissed(ok) } } catch (_: Exception) { dismissed(false) }
    }

    /** Explicit privacy choices stay available to a customer who has since purchased Pro. */
    fun privacyOptions() {
        if (!foreground || !safe || state.value.showing || state.value.refreshing) return
        val snapshot = readSnapshot()
        privacyRequested = true
        mutable.value = state.value.copy(snapshot = snapshot)
        if (!snapshot.privacyRequired) {
            refresh(explicitPrivacy = true)
            return
        }
        presentIfSafe()
    }

    private fun dismissed(ok: Boolean) {
        mutable.value = state.value.copy(snapshot = readSnapshot(), showing = false, error = !ok, formPending = false)
    }

    private fun deadline(token: Long, onTimeout: () -> Unit) {
        timeout?.cancel()
        timeout = scope.launch { delay(10_000); if (token == generation) { generation++; onTimeout() } }
    }
}
