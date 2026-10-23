package com.parlor.core.game

import com.parlor.core.model.*

object BlockEscapeEngine : PuzzleEngine {
    override fun validate(board: BoardState): Boolean {
        if (board !is BlockEscapeBoard || board.width !in 4..6 || board.height !in 4..7 || board.exitColumn !in 0..board.width - 2 ||
            board.blocks.isEmpty() || board.blocks.size > board.width * board.height - 3 || board.blocks.map { it.id }.distinct().size != board.blocks.size) return false
        val target = board.blocks.singleOrNull { it.id == board.targetId } ?: return false
        if (target.width != 2 || target.height != 2) return false
        val filled = mutableSetOf<Int>()
        for (b in board.blocks) {
            if (b.id.isBlank() || b.width !in 1..2 || b.height !in 1..2) return false
            if (b.id == board.targetId && b.x == board.exitColumn && b.y == board.height) continue
            if (b.x !in 0..board.width - b.width || b.y !in 0..board.height - b.height) return false
            for (y in b.y until b.y + b.height) for (x in b.x until b.x + b.width) if (!filled.add(y * board.width + x)) return false
        }
        return true
    }
    override fun isGoal(board: BoardState): Boolean = board is BlockEscapeBoard && validate(board) && board.blocks.single { it.id == board.targetId }.y == board.height
    override fun apply(board: BoardState, move: Move): BoardState? {
        if (board !is BlockEscapeBoard || move !is SlideBlock || !validate(board) || isGoal(board)) return null
        val b = board.blocks.find { it.id == move.id } ?: return null
        if ((move.x == b.x) == (move.y == b.y)) return null // Exactly one axis, and at least one cell.
        val exit = b.id == board.targetId && move.x == board.exitColumn && move.y == board.height && b.x == board.exitColumn
        if (!exit && (move.x !in 0..board.width - b.width || move.y !in 0..board.height - b.height)) return null
        val left = minOf(b.x, move.x); val right = maxOf(b.x, move.x) + b.width
        val top = minOf(b.y, move.y); val bottom = minOf(maxOf(b.y, move.y) + b.height, board.height)
        if (board.blocks.any { other -> other.id != b.id && left < other.x + other.width && right > other.x && top < other.y + other.height && bottom > other.y }) return null
        return board.copy(blocks = board.blocks.map { if (it.id == b.id) it.copy(x = move.x, y = move.y) else it })
    }
    override fun legalMoves(board: BoardState): List<Move> {
        if (board !is BlockEscapeBoard || !validate(board) || isGoal(board)) return emptyList()
        return buildList {
            for (b in board.blocks) {
                for (x in 0..board.width - b.width) SlideBlock(b.id, x, b.y).takeIf { apply(board, it) != null }?.let(::add)
                for (y in 0..board.height - b.height) SlideBlock(b.id, b.x, y).takeIf { apply(board, it) != null }?.let(::add)
                if (b.id == board.targetId) SlideBlock(b.id, board.exitColumn, board.height).takeIf { apply(board, it) != null }?.let(::add)
            }
        }
    }
    // Equivalent rectangles share a search state; reconstructed actions retain the actual display IDs.
    override fun searchKey(board: BoardState): Any {
        board as BlockEscapeBoard
        val target = board.blocks.single { it.id == board.targetId }
        return listOf(board.width, board.height, board.exitColumn, target.x, target.y) + board.blocks.filter { it.id != board.targetId }
            .sortedWith(compareBy({ it.width }, { it.height }, { it.y }, { it.x })).flatMap { listOf(it.width, it.height, it.x, it.y) }
    }
}
