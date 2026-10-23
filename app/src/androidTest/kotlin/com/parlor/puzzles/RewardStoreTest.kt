package com.parlor.puzzles

import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import com.parlor.core.platform.ads.DeviceRewardStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import java.io.File

class RewardStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "earned-reward-test.json"
    private val file get() = File(context.noBackupFilesDir, name)
    private val receipt = EarnedReward("earned-receipt", Reward.Hints, "2026-09-28", 100)
    @After fun cleanup() { listOf("", ".bak", ".new").forEach { File(file.path + it).delete() } }

    @Test fun earnedReceiptReopensOutsideBackupAndClearsOnlyItsOwnIdentity() = runBlocking {
        val store = DeviceRewardStore(context, name)
        assertNull(store.load()); store.save(receipt)
        assertTrue(file.exists()); assertFalse(File(context.filesDir, name).exists())
        val reopened = DeviceRewardStore(context, name)
        assertEquals(receipt, reopened.load())
        assertTrue(runCatching { reopened.save(receipt.copy(id = "different")) }.isFailure)
        assertTrue(runCatching { reopened.clear("different") }.isFailure)
        assertEquals(receipt, reopened.load())
        reopened.save(receipt); reopened.clear(receipt.id)
        assertNull(DeviceRewardStore(context, name).load())
    }

    @Test fun corruptionAndUnknownVersionsAreNotSilentlyReplaced() = runBlocking {
        for (invalid in listOf("not json", "{\"version\":2,\"receipt\":null}",
            "{\"version\":1,\"receipt\":null,\"unexpected\":true}", "x".repeat(16_385))) {
            file.writeText(invalid)
            val store = DeviceRewardStore(context, name)
            assertTrue(runCatching { store.load() }.isFailure)
            assertTrue(runCatching { store.save(receipt) }.isFailure)
            assertTrue(runCatching { store.clear(receipt.id) }.isFailure)
            assertEquals(invalid, file.readText())
        }
    }

    @Test fun invalidDatesAndPathsCannotCreateARecoveryRecord() = runBlocking {
        assertTrue(runCatching { DeviceRewardStore(context, "../invalid.json") }.isFailure)
        val store = DeviceRewardStore(context, name)
        assertTrue(runCatching { store.save(receipt.copy(earnedDate = "2026-02-30")) }.isFailure)
        assertTrue(runCatching { store.save(receipt.copy(reward = Reward.DailyRetry("2026-99-01"))) }.isFailure)
        assertNull(store.load())
    }
}
