package com.parlor.core.data

import android.content.Context
import android.util.AtomicFile
import androidx.room.RoomDatabase
import com.parlor.core.model.ParlorJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import java.io.File

@Serializable private data class DatabaseSelection(val version: Int, val slot: Int)

/** Both complete database slots and the atomic selector are included in Android backup. */
class DatabaseSlots(
    private val context: Context,
    private val names: List<String> = listOf("parlor.db", "parlor-recovered.db"),
    selection: File = File(context.filesDir, "recovery/active-database.json"),
    private val writeSelection: (AtomicFile, ByteArray) -> Unit = DurableFiles::write,
) {
    init { require(names.size == 2 && names.distinct().size == 2 && names.all { it.matches(Regex("[a-z0-9-]+\\.db")) }) }
    private val pointer = AtomicFile(selection)

    @OptIn(ExperimentalSerializationApi::class)
    fun activeSlot(): Int {
        if (!pointer.baseFile.exists() && !File(pointer.baseFile.path + ".bak").exists()) {
            check(context.getDatabasePath(names[0]).exists() || !context.getDatabasePath(names[1]).exists()) {
                "Active database selection is missing"
            }
            return 0
        }
        val saved = pointer.openRead().use { input ->
            BackupFile.readChecked(input) { ParlorJson.decodeFromStream<DatabaseSelection>(it) }
        }
        if (saved.version > 1) throw NewerStorageVersion()
        require(saved.version == 1 && saved.slot in 0..1) { "Invalid active database selection" }
        return saved.slot
    }

    fun openActive(): ParlorDatabase {
        val slot = activeSlot()
        if (pointer.baseFile.exists()) check(context.getDatabasePath(names[slot]).isFile) { "Selected database is missing" }
        return ParlorDatabase.open(context, names[slot])
    }
    internal fun openCandidate(slot: Int): ParlorDatabase {
        require(slot in 0..1 && slot != activeSlot())
        deleteSlot(slot)
        return ParlorDatabase.open(context, names[slot], RoomDatabase.JournalMode.TRUNCATE)
    }

    internal fun commit(slot: Int) {
        require(slot in 0..1 && slot != activeSlot())
        val file = context.getDatabasePath(names[slot])
        require(file.isFile && file.length() > 0)
        // The candidate is closed and uses a rollback journal, so its main file is self-contained.
        check(!File(file.path + "-wal").exists() || File(file.path + "-wal").length() == 0L)
        file.inputStream().use { it.fd.sync() }
        DurableFiles.syncDirectory(requireNotNull(file.parentFile))
        writeSelection(pointer, ParlorJson.encodeToString(DatabaseSelection(1, slot)).toByteArray())
        check(activeSlot() == slot)
    }

    /** Called only from confirmed photo deletion while all backup/restore operations are serialized. */
    fun deletePrevious() = deleteSlot(1 - activeSlot())

    private fun deleteSlot(slot: Int) {
        val base = context.getDatabasePath(names[slot])
        for (suffix in listOf("", "-wal", "-shm", "-journal")) {
            val file = File(base.path + suffix)
            check(!file.exists() || file.delete()) { "Previous database could not be removed" }
        }
        base.parentFile?.takeIf { it.isDirectory }?.let(DurableFiles::syncDirectory)
    }
}
