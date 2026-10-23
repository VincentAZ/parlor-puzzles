package com.parlor.core.platform.ads

import android.content.Context
import android.provider.Settings
import android.util.AtomicFile
import java.io.File
import java.util.UUID

/** A boot counter alone can collide after progress transfers to another device. */
class DeviceAdClock(private val context: Context, private val fileName: String = "ads-clock-device") {
    private val fallback = "session:${UUID.randomUUID()}"
    fun bootMarker(): String = try {
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        val file = AtomicFile(File(context.noBackupFilesDir, fileName))
        val saved = try {
            file.openRead().use { input ->
                val bytes = ByteArray(37)
                var count = 0
                while (count < bytes.size) {
                    val read = input.read(bytes, count, bytes.size - count)
                    if (read < 0) break
                    count += read
                }
                if (count != 36) null else bytes.copyOf(count).toString(Charsets.US_ASCII).takeIf { UUID.fromString(it).toString() == it }
            }
        } catch (_: Exception) { null }
        val device = saved ?: UUID.randomUUID().toString().also { id ->
            val stream = file.startWrite()
            try { stream.write(id.toByteArray(Charsets.US_ASCII)); file.finishWrite(stream) }
            catch (failure: Exception) { file.failWrite(stream); throw failure }
        }
        "$device:$boot"
    } catch (_: Exception) { fallback }
}
