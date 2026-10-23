package com.parlor.core.game

import com.parlor.core.model.*

object SwitchEngine : PuzzleEngine {
    override fun validate(board: BoardState): Boolean = board is SwitchBoard && board.side in 3..8 && board.lights.size == board.side * board.side
    override fun isGoal(board: BoardState): Boolean = board is SwitchBoard && validate(board) && board.lights.isNotEmpty() && board.lights.none { it }
    fun affected(side: Int, index: Int): List<Int> = buildList {
        add(index)
        if (index % side > 0) add(index - 1)
        if (index % side < side - 1) add(index + 1)
        if (index >= side) add(index - side)
        if (index < side * (side - 1)) add(index + side)
    }
    override fun legalMoves(board: BoardState): List<Move> = if (!validate(board) || isGoal(board)) emptyList()
        else (board as SwitchBoard).lights.indices.map(::Toggle)
    override fun apply(board: BoardState, move: Move): BoardState? {
        if (board !is SwitchBoard || move !is Toggle || !validate(board) || isGoal(board) || move.index !in board.lights.indices) return null
        val changed = affected(board.side, move.index)
        return board.copy(lights = board.lights.mapIndexed { i, lit -> if (i in changed) !lit else lit })
    }
    override fun continuation(board: BoardState): List<Move>? = (board as? SwitchBoard)?.let { solveSwitch(it)?.moves }
}

data class SwitchSolution(val moves: List<Move>, val provenOptimal: Boolean)

/** RREF over GF(2); enumerate the null space only within an explicit work budget. */
fun solveSwitch(board: SwitchBoard, maxFreeVariables: Int = 16): SwitchSolution? {
    require(maxFreeVariables in 0..20)
    if (!SwitchEngine.validate(board)) return null
    val n = board.lights.size
    val rows = LongArray(n)
    val rhs = board.lights.toBooleanArray()
    for (column in 0 until n) for (row in SwitchEngine.affected(board.side, column)) rows[row] = rows[row] xor (1L shl column)
    val pivots = mutableListOf<Int>()
    for (column in 0 until n) {
        val next = (pivots.size until n).firstOrNull { rows[it] and (1L shl column) != 0L } ?: continue
        val pivot = pivots.size
        val old = rows[pivot]; rows[pivot] = rows[next]; rows[next] = old
        val b = rhs[pivot]; rhs[pivot] = rhs[next]; rhs[next] = b
        for (row in 0 until n) if (row != pivot && rows[row] and (1L shl column) != 0L) {
            rows[row] = rows[row] xor rows[pivot]; rhs[row] = rhs[row] xor rhs[pivot]
        }
        pivots += column
    }
    if ((pivots.size until n).any { rows[it] == 0L && rhs[it] }) return null
    var particular = 0L
    pivots.forEachIndexed { row, col -> if (rhs[row]) particular = particular or (1L shl col) }
    val free = (0 until n).filter { it !in pivots }
    var best = particular
    val exhaustive = free.size <= maxFreeVariables
    if (exhaustive) {
        val basis = free.map { column ->
            var bits = 1L shl column
            pivots.forEachIndexed { row, col -> if (rows[row] and (1L shl column) != 0L) bits = bits or (1L shl col) }
            bits
        }
        for (combination in 1 until (1 shl free.size)) {
            var candidate = particular
            basis.forEachIndexed { i, bits -> if (combination and (1 shl i) != 0) candidate = candidate xor bits }
            if (candidate.countOneBits() < best.countOneBits()) best = candidate
        }
    }
    return SwitchSolution((0 until n).filter { best and (1L shl it) != 0L }.map(::Toggle), exhaustive)
}
