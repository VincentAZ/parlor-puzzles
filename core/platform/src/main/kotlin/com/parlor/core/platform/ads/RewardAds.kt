package com.parlor.core.platform.ads

import com.parlor.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume

data class RewardMoment(val date: String, val wallTimeMs: Long)
fun interface RewardLoad { fun cancel() }
interface RewardAd {
    fun show(onShown: () -> Unit, onEarned: (RewardMoment) -> Unit, onClosed: () -> Unit)
    fun close()
}

enum class RewardStage { OFFER, LOADING, SHOWING, SAVING, EARNED, UNAVAILABLE, LIMIT_REACHED, CANCELED, SAVE_ERROR }
data class RewardAdState(val reward: Reward, val stage: RewardStage = RewardStage.OFFER)

/** One explicit offer. Construction and offer() never load an advertisement. Use a Main dispatcher scope. */
class RewardAds(
    private val scope: CoroutineScope,
    private val delivery: RewardDelivery,
    private val load: ((RewardAd?) -> Unit) -> RewardLoad,
    private val mayPresent: () -> Boolean,
    private val availability: suspend (Reward) -> RewardAvailability,
    private val moment: () -> RewardMoment,
    private val newId: () -> String,
) {
    private val mutable = MutableStateFlow<RewardAdState?>(null)
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var loading: Job? = null
    private var activeAd: RewardAd? = null
    private var earnedId: String? = null
    private var adClosed = false

    init { scope.launch { delivery.state.collect { updateDelivery() } } }

    fun offer(reward: Reward): Boolean {
        if (mutable.value != null) return false
        mutable.value = RewardAdState(reward)
        return true
    }

    fun watch() {
        val offer = mutable.value ?: return
        if (offer.stage !in setOf(RewardStage.OFFER, RewardStage.UNAVAILABLE, RewardStage.CANCELED)) return
        val issued = ++generation
        earnedId = null; adClosed = false
        mutable.value = offer.copy(stage = RewardStage.LOADING)
        loading = scope.launch {
            var loaded: RewardAd? = null
            try {
                if (!mayPresent() || !delivery.state.value.canStart) {
                    stage(issued, RewardStage.UNAVAILABLE); return@launch
                }
                if (!checkAvailability(issued, offer.reward)) return@launch
                loaded = withTimeoutOrNull(20_000) {
                    suspendCancellableCoroutine { continuation ->
                        val request = load { ad ->
                            if (continuation.isActive) continuation.resume(ad) { _, value, _ -> value?.close() }
                            else ad?.close()
                        }
                        continuation.invokeOnCancellation { request.cancel() }
                    }
                }
                if (loaded == null || issued != generation || !mayPresent() || !delivery.state.value.canStart) {
                    loaded?.close(); stage(issued, RewardStage.UNAVAILABLE); return@launch
                }
                if (!checkAvailability(issued, offer.reward)) { loaded.close(); return@launch }
                val offeredDate = moment().date
                if (!mayPresent() || (offer.reward is Reward.DailyRetry && offer.reward.date != offeredDate)) {
                    loaded.close(); stage(issued, RewardStage.UNAVAILABLE); return@launch
                }
                val receiptId = newId()
                var shown = false
                var earned = false
                var closed = false
                val shownAd = loaded
                activeAd = loaded
                stage(issued, RewardStage.SHOWING)
                loaded.show(onShown = { shown = true }, onEarned = { completed ->
                    if (!earned) {
                        earned = true
                        val receipt = EarnedReward(receiptId, offer.reward, completed.date, completed.wallTimeMs, offeredDate = offeredDate)
                        // Delivery is application-owned and survives this offer's dismissal or Activity loss.
                        val accepted = delivery.earned(receipt)
                        if (issued == generation) {
                            earnedId = receiptId
                            if (!accepted) stage(issued, RewardStage.SAVE_ERROR)
                            else updateDelivery()
                        }
                    }
                }, onClosed = {
                    if (!closed) {
                        closed = true
                        shownAd.close()
                        if (issued == generation) {
                            activeAd = null; adClosed = true
                            if (earned) updateDelivery()
                            else stage(issued, if (shown) RewardStage.CANCELED else RewardStage.UNAVAILABLE)
                        }
                    }
                })
            } catch (cancelled: CancellationException) { loaded?.close(); throw cancelled }
            catch (_: Exception) { loaded?.close(); stage(issued, RewardStage.UNAVAILABLE) }
        }
    }

    private suspend fun checkAvailability(issued: Long, reward: Reward): Boolean {
        val available = withTimeoutOrNull(2_000) { availability(reward) }
        if (issued != generation) return false
        if (available == RewardAvailability.AVAILABLE) return true
        stage(issued, if (available == RewardAvailability.DAILY_LIMIT) RewardStage.LIMIT_REACHED else RewardStage.UNAVAILABLE)
        return false
    }

    private fun updateDelivery() {
        val id = earnedId ?: return
        if (!adClosed) return
        val status = delivery.state.value
        stage(generation, when {
            status.lastApplied?.id == id -> RewardStage.EARNED
            status.error -> RewardStage.SAVE_ERROR
            else -> RewardStage.SAVING
        })
    }

    fun retryDelivery() { delivery.retry(); updateDelivery() }
    fun cancel() {
        generation++
        loading?.cancel(); loading = null
        activeAd?.close(); activeAd = null
        earnedId = null; adClosed = false
        mutable.value = null
    }
    private fun stage(issued: Long, stage: RewardStage) {
        if (issued == generation) mutable.value = mutable.value?.copy(stage = stage)
    }
}
