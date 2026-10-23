package com.parlor.core.game

import com.parlor.core.model.*
import kotlin.math.abs

object TilesEngine : PuzzleEngine {
    override fun validate(board: BoardState): Boolean = board is TilesBoard && board.side in 3..5 &&
        board.tiles.size == board.side * board.side && board.tiles.sorted() == (0 until board.side * board.side).toList()
    override fun isGoal(board: BoardState): Boolean = board is TilesBoard && validate(board) &&
        board.tiles == (1 until board.side * board.side).toList() + 0
    fun solvable(board: TilesBoard): Boolean {
        if (!validate(board)) return false
        var inversions = 0
        for (i in board.tiles.indices) for (j in i + 1 until board.tiles.size)
            if (board.tiles[i] != 0 && board.tiles[j] != 0 && board.tiles[i] > board.tiles[j]) inversions++
        return if (board.side % 2 == 1) inversions % 2 == 0
            else (inversions + board.side - board.tiles.indexOf(0) / board.side) % 2 == 1
    }
    override fun legalMoves(board: BoardState): List<Move> {
        if (board !is TilesBoard || !validate(board) || isGoal(board)) return emptyList()
        val empty = board.tiles.indexOf(0)
        return board.tiles.indices.filter { abs(it / board.side - empty / board.side) + abs(it % board.side - empty % board.side) == 1 }
            .map { SlideTile(board.tiles[it]) }
    }
    override fun apply(board: BoardState, move: Move): BoardState? {
        if (board !is TilesBoard || move !is SlideTile || !validate(board) || isGoal(board) || move.tile == 0) return null
        val from = board.tiles.indexOf(move.tile)
        val empty = board.tiles.indexOf(0)
        if (from < 0 || abs(from / board.side - empty / board.side) + abs(from % board.side - empty % board.side) != 1) return null
        return board.copy(tiles = board.tiles.toMutableList().also { it[empty] = move.tile; it[from] = 0 })
    }
    override fun search(board: BoardState, maxStates: Int): SearchResult = solveTiles(board as TilesBoard, maxStates)
}

/** IDA* with an admissible Manhattan bound and a strict expansion budget; no optimum claim on exhaustion. */
fun solveTiles(board: TilesBoard, maxNodes: Int = 100_000): SearchResult {
    require(TilesEngine.validate(board) && maxNodes > 0)
    if (!TilesEngine.solvable(board)) return SearchResult.Unsolvable
    if (TilesEngine.isGoal(board)) return SearchResult.Solved(emptyList(), 1)
    val n = board.side
    val cells = board.tiles.toIntArray()
    fun distance(tile: Int, index: Int) = if (tile == 0) 0 else abs(index / n - (tile - 1) / n) + abs(index % n - (tile - 1) % n)
    val initialH = cells.indices.sumOf { distance(cells[it], it) }
    val path = mutableListOf<Move>()
    val ancestors = mutableSetOf(cells.toList())
    var expanded = 0
    var exhausted = false
    val found = -1
    fun visit(empty: Int, previousEmpty: Int, g: Int, h: Int, bound: Int): Int {
        if (g + h > bound) return g + h
        if (h == 0) return found
        if (expanded >= maxNodes) { exhausted = true; return Int.MAX_VALUE }
        expanded++
        var nextBound = Int.MAX_VALUE
        val candidates = listOf(empty - n, empty + n, empty - 1, empty + 1).filter {
            it in cells.indices && it != previousEmpty && abs(it / n - empty / n) + abs(it % n - empty % n) == 1
        }.sortedBy { distance(cells[it], empty) - distance(cells[it], it) }
        for (from in candidates) {
            val tile = cells[from]
            cells[empty] = tile; cells[from] = 0
            val key = cells.toList()
            if (ancestors.add(key)) {
                path += SlideTile(tile)
                val result = visit(from, empty, g + 1, h - distance(tile, from) + distance(tile, empty), bound)
                if (result == found) return found
                path.removeAt(path.lastIndex); ancestors.remove(key)
                nextBound = minOf(nextBound, result)
            }
            cells[from] = tile; cells[empty] = 0
            if (exhausted) return Int.MAX_VALUE
        }
        return nextBound
    }
    var bound = initialH
    while (true) {
        val next = visit(cells.indexOf(0), -1, 0, initialH, bound)
        if (next == found) return SearchResult.Solved(path.toList(), expanded)
        if (exhausted) return SearchResult.LimitReached(expanded)
        if (next == Int.MAX_VALUE) return SearchResult.Unsolvable
        bound = next
    }
}
