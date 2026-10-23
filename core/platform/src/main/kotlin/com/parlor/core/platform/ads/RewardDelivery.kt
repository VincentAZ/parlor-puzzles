package com.parlor.core.platform.ads

import com.parlor.core.model.EarnedReward
import com.parlor.core.model.RewardApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface EarnedRewardStore {
    suspend fun load(): EarnedReward?
    suspend fun save(receipt: EarnedReward)
    suspend fun clear(id: String)
}

data class RewardDeliveryState(
    val checking: Boolean = true,
    val pending: EarnedReward? = null,
    val durable: Boolean = false,
    val error: Boolean = false,
    val lastApplied: EarnedReward? = null,
) {
    val canStart get() = !checking && !error && pending == null
}

/** Application-owned. Calls are serialized on the supplied UI scope, independently of any Activity. */
class RewardDelivery(
    private val scope: CoroutineScope,
    private val store: EarnedRewardStore,
    private val apply: suspend (EarnedReward) -> RewardApplication,
) {
    private val mutable = MutableStateFlow(RewardDeliveryState())
    val state = mutable.asStateFlow()
    private var incoming: EarnedReward? = null
    private var work: Job? = null

    /** Must be called only from the earned callback. Retain in memory if the first durable write fails. */
    fun earned(receipt: EarnedReward): Boolean {
        if (mutable.value.lastApplied?.id == receipt.id) return mutable.value.lastApplied == receipt
        val previous = incoming ?: mutable.value.pending
        if (previous != null && previous != receipt) return false
        incoming = receipt
        mutable.value = mutable.value.copy(pending = receipt)
        retry()
        return true
    }

    /** Run after content is configured, on foreground return, or from the visible retry action. */
    fun retry(): Job {
        work?.takeIf { it.isActive }?.let { return it }
        return scope.launch {
            mutable.value = mutable.value.copy(checking = true, error = false)
            try {
                val saved = store.load()
                val receipt = incoming ?: saved
                check(saved == null || incoming == null || saved == incoming) { "An earlier earned reward needs recovery" }
                if (receipt == null) {
                    mutable.value = mutable.value.copy(checking = false, pending = null, durable = false)
                    return@launch
                }
                incoming = receipt
                mutable.value = mutable.value.copy(pending = receipt, durable = saved == receipt)
                if (saved == null) store.save(receipt)
                mutable.value = mutable.value.copy(durable = true)
                check(apply(receipt) != RewardApplication.REJECTED) { "Earned reward could not be applied" }
                // A crash here replays the same ID; the Room transaction returns ALREADY_APPLIED.
                store.clear(receipt.id)
                incoming = null
                mutable.value = RewardDeliveryState(checking = false, lastApplied = receipt)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutable.value = mutable.value.copy(checking = false, error = true)
            }
        }.also { work = it }
    }
}
