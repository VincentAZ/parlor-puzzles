package com.parlor.puzzles

import androidx.compose.ui.test.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.data.*
import com.parlor.core.model.*
import com.parlor.feature.library.ParlorTheme
import com.parlor.feature.play.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AccessReturnTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aWarmReturnChecksExpiryAndRejectsAlreadyQueuedInput() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        val db = Room.inMemoryDatabaseBuilder(app, ParlorDatabase::class.java).build()
        var wallTime = 1_000L
        val repo = ProgressRepository(db, today = { "2026-09-28" }, wallTime = { wallTime })
        val catalog = runBlocking { app.content.load() }
        val level = catalog.levels.first { it.tier == Tier.EXPERT }
        repo.configure(catalog, runBlocking { app.content.dailySchedule() })
        runBlocking { assertTrue(repo.grantReward("expiry-test", Reward.LevelAccess(level.id))) }
        lateinit var vm: PlayViewModel
        val visible = mutableStateOf(true)
        compose.setContent {
            if (visible.value) ParlorTheme {
                vm = viewModel(factory = PlayViewModel.factory(level, repo))
                PlayScreen(vm, PlayerSettings(), 3, false, app.feedback, onLeave = {}, onNext = null)
            }
        }
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("piece-A").fetchSemanticsNodes().isNotEmpty() }
            val original = runBlocking { repo.resume.first()!! }
            compose.runOnIdle {
                vm.lifecycle(false)
                wallTime += 86_400_000
                vm.lifecycle(true)
                // Simulate a touch delivered as the resume check starts.
                vm.move(level.reference.first())
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("This puzzle is no longer available through your current access. Your saved progress is safe. Return to choose another puzzle.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("piece-A").assertDoesNotExist()
            val saved = runBlocking { repo.observeAttempt(original.id).first()!! }
            assertEquals(original.board, saved.board)
            assertEquals(0, saved.scoredMoves)
            assertTrue(saved.history.isEmpty())
        } finally {
            compose.runOnIdle { vm.lifecycle(false); visible.value = false }
            compose.waitForIdle()
            db.close()
        }
    }
}
