package com.parlor.feature.play

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.parlor.core.data.ProgressRepository
import com.parlor.core.data.AccessDenied
import com.parlor.core.data.DailyUnavailable
import com.parlor.core.data.PhotoCodec
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.parlor.core.game.*
import com.parlor.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PlayState(
    val attempt: Attempt? = null,
    val busy: Boolean = false,
    val paused: Boolean = false,
    val error: Boolean = false,
    val hint: Hint? = null,
    val noHints: Boolean = false,
    val unavailable: Boolean = false,
    val checkingAccess: Boolean = false,
)

data class PhotoEditState(val busy: Boolean = false, val preview: PuzzlePhoto? = null,
                         val bitmap: Bitmap? = null, val attemptId: String? = null, val error: Boolean = false,
                         val revision: Long = 0) {
    val pausesPlay get() = busy || preview != null
}

class PlayViewModel(val level: LevelDefinition, private val repository: ProgressRepository,
                    private val challengeDate: String? = null) : ViewModel() {
    private val mutable = MutableStateFlow(PlayState())
    val state = mutable.asStateFlow()
    private val mutablePhoto = MutableStateFlow(PhotoEditState())
    val photoEdit = mutablePhoto.asStateFlow()
    private fun publishPhoto(value: PhotoEditState) { mutablePhoto.value = value.copy(revision = mutablePhoto.value.revision + 1) }
    private val mutex = Mutex()
    private var foreground = false
    private var lastTime = SystemClock.elapsedRealtime()
    private var pendingMs = 0L
    private var inputGeneration = 0L
    private var attemptObserver: Job? = null

    init {
        open()
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                if (foreground && !mutable.value.paused && !mutable.value.busy && !mutable.value.unavailable) checkpoint()
            }
        }
    }

    fun open() = viewModelScope.launch {
        mutable.update { it.copy(error = false, unavailable = false) }
        try { bind(if (challengeDate == null) repository.startOrResume(level) else repository.startDaily(challengeDate).attempt) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: AccessDenied) { mutable.update { it.copy(unavailable = true) } }
        catch (_: DailyUnavailable) { mutable.update { it.copy(unavailable = true) } }
        catch (_: Exception) { mutable.update { it.copy(error = true) } }
    }

    private fun bind(attempt: Attempt) {
        mutable.value.attempt?.let { repository.retainActiveDaily(it.id, null) }
        if (foreground) repository.retainActiveDaily(attempt.id, attempt.challengeDate)
        captureTime()
        pendingMs = 0
        mutable.update { it.copy(attempt = attempt, hint = null, error = false, unavailable = false) }
        attemptObserver?.cancel()
        attemptObserver = viewModelScope.launch {
            repository.observeAttempt(attempt.id).filterNotNull().catch { failure ->
                if (failure is CancellationException) throw failure
                if (failure is Error) throw failure
                captureTime()
                inputGeneration++
                mutable.update { it.copy(error = true, paused = true) }
            }.collect { saved ->
                mutable.update { it.copy(attempt = saved) }
            }
        }
    }

    fun lifecycle(resumed: Boolean) {
        captureTime()
        if (!resumed) inputGeneration++
        foreground = resumed
        mutable.value.attempt?.let { repository.retainActiveDaily(it.id, if (resumed) it.challengeDate else null) }
        if (!resumed) checkpoint()
        else {
            val attempt = mutable.value.attempt
            if (attempt != null && !attempt.completed) {
                mutable.update { it.copy(checkingAccess = true) }
                viewModelScope.launch {
                    mutex.withLock {
                        try {
                            repository.checkReturnAccess(level, attempt.id)
                            mutable.update { it.copy(unavailable = false) }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { mutable.update { it.copy(unavailable = true) } }
                        finally {
                            captureTime()
                            mutable.update { it.copy(checkingAccess = false) }
                        }
                    }
                }
            }
            viewModelScope.launch { runCatching { repository.refreshDay() } }
        }
    }

    override fun onCleared() {
        mutable.value.attempt?.let { repository.retainActiveDaily(it.id, null) }
    }

    fun pause(paused: Boolean) {
        captureTime()
        if (paused && !mutable.value.paused) inputGeneration++
        mutable.update { it.copy(paused = paused) }
        if (paused) checkpoint()
    }

    private fun captureTime() {
        val now = SystemClock.elapsedRealtime()
        if (foreground && !mutable.value.paused && !mutablePhoto.value.pausesPlay && !mutable.value.checkingAccess && !mutable.value.unavailable && mutable.value.attempt?.completed == false) {
            pendingMs += (now - lastTime).coerceAtLeast(0)
        }
        lastTime = now
    }

    private fun perform(blockInput: Boolean = true, requiresAccess: Boolean = true, change: suspend (Attempt, Long) -> Attempt?) {
        val generation = inputGeneration
        if (requiresAccess && !acceptsInput()) return
        viewModelScope.launch {
            mutex.withLock {
                val attempt = mutable.value.attempt ?: return@withLock
                if (attempt.completed || (requiresAccess && (generation != inputGeneration || !acceptsInput()))) return@withLock
                captureTime()
                val elapsed = pendingMs
                if (blockInput) mutable.update { it.copy(busy = true, error = false) }
                try {
                    val saved = change(attempt, elapsed)
                    pendingMs -= elapsed
                    if (saved != null) mutable.update { it.copy(attempt = saved) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutable.update { it.copy(error = true) } }
                finally { if (blockInput) mutable.update { it.copy(busy = false) } }
            }
        }
    }

    private fun acceptsInput() = foreground && !mutable.value.paused && !mutablePhoto.value.pausesPlay && !mutable.value.unavailable && !mutable.value.checkingAccess

    // Timer checkpoints serialize with moves but never disable/cancel an in-flight touch.
    fun checkpoint() = perform(blockInput = false, requiresAccess = false) { a, elapsed -> repository.checkpoint(level, a.id, elapsed) }
    fun leave(onSaved: () -> Unit) = savePaused(false, onSaved)
    fun prepareReward(onSaved: () -> Unit) {
        if (acceptsInput()) savePaused(true, onSaved)
    }
    private fun savePaused(requireUnfinished: Boolean, onSaved: () -> Unit) {
        pause(true)
        viewModelScope.launch {
            mutex.withLock {
                val attempt = mutable.value.attempt
                if (requireUnfinished && (attempt == null || attempt.completed || mutable.value.unavailable)) {
                    pause(false); return@withLock
                }
                try {
                    if (attempt != null && !attempt.completed) repository.checkpoint(level, attempt.id, pendingMs)
                    pendingMs = 0
                    onSaved()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { pause(false); mutable.update { it.copy(error = true) } }
            }
        }
    }
    fun move(move: Move) {
        if (!acceptsInput()) return
        mutable.update { it.copy(hint = null) }
        perform { a, elapsed -> repository.move(level, a.id, move, elapsed) }
    }
    fun undo() { perform { a, elapsed -> repository.undo(level, a.id, elapsed) } }
    fun redo() { perform { a, elapsed -> repository.redo(level, a.id, elapsed) } }
    fun viewport(viewport: Viewport) {
        perform(blockInput = false) { a, elapsed ->
            repository.saveViewport(level, a.id, viewport, elapsed)
        }
    }

    suspend fun loadPhoto(id: String): Bitmap? = withContext(Dispatchers.IO) {
        if (!repository.hasPro()) return@withContext null
        repository.photo(id)?.let { bytes ->
            PhotoCodec.validate(PhotoCodec.payload(bytes).also { require(it.id == id) })
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    /** The picker callback is bound to the attempt that opened it, including after Activity recreation. */
    fun preparePhoto(attemptId: String, read: () -> PuzzlePhoto) {
        if (mutablePhoto.value.busy) return
        pause(true)
        publishPhoto(PhotoEditState(busy = true, attemptId = attemptId))
        viewModelScope.launch {
            try {
                val opened = withTimeout(10_000) { state.map { it.attempt }.filterNotNull().first() }
                check(opened.id == attemptId && !opened.completed)
                check(repository.hasPro())
                val (photo, bitmap) = withContext(Dispatchers.IO) {
                    val p = read(); val bytes = PhotoCodec.validate(p)
                    p to requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                }
                check(repository.hasPro() && mutable.value.attempt?.id == attemptId && mutable.value.attempt?.completed == false)
                publishPhoto(PhotoEditState(preview = photo, bitmap = bitmap, attemptId = attemptId))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { publishPhoto(PhotoEditState(error = true)) }
        }
    }

    fun discardPhoto() { if (!mutablePhoto.value.busy) publishPhoto(PhotoEditState()) }

    fun confirmPhoto() {
        val offered = mutablePhoto.value
        if (offered.busy || offered.preview == null) return
        savePhoto(offered.attemptId ?: return, offered.preview)
    }

    fun removePhoto() { mutable.value.attempt?.id?.let { savePhoto(it, null) } }

    private fun savePhoto(attemptId: String, photo: PuzzlePhoto?) {
        if (mutablePhoto.value.busy) return
        pause(true)
        publishPhoto(mutablePhoto.value.copy(busy = true, error = false))
        viewModelScope.launch {
            mutex.withLock {
                try {
                    check(mutable.value.attempt?.id == attemptId)
                    val saved = repository.setPhoto(level, attemptId, photo)
                    mutable.update { it.copy(attempt = saved) }
                    publishPhoto(PhotoEditState())
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { publishPhoto(PhotoEditState(error = true)) }
            }
        }
    }

    fun hint() {
        if (mutable.value.busy) return
        perform { a, elapsed ->
            val hint = withContext(Dispatchers.Default) { hintFor(level, a) }
            if (hint == Hint.Unavailable) {
                mutable.update { it.copy(hint = hint) }
                repository.checkpoint(level, a.id, elapsed)
            } else {
                val saved = repository.useHint(level, a.id, elapsed)
                if (saved != null) mutable.update { it.copy(hint = hint, noHints = false) }
                else {
                    mutable.update { it.copy(noHints = true) }
                    repository.checkpoint(level, a.id, elapsed)
                }
                saved
            }
        }
    }

    fun restart(): Job {
        val generation = inputGeneration
        return viewModelScope.launch {
            mutex.withLock {
                // Restart is also an explicit action in the paused confirmation dialog.
                if (!foreground || generation != inputGeneration || mutable.value.unavailable || mutable.value.checkingAccess) return@withLock
                val attempt = mutable.value.attempt ?: return@withLock
                mutable.update { it.copy(busy = true, error = false) }
                try {
                    captureTime()
                    if (!attempt.completed) {
                        val elapsed = pendingMs
                        repository.checkpoint(level, attempt.id, elapsed)
                        pendingMs -= elapsed
                    }
                    bind(repository.restart(level, attempt.id))
                    mutable.update { it.copy(paused = false, noHints = false) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: AccessDenied) { mutable.update { it.copy(unavailable = true) } }
                catch (_: DailyUnavailable) { mutable.update { it.copy(unavailable = true) } }
                catch (_: Exception) { mutable.update { it.copy(error = true) } }
                finally { mutable.update { it.copy(busy = false) } }
            }
        }
    }

    companion object {
        fun factory(level: LevelDefinition, repository: ProgressRepository, challengeDate: String? = null) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PlayViewModel(level, repository, challengeDate) as T
        }
    }
}
