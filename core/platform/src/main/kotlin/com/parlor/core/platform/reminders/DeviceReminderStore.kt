package com.parlor.core.platform.reminders

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

class DeviceReminderStore(context: Context, fileName: String = "reminders-v1.json") : ReminderStore {
    init { require(fileName.matches(Regex("[a-z0-9-]+\\.json"))) }
    private val file = AtomicFile(File(context.noBackupFilesDir, fileName))
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    override suspend fun load(): ReminderRecord = withContext(Dispatchers.IO) { mutex.withLock { read() } }
    override suspend fun save(record: ReminderRecord) = withContext(Dispatchers.IO) { mutex.withLock {
        val previous = read() // Corruption must not silently discard a sent-date claim.
        check(previous.lastClaimedDate == null || (record.lastClaimedDate != null && record.lastClaimedDate >= previous.lastClaimedDate))
        val bytes = json.encodeToString(record).toByteArray(Charsets.UTF_8)
        require(bytes.size <= 4_096)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (failure: Exception) { file.failWrite(stream); throw failure }
    } }

    private fun read(): ReminderRecord {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return ReminderRecord()
        val bytes = file.openRead().use { input ->
            val result = ByteArrayOutputStream(); val buffer = ByteArray(1024)
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                require(result.size() + count <= 4_096); result.write(buffer, 0, count)
            }
            result.toByteArray()
        }
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return json.decodeFromString(decoder.decode(ByteBuffer.wrap(bytes)).toString())
    }
}
