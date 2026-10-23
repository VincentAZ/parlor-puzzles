package com.parlor.core.game

import com.parlor.core.model.*

object TrafficJamEngine : PuzzleEngine {
    override fun validate(board: BoardState): Boolean {
        if (board !is TrafficJamBoard || board.width != 6 || board.height != 6 || board.exitRow !in 0 until board.height) return false
        if (board.pieces.isEmpty() || board.pieces.size > 18 || board.pieces.map { it.id }.toSet().size != board.pieces.size) return false
        val target = board.pieces.singleOrNull { it.id == board.targetId } ?: return false
        if (target.axis != Axis.HORIZONTAL || target.length != 2 || target.y != board.exitRow) return false
        val occupied = mutableSetOf<Int>()
        for (p in board.pieces) {
            if (p.id.isBlank() || p.length !in 2..3) return false
            if (p.id == board.targetId && p.x == board.width) continue
            for (offset in 0 until p.length) {
                val x = p.x + if (p.axis == Axis.HORIZONTAL) offset else 0
                val y = p.y + if (p.axis == Axis.VERTICAL) offset else 0
                if (x !in 0 until board.width || y !in 0 until board.height || !occupied.add(y * board.width + x)) return false
            }
        }
        return true
    }

    override fun legalMoves(board: BoardState): List<Move> {
        require(board is TrafficJamBoard && validate(board))
        if (isGoal(board)) return emptyList()
        return buildList {
            for (piece in board.pieces) {
                val bound = if (piece.axis == Axis.HORIZONTAL) board.width else board.height
                for (to in 0..bound - piece.length) {
                    val move = Translate(piece.id, to)
                    if (translate(board, move) != null) add(move)
                }
                if (piece.id == board.targetId) {
                    val exit = Translate(piece.id, board.width)
                    if (translate(board, exit) != null) add(exit)
                }
            }
        }
    }

    override fun apply(board: BoardState, move: Move): BoardState? {
        if (board !is TrafficJamBoard || move !is Translate || !validate(board) || isGoal(board)) return null
        return translate(board, move)
    }

    private fun translate(board: TrafficJamBoard, move: Translate): TrafficJamBoard? {
        val piece = board.pieces.find { it.id == move.pieceId } ?: return null
        val bound = if (piece.axis == Axis.HORIZONTAL) board.width else board.height
        val exits = piece.id == board.targetId && move.to == board.width
        if (move.to == piece.position || (!exits && move.to !in 0..bound - piece.length)) return null
        // Inspect every cell swept by the piece, not only its destination.
        val from = minOf(piece.position, move.to)
        val until = minOf(bound, maxOf(piece.position, move.to) + piece.length)
        for (other in board.pieces) {
            if (other.id == piece.id) continue
            for (i in 0 until other.length) {
                val x = other.x + if (other.axis == Axis.HORIZONTAL) i else 0
                val y = other.y + if (other.axis == Axis.VERTICAL) i else 0
                if (piece.axis == Axis.HORIZONTAL && y == piece.y && x in from until until) return null
                if (piece.axis == Axis.VERTICAL && x == piece.x && y in from until until) return null
            }
        }
        val moved = if (piece.axis == Axis.HORIZONTAL) piece.copy(x = move.to) else piece.copy(y = move.to)
        return board.copy(pieces = board.pieces.map { if (it.id == moved.id) moved else it })
    }

    override fun isGoal(board: BoardState): Boolean = board is TrafficJamBoard &&
        board.pieces.any { it.id == board.targetId && it.x == board.width }
}
