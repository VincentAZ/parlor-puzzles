package com.parlor.core.game

import com.parlor.core.model.*
import kotlin.test.*

class TrafficJamTest {
    private val board = TrafficJamBoard(pieces = listOf(
        TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL),
        TrafficPiece("B", 2, 0, 3, Axis.VERTICAL),
        TrafficPiece("C", 3, 3, 2, Axis.HORIZONTAL),
    ))
    private val level = LevelDefinition("traffic-test", puzzle = Puzzle.TRAFFIC_JAM, tier = Tier.EASY, order = 1,
        initial = board, reference = listOf(Translate("B", 3), Translate("A", 6)), par = 2,
        parKind = ParKind.PROVEN_OPTIMAL, targetTimeMs = 60_000,
        provenance = Provenance("Parlor", "test fixture", 0, "original"))

    @Test fun rejectsCollisionIncludingSweptPathAndOutOfBounds() {
        assertTrue(TrafficJamEngine.validate(board))
        assertNull(TrafficJamEngine.apply(board, Translate("A", 4)))
        assertNull(TrafficJamEngine.apply(board, Translate("A", 6)))
        assertNull(TrafficJamEngine.apply(board, Translate("B", 4)))
        assertNull(TrafficJamEngine.apply(board, Translate("C", 6)))
        assertNull(TrafficJamEngine.apply(board, Translate("C", 3)))
        assertNull(TrafficJamEngine.apply(board, Translate("missing", 0)))
    }

    @Test fun validatesGeometryAndIdentity() {
        assertFalse(TrafficJamEngine.validate(board.copy(pieces = board.pieces + board.pieces.first())))
        assertFalse(TrafficJamEngine.validate(board.copy(exitRow = 0)))
        assertFalse(TrafficJamEngine.validate(board.copy(pieces = board.pieces.map { it.copy(x = 0) })))
    }

    @Test fun solverAndReferenceAgreeOnAchievableOptimum() {
        verifyReference(level)
        val result = assertIs<SearchResult.Solved>(breadthFirst(TrafficJamEngine, board))
        assertEquals(2, result.moves.size)
        assertTrue(TrafficJamEngine.isGoal(replay(TrafficJamEngine, board, result.moves).final))
        assertIs<SearchResult.LimitReached>(breadthFirst(TrafficJamEngine, board, 1))
    }

    @Test fun undoRedoPenaltiesAndHintUseSurviveSerialization() {
        val initial = Attempt.start("attempt", level)
        val moved = requireNotNull(Session.move(TrafficJamEngine, initial, Translate("B", 3))).copy(hintsUsed = true)
        val undone = requireNotNull(Session.undo(moved, ScoringUnit.MOVES))
        assertEquals(board, undone.board)
        assertEquals(2, undone.scoredMoves)
        val saved = ParlorJson.decodeFromString<Attempt>(ParlorJson.encodeToString(undone))
        val redone = requireNotNull(Session.redo(TrafficJamEngine, saved))
        assertEquals(moved.board, redone.board)
        assertEquals(3, redone.scoredMoves)
        assertTrue(redone.hintsUsed)
        assertTrue(redone.redo.isEmpty())
    }

    @Test fun hintFallsBackToExplicitRecoveryWithoutMutatingBoard() {
        val first = requireNotNull(Session.move(TrafficJamEngine, Attempt.start("attempt", level), Translate("C", 4)))
        val hint = assertIs<Hint.Recover>(hintFor(level, first, 1))
        assertEquals(1, hint.undoCount)
        assertEquals(Translate("B", 3), hint.nextMove)
        assertEquals(4, (first.board as TrafficJamBoard).pieces.last().x)
    }

    @Test fun allReachableActionsPreserveInvariantsAndCanBeUndone() {
        val queue = ArrayDeque<TrafficJamBoard>()
        val seen = mutableSetOf(board)
        queue.add(board)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (move in TrafficJamEngine.legalMoves(current)) {
                val next = TrafficJamEngine.apply(current, move) as TrafficJamBoard
                assertTrue(TrafficJamEngine.validate(next))
                if (!TrafficJamEngine.isGoal(next)) {
                    val p = current.pieces.first { it.id == (move as Translate).pieceId }
                    assertEquals(current, TrafficJamEngine.apply(next, Translate(p.id, p.position)))
                }
                if (seen.add(next)) queue.add(next)
            }
        }
        assertTrue(seen.size > 20)
    }
}
