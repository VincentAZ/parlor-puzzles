package com.parlor.core.data

import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

internal object DurableFiles {
    fun syncDirectory(directory: File) {
        check(directory.isDirectory)
        val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }

    fun write(file: AtomicFile, bytes: ByteArray) {
        var output: FileOutputStream? = null
        try {
            val parent = requireNotNull(file.baseFile.parentFile)
            check(parent.isDirectory || parent.mkdir()) { "Recovery directory is unavailable" }
            output = file.startWrite()
            output.write(bytes); output.fd.sync(); file.finishWrite(output)
            // finishWrite ends this write session. API 26 failWrite would delete the committed base.
            output = null
            val digest = MessageDigest.getInstance("SHA-256")
            file.openRead().use { input ->
                val buffer = ByteArray(8_192); var total = 0
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    total += count; check(total <= bytes.size); digest.update(buffer, 0, count)
                }
                check(total == bytes.size)
            }
            check(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(bytes), digest.digest())) {
                "Recovery file could not be verified"
            }
            syncDirectory(parent); parent.parentFile?.let(::syncDirectory)
        } catch (failure: Exception) { output?.let(file::failWrite); throw failure }
    }
}
