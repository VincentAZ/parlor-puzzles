package com.parlor.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.game.Session
import com.parlor.core.game.TrafficJamEngine
import com.parlor.core.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @get:Rule val helper = MigrationTestHelper(instrumentation, ParlorDatabase::class.java)

    @Test fun versionThreeKeepsItsSchemaTwoRecoveryCopyWithoutGrantingPro() = runBlocking {
        val name = "migration-photo-test.db"
        val old = ProgressEnvelope(schemaVersion = 2, snapshotId = "legacy-recovery", settings = PlayerSettings(showTimer = true))
        val json = JsonObject(ParlorJson.encodeToJsonElement(old).jsonObject - "photos").toString()
        helper.createDatabase(name, 3).apply {
            execSQL("INSERT INTO backup_state VALUES (1, ?, NULL, 'previous-import')", arrayOf(json))
            close()
        }
        helper.runMigrationsAndValidate(name, 4, true, ParlorDatabase.MIGRATION_3_4).close()
        val database = ParlorDatabase.open(instrumentation.targetContext, name)
        try {
            val level = LevelDefinition("legacy-tile", puzzle = Puzzle.SLIDING_TILES, tier = Tier.EASY, order = 1,
                initial = TilesBoard(3, listOf(1,2,3,4,5,6,7,0,8)), reference = listOf(SlideTile(8)), par = 1,
                parKind = ParKind.PROVEN_OPTIMAL, targetTimeMs = 60_000, provenance = Provenance("Parlor", "legacy photo migration fixture", 0, "original"))
            val repo = ProgressRepository(database, today = { "2026-09-28" }, isPro = { false })
            repo.configure(Catalog(levels = listOf(level)), DailySchedule(listOf(ScheduleEpoch(1, "2026-09-28", listOf(LevelRef(level.id, 1))))))
            assertEquals(old, repo.recoverySnapshot())
            repo.importProgress(requireNotNull(repo.recoverySnapshot()), PlayerSettings(), recovery = true)
            assertEquals(old.settings, repo.pendingBackupSettings()); assertFalse(repo.hasPro())
            assertTrue(database.progress().photoIds().isEmpty())
            assertNull(repo.startOrResume(level).photoId)
        } finally { database.close(); instrumentation.targetContext.deleteDatabase(name) }
    }

    @Test fun versionOneRetainsBoardHistoryHintsBestsAndRewardReceipts() = runBlocking {
        val name = "migration-test.db"
        val level = LevelDefinition("traffic-v1", puzzle = Puzzle.TRAFFIC_JAM, tier = Tier.EASY, order = 1,
            initial = TrafficJamBoard(pieces = listOf(TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL), TrafficPiece("B", 2, 0, 3, Axis.VERTICAL))),
            reference = listOf(Translate("B", 3), Translate("A", 6)), par = 2, parKind = ParKind.PROVEN_OPTIMAL,
            targetTimeMs = 60_000, provenance = Provenance("Parlor", "fixture", 0, "original"))
        val attempt = Session.move(TrafficJamEngine, Attempt.start("old-attempt", level), level.reference.first())!!
            .copy(hintsUsed = true, activeTimeMs = 1234, viewport = Viewport(selectedId = "B"))
        // Serialize the actual v1 shape, without fields introduced in the new version.
        val legacyAttempt = JsonObject(ParlorJson.encodeToJsonElement(attempt).jsonObject - setOf("dailyEligibility", "undoCount", "photoId"))
        val ledger = RewardLedger("2026-09-28", 2, 4, 2, setOf("old-reward"), setOf("2026-09-28"))
        val legacyLedger = JsonObject(ParlorJson.encodeToJsonElement(ledger).jsonObject -
            setOf("dailyClaims", "ads", "rewardedViewsByDate", "earnedHintsSpent", "hintedAttemptIds"))
        val best = LevelBest(level.id, 2, 250, 1000, 2, "2026-09-28")
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO attempts VALUES (?, ?, ?, ?, ?, ?)", arrayOf(attempt.id, level.id, "CAMPAIGN", 0, 100, legacyAttempt.toString()))
            execSQL("INSERT INTO player VALUES (1, ?, 1234)", arrayOf(legacyLedger.toString()))
            execSQL("INSERT INTO bests VALUES (?, ?)", arrayOf(level.id, ParlorJson.encodeToString(best)))
            execSQL("INSERT INTO completions VALUES ('finished-before-upgrade', '2026-09-28')")
            close()
        }
        helper.runMigrationsAndValidate(name, 4, true, ParlorDatabase.MIGRATION_1_2, ParlorDatabase.MIGRATION_2_3, ParlorDatabase.MIGRATION_3_4).close()
        val database = ParlorDatabase.open(instrumentation.targetContext, name)
        try {
            val repo = ProgressRepository(database, today = { "2026-09-28" })
            repo.configure(Catalog(levels = listOf(level)), DailySchedule(listOf(ScheduleEpoch(1, "2026-09-28", listOf(LevelRef(level.id, 1))))))
            assertEquals(attempt, repo.startOrResume(level))
            assertEquals(listOf(best), repo.bests.first())
            assertEquals(ledger, repo.player.first().ledger)
            assertEquals(1234L, repo.player.first().profile.playTimeByPuzzle[Puzzle.TRAFFIC_JAM])
            assertEquals(1, repo.completionCount.first())
            assertTrue(repo.dailies.first().isEmpty())
            repo.refreshDay()
            assertEquals(2, repo.player.first().ledger.dailyHints)
            assertTrue(repo.move(level, attempt.id, level.reference.last(), 1)!!.completed)
            assertEquals(2, repo.completionCount.first())
        } finally {
            database.close()
            instrumentation.targetContext.deleteDatabase(name)
        }
    }
}
