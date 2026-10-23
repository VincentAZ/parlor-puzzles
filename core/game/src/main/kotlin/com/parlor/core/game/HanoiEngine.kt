package com.parlor.core.game

import com.parlor.core.model.*

object HanoiEngine : PuzzleEngine {
    override fun validate(board: BoardState): Boolean {
        if (board !is HanoiBoard || board.pegs.size !in 3..4 || board.goalPeg !in board.pegs.indices) return false
        val disks = board.pegs.flatten()
        return disks.size in 1..10 && disks.sorted() == (1..disks.size).toList() && board.pegs.all { peg -> peg.zipWithNext().all { (a,b) -> a > b } }
    }
    override fun legalMoves(board: BoardState): List<Move> {
        if (!validate(board) || isGoal(board)) return emptyList()
        board as HanoiBoard
        return board.pegs.indices.flatMap { from -> board.pegs.indices.mapNotNull { to ->
            TransferDisk(from, to).takeIf { apply(board, it) != null }
        } }
    }
    override fun apply(board: BoardState, move: Move): BoardState? {
        if (board !is HanoiBoard || move !is TransferDisk || !validate(board) || isGoal(board)) return null
        if (move.from !in board.pegs.indices || move.to !in board.pegs.indices || move.from == move.to) return null
        val disk = board.pegs[move.from].lastOrNull() ?: return null
        if ((board.pegs[move.to].lastOrNull() ?: Int.MAX_VALUE) < disk) return null
        return board.copy(pegs = board.pegs.mapIndexed { i, peg -> when (i) { move.from -> peg.dropLast(1); move.to -> peg + disk; else -> peg } })
    }
    override fun isGoal(board: BoardState): Boolean = board is HanoiBoard && validate(board) && board.goalPeg in board.pegs.indices &&
        board.pegs[board.goalPeg].isNotEmpty() && board.pegs.withIndex().all { it.index == board.goalPeg || it.value.isEmpty() }
    override fun completionFacts(board: BoardState): CompletionFacts =
        if (validate(board) && isGoal(board)) CompletionFacts(hanoiDisks = (board as HanoiBoard).pegs.flatten().size) else CompletionFacts()

    /** A verified continuation for any legal arrangement, using the classic recursive transfer. */
    override fun continuation(board: BoardState): List<Move>? {
        if (!validate(board)) return null
        var state = board as HanoiBoard
        val moves = mutableListOf<Move>()
        fun gather(n: Int, target: Int) {
            if (n == 0) return
            val from = state.pegs.indexOfFirst { n in it }
            if (from == target) { gather(n - 1, target); return }
            val spare = state.pegs.indices.first { it != from && it != target }
            gather(n - 1, spare)
            val move = TransferDisk(from, target)
            state = requireNotNull(apply(state, move)) as HanoiBoard
            moves += move
            gather(n - 1, target)
        }
        gather(state.pegs.flatten().size, state.goalPeg)
        return moves
    }
}

/** Three-peg recursion and four-peg Frame–Stewart reference construction. */
fun hanoiReference(disks: Int, pegCount: Int): List<Move> {
    require(disks in 1..10 && pegCount in 3..4)
    val costs = IntArray(disks + 1)
    val split = IntArray(disks + 1)
    costs[1] = 1
    for (n in 2..disks) {
        split[n] = (1 until n).minBy { k -> 2 * costs[k] + (1 shl (n - k)) - 1 }
        costs[n] = 2 * costs[split[n]] + (1 shl (n - split[n])) - 1
    }
    val result = mutableListOf<Move>()
    fun transfer(n: Int, from: Int, to: Int, pegs: List<Int>) {
        if (n == 0) return
        if (n == 1) { result += TransferDisk(from, to); return }
        val spare = pegs.first { it != from && it != to }
        val k = if (pegs.size == 3) n - 1 else split[n]
        transfer(k, from, spare, pegs)
        transfer(n - k, from, to, pegs - spare)
        transfer(k, spare, to, pegs)
    }
    // In the three-peg case the middle transfer is exactly one disk, so two pegs suffice there.
    transfer(disks, 0, pegCount - 1, (0 until pegCount).toList())
    return result
}
