package com.parlor.core.data

import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerializationException

enum class StorageArea { PROGRESS, SETTINGS }
enum class StorageProblem { UNAVAILABLE, DAMAGED, NEWER_VERSION }
data class StorageIssue(val area: StorageArea, val problem: StorageProblem)
data class StorageState(val generation: Long = 0, val issue: StorageIssue? = null)
class NewerStorageVersion : IllegalStateException("A newer app version is needed to read this save")

/** Read failures stop presentation; they never turn unreadable data into empty progress. */
class StorageHealth {
    private val mutable = MutableStateFlow(StorageState())
    val state = mutable.asStateFlow()
    fun retry() { mutable.update { StorageState(generation = it.generation + 1) } }
    fun failed(area: StorageArea, failure: Throwable, generation: Long = state.value.generation) {
        if (failure is CancellationException) throw failure
        if (failure is Error) throw failure
        val causes = generateSequence(failure) { it.cause?.takeUnless { cause -> cause === it } }.take(12).toList()
        val problem = when {
            causes.any { it is NewerStorageVersion } -> StorageProblem.NEWER_VERSION
            causes.any { it is SQLiteDatabaseCorruptException || it is CorruptionException || it is SerializationException } -> StorageProblem.DAMAGED
            else -> StorageProblem.UNAVAILABLE
        }
        mutable.update { current ->
            if (current.generation != generation || current.issue != null) current
            else current.copy(issue = StorageIssue(area, problem))
        }
    }
}

internal fun <T> Flow<T>.observeStorage(health: StorageHealth?, area: StorageArea): Flow<T> =
    if (health == null) this else flow {
        val generation = health.state.value.generation
        emitAll(this@observeStorage.catch { failure -> health.failed(area, failure, generation) })
    }
