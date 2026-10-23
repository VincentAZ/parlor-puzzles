package com.parlor.core.game

import com.parlor.core.model.*

object BoxPusherEngine : PuzzleEngine {
    override fun validate(board: BoardState): Boolean = board is BoxPusherBoard && board.width in 4..12 && board.height in 4..12 &&
        board.floor.isNotEmpty() && board.floor.distinct().size == board.floor.size && board.floor.all { it in 0 until board.width * board.height } &&
        board.boxes.size in 1..6 && board.boxes.distinct().size == board.boxes.size && board.goals.size == board.boxes.size &&
        board.goals.distinct().size == board.goals.size && board.goals.all { it in board.floor } && board.boxes.all { it in board.floor } &&
        board.worker in board.floor && board.worker !in board.boxes
    override fun isGoal(board: BoardState): Boolean = board is BoxPusherBoard && validate(board) && board.boxes.toSet() == board.goals.toSet()
    fun adjacent(board: BoxPusherBoard, cell: Int, direction: Direction): Int? {
        val x = cell % board.width + direction.dx; val y = cell / board.width + direction.dy
        return if (x in 0 until board.width && y in 0 until board.height) y * board.width + x else null
    }
    /** Reachability treats boxes as obstacles and never pushes. Also valid on a completed layout for authoring. */
    fun walks(board: BoxPusherBoard): Map<Int, Int?> {
        require(validate(board))
        val floor = board.floor.toSet(); val boxes = board.boxes.toSet()
        val previous = mutableMapOf<Int, Int?>(board.worker to null)
        val queue = ArrayDeque<Int>(); queue.add(board.worker)
        while (queue.isNotEmpty()) {
            val at = queue.removeFirst()
            for (d in Direction.entries) {
                val next = adjacent(board, at, d) ?: continue
                if (next in floor && next !in boxes && next !in previous) { previous[next] = at; queue.add(next) }
            }
        }
        return previous
    }
    fun walkingPath(board: BoxPusherBoard, to: Int): List<Int>? {
        val previous = walks(board)
        if (to !in previous) return null
        val path = mutableListOf<Int>()
        var current = to
        while (current != board.worker) { path += current; current = requireNotNull(previous[current]) }
        return path.asReversed()
    }
    override fun apply(board: BoardState, move: Move): BoardState? {
        if (board !is BoxPusherBoard || !validate(board) || isGoal(board)) return null
        return when (move) {
            is WalkTo -> if (move.cell != board.worker && walkingPath(board, move.cell) != null) board.copy(worker = move.cell) else null
            is WorkerStep -> {
                val next = adjacent(board, board.worker, move.direction) ?: return null
                if (next !in board.floor) return null
                if (next !in board.boxes) board.copy(worker = next)
                else {
                    val beyond = adjacent(board, next, move.direction) ?: return null
                    if (beyond !in board.floor || beyond in board.boxes) return null
                    board.copy(worker = next, boxes = (board.boxes - next + beyond).sorted())
                }
            }
            else -> null
        }
    }
    override fun cost(board: BoardState, move: Move): Int = if (board is BoxPusherBoard && move is WorkerStep && adjacent(board, board.worker, move.direction) in board.boxes) 1 else 0
    override fun walkingCost(board: BoardState, move: Move): Int {
        board as BoxPusherBoard
        return when (move) {
            is WalkTo -> walkingPath(board, move.cell)?.size ?: 0
            is WorkerStep -> if (cost(board, move) == 0) 1 else 0
            else -> 0
        }
    }
    override fun legalMoves(board: BoardState): List<Move> = Direction.entries.map(::WorkerStep).filter { apply(board, it) != null }
    /** Squares from which a lone box could never reach any goal, even with all other boxes removed. */
    fun deadSquares(board: BoxPusherBoard): Set<Int> {
        val floor = board.floor.toSet()
        val reachable = board.goals.toMutableSet()
        val queue = ArrayDeque(board.goals)
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            for (d in Direction.entries) {
                val before = adjacent(board, cell, d) ?: continue
                val stand = adjacent(board, before, d) ?: continue
                if (before in floor && stand in floor && reachable.add(before)) queue.add(before)
            }
        }
        return board.floor.toSet() - reachable
    }
    fun deadlocked(board: BoxPusherBoard, dead: Set<Int> = deadSquares(board)): Boolean {
        if (board.boxes.any { it in dead }) return true
        val floor = board.floor.toSet(); val boxes = board.boxes.toSet(); val goals = board.goals.toSet()
        for (y in 0 until board.height - 1) for (x in 0 until board.width - 1) {
            val square = listOf(y * board.width + x, y * board.width + x + 1, (y + 1) * board.width + x, (y + 1) * board.width + x + 1)
            if (square.all { it !in floor || it in boxes } && square.any { it in boxes && it !in goals }) return true
        }
        return false
    }
    override fun search(board: BoardState, maxStates: Int): SearchResult = solveBoxes(board as BoxPusherBoard, maxStates)
}

/** BFS over pushes, merging worker positions in the same reachable region. Walking remains explicit in the reference. */
fun solveBoxes(start: BoxPusherBoard, maxStates: Int = 50_000): SearchResult {
    require(BoxPusherEngine.validate(start) && maxStates > 0)
    if (BoxPusherEngine.isGoal(start)) return SearchResult.Solved(emptyList(), 1)
    val dead = BoxPusherEngine.deadSquares(start)
    if (BoxPusherEngine.deadlocked(start, dead)) return SearchResult.Unsolvable
    data class Node(val board: BoxPusherBoard, val parent: Int, val moves: List<Move>)
    fun key(board: BoxPusherBoard, region: Set<Int>) = board.boxes.sorted() + requireNotNull(region.minOrNull())
    val nodes = mutableListOf(Node(start, -1, emptyList()))
    val seen = mutableSetOf(key(start, BoxPusherEngine.walks(start).keys))
    var cursor = 0
    while (cursor < nodes.size) {
        val state = nodes[cursor].board
        val region = BoxPusherEngine.walks(state).keys
        for (box in state.boxes) for (direction in Direction.entries) {
            val opposite = Direction.entries.single { it.dx == -direction.dx && it.dy == -direction.dy }
            val stand = BoxPusherEngine.adjacent(state, box, opposite) ?: continue
            val to = BoxPusherEngine.adjacent(state, box, direction) ?: continue
            if (stand !in region || to !in state.floor || to in state.boxes || to in dead) continue
            val actions = buildList<Move> { if (stand != state.worker) add(WalkTo(stand)); add(WorkerStep(direction)) }
            val next = state.copy(worker = box, boxes = (state.boxes - box + to).sorted())
            if (BoxPusherEngine.deadlocked(next, dead)) continue
            val nextKey = key(next, BoxPusherEngine.walks(next).keys)
            if (nextKey in seen) continue
            if (nodes.size >= maxStates) return SearchResult.LimitReached(nodes.size)
            seen.add(nextKey); nodes += Node(next, cursor, actions)
            if (BoxPusherEngine.isGoal(next)) {
                val chunks = mutableListOf<List<Move>>()
                var at = nodes.lastIndex
                while (nodes[at].parent != -1) { chunks += nodes[at].moves; at = nodes[at].parent }
                return SearchResult.Solved(chunks.asReversed().flatten(), nodes.size)
            }
        }
        cursor++
    }
    return SearchResult.Unsolvable
}
