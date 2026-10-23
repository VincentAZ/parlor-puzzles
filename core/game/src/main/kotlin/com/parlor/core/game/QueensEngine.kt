package com.parlor.core.game

import com.parlor.core.model.*
import kotlin.math.abs

object QueensEngine : PuzzleEngine {
    fun attacks(side: Int, a: Int, b: Int): Boolean = a/side == b/side || a%side == b%side || abs(a/side-b/side) == abs(a%side-b%side)
    override fun validate(board: BoardState): Boolean = board is QueensBoard && board.side in 4..8 &&
        board.queens.size <= board.side && board.queens.distinct().size == board.queens.size &&
        board.fixed.distinct().size == board.fixed.size && board.fixed.all { it in board.queens } &&
        board.blocked.distinct().size == board.blocked.size && board.blocked.all { it in 0 until board.side*board.side } &&
        board.queens.all { it in 0 until board.side*board.side && it !in board.blocked } &&
        board.queens.indices.all { i -> (i+1 until board.queens.size).none { j -> attacks(board.side,board.queens[i],board.queens[j]) } }
    override fun isGoal(board: BoardState): Boolean = board is QueensBoard && validate(board) && board.queens.size == board.side
    override fun apply(board: BoardState, move: Move): BoardState? {
        if (board !is QueensBoard || !validate(board) || isGoal(board)) return null
        return when (move) {
            is PlaceQueen -> if (move.cell in 0 until board.side*board.side && move.cell !in board.blocked &&
                board.queens.none { attacks(board.side,it,move.cell) }) board.copy(queens=(board.queens+move.cell).sorted()) else null
            is RemoveQueen -> if (move.cell in board.queens && move.cell !in board.fixed) board.copy(queens=board.queens-move.cell) else null
            else -> null
        }
    }
    override fun legalMoves(board: BoardState): List<Move> {
        if (board !is QueensBoard || !validate(board) || isGoal(board)) return emptyList()
        return (0 until board.side*board.side).mapNotNull { PlaceQueen(it).takeIf { move -> apply(board,move) != null } } +
            (board.queens-board.fixed.toSet()).map(::RemoveQueen)
    }
    override fun continuation(board: BoardState): List<Move>? {
        board as QueensBoard
        val extension = solveQueens(board)
        if (extension is SearchResult.Solved) return extension.moves
        // If current placements cannot extend, propose explicit removals, never a silent reset.
        val cleared = board.copy(queens=board.fixed.sorted())
        val replacement = solveQueens(cleared) as? SearchResult.Solved ?: return null
        return (board.queens-board.fixed.toSet()).map(::RemoveQueen) + replacement.moves
    }
    override fun search(board: BoardState, maxStates: Int): SearchResult = solveQueens(board as QueensBoard,maxStates)
}

fun solveQueens(board: QueensBoard, maxStates: Int = 50_000): SearchResult {
    require(QueensEngine.validate(board) && maxStates > 0)
    val placed = board.queens.toMutableList()
    val route = mutableListOf<Move>()
    var visited = 0
    var exhausted = false
    fun visit(): Boolean {
        if (placed.size == board.side) return true
        if (visited >= maxStates) { exhausted=true;return false }
        visited++
        val rows = (0 until board.side).filter { row -> placed.none { it/board.side == row } }
        val choices = rows.map { row -> (0 until board.side).map { row*board.side+it }.filter { cell ->
            cell !in board.blocked && placed.none { QueensEngine.attacks(board.side,it,cell) }
        } }.minBy { it.size }
        for (cell in choices) {
            placed += cell; route += PlaceQueen(cell)
            if (visit()) return true
            placed.removeAt(placed.lastIndex);route.removeAt(route.lastIndex)
            if (exhausted) return false
        }
        return false
    }
    return if (visit()) SearchResult.Solved(route.toList(),visited)
        else if (exhausted) SearchResult.LimitReached(visited) else SearchResult.Unsolvable
}
