package com.parlor.core.data

import android.content.Context
import android.util.AtomicFile
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import com.parlor.core.model.ParlorJson
import com.parlor.core.model.PlayerSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

/** One DataStore per path, including its explicit, settings-only recovery operation. */
internal class SettingsDisk(
    private val file: File,
    private val preserved: File,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val writes = Mutex()
    @Volatile private var replacementApproved = false
    val store = PreferenceDataStoreFactory.create(
        scope = scope,
        corruptionHandler = ReplaceFileCorruptionHandler { failure ->
            if (!replacementApproved) throw failure
            preferencesOf(SETTINGS_KEY to ParlorJson.encodeToString(PlayerSettings(onboarded = true)))
        },
        produceFile = { file },
    )

    suspend fun edit(action: (MutablePreferences) -> Unit) = writes.withLock { store.edit { action(it) } }

    suspend fun reset() = writes.withLock {
        // Preserve the exact previous file before authorizing any DataStore replacement.
        withContext(Dispatchers.IO) {
            check(file.isFile) { "The unreadable settings file is no longer available" }
            check(preserved.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
            val copy = AtomicFile(preserved)
            val output = copy.startWrite()
            var finished = false
            try {
                val expected = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8_192)
                file.inputStream().use { input ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        expected.update(buffer, 0, count)
                    }
                }
                output.fd.sync()
                copy.finishWrite(output)
                finished = true
                // AtomicFile can log a failed rename without throwing. Verify the committed file.
                val actual = MessageDigest.getInstance("SHA-256")
                copy.openRead().use { input ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        actual.update(buffer, 0, count)
                    }
                }
                check(MessageDigest.isEqual(expected.digest(), actual.digest())) { "The previous settings copy could not be verified" }
            } catch (failure: Exception) { if (!finished) copy.failWrite(output); throw failure }
        }
        try {
            replacementApproved = true
            store.edit { it[SETTINGS_KEY] = ParlorJson.encodeToString(PlayerSettings(onboarded = true)) }
        } finally { replacementApproved = false }
    }

    companion object {
        val SETTINGS_KEY = stringPreferencesKey("settings_v1")
        private val owners = mutableMapOf<String, SettingsDisk>()
        @Synchronized fun get(context: Context): SettingsDisk {
            val app = context.applicationContext
            val file = File(app.filesDir, "datastore/player.preferences_pb")
            return owners.getOrPut(file.absolutePath) {
                SettingsDisk(file, File(app.noBackupFilesDir, "settings-recovery/previous.preferences_pb"))
            }
        }
    }
}
