package com.parlor.puzzles

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.data.*
import com.parlor.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File

class SavedBestRecoveryNavigationTest {
    private val compose = navigationComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(NativeCompactDisplayRule()).around(compose)

    @Test fun invalidStarsOpenRecoveryAndRetryPreservesTheRowUntilItIsRepaired() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ParlorApplication
        val settings = runBlocking { app.settings.settings.first() }
        val level = runBlocking { app.content.load() }.levels.first()
        val dao = app.database.progress()
        val original = runBlocking { dao.best(level.id) }
        fun restoreRow() = runBlocking {
            if (original != null) dao.saveBest(original)
            else withContext(Dispatchers.IO) {
                app.database.openHelper.writableDatabase.execSQL("DELETE FROM bests WHERE levelId = ?", arrayOf(level.id))
            }
        }
        runBlocking { app.settings.update { it.copy(onboarded = true) } }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Find your next puzzle").fetchSemanticsNodes().isNotEmpty() }
                val before = runBlocking { Triple(dao.allAttempts(), dao.allCompletions(), dao.allBests()) }
                val playerBefore = runBlocking { dao.player() }
                val broken = BestRow(level.id, ParlorJson.encodeToString(LevelBest(level.id, 4, 350, 1000, 1, "2026-09-28")), level.puzzle.name)
                runBlocking { dao.saveBest(broken) }
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("storage-recovery").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Your saved room needs attention").assertIsDisplayed()
                compose.onNodeWithTag("storage-reset").assertDoesNotExist()
                compose.onNodeWithText("Find your next puzzle").assertDoesNotExist()
                compose.waitForIdle(); SystemClock.sleep(300)
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                File(app.filesDir, "saved-best-recovery-compact-200.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                assertEquals(StorageIssue(StorageArea.PROGRESS, StorageProblem.DAMAGED), app.storageHealth.state.value.issue)
                scenario.recreate()
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("storage-retry").fetchSemanticsNodes().isNotEmpty() }
                val generation = app.storageHealth.state.value.generation
                compose.onNodeWithTag("storage-retry").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
                compose.waitUntil(15_000) { app.storageHealth.state.value.let { it.generation > generation && it.issue == StorageIssue(StorageArea.PROGRESS, StorageProblem.DAMAGED) } }
                assertEquals(broken, runBlocking { dao.best(level.id) })
                assertEquals(before.first, runBlocking { dao.allAttempts() })
                assertEquals(before.second, runBlocking { dao.allCompletions() })
                assertEquals(playerBefore, runBlocking { dao.player() })
                restoreRow()
                compose.onNodeWithTag("storage-retry").performScrollTo().performClick()
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Find your next puzzle").fetchSemanticsNodes().isNotEmpty() }
                assertNull(app.storageHealth.state.value.issue)
                assertEquals(before, runBlocking { Triple(dao.allAttempts(), dao.allCompletions(), dao.allBests()) })
                assertEquals(playerBefore, runBlocking { dao.player() })
            }
        } finally {
            restoreRow()
            app.storageHealth.retry()
            runBlocking { app.settings.replace(settings) }
        }
    }
}
