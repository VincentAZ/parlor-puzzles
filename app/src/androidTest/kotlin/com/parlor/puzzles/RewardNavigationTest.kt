package com.parlor.puzzles

import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.parlor.core.data.PlayerRow
import com.parlor.core.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class RewardNavigationTest {
    @get:Rule val compose = navigationComposeRule()

    @Test fun allThreeOffersCanBeDeclinedWithoutAdsOrRewardsAndThePuzzleRemainsPlayable() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ParlorApplication
        assumeTrue(!app.billing.configured)
        val catalog = runBlocking {
            val catalog = app.content.load()
            app.progress.configure(catalog, app.content.dailySchedule())
            app.progress.resetProgress()
            app.database.progress().savePlayer(PlayerRow(ledgerJson = ParlorJson.encodeToString(RewardLedger())))
            app.progress.refreshDay()
            app.settings.update { it.copy(onboarded = true, tutorialsSeen = Puzzle.entries.toSet()) }
            val daily = app.progress.startDaily()
            daily.level.reference.forEach { app.progress.move(daily.level, daily.attempt.id, it, 10) }
            catalog
        }
        val hard = catalog.levels.first { it.puzzle == Puzzle.TRAFFIC_JAM && it.tier == Tier.HARD }
        val easy = catalog.levels.first { it.puzzle == Puzzle.TRAFFIC_JAM && it.tier == Tier.EASY }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Find your next puzzle").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Find your next puzzle").performScrollTo().performClick()
            compose.onNodeWithTag("level-list").performScrollToNode(hasTestTag("level-${hard.id}"))
            compose.onNodeWithTag("level-${hard.id}").performClick()
            compose.onNodeWithText("Earn 24-hour access").performClick()
            compose.onNodeWithText("Time to explore").assertExists()
            compose.onNodeWithText("Watch an ad").assertIsNotEnabled()
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithTag("level-list").performScrollToNode(hasTestTag("level-${easy.id}"))
            compose.onNodeWithTag("level-${easy.id}").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Get 2 hints with an ad").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Get 2 hints with an ad").performScrollTo().performClick()
            compose.onNodeWithText("A little help").assertExists()
            compose.onNodeWithText("Watch an ad").assertIsNotEnabled()
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithTag("piece-${(easy.reference.first() as Translate).pieceId}").performScrollTo().assertHasClickAction()
            compose.onNodeWithText("Back").performScrollTo().performClick()
            compose.onNodeWithText("Back").assertIsDisplayed().performClick()
            compose.onNodeWithText("Daily Challenge").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Get a scored retry with an ad").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Get a scored retry with an ad").performScrollTo().performClick()
            compose.onNodeWithText("Another try for stars").assertExists()
            compose.onNodeWithText("Watch an ad").assertIsNotEnabled()
            compose.onNodeWithText("Close").performClick()
            assertFalse(MobileAds.isInitialized)
            val ledger = runBlocking { app.progress.player.first().ledger }
            assertEquals(0, ledger.rewardedViews); assertEquals(0, ledger.earnedHints)
            assertTrue(ledger.temporaryUnlocks.isEmpty())
            assertEquals(0, ledger.dailyClaims.getValue(app.progress.localDate()).earnedRetries)
        }
    }
}
