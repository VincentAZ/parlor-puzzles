package com.parlor.core.game

import com.parlor.core.model.*
import kotlin.math.abs

data class PegGeometry(val width: Int, val height: Int, val holes: Set<Int>, val directions: List<Pair<Int,Int>>)

fun pegGeometry(layout: PegLayout): PegGeometry {
    val side = if (layout == PegLayout.TRIANGLE) 5 else 7
    val holes = (0 until side * side).filter { cell ->
        val x = cell % side; val y = cell / side
        when (layout) {
            PegLayout.ENGLISH -> x in 2..4 || y in 2..4
            PegLayout.EUROPEAN -> x in 2..4 || y in 2..4 || (x in 1..5 && y in 1..5)
            PegLayout.TRIANGLE -> x <= y
            PegLayout.DIAMOND -> abs(x-3) + abs(y-3) <= 3
        }
    }.toSet()
    val axes = if (layout == PegLayout.TRIANGLE) listOf(1 to 0, 0 to 1, 1 to 1) else listOf(1 to 0, 0 to 1)
    return PegGeometry(side, side, holes, axes + axes.map { -it.first to -it.second })
}

object PegEngine : PuzzleEngine {
    override fun validate(board: BoardState): Boolean {
        if (board !is PegBoard) return false
        val holes = pegGeometry(board.layout).holes
        return board.pegs.isNotEmpty() && board.pegs.distinct().size == board.pegs.size &&
            board.pegs.all { it in holes } && board.goal in holes
    }
    override fun isGoal(board: BoardState): Boolean = board is PegBoard && validate(board) && board.pegs == listOf(board.goal)
    fun jumped(board: PegBoard, move: JumpPeg): Int? {
        val geometry = pegGeometry(board.layout)
        if (move.from !in geometry.holes || move.to !in geometry.holes) return null
        val dx = move.to % geometry.width - move.from % geometry.width
        val dy = move.to / geometry.width - move.from / geometry.width
        if (dx % 2 != 0 || dy % 2 != 0 || dx/2 to dy/2 !in geometry.directions) return null
        return ((move.from / geometry.width + move.to / geometry.width)/2) * geometry.width +
            (move.from % geometry.width + move.to % geometry.width)/2
    }
    override fun apply(board: BoardState, move: Move): BoardState? {
        if (board !is PegBoard || move !is JumpPeg || !validate(board) || isGoal(board)) return null
        val over = jumped(board, move) ?: return null
        if (move.from !in board.pegs || over !in board.pegs || move.to in board.pegs) return null
        return board.copy(pegs = (board.pegs - move.from - over + move.to).sorted())
    }
    override fun legalMoves(board: BoardState): List<Move> {
        if (board !is PegBoard || !validate(board) || isGoal(board)) return emptyList()
        val geometry = pegGeometry(board.layout)
        return board.pegs.flatMap { from -> geometry.directions.mapNotNull { (dx,dy) ->
            val x = from % geometry.width + 2*dx; val y = from / geometry.width + 2*dy
            if (x !in 0 until geometry.width || y !in 0 until geometry.height) null
            else JumpPeg(from,y*geometry.width+x).takeIf { apply(board,it) != null }
        } }
    }
    override fun searchKey(board: BoardState): Any = (board as PegBoard).pegs.sorted()
    override fun search(board: BoardState, maxStates: Int): SearchResult = solvePegs(board as PegBoard,maxStates)
}

/** Every solution has pegs-1 jumps. Depth-first search has an explicit distinct-state budget. */
fun solvePegs(board: PegBoard, maxStates: Int = 20_000): SearchResult {
    require(PegEngine.validate(board) && maxStates > 0)
    val seen = mutableSetOf<List<Int>>()
    val route = mutableListOf<Move>()
    var exhausted = false
    fun visit(state: PegBoard): Boolean {
        if (PegEngine.isGoal(state)) return true
        val key = state.pegs.sorted()
        if (key in seen) return false
        if (seen.size >= maxStates) { exhausted = true; return false }
        seen.add(key)
        for (move in PegEngine.legalMoves(state)) {
            route += move
            if (visit(PegEngine.apply(state,move) as PegBoard)) return true
            route.removeAt(route.lastIndex)
            if (exhausted) return false
        }
        return false
    }
    return if (visit(board)) SearchResult.Solved(route.toList(),seen.size)
        else if (exhausted) SearchResult.LimitReached(seen.size) else SearchResult.Unsolvable
}
