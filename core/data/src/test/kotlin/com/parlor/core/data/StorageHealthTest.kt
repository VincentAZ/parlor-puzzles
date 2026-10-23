package com.parlor.core.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import java.io.IOException
import kotlin.test.*

class StorageHealthTest {
    @Test fun aReadFailureKeepsTheLastEmissionWithoutInventingEmptyProgress() = runTest {
        val health = StorageHealth()
        val values = flow { emit(listOf("saved")); throw SerializationException("fixture") }
            .observeStorage(health, StorageArea.PROGRESS).toList()
        assertEquals(listOf(listOf("saved")), values)
        assertEquals(StorageIssue(StorageArea.PROGRESS, StorageProblem.DAMAGED), health.state.value.issue)
    }

    @Test fun retryStartsANewSubscriptionAndIgnoresLateFailuresFromAnOldOne() = runTest {
        val health = StorageHealth()
        val oldGeneration = health.state.value.generation
        var available = false
        val source = flow { if (!available) throw IOException("fixture"); emit(7) }.observeStorage(health, StorageArea.SETTINGS)
        assertTrue(source.toList().isEmpty())
        assertEquals(StorageProblem.UNAVAILABLE, health.state.value.issue?.problem)
        health.retry(); available = true
        health.failed(StorageArea.PROGRESS, SerializationException("late failure"), oldGeneration)
        assertEquals(listOf(7), source.toList())
        assertNull(health.state.value.issue)
    }

    @Test fun futureSchemaIsIdentifiedThroughWrappersAndTheFirstFailureRemainsVisible() {
        val health = StorageHealth()
        health.failed(StorageArea.PROGRESS, IllegalStateException(NewerStorageVersion()))
        health.failed(StorageArea.SETTINGS, IOException("later failure"))
        assertEquals(StorageIssue(StorageArea.PROGRESS, StorageProblem.NEWER_VERSION), health.state.value.issue)
    }

    @Test fun cancellationFatalErrorsAndConsumerFailuresAreNeverReportedAsStorageDamage() = runTest {
        val health = StorageHealth()
        assertFailsWith<CancellationException> {
            flow<Int> { throw CancellationException("fixture") }.observeStorage(health, StorageArea.SETTINGS).collect()
        }
        assertFailsWith<AssertionError> {
            flow<Int> { throw AssertionError("fixture") }.observeStorage(health, StorageArea.SETTINGS).collect()
        }
        assertFailsWith<IOException> {
            flowOf(1).observeStorage(health, StorageArea.PROGRESS).collect { throw IOException("consumer") }
        }
        assertNull(health.state.value.issue)
    }
}
