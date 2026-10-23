package com.parlor.core.game

import com.parlor.core.model.*
import kotlin.test.*
import kotlin.random.Random

class WaveAEngineTest {
    @Test fun malformedBoardsCannotClaimCompletion() {
        val invalid = listOf(
            HanoiEngine to HanoiBoard(listOf(emptyList(), emptyList(), listOf(1, 2))),
            JugsEngine to JugsBoard(listOf(3, 5), listOf(4, 0), 4),
            SwitchEngine to SwitchBoard(8, List(9) { false }),
            PancakeEngine to PancakeBoard(listOf(1, 2)),
        )
        invalid.forEach { (engine, board) -> assertFalse(engine.isGoal(board)) }
    }

    @Test fun hanoiReferencesAndArbitraryContinuationsObeyEveryStackRule() {
        for (pegs in 3..4) for (n in 3..10) {
            val initial = HanoiBoard(List(pegs) { if (it == 0) (n downTo 1).toList() else emptyList() })
            val route = hanoiReference(n, pegs)
            assertTrue(HanoiEngine.isGoal(replay(HanoiEngine, initial, route).final))
            if (pegs == 3) assertEquals((1 shl n) - 1, route.size)
            if (pegs == 4 && n <= 5) assertEquals(route.size, (breadthFirst(HanoiEngine, initial) as SearchResult.Solved).moves.size)
        }
        for (pegs in 3..4) for (assignment in 0 until if (pegs == 3) 81 else 256) {
            var encoded = assignment
            val stacks = List(pegs) { mutableListOf<Int>() }
            for (disk in 4 downTo 1) { stacks[encoded % pegs] += disk; encoded /= pegs }
            val board = HanoiBoard(stacks.map { it.toList() })
            val route = requireNotNull(HanoiEngine.continuation(board))
            assertTrue(HanoiEngine.isGoal(replay(HanoiEngine, board, route).final))
        }
        val invalid = HanoiBoard(listOf(listOf(1, 3), listOf(2), emptyList()))
        assertFalse(HanoiEngine.validate(invalid))
        val blocked = HanoiBoard(listOf(listOf(3), listOf(2,1), emptyList()))
        assertNull(HanoiEngine.apply(blocked, TransferDisk(0, 1)))
        assertNull(HanoiEngine.apply(blocked, TransferDisk(-1, 1)))
        assertNull(HanoiEngine.apply(blocked, TransferDisk(2, 1)))
    }

    @Test fun switchAlgebraMatchesAnIndependentExhaustiveMinimumAndFindsUnsolvableBoards() {
        for (side in 3..4) {
            val n = side * side
            // Enumerate press sets independently using row/column Manhattan distance.
            val best = mutableMapOf<Int, Int>()
            for (presses in 0 until (1 shl n)) {
                var lights = 0
                for (cell in 0 until n) {
                    var parity = false
                    for (button in 0 until n) if (presses and (1 shl button) != 0 &&
                        kotlin.math.abs(cell / side - button / side) + kotlin.math.abs(cell % side - button % side) <= 1) parity = !parity
                    if (parity) lights = lights or (1 shl cell)
                }
                val count = presses.countOneBits()
                best[lights] = minOf(best[lights] ?: n, count)
            }
            for ((bits, cost) in best.entries.filterIndexed { i, _ -> i % 17 == 0 }) {
                val board = SwitchBoard(side, List(n) { bits and (1 shl it) != 0 })
                val solved = requireNotNull(solveSwitch(board))
                assertTrue(solved.provenOptimal)
                assertEquals(cost, solved.moves.size)
                assertTrue(SwitchEngine.isGoal(replay(SwitchEngine, board, solved.moves).final))
            }
            (0 until (1 shl n)).firstOrNull { it !in best }?.let { bits ->
                assertNull(solveSwitch(SwitchBoard(side, List(n) { bits and (1 shl it) != 0 })))
            }
        }
        val lights = MutableList(64) { false }
        listOf(0,7,8,55,63).forEach { p -> SwitchEngine.affected(8, p).forEach { lights[it] = !lights[it] } }
        val board = SwitchBoard(8, lights)
        assertTrue(SwitchEngine.isGoal(replay(SwitchEngine, board, requireNotNull(solveSwitch(board)).moves).final))
        assertNull(SwitchEngine.apply(board, Toggle(64)))
        assertFalse(SwitchEngine.validate(board.copy(lights = lights.dropLast(1))))
    }

    @Test fun jugPoursConserveWaterAndStopExactlyAtCapacityOrAnEmptySource() {
        val start = JugsBoard(listOf(3,5,7), listOf(3,2,0), 6, 2)
        val poured = JugsEngine.apply(start, PourJug(0,1)) as JugsBoard
        assertEquals(listOf(0,5,0), poured.amounts)
        assertEquals(start.amounts.sum(), poured.amounts.sum())
        assertNull(JugsEngine.apply(start, PourJug(0,0)))
        assertNull(JugsEngine.apply(start, FillJug(0)))
        assertNull(JugsEngine.apply(start, EmptyJug(2)))
        assertFalse(JugsEngine.isGoal(start.copy(amounts = listOf(3,5,0))))
        assertTrue(JugsEngine.isGoal(start.copy(amounts = listOf(0,0,6))))
        val level = JugsBoard(listOf(3,5), listOf(0,0), 4)
        val solution = breadthFirst(JugsEngine, level) as SearchResult.Solved
        assertEquals(6, solution.moves.size)
        assertTrue(JugsEngine.isGoal(replay(JugsEngine, level, solution.moves).final))
        assertEquals(SearchResult.Unsolvable, breadthFirst(JugsEngine, JugsBoard(listOf(2,4), listOf(0,0), 3)))
        assertFalse(JugsEngine.validate(start.copy(amounts = listOf(4,0,0))))
    }

    @Test fun pancakeAStarMatchesBfsAndAllLargeStacksHaveVerifiedContinuations() {
        fun permutations(xs: List<Int>): List<List<Int>> = if (xs.isEmpty()) listOf(emptyList()) else xs.flatMap { v -> permutations(xs - v).map { listOf(v) + it } }
        for (stack in permutations((1..4).toList())) {
            val board = PancakeBoard(stack)
            val aStar = solvePancake(board) as SearchResult.Solved
            val bfs = breadthFirst(PancakeEngine, board) as SearchResult.Solved
            assertEquals(bfs.moves.size, aStar.moves.size)
            assertTrue(PancakeEngine.isGoal(replay(PancakeEngine, board, aStar.moves).final))
        }
        val random = Random(17)
        for (n in 4..12) repeat(10) {
            val board = PancakeBoard((1..n).shuffled(random))
            assertTrue(PancakeEngine.isGoal(replay(PancakeEngine, board, requireNotNull(PancakeEngine.continuation(board))).final))
        }
        val board = PancakeBoard(listOf(3,1,4,2))
        assertNull(PancakeEngine.apply(board, FlipPrefix(1)))
        assertNull(PancakeEngine.apply(board, FlipPrefix(5)))
        assertFalse(PancakeEngine.validate(PancakeBoard(listOf(1,2,2,4))))
        assertTrue(solvePancake(board, 1) is SearchResult.LimitReached)
    }

    @Test fun everyWaveABoardRoundTripsWithHistoryUndoRedoAndVerifiedOffRouteHints() {
        val boards = listOf<BoardState>(HanoiBoard(listOf(listOf(3,2,1), emptyList(), emptyList())),
            SwitchBoard(3, listOf(true,true,false,true,false,false,false,false,false)),
            JugsBoard(listOf(3,5), listOf(0,0), 4), PancakeBoard(listOf(3,1,4,2)))
        for ((puzzle, board) in listOf(Puzzle.HANOI, Puzzle.SWITCH_GRID, Puzzle.WATER_JUGS, Puzzle.PANCAKE_FLIP).zip(boards)) {
            val engine = engineFor(puzzle)
            val reference = engine.continuation(board) ?: (breadthFirst(engine, board) as SearchResult.Solved).moves
            val level = LevelDefinition("test-${puzzle.name.lowercase()}", puzzle = puzzle, tier = Tier.EASY, order = 1,
                initial = board, reference = reference, par = reference.size, parKind = ParKind.REFERENCE, targetTimeMs = 60_000,
                provenance = Provenance("Parlor", "fixture", 0, "original"))
            val start = Attempt.start("attempt", level)
            val action = engine.legalMoves(board).last()
            val moved = Session.move(engine, start, action)!!
            val wire = ParlorJson.encodeToString(moved)
            assertEquals(moved, ParlorJson.decodeFromString<Attempt>(wire))
            if (!moved.completed) {
                val undone = Session.undo(moved, level.scoringUnit)!!
                assertEquals(board, undone.board)
                assertEquals(2, undone.scoredMoves)
                val redone = Session.redo(engine, undone)!!
                assertEquals(moved.board, redone.board)
                assertEquals(3, redone.scoredMoves)
                val hint = hintFor(level, moved)
                assertTrue(hint is Hint.Next || hint is Hint.Recover)
            }
        }
    }
}
