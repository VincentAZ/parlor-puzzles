package com.parlor.core.data

import android.content.Context
import android.util.AtomicFile
import android.system.Os
import android.system.OsConstants
import com.parlor.core.model.*
import com.parlor.core.progress.BackupRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

interface RecoveryProtectionStore {
    suspend fun preserve(value: RecoveryProtection, today: String)
    /** Missing or unreadable safeguards must never be interpreted as an empty ledger during recovery. */
    suspend fun read(): RecoveryProtection
}

/** Write-ahead safeguards. Room may roll back after this file advances, but cannot commit ahead of it. */
class RecoveryProtectionFile(private val path: File) : RecoveryProtectionStore {
    constructor(context: Context) : this(File(context.applicationContext.filesDir, "recovery/protection.json"))
    private val file = AtomicFile(path)
    private val mutex = Mutex()
    private var cached: RecoveryProtection? = null
    private var knownRecord = false

    override suspend fun read(): RecoveryProtection = withContext(Dispatchers.IO) { mutex.withLock {
        try { readDisk() } catch (failure: Exception) { cached = null; throw failure }
    } }

    override suspend fun preserve(value: RecoveryProtection, today: String) = withContext(Dispatchers.IO) { mutex.withLock {
        val previous = cached ?: if (knownRecord || path.exists() || File(path.path + ".bak").exists()) readDisk() else null
        val merged = BackupRules.combineProtection(previous, value, today)
        if (merged == cached && path.isFile) return@withLock
        val bytes = ParlorJson.encodeToString(merged).toByteArray(Charsets.UTF_8)
        require(bytes.size <= BackupFile.MAX_BYTES) { "Recovery safeguards are too large" }
        var output: FileOutputStream? = null
        try {
            val parent = requireNotNull(path.parentFile)
            check(parent.isDirectory || parent.mkdir()) { "Recovery directory is unavailable" }
            output = file.startWrite()
            output.write(bytes)
            output.fd.sync()
            file.finishWrite(output)
            // Do not roll back an already finished session after a verification/directory-sync error.
            output = null
            // Some AtomicFile commit failures are logged rather than thrown.
            val digest = MessageDigest.getInstance("SHA-256")
            file.openRead().use { input ->
                val buffer = ByteArray(8_192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= bytes.size)
                    digest.update(buffer, 0, count)
                }
                check(total == bytes.size)
            }
            check(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(bytes), digest.digest())) {
                "Recovery safeguards could not be verified"
            }
            // Persist the rename and the recovery directory's entry before Room can commit.
            syncDirectory(parent)
            parent.parentFile?.let(::syncDirectory)
            cached = merged
            knownRecord = true
        } catch (failure: Exception) {
            // A failed commit/read-back invalidates the cached proof. Retry must verify disk again.
            cached = null
            output?.let(file::failWrite)
            throw failure
        }
    } }

    @OptIn(ExperimentalSerializationApi::class)
    private fun readDisk(): RecoveryProtection = file.openRead().use { input ->
        BackupFile.readChecked(input) { ParlorJson.decodeFromStream<RecoveryProtection>(it) }.also {
            if (it.version > 1) throw NewerStorageVersion()
            BackupRules.validateProtection(it)
            knownRecord = true
        }
    }

    private fun syncDirectory(directory: File) {
        check(directory.isDirectory)
        val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }
}
