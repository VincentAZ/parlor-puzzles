package com.parlor.puzzles

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.data.*
import com.parlor.core.model.*
import com.parlor.feature.library.ParlorTheme
import com.parlor.feature.play.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class PausedInputTest {
    @get:Rule val compose = createComposeRule()

    @Test fun anUnreadableObservedAttemptPausesInputAndKeepsTheLastBoardUntilRetry() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        val db = Room.inMemoryDatabaseBuilder(app, ParlorDatabase::class.java).build()
        val repo = ProgressRepository(db)
        val catalog = runBlocking { app.content.load() }
        val level = catalog.levels.first()
        repo.configure(catalog, runBlocking { app.content.dailySchedule() })
        lateinit var vm: PlayViewModel
        compose.setContent { vm = viewModel(factory = PlayViewModel.factory(level, repo)) }
        try {
            compose.waitUntil(10_000) { vm.state.value.attempt != null }
            compose.runOnIdle { vm.lifecycle(true) }
            compose.waitUntil(10_000) { !vm.state.value.checkingAccess }
            val before = runBlocking { db.progress().attempt(vm.state.value.attempt!!.id)!! }
            val board = vm.state.value.attempt!!.board
            runBlocking { db.progress().saveAttempt(before.copy(json = "{truncated")) }
            compose.waitUntil(10_000) { vm.state.value.error && vm.state.value.paused }
            assertEquals(board, vm.state.value.attempt!!.board)
            compose.runOnIdle { vm.move(level.reference.first()); vm.undo(); vm.hint() }
            assertEquals(0, vm.state.value.attempt!!.scoredMoves)
            assertEquals("{truncated", runBlocking { db.progress().attempt(before.id)!!.json })
            runBlocking { db.progress().saveAttempt(before) }
            compose.runOnIdle { vm.open() }
            compose.waitUntil(10_000) { !vm.state.value.error }
            compose.runOnIdle { vm.pause(false); vm.move(level.reference.first()) }
            compose.waitUntil(10_000) { vm.state.value.attempt?.scoredMoves == 1 }
        } finally {
            val saved = AtomicBoolean(false)
            compose.runOnIdle { vm.lifecycle(false); vm.leave { saved.set(true) } }
            compose.waitUntil(10_000) { saved.get() }
            db.close()
        }
    }

    @Test fun aPauseInvalidatesQueuedInputAndStopsMovesHintsAndTimeUntilResumed() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        val db = Room.inMemoryDatabaseBuilder(app, ParlorDatabase::class.java).build()
        val repo = ProgressRepository(db)
        val catalog = runBlocking { app.content.load() }
        val level = catalog.levels.first()
        repo.configure(catalog, runBlocking { app.content.dailySchedule() })
        runBlocking { repo.refreshDay() }
        lateinit var vm: PlayViewModel
        val visible = mutableStateOf(true)
        val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val release = CompletableDeferred<Unit>()
        compose.setContent {
            if (visible.value) ParlorTheme {
                vm = viewModel(factory = PlayViewModel.factory(level, repo))
                PlayScreen(vm, PlayerSettings(), 3, false, app.feedback, onLeave = {}, onNext = null)
            }
        }
        try {
            compose.waitUntil(10_000) { vm.state.value.attempt != null && !vm.state.value.checkingAccess }
            val entered = CompletableDeferred<Unit>()
            io.launch { db.withTransaction { entered.complete(Unit); release.await() } }
            runBlocking { withTimeout(10_000) { entered.await() } }
            compose.runOnIdle {
                vm.move(level.reference[0]) // Accepted, awaiting its transaction.
                vm.move(level.reference[1]) // Queued behind the first move.
                vm.pause(true)
                vm.pause(false) // Returning quickly must not replay the stale queued input.
            }
            release.complete(Unit)
            compose.waitUntil(10_000) { vm.state.value.attempt?.scoredMoves == 1 && !vm.state.value.busy }
            val saved = AtomicBoolean(false)
            compose.runOnIdle { vm.leave { saved.set(true) } }
            compose.waitUntil(10_000) { saved.get() }
            val baseline = runBlocking { repo.observeAttempt(vm.state.value.attempt!!.id).first()!! }
            val hints = runBlocking { repo.player.first().ledger.dailyHints }
            compose.runOnIdle {
                vm.move(level.reference[1]); vm.undo(); vm.redo(); vm.hint()
                vm.viewport(Viewport(selectedId = "B"))
            }
            Thread.sleep(250)
            saved.set(false)
            compose.runOnIdle { vm.leave { saved.set(true) } }
            compose.waitUntil(10_000) { saved.get() }
            val paused = runBlocking { repo.observeAttempt(baseline.id).first()!! }
            assertEquals(baseline, paused)
            assertEquals(hints, runBlocking { repo.player.first().ledger.dailyHints })
            compose.runOnIdle { vm.pause(false); vm.move(level.reference[1]) }
            compose.waitUntil(10_000) { vm.state.value.attempt?.scoredMoves == 2 }
        } finally {
            release.complete(Unit)
            compose.runOnIdle { vm.lifecycle(false); visible.value = false }
            compose.waitForIdle(); io.cancel(); db.close()
        }
    }
}
