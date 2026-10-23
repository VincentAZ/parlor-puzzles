package com.parlor.core.data

import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/** One database owner. Replacement drains transactions and detaches observers before closing a file. */
class ProgressDatabase(private val open: () -> ParlorDatabase) {
    private val mutex = Mutex()
    private val active = MutableStateFlow<ParlorDatabase?>(null)
    private var replacing = false

    /** Repository calls use this only inside a lease; also available for isolated database fixtures. */
    val current: ParlorDatabase
        @Synchronized get() {
            check(!replacing) { "Progress replacement is in progress" }
            return active.value ?: open().also { active.value = it }
        }

    private class Lease(val owner: ProgressDatabase) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Lease>
    }

    suspend fun <T> transaction(action: suspend () -> T): T {
        if (coroutineContext[Lease]?.owner === this) return action()
        return mutex.withLock { withContext(Dispatchers.IO) {
            current.withTransaction { withContext(Lease(this@ProgressDatabase)) { action() } }
        } }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> observe(query: (ParlorDatabase) -> Flow<T>): Flow<T> = flow {
        mutex.withLock { withContext(Dispatchers.IO) { current } }
        emitAll(active.flatMapLatest { database -> database?.let(query) ?: emptyFlow() })
    }

    suspend fun <T> replace(action: suspend () -> T): T {
        check(coroutineContext[Lease]?.owner !== this@ProgressDatabase)
        return mutex.withLock { withContext(Dispatchers.IO) {
        val previous = synchronized(this@ProgressDatabase) {
            replacing = true
            active.value.also { active.value = null }
        }
        try {
            previous?.close()
            action()
        } finally {
            synchronized(this@ProgressDatabase) { replacing = false }
        }
        // The next read opens the selected file. Existing services keep this stable owner.
        } }
    }
}
