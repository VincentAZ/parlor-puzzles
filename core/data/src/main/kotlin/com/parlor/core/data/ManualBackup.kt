package com.parlor.core.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.parlor.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

enum class BackupPhase { READY, READING, PREVIEW, EXPORTING, IMPORTING, EXPORTED, IMPORTED, ERROR, FINISH_REQUIRED }
enum class BackupProblem { NONE, FILE, PRO_REQUIRED, REWARD_PENDING }
data class BackupPreview(val snapshotId: String, val createdAtMs: Long, val levels: Int, val unfinished: Int, val stars: Int, val recovery: Boolean,
                         val photos: Int = 0)
data class BackupUiState(val phase: BackupPhase = BackupPhase.READY, val preview: BackupPreview? = null,
                         val canRecover: Boolean = false, val problem: BackupProblem = BackupProblem.NONE) {
    val busy get() = phase in setOf(BackupPhase.READING, BackupPhase.EXPORTING, BackupPhase.IMPORTING)
}

/** Application-owned operations. Nothing is imported until the staged preview is explicitly confirmed. */
class ManualBackup(
    private val context: Context,
    private val progress: ProgressRepository,
    private val settings: BackupSettingsStore,
    private val scope: CoroutineScope,
    private val storageHealth: StorageHealth? = null,
    private val mutex: Mutex = Mutex(),
    private val deleteOtherPhotoCopies: suspend () -> Unit = {},
    private val rewardsReady: () -> Boolean = { true },
) {
    private val mutable = MutableStateFlow(BackupUiState())
    val state = mutable.asStateFlow()
    // Preview confirmation is deliberately not restored after process death. Room owns committed recovery.
    private val staged = AtomicFile(File(context.noBackupFilesDir, "backup-preview.json")).also { it.delete() }

    /** Serialize privacy deletion with any staged read/import so a late provider cannot leave a new copy. */
    suspend fun deletePhotos() = scope.async { mutex.withLock {
        progress.deleteAllPhotos()
        invalidatePreview()
        deleteOtherPhotoCopies()
        mutable.value = BackupUiState(canRecover = progress.hasRecoverySnapshot())
    } }.await()

    /** The caller owns the shared backup mutex; replacement cannot reuse an earlier import preview. */
    suspend fun invalidatePreview() = withContext(Dispatchers.IO) {
        staged.delete()
        check(listOf(staged.baseFile, File(staged.baseFile.path + ".bak"), File(staged.baseFile.path + ".new")).none { it.exists() })
        mutable.value = BackupUiState()
    }

    /** Run during app loading, before exposing Home. A marker survives a kill between Room and DataStore. */
    suspend fun finishPendingSettings() {
        // A slow document provider must not hold Home behind a read/export after Activity recreation.
        if (mutable.value.phase in setOf(BackupPhase.READING, BackupPhase.EXPORTING)) return
        mutex.withLock {
        var finishing = mutable.value.phase == BackupPhase.FINISH_REQUIRED
        var area = StorageArea.PROGRESS
        val generation = storageHealth?.state?.value?.generation
        try {
            val pending = progress.pendingBackupSettings()
            finishing = pending != null
            if (pending != null) {
                area = StorageArea.SETTINGS
                settings.replace(pending)
                area = StorageArea.PROGRESS
                progress.finishBackupSettings(pending)
            }
            mutable.value = mutable.value.copy(canRecover = progress.hasRecoverySnapshot(),
                phase = if (pending != null || mutable.value.phase == BackupPhase.FINISH_REQUIRED) BackupPhase.IMPORTED else mutable.value.phase)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            // An unreadable database is not evidence that an import has committed.
            if (finishing) mutable.value = mutable.value.copy(phase = BackupPhase.FINISH_REQUIRED)
            if (generation != null) storageHealth?.failed(area, failure, generation)
            throw failure
        }
        }
    }

    fun select(uri: Uri) = operation(BackupPhase.READING) {
        requireReady()
        val file = withContext(Dispatchers.IO) {
            requireNotNull(context.contentResolver.openInputStream(uri)).use(BackupFile::read).also(progress::validateBackup)
        }
        withContext(Dispatchers.IO) {
            val stream = staged.startWrite()
            try { stream.write(BackupFile.bytes(file)); staged.finishWrite(stream) }
            catch (failure: Exception) { staged.failWrite(stream); throw failure }
        }
        mutable.value = mutable.value.copy(phase = BackupPhase.PREVIEW, preview = preview(file, false))
    }

    fun previewRecovery() = operation(BackupPhase.READING) {
        check(rewardsReady()) { "Reward pending" }
        val file = withContext(Dispatchers.Default) { requireNotNull(progress.recoverySnapshot()).also(progress::validateBackup) }
        mutable.value = mutable.value.copy(phase = BackupPhase.PREVIEW, preview = preview(file, true))
    }

    fun discard() {
        if (mutable.value.busy || mutable.value.phase == BackupPhase.FINISH_REQUIRED) return
        mutable.value = mutable.value.copy(phase = BackupPhase.READY, preview = null, problem = BackupProblem.NONE)
        // Enqueue cleanup before another selection can acquire the same mutex.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { mutex.withLock { withContext(Dispatchers.IO) { staged.delete() } } }
    }

    fun export(uri: Uri) = operation(BackupPhase.EXPORTING) {
        requireReady()
        val file = progress.exportProgress(settings.settings.first())
        withContext(Dispatchers.IO) {
            val bytes = BackupFile.bytes(file)
            requireReady()
            requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(bytes); it.flush() }
        }
        mutable.value = mutable.value.copy(phase = BackupPhase.EXPORTED, preview = null)
    }

    fun confirm() {
        val offered = mutable.value.preview ?: return
        if (mutable.value.phase != BackupPhase.PREVIEW) return
        operation(BackupPhase.IMPORTING) {
            check(rewardsReady()) { "Reward pending" }
            val file = if (offered.recovery) requireNotNull(progress.recoverySnapshot())
                else withContext(Dispatchers.IO) { staged.openRead().use(BackupFile::read) }
            check(file.snapshotId == offered.snapshotId)
            // After Room commits, finish settings even if the Activity goes away. Process death uses the marker.
            withContext(NonCancellable) {
                progress.importProgress(file, settings.settings.first(), offered.recovery)
                try {
                    val pending = requireNotNull(progress.pendingBackupSettings())
                    settings.replace(pending); progress.finishBackupSettings(pending)
                } catch (failure: Exception) {
                    mutable.value = mutable.value.copy(phase = BackupPhase.FINISH_REQUIRED, canRecover = true)
                    throw failure
                }
            }
            withContext(Dispatchers.IO) { runCatching { staged.delete() } }
            mutable.value = BackupUiState(phase = BackupPhase.IMPORTED, canRecover = true)
        }
    }

    private fun requireReady() { check(rewardsReady()) { "Reward pending" } }
    private fun preview(file: ProgressEnvelope, recovery: Boolean) = BackupPreview(file.snapshotId, file.createdAtMs,
        file.bests.size, file.attempts.count { !it.completed }, file.bests.sumOf { it.stars }, recovery, file.photos.size)
    private fun operation(phase: BackupPhase, action: suspend () -> Unit): Job? {
        if (mutable.value.busy || mutable.value.phase == BackupPhase.FINISH_REQUIRED) return null
        mutable.value = mutable.value.copy(phase = phase, problem = BackupProblem.NONE)
        return scope.launch {
            mutex.withLock {
                try { action() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    if (mutable.value.phase != BackupPhase.FINISH_REQUIRED) mutable.value = mutable.value.copy(phase = BackupPhase.ERROR,
                        problem = if (!rewardsReady()) BackupProblem.REWARD_PENDING else BackupProblem.FILE)
                }
            }
        }
    }
}
