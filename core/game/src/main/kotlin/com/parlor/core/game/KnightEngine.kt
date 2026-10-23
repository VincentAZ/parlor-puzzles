package com.parlor.core.game

import com.parlor.core.model.*
import kotlin.math.abs

object KnightEngine : PuzzleEngine {
    fun adjacent(side: Int, from: Int, to: Int): Boolean {
        if (from !in 0 until side*side || to !in 0 until side*side) return false
        val dx=abs(from%side-to%side);val dy=abs(from/side-to/side)
        return (dx==1&&dy==2)||(dx==2&&dy==1)
    }
    override fun validate(board: BoardState): Boolean = board is KnightBoard && board.side in 5..8 &&
        (!board.closed || board.side%2==0) && board.path.isNotEmpty() && board.path.size<=board.side*board.side &&
        board.path.distinct().size==board.path.size && board.path.all { it in 0 until board.side*board.side } &&
        board.path.zipWithNext().all { (a,b) -> adjacent(board.side,a,b) }
    override fun isGoal(board: BoardState): Boolean = board is KnightBoard && validate(board) &&
        board.path.size==board.side*board.side && (!board.closed || adjacent(board.side,board.path.last(),board.path.first()))
    override fun apply(board: BoardState, move: Move): BoardState? {
        if (board !is KnightBoard || move !is KnightStep || !validate(board) || isGoal(board) ||
            move.cell in board.path || !adjacent(board.side,board.path.last(),move.cell)) return null
        return board.copy(path=board.path+move.cell)
    }
    override fun legalMoves(board: BoardState): List<Move> = if (board !is KnightBoard || !validate(board) || isGoal(board)) emptyList()
        else (0 until board.side*board.side).filter { it !in board.path && adjacent(board.side,board.path.last(),it) }.map(::KnightStep)
    override fun search(board: BoardState, maxStates: Int): SearchResult = solveKnight(board as KnightBoard,maxStates)
    override fun completionFacts(board: BoardState): CompletionFacts = if (board is KnightBoard && isGoal(board) && board.closed)
        CompletionFacts(closedKnightSide=board.side) else CompletionFacts()
}

/** Warnsdorff ordering guides bounded backtracking; the returned route is still fully validated. */
fun solveKnight(board: KnightBoard, maxNodes: Int = 50_000): SearchResult {
    require(KnightEngine.validate(board) && maxNodes>0)
    val count=board.side*board.side
    val neighbors=List(count) { from -> (0 until count).filter { KnightEngine.adjacent(board.side,from,it) } }
    val used=BooleanArray(count);board.path.forEach {used[it]=true}
    val path=board.path.toMutableList()
    var visited=0
    var exhausted=false
    fun visit():Boolean {
        if (path.size==count) return !board.closed || path.first() in neighbors[path.last()]
        if (visited>=maxNodes) {exhausted=true;return false}
        visited++
        if (board.closed && neighbors[path.first()].none { !used[it] }) return false
        val candidates=neighbors[path.last()].filter { !used[it] }.sortedWith(compareBy<Int>(
            { cell -> neighbors[cell].count { !used[it] } }, { it }))
        for(cell in candidates) {
            used[cell]=true;path+=cell
            if(visit()) return true
            used[cell]=false;path.removeAt(path.lastIndex)
            if(exhausted) return false
        }
        return false
    }
    return if(visit()) SearchResult.Solved(path.drop(board.path.size).map(::KnightStep),visited)
        else if(exhausted) SearchResult.LimitReached(visited) else SearchResult.Unsolvable
}
