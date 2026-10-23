package com.parlor.core.platform.ads

import android.content.Context
import android.util.AtomicFile
import com.parlor.core.model.EarnedReward
import com.parlor.core.model.Reward
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

/** One earned callback awaiting Room delivery. Never exported, transferred or treated as entitlement. */
class DeviceRewardStore(context: Context, fileName: String = "earned-reward-v1.json") : EarnedRewardStore {
    init { require(fileName.matches(Regex("[a-z0-9-]+\\.json"))) }
    private val file = AtomicFile(File(context.noBackupFilesDir, fileName))
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }

    @Serializable private data class Journal(val version: Int = 1, val receipt: EarnedReward? = null)

    override suspend fun load(): EarnedReward? = withContext(Dispatchers.IO) { mutex.withLock { read() } }
    override suspend fun save(receipt: EarnedReward) = withContext(Dispatchers.IO) {
        mutex.withLock {
            validate(receipt)
            val previous = read()
            check(previous == null || previous == receipt) { "Cannot replace an undelivered reward" }
            write(receipt)
        }
    }
    override suspend fun clear(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val previous = read()
            check(previous == null || previous.id == id) { "Cannot clear another reward" }
            write(null)
        }
    }

    private fun read(): EarnedReward? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        val bytes = file.openRead().use { input ->
            val result = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(result.size() + count <= 16_384)
                result.write(buffer, 0, count)
            }
            result.toByteArray()
        }
        val journal = json.decodeFromString<Journal>(bytes.toString(Charsets.UTF_8))
        require(journal.version == 1)
        return journal.receipt?.also(::validate)
    }

    private fun validate(receipt: EarnedReward) {
        LocalDate.parse(receipt.earnedDate)
        LocalDate.parse(receipt.offeredDate)
        (receipt.reward as? Reward.DailyRetry)?.let { LocalDate.parse(it.date) }
    }

    private fun write(receipt: EarnedReward?) {
        val bytes = json.encodeToString(Journal(receipt = receipt)).toByteArray(Charsets.UTF_8)
        require(bytes.size <= 16_384)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (failure: Exception) { file.failWrite(stream); throw failure }
    }
}
