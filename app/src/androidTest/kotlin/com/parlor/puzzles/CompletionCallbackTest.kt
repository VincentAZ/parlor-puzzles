package com.parlor.puzzles

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.model.*
import com.parlor.core.platform.ads.AdPlacement
import com.parlor.feature.library.ParlorTheme
import com.parlor.feature.play.PlayScreen
import com.parlor.feature.play.PlayViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CompletionCallbackTest {
    @get:Rule val compose = createComposeRule()

    @Test fun resultsCallbackWaitsForTheFinalSlideAndUsesResultsPlacement() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        val catalog = runBlocking { app.content.load() }
        val level = catalog.levels.first { it.puzzle == Puzzle.TRAFFIC_JAM && it.tier == Tier.EASY }
        val attempt = runBlocking {
            app.progress.configure(catalog, app.content.dailySchedule())
            val fresh = app.progress.restart(level, app.progress.startOrResume(level).id)
            level.reference.dropLast(1).forEach { app.progress.move(level, fresh.id, it, 10) }
            fresh
        }
        var placement = AdPlacement.PLAY
        val callbackPlacements = mutableListOf<AdPlacement>()
        compose.setContent {
            ParlorTheme {
                val vm: PlayViewModel = viewModel(factory = PlayViewModel.factory(level, app.progress))
                PlayScreen(vm, PlayerSettings(sfxVolume = 0f, haptics = false, reduceMotion = false), 3, false,
                    app.feedback, onLeave = {}, onNext = null,
                    onPlacement = { placement = it }, onCompleted = { callbackPlacements += placement })
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("piece-A").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val saved = runBlocking { app.progress.move(level, attempt.id, level.reference.last(), 10) }
        assertTrue(requireNotNull(saved).completed)
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("piece-A").assertExists()
        assertEquals(AdPlacement.PLAY, placement)
        assertTrue(callbackPlacements.isEmpty())
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("A clear way through.").assertExists()
        assertEquals(listOf(AdPlacement.RESULTS), callbackPlacements)
        compose.mainClock.advanceTimeBy(400)
        assertEquals(listOf(AdPlacement.RESULTS), callbackPlacements)
        compose.mainClock.autoAdvance = true
    }
}
