package com.parlor.core.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.parlor.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

enum class DatabaseRecoveryPhase { READY, READING, PREVIEW, RESTORING, ERROR }
enum class DatabaseRecoveryProblem { NONE, FILE, PRO_REQUIRED, SAFEGUARDS, STORAGE }
data class DatabaseRecoveryState(
    val phase: DatabaseRecoveryPhase = DatabaseRecoveryPhase.READY,
    val preview: BackupPreview? = null,
    val problem: DatabaseRecoveryProblem = DatabaseRecoveryProblem.NONE,
) {
    val busy get() = phase == DatabaseRecoveryPhase.READING || phase == DatabaseRecoveryPhase.RESTORING
}

/** Confirmation prepares a separate database; only a complete, closed file can become the active save. */
class DamagedDatabaseRecovery(
    context: Context,
    private val database: ProgressDatabase,
    private val slots: DatabaseSlots,
    private val protection: RecoveryProtectionStore,
    private val scope: CoroutineScope,
    private val mutex: Mutex,
    private val loadContent: suspend () -> Pair<Catalog, DailySchedule>,
    private val isPro: () -> Boolean,
    private val isRecoverable: () -> Boolean,
    private val beforeSwitch: suspend () -> Unit = {},
    private val onRestored: () -> Unit = {},
    private val today: () -> String = { java.time.LocalDate.now().toString() },
    previewFile: File = File(context.noBackupFilesDir, "database-recovery-preview.json"),
) {
    private val resolver = context.contentResolver
    private val staged = AtomicFile(previewFile).also { it.delete() }
    private val mutable = MutableStateFlow(DatabaseRecoveryState())
    val state = mutable.asStateFlow()

    fun select(uri: Uri): Job? = operation(DatabaseRecoveryPhase.READING) {
        requireAllowed()
        safeguards()
        val (catalog, schedule) = loadContent()
        val file = try {
            requireNotNull(resolver.openInputStream(uri)).use(BackupFile::read).also {
                BackupValidation.validate(it, catalog, schedule)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { throw RecoveryFailure(DatabaseRecoveryProblem.FILE, failure) }
        requireAllowed()
        DurableFiles.write(staged, BackupFile.bytes(file))
        mutable.value = DatabaseRecoveryState(DatabaseRecoveryPhase.PREVIEW,
            BackupPreview(file.snapshotId, file.createdAtMs, file.bests.size, file.attempts.count { !it.completed },
                file.bests.sumOf { it.stars }, false, file.photos.size))
    }

    fun confirm(): Job? {
        val offered = mutable.value.preview ?: return null
        if (mutable.value.phase != DatabaseRecoveryPhase.PREVIEW) return null
        return operation(DatabaseRecoveryPhase.RESTORING) {
            requireAllowed()
            check(mutable.value.preview == offered) { "Preview changed" }
            val file = try {
                staged.openRead().use(BackupFile::read).also { check(it.snapshotId == offered.snapshotId) }
            } catch (failure: Exception) { throw RecoveryFailure(DatabaseRecoveryProblem.FILE, failure) }
            val (catalog, schedule) = loadContent()
            BackupValidation.validate(file, catalog, schedule)
            // Drains every mutation on the old database, including background reminder/reward work.
            database.replace {
                requireAllowed()
                val retained = safeguards()
                val candidateSlot = 1 - slots.activeSlot()
                val candidate = slots.openCandidate(candidateSlot)
                try {
                    val repository = ProgressRepository(candidate, today = today, isPro = isPro, protection = protection)
                    repository.configure(catalog, schedule)
                    repository.restoreIntoEmptyDatabase(file, retained)
                } finally { candidate.close() }
                beforeSwitch()
                requireAllowed()
                try { slots.commit(candidateSlot) }
                catch (failure: Exception) {
                    // The rename may succeed before a directory fsync reports failure. Both slots remain
                    // complete; resume whichever selector is now visible instead of importing twice.
                    if (runCatching { slots.activeSlot() }.getOrNull() != candidateSlot) throw failure
                }
            }
            staged.delete()
            mutable.value = DatabaseRecoveryState()
            onRestored()
        }
    }

    fun discard(): Job? {
        if (mutable.value.busy) return null
        return scope.launch(start = CoroutineStart.UNDISPATCHED) { mutex.withLock {
            try { clearPreview() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // Cancel always revokes confirmation, even if the private file cannot yet be removed.
                mutable.value = DatabaseRecoveryState(DatabaseRecoveryPhase.ERROR, problem = DatabaseRecoveryProblem.STORAGE)
            }
        } }
    }

    /** Called by confirmed photo deletion while the shared backup mutex is held. */
    suspend fun deletePhotoCopies() = withContext(Dispatchers.IO) {
        clearPreview()
        slots.deletePrevious()
    }

    private suspend fun clearPreview() = withContext(Dispatchers.IO) {
        staged.delete()
        check(listOf("", ".bak", ".new").none { File(staged.baseFile.path + it).exists() })
        mutable.value = DatabaseRecoveryState()
    }

    private fun requireAllowed() {
        if (!isPro()) throw RecoveryFailure(DatabaseRecoveryProblem.PRO_REQUIRED)
        check(isRecoverable()) { "Storage is no longer eligible for replacement" }
    }

    private suspend fun safeguards(): RecoveryProtection = try { protection.read() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { throw RecoveryFailure(DatabaseRecoveryProblem.SAFEGUARDS, failure) }

    private class RecoveryFailure(val problem: DatabaseRecoveryProblem, cause: Exception? = null) : Exception(cause)

    private fun operation(phase: DatabaseRecoveryPhase, action: suspend () -> Unit): Job? {
        if (mutable.value.busy) return null
        mutable.value = mutable.value.copy(phase = phase, problem = DatabaseRecoveryProblem.NONE)
        return scope.launch {
            mutex.withLock { withContext(Dispatchers.IO) {
                try { action() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    mutable.value = mutable.value.copy(phase = DatabaseRecoveryPhase.ERROR,
                        problem = (failure as? RecoveryFailure)?.problem ?: if (!isPro())
                            DatabaseRecoveryProblem.PRO_REQUIRED else DatabaseRecoveryProblem.STORAGE)
                }
            } }
        }
    }
}
