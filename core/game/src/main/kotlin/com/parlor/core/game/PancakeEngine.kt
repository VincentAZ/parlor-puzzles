package com.parlor.core.game

import com.parlor.core.model.*
import kotlin.math.abs

object PancakeEngine : PuzzleEngine {
    override fun validate(board: BoardState): Boolean = board is PancakeBoard && board.stack.size in 4..12 && board.stack.sorted() == (1..board.stack.size).toList()
    override fun isGoal(board: BoardState): Boolean = board is PancakeBoard && validate(board) && board.stack.isNotEmpty() && board.stack == (1..board.stack.size).toList()
    override fun legalMoves(board: BoardState): List<Move> = if (!validate(board) || isGoal(board)) emptyList()
        else (2..(board as PancakeBoard).stack.size).map(::FlipPrefix)
    override fun apply(board: BoardState, move: Move): BoardState? {
        if (board !is PancakeBoard || move !is FlipPrefix || !validate(board) || isGoal(board) || move.count !in 2..board.stack.size) return null
        return board.copy(stack = board.stack.take(move.count).asReversed() + board.stack.drop(move.count))
    }
    override fun continuation(board: BoardState): List<Move>? {
        if (!validate(board)) return null
        var state = board as PancakeBoard
        val result = mutableListOf<Move>()
        fun flip(count: Int) {
            val move = FlipPrefix(count)
            state = requireNotNull(apply(state, move)) as PancakeBoard
            result += move
        }
        for (size in state.stack.size downTo 2) {
            val index = state.stack.indexOf(size)
            if (index == size - 1) continue
            if (index > 0) flip(index + 1)
            flip(size)
        }
        return result
    }
}

/** Unit-cost A* with an admissible gap heuristic and a hard discovered-state budget. */
fun solvePancake(board: PancakeBoard, maxStates: Int = 100_000): SearchResult {
    require(PancakeEngine.validate(board) && maxStates > 0)
    fun gaps(state: PancakeBoard): Int = (state.stack + (state.stack.size + 1)).zipWithNext().count { (a,b) -> abs(a - b) != 1 }
    data class Node(val state: PancakeBoard, val g: Int, val h: Int, val serial: Int)
    val order = compareBy<Node>({ it.g + it.h }, { it.h }, { it.serial })
    val heap = mutableListOf<Node>()
    fun push(node: Node) {
        heap += node
        var i = heap.lastIndex
        while (i > 0) {
            val parent = (i - 1) / 2
            if (order.compare(heap[parent], heap[i]) <= 0) break
            val old = heap[i]; heap[i] = heap[parent]; heap[parent] = old; i = parent
        }
    }
    fun pop(): Node {
        val result = heap.first()
        val last = heap.removeAt(heap.lastIndex)
        if (heap.isNotEmpty()) {
            heap[0] = last
            var i = 0
            while (i * 2 + 1 < heap.size) {
                var child = i * 2 + 1
                if (child + 1 < heap.size && order.compare(heap[child + 1], heap[child]) < 0) child++
                if (order.compare(heap[i], heap[child]) <= 0) break
                val old = heap[i]; heap[i] = heap[child]; heap[child] = old; i = child
            }
        }
        return result
    }
    data class Parent(val before: PancakeBoard, val move: Move)
    val parents = mutableMapOf<PancakeBoard, Parent>()
    val costs = mutableMapOf(board to 0)
    var serial = 0
    push(Node(board, 0, gaps(board), serial++))
    while (heap.isNotEmpty()) {
        val node = pop()
        if (costs[node.state] != node.g) continue
        if (PancakeEngine.isGoal(node.state)) {
            val moves = mutableListOf<Move>()
            var current = node.state
            while (current != board) {
                val parent = parents.getValue(current)
                moves += parent.move; current = parent.before
            }
            return SearchResult.Solved(moves.asReversed(), costs.size)
        }
        for (move in PancakeEngine.legalMoves(node.state)) {
            val next = requireNotNull(PancakeEngine.apply(node.state, move)) as PancakeBoard
            val previous = costs[next]
            if (previous != null && previous <= node.g + 1) continue
            if (previous == null && costs.size >= maxStates) return SearchResult.LimitReached(costs.size)
            costs[next] = node.g + 1
            parents[next] = Parent(node.state, move)
            push(Node(next, node.g + 1, gaps(next), serial++))
        }
    }
    return SearchResult.Unsolvable
}
