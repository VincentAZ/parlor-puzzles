package com.parlor.core.data

import com.parlor.core.model.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.*

class BackupFileTest {
    @Test fun unicodeBackupRoundTripsAndMalformedOrTruncatedDataFailsClosed() {
        val file = ProgressEnvelope(snapshotId = "échantillon", createdAtMs = 1)
        val bytes = BackupFile.bytes(file)
        assertEquals(file, BackupFile.read(ByteArrayInputStream(bytes)))
        assertFails { BackupFile.read(ByteArrayInputStream(bytes.dropLast(1).toByteArray())) }
        assertFails { BackupFile.read(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28))) }
        assertFails { BackupFile.read(ByteArrayInputStream("{\"purchaseToken\":\"not-allowed\"}".toByteArray())) }
    }

    @Test fun sizeAndNestingAreBoundedBeforeDeserialization() {
        val oversized = object : InputStream() {
            override fun read(): Int = 32
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int { buffer.fill(32, offset, offset + length); return length }
        }
        assertFails { BackupFile.read(oversized) }
        assertFails { BackupFile.read(ByteArrayInputStream(("[".repeat(65) + "]".repeat(65)).toByteArray())) }
    }
}
