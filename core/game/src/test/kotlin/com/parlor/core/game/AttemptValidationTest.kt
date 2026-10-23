package com.parlor.core.game

import com.parlor.core.model.*
import java.io.File
import kotlin.test.*

class AttemptValidationTest {
    private val level = LevelDefinition("backup-traffic", puzzle = Puzzle.TRAFFIC_JAM, tier = Tier.EASY, order = 1,
        initial = TrafficJamBoard(pieces = listOf(TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL))),
        reference = listOf(Translate("A", 6)), par = 1, parKind = ParKind.PROVEN_OPTIMAL,
        targetTimeMs = 60_000, provenance = Provenance("Parlor", "backup fixture", 0, "original"))

    @Test fun everyBundledFamilyAcceptsSavedForwardUndoRedoAndCompletedStates() {
        val assets = File("../../app/src/main/assets/levels")
        val index = ParlorJson.decodeFromString<CatalogIndex>(File(assets, "catalog-index.json").readText())
        val levels = index.files.flatMap { ParlorJson.decodeFromString<Catalog>(File(assets, it).readText()).levels }
        assertEquals(Puzzle.entries.toSet(), levels.map { it.puzzle }.toSet())
        for (level in levels) {
            val engine = engineFor(level.puzzle)
            var attempt = Attempt.start("test-${level.id}", level)
            AttemptValidation.validate(level, attempt)
            for ((index, move) in level.reference.withIndex()) {
                attempt = requireNotNull(Session.move(engine, attempt, move))
                if (attempt.completed) attempt = attempt.copy(history = emptyList(), redo = emptyList())
                if (index == 0 && !attempt.completed) {
                    val undone = requireNotNull(Session.undo(attempt, level.scoringUnit))
                    AttemptValidation.validate(level, undone)
                    attempt = requireNotNull(Session.redo(engine, undone))
                }
                if (index == 0 || index == level.reference.lastIndex) AttemptValidation.validate(level, attempt)
            }
        }
    }

    @Test fun malformedGeometryHistoryCostsAndCompletionCannotEnterStorage() {
        val started = Attempt.start("one", level)
        val moved = requireNotNull(Session.move(TrafficJamEngine, started, Translate("A", 1)))
        val board = moved.board as TrafficJamBoard
        assertFails { AttemptValidation.validate(level, moved.copy(board = board.copy(width = 7))) }
        assertFails { AttemptValidation.validate(level, moved.copy(history = emptyList())) }
        assertFails { AttemptValidation.validate(level, moved.copy(history = moved.history.map { it.copy(cost = 0) })) }
        assertFails { AttemptValidation.validate(level, moved.copy(completed = true, history = emptyList())) }
        assertFails { AttemptValidation.validate(level, moved.copy(redo = moved.history)) }
        assertFails { AttemptValidation.validate(level, moved.copy(viewport = Viewport(zoom = 0))) }
        assertFails { AttemptValidation.validate(level, moved.copy(levelRevision = 2)) }
        assertFails { AttemptValidation.validate(level, moved.copy(mode = AttemptMode.DEMONSTRATION)) }
        assertFails { AttemptValidation.validate(level, moved.copy(undoCount = 10)) }
        assertFails { AttemptValidation.validate(level, moved.copy(backtracks = 1, undoCount = 1)) }
    }
}
