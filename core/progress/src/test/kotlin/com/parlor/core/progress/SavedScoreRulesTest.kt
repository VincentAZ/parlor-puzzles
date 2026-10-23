package com.parlor.core.progress

import com.parlor.core.game.AttemptValidation
import com.parlor.core.model.*
import kotlin.test.*

class SavedScoreRulesTest {
    private val level = LevelDefinition("saved-best-test", puzzle = Puzzle.TRAFFIC_JAM, tier = Tier.EASY, order = 1,
        initial = TrafficJamBoard(pieces = listOf(TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL))),
        reference = listOf(Translate("A", 6)), par = 1, parKind = ParKind.PROVEN_OPTIMAL,
        targetTimeMs = 60_000, provenance = Provenance("Parlor", "saved score fixture", 0, "original"))
    private val good = LevelBest(level.id, 2, 250, 10_000, 2, "2026-09-28")
    private fun backup(best: LevelBest) = ProgressEnvelope(snapshotId = "saved-score", bests = listOf(best))
    private val catalog = Catalog(levels = listOf(level))

    @Test fun validBoundaryScoresAndIndependentBestsKeepTheirOriginalValues() {
        for (stars in 1..3) for (points in listOf(stars * 100, stars * 100 + 50)) {
            for (time in listOf(0L, AttemptValidation.MAX_TIME)) for (moves in listOf(0, 100_000_000)) {
                val best = good.copy(stars = stars, points = points, timeMs = time, moves = moves, firstCompletedDate = "2024-02-29")
                SavedScoreRules.best(best); BackupRules.validate(backup(best), catalog)
                assertEquals(best, ParlorJson.decodeFromString<LevelBest>(ParlorJson.encodeToString(best)))
            }
        }
        val improved = Scoring.improve(level.id, good, Result(3, 320, 20_000, 1), "2026-09-29")
        SavedScoreRules.best(improved)
        assertEquals(LevelBest(level.id, 3, 320, 10_000, 1, good.firstCompletedDate), improved)
    }

    @Test fun invalidStoredFieldsAndBackupsUseTheSameScoreAndCalendarBounds() {
        val invalid = listOf(good.copy(stars = 0), good.copy(stars = -1), good.copy(stars = 4), good.copy(stars = Int.MAX_VALUE),
            good.copy(points = 199), good.copy(points = 251), good.copy(points = Int.MAX_VALUE),
            good.copy(moves = -1), good.copy(moves = 100_000_001), good.copy(timeMs = -1), good.copy(timeMs = AttemptValidation.MAX_TIME + 1),
            good.copy(firstCompletedDate = "2026-02-29"), good.copy(firstCompletedDate = "2026-9-28"),
            good.copy(firstCompletedDate = "2026-09-28T12:00:00"), good.copy(levelId = ""),
            good.copy(levelId = "a".repeat(201)), good.copy(levelId = "saved\nlevel"))
        invalid.forEach { best ->
            assertFailsWith<IllegalArgumentException>("Stored best: $best") { SavedScoreRules.best(best) }
            assertFailsWith<IllegalArgumentException>("Backup best: $best") { BackupRules.validate(backup(best), catalog) }
        }
    }
}
