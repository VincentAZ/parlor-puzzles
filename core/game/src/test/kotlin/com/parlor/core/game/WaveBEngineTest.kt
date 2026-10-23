package com.parlor.core.game

import com.parlor.core.model.*
import kotlin.math.abs
import kotlin.test.*

class WaveBEngineTest {
    @Test fun tileIdaStarMatchesIndependentDistancesAndParityInEverySupportedSize() {
        // Independent reverse BFS enumerates the whole 3×3 component; no production transitions.
        val goal = (1..8).toList() + 0
        val distances = mutableMapOf(goal to 0)
        val queue = ArrayDeque<List<Int>>(); queue.add(goal)
        while (queue.isNotEmpty()) {
            val state = queue.removeFirst(); val empty = state.indexOf(0)
            for (from in state.indices) if (abs(from / 3 - empty / 3) + abs(from % 3 - empty % 3) == 1) {
                val next = state.toMutableList().also { it[empty] = it[from]; it[from] = 0 }.toList()
                if (next !in distances) { distances[next] = distances.getValue(state) + 1; queue.add(next) }
            }
        }
        assertEquals(181_440, distances.size)
        for (depth in 0..18) {
            val state = distances.entries.first { it.value == depth }.key
            val board = TilesBoard(3, state)
            val solution = solveTiles(board, 100_000) as SearchResult.Solved
            assertEquals(depth, solution.moves.size)
            assertTrue(TilesEngine.isGoal(replay(TilesEngine, board, solution.moves).final))
        }
        for (side in 3..5) {
            val solved = (1 until side * side).toList() + 0
            val impossible = solved.toMutableList().also { it[0] = 2; it[1] = 1 }
            assertFalse(TilesEngine.solvable(TilesBoard(side, impossible)))
            assertEquals(SearchResult.Unsolvable, solveTiles(TilesBoard(side, impossible)))
            val one = solved.toMutableList().also { it[it.lastIndex] = it[it.lastIndex - 1]; it[it.lastIndex - 1] = 0 }
            val image = TilesBoard(side, one, TileArt.GARDEN)
            assertTrue(TilesEngine.solvable(image))
            assertEquals(1, (solveTiles(image) as SearchResult.Solved).moves.size)
            assertEquals(TileArt.GARDEN, (TilesEngine.apply(image, SlideTile(side * side - 1)) as TilesBoard).art)
            assertNull(TilesEngine.apply(image, SlideTile(1)))
            assertNull(TilesEngine.apply(image, SlideTile(0)))
        }
        assertFalse(TilesEngine.validate(TilesBoard(3, List(9) { 1 })))
        assertFalse(TilesEngine.isGoal(TilesBoard(2, listOf(1,2,3,0))))
    }

    @Test fun blockSearchMergesEquivalentShapesWithoutLosingDisplayIdentitiesOrCosts() {
        val board = BlockEscapeBoard(blocks = listOf(EscapeBlock("A",0,0,2,2), EscapeBlock("B",2,0,1,2),
            EscapeBlock("C",3,0,1,2), EscapeBlock("D",1,3,1,1)))
        assertNull(BlockEscapeEngine.apply(board, SlideBlock("A",2,0)))
        assertNull(BlockEscapeEngine.apply(board, SlideBlock("D",0,4)))
        assertNull(BlockEscapeEngine.apply(board, SlideBlock("A",1,5)))
        assertNull(BlockEscapeEngine.apply(board, SlideBlock("D",1,3)))
        val renamed = board.copy(blocks = board.blocks.map { when (it.id) { "B" -> it.copy(id="C"); "C" -> it.copy(id="B"); else -> it } })
        assertEquals(BlockEscapeEngine.searchKey(board), BlockEscapeEngine.searchKey(renamed))
        val labelled = object : PuzzleEngine by BlockEscapeEngine { override fun searchKey(board: BoardState): Any = board }
        val solved = breadthFirst(BlockEscapeEngine, board, 150_000) as SearchResult.Solved
        val independent = breadthFirst(labelled, board, 300_000) as SearchResult.Solved
        assertEquals(independent.moves.size, solved.moves.size)
        assertTrue(solved.visited <= independent.visited)
        assertTrue(BlockEscapeEngine.isGoal(replay(BlockEscapeEngine, board, solved.moves).final))
        assertTrue(solved.moves.filterIsInstance<SlideBlock>().all { m -> board.blocks.any { it.id == m.id } })
        assertFalse(BlockEscapeEngine.validate(board.copy(blocks = board.blocks + board.blocks.first())))
    }

    @Test fun pushSearchAndDeadlockPruningMatchIndependentZeroOneSearch() {
        val floor = (1..3).flatMap { y -> (1..3).map { x -> y * 5 + x } }.toSet()
        for (count in 1..2) {
            val goals = if (count == 1) setOf(8) else setOf(6,8)
            val arrangements = if (count == 1) floor.map { setOf(it) } else floor.flatMap { a -> floor.filter { it > a }.map { b -> setOf(a,b) } }
            for (boxes in arrangements) for (worker in floor - boxes) {
                data class State(val worker: Int, val boxes: Set<Int>)
                val initial = State(worker, boxes)
                val distance = mutableMapOf(initial to 0)
                val queue = ArrayDeque<State>(); queue.add(initial)
                var minimum: Int? = null
                while (queue.isNotEmpty()) {
                    val state = queue.removeFirst(); val cost = distance.getValue(state)
                    if (state.boxes == goals) { minimum = cost; break }
                    for ((dx,dy) in listOf(1 to 0,-1 to 0,0 to 1,0 to -1)) {
                        val nextCell = state.worker + dx + 5 * dy
                        if (nextCell !in floor || abs(nextCell / 5 - state.worker / 5) + abs(nextCell % 5 - state.worker % 5) != 1) continue
                        val push = nextCell in state.boxes
                        val beyond = nextCell + dx + 5 * dy
                        if (push && (beyond !in floor || beyond in state.boxes)) continue
                        val next = State(nextCell, if (push) state.boxes - nextCell + beyond else state.boxes)
                        val nextCost = cost + if (push) 1 else 0
                        if ((distance[next] ?: Int.MAX_VALUE) <= nextCost) continue
                        distance[next] = nextCost
                        if (push) queue.addLast(next) else queue.addFirst(next)
                    }
                }
                val board = BoxPusherBoard(5,5,floor.sorted(),goals.sorted(),boxes.sorted(),worker)
                val actual = solveBoxes(board, 5_000)
                if (minimum == null) assertEquals(SearchResult.Unsolvable, actual, "$board")
                else {
                    actual as SearchResult.Solved
                    val replay = replay(BoxPusherEngine, board, actual.moves)
                    assertEquals(minimum, replay.cost, "$board")
                    assertTrue(BoxPusherEngine.isGoal(replay.final))
                }
            }
        }
    }

    @Test fun walkingNeverPushesAndHistoryPreservesWalkingAndScoredPushes() {
        val board = boxFixture()
        assertNull(BoxPusherEngine.apply(board, WalkTo(board.boxes.single())))
        assertNull(BoxPusherEngine.apply(board, WalkTo(0)))
        val level = boxLevel(board)
        var attempt = Attempt.start("boxes", level)
        attempt = Session.move(BoxPusherEngine, attempt, WalkTo(13))!!
        assertEquals(0, attempt.scoredMoves); assertEquals(1, attempt.walkingSteps)
        attempt = Session.move(BoxPusherEngine, attempt, WorkerStep(Direction.RIGHT))!!
        assertEquals(1, attempt.scoredMoves); assertEquals(1, attempt.walkingSteps)
        attempt = Session.undo(attempt, ScoringUnit.PUSHES)!!
        assertEquals(2, attempt.scoredMoves)
        attempt = Session.redo(BoxPusherEngine, attempt)!!
        assertEquals(3, attempt.scoredMoves)
        assertEquals(attempt, ParlorJson.decodeFromString<Attempt>(ParlorJson.encodeToString(attempt)))
        val walkUndo = Session.undo(Session.move(BoxPusherEngine, Attempt.start("walk",level), WalkTo(13))!!, ScoringUnit.PUSHES)!!
        assertEquals(1, walkUndo.scoredMoves)
        val walkRedo = Session.redo(BoxPusherEngine, walkUndo)!!
        assertEquals(1, walkRedo.scoredMoves); assertEquals(2, walkRedo.walkingSteps)
        val offRoute = Session.move(BoxPusherEngine, Attempt.start("off",level), WalkTo(22))!!
        assertTrue(hintFor(level, offRoute) is Hint.Next)
        var dead = Session.move(BoxPusherEngine, Attempt.start("dead", level), WalkTo(8))!!
        dead = Session.move(BoxPusherEngine, dead, WorkerStep(Direction.DOWN))!!
        assertTrue(BoxPusherEngine.deadlocked(dead.board as BoxPusherBoard))
        assertEquals(2, (hintFor(level, dead) as Hint.Recover).undoCount)
    }

    @Test fun searchBudgetsAndIllegalEdgesDoNotBecomeFalseSolutions() {
        assertTrue(solveBoxes(boxFixture(), 1) is SearchResult.LimitReached)
        assertTrue(solveTiles(TilesBoard(3,listOf(1,2,3,4,5,6,0,7,8)),1) is SearchResult.LimitReached)
        val bad = boxFixture().copy(worker=14)
        assertFalse(BoxPusherEngine.validate(bad))
        val edge = BoxPusherBoard(4,4,(0..15).toList(),listOf(0),listOf(10),3)
        assertNull(BoxPusherEngine.apply(edge,WorkerStep(Direction.RIGHT)))
        assertFalse(BoxPusherEngine.isGoal(edge.copy(boxes=listOf(0),worker=0)))
    }

    private fun boxFixture() = BoxPusherBoard(6,5,(1..3).flatMap { y -> (1..4).map { x -> y*6+x } }, listOf(16),listOf(14),7)
    private fun boxLevel(board: BoxPusherBoard) = LevelDefinition("boxes-test",puzzle=Puzzle.BOX_PUSHER,tier=Tier.EASY,order=1,initial=board,
        reference=listOf(WalkTo(13),WorkerStep(Direction.RIGHT),WorkerStep(Direction.RIGHT)),par=2,parKind=ParKind.PROVEN_OPTIMAL,
        targetTimeMs=60_000,scoringUnit=ScoringUnit.PUSHES,provenance=Provenance("Parlor","fixture",0,"original"))
}
