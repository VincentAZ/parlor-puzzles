package com.parlor.core.game

import com.parlor.core.model.*

object JugsEngine : PuzzleEngine {
    override fun validate(board: BoardState): Boolean = board is JugsBoard && board.capacities.size in 2..3 &&
        board.capacities.all { it in 1..40 } && board.amounts.size == board.capacities.size &&
        board.amounts.indices.all { board.amounts[it] in 0..board.capacities[it] } &&
        (board.targetJug == null || board.targetJug in board.capacities.indices) &&
        board.target > 0 && board.target <= (board.targetJug?.let { board.capacities[it] } ?: board.capacities.max())
    override fun isGoal(board: BoardState): Boolean = board is JugsBoard && validate(board) &&
        (board.targetJug?.let { board.amounts.getOrNull(it) == board.target } ?: (board.target in board.amounts))
    override fun legalMoves(board: BoardState): List<Move> {
        if (!validate(board) || isGoal(board)) return emptyList()
        board as JugsBoard
        return board.capacities.indices.flatMap { from -> listOf(FillJug(from), EmptyJug(from)) + board.capacities.indices.map { PourJug(from, it) } }
            .filter { apply(board, it) != null }
    }
    override fun apply(board: BoardState, move: Move): BoardState? {
        if (board !is JugsBoard || !validate(board) || isGoal(board)) return null
        val amounts = board.amounts.toMutableList()
        when (move) {
            is FillJug -> {
                if (move.jug !in amounts.indices || amounts[move.jug] == board.capacities[move.jug]) return null
                amounts[move.jug] = board.capacities[move.jug]
            }
            is EmptyJug -> {
                if (move.jug !in amounts.indices || amounts[move.jug] == 0) return null
                amounts[move.jug] = 0
            }
            is PourJug -> {
                if (move.from !in amounts.indices || move.to !in amounts.indices || move.from == move.to) return null
                val quantity = minOf(amounts[move.from], board.capacities[move.to] - amounts[move.to])
                if (quantity <= 0) return null
                amounts[move.from] -= quantity; amounts[move.to] += quantity
            }
            else -> return null
        }
        return board.copy(amounts = amounts)
    }
}
