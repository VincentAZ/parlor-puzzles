package com.parlor.core.platform.ads

import com.parlor.core.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RewardDeliveryTest {
    private val receipt = EarnedReward("earned-one", Reward.Hints, "2026-09-28", 100)
    private class Store : EarnedRewardStore {
        var value: EarnedReward? = null
        var readFailure = false; var writeFailure = false; var clearFailure = false
        var saves = 0; var clears = 0
        override suspend fun load(): EarnedReward? { check(!readFailure); return value }
        override suspend fun save(receipt: EarnedReward) { check(!writeFailure); saves++; value = receipt }
        override suspend fun clear(id: String) { check(!clearFailure); clears++; assertEquals(value?.id, id); value = null }
    }

    @Test fun anEmptyJournalBecomesReadyWithoutInventingAReward() = runTest {
        val store = Store()
        val delivery = RewardDelivery(backgroundScope, store) { fail("No earned callback") }
        assertFalse(delivery.state.value.canStart)
        delivery.retry(); runCurrent()
        assertTrue(delivery.state.value.canStart)
        assertEquals(0, store.saves)
    }

    @Test fun failedFirstWriteKeepsTheCallbackInMemoryAndBlocksAnotherAdUntilRetry() = runTest {
        val store = Store().apply { writeFailure = true }
        var grants = 0
        val delivery = RewardDelivery(backgroundScope, store) { grants++; RewardApplication.APPLIED }
        assertTrue(delivery.earned(receipt)); runCurrent()
        assertEquals(0, grants); assertNull(store.value)
        assertTrue(delivery.state.value.error); assertFalse(delivery.state.value.durable)
        assertFalse(delivery.state.value.canStart); assertEquals(receipt, delivery.state.value.pending)
        store.writeFailure = false; delivery.retry(); runCurrent()
        assertEquals(1, grants); assertNull(store.value); assertTrue(delivery.state.value.canStart)
    }

    @Test fun databaseFailureRecoversFromDiskInANewCoordinator() = runTest {
        val store = Store()
        val original = RewardDelivery(backgroundScope, store) { error("Disk transaction unavailable") }
        original.earned(receipt); runCurrent()
        assertEquals(receipt, store.value); assertTrue(original.state.value.durable)
        var applied: EarnedReward? = null
        val restarted = RewardDelivery(backgroundScope, store) { applied = it; RewardApplication.APPLIED }
        restarted.retry(); runCurrent()
        assertEquals(receipt, applied); assertNull(store.value); assertTrue(restarted.state.value.canStart)
    }

    @Test fun processDeathAfterRoomCommitBeforeJournalClearDoesNotApplyTwice() = runTest {
        val store = Store().apply { clearFailure = true }
        val applied = mutableSetOf<String>(); var grants = 0
        val apply: suspend (EarnedReward) -> RewardApplication = {
            if (applied.add(it.id)) { grants++; RewardApplication.APPLIED } else RewardApplication.ALREADY_APPLIED
        }
        val original = RewardDelivery(backgroundScope, store, apply)
        original.earned(receipt); runCurrent()
        assertEquals(1, grants); assertEquals(receipt, store.value)
        store.clearFailure = false
        val restarted = RewardDelivery(backgroundScope, store, apply)
        restarted.retry(); runCurrent()
        assertEquals(1, grants); assertEquals(1, store.clears); assertTrue(restarted.state.value.canStart)
    }

    @Test fun duplicateCallbacksAndRetriesSerializeAndCannotReplaceAnEarnedReward() = runTest {
        val store = Store(); val commit = CompletableDeferred<Unit>(); var grants = 0
        val delivery = RewardDelivery(backgroundScope, store) { grants++; commit.await(); RewardApplication.APPLIED }
        assertTrue(delivery.earned(receipt)); runCurrent()
        repeat(3) { assertTrue(delivery.earned(receipt)); delivery.retry() }
        assertFalse(delivery.earned(receipt.copy(id = "another")))
        assertFalse(delivery.earned(receipt.copy(reward = Reward.DailyRetry(receipt.earnedDate))))
        runCurrent(); assertEquals(1, grants); assertEquals(1, store.saves)
        commit.complete(Unit); runCurrent()
        assertTrue(delivery.earned(receipt)); runCurrent()
        assertEquals(1, grants); assertEquals(1, store.clears)
    }

    @Test fun corruptJournalFailsClosedWithoutBeingReplacedByANewCallback() = runTest {
        val store = Store().apply { readFailure = true }
        val delivery = RewardDelivery(backgroundScope, store) { fail("Unreadable journal") }
        delivery.retry(); runCurrent()
        assertTrue(delivery.state.value.error); assertFalse(delivery.state.value.canStart)
        delivery.earned(receipt); runCurrent()
        assertEquals(0, store.saves); assertEquals(0, store.clears)
    }

    @Test fun anUnappliedReceiptIsRetainedForRecoveryInsteadOfDiscardedOrReportedSuccessful() = runTest {
        val store = Store().apply { value = receipt }
        val delivery = RewardDelivery(backgroundScope, store) { RewardApplication.REJECTED }
        delivery.retry(); runCurrent()
        assertEquals(receipt, store.value); assertNull(delivery.state.value.lastApplied)
        assertTrue(delivery.state.value.error); assertFalse(delivery.state.value.canStart)
    }
}
