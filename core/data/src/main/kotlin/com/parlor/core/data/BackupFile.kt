package com.parlor.core.data

import com.parlor.core.model.*
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream

/** Bounded UTF-8 JSON; no credentials, entitlement, consent or executable/archive entries. */
object BackupFile {
    const val MAX_BYTES = 32 * 1024 * 1024
    private class Buffer : ByteArrayOutputStream() { fun input() = ByteArrayInputStream(buf, 0, count) }
    @OptIn(ExperimentalSerializationApi::class)
    fun read(input: InputStream): ProgressEnvelope = readChecked(input) { ParlorJson.decodeFromStream(it) }

    internal fun <T> readChecked(input: InputStream, decode: (InputStream) -> T): T {
        val output = Buffer()
        val buffer = ByteArray(16_384)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BYTES) { "Backup is too large" }
            output.write(buffer, 0, count)
        }
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        var depth = 0; var quoted = false; var escaped = false; var separators = 0; var stringLength = 0
        InputStreamReader(output.input(), decoder).use { reader ->
            val characters = CharArray(8_192)
            while (true) {
                val count = reader.read(characters)
                if (count < 0) break
                for (index in 0 until count) {
                    val c = characters[index]
                    if (quoted) {
                        stringLength++; require(stringLength <= 4_096) { "Backup string is too long" }
                        if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
                    } else when (c) {
                        '"' -> { quoted = true; stringLength = 0 }
                        '{', '[' -> { depth++; require(depth <= 64) { "Backup nesting is too deep" } }
                        '}', ']' -> depth--
                        ',', ':' -> { separators++; require(separators <= 1_000_000) { "Backup contains too many entries" } }
                    }
                }
            }
        }
        return decode(output.input())
    }
    fun bytes(file: ProgressEnvelope): ByteArray = ParlorJson.encodeToString(file).toByteArray(Charsets.UTF_8).also {
        require(it.size <= MAX_BYTES) { "Backup is too large" }
    }
}
