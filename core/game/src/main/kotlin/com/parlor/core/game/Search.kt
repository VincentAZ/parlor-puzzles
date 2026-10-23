package com.parlor.core.game

import com.parlor.core.model.*

sealed interface SearchResult {
    data class Solved(val moves: List<Move>, val visited: Int) : SearchResult
    data class LimitReached(val visited: Int) : SearchResult
    data object Unsolvable : SearchResult
}

/** Unit-cost BFS. Budget exhaustion is never an optimality or unsolvability claim. */
fun breadthFirst(engine: PuzzleEngine, start: BoardState, maxStates: Int = 50_000): SearchResult {
    require(engine.validate(start) && maxStates > 0)
    if (engine.isGoal(start)) return SearchResult.Solved(emptyList(), 1)
    data class Node(val board: BoardState, val parent: Int, val action: Move?)
    val nodes = mutableListOf(Node(start, -1, null))
    val seen = mutableSetOf(engine.searchKey(start))
    var cursor = 0
    while (cursor < nodes.size) {
        val state = nodes[cursor].board
        for (move in engine.legalMoves(state)) {
            require(engine.cost(state, move) == 1) { "BFS requires unit costs" }
            val next = requireNotNull(engine.apply(state, move))
            val key = engine.searchKey(next)
            if (key in seen) continue
            if (nodes.size >= maxStates) return SearchResult.LimitReached(nodes.size)
            seen.add(key); nodes += Node(next, cursor, move)
            if (engine.isGoal(next)) {
                val path = mutableListOf<Move>()
                var at = nodes.lastIndex
                while (nodes[at].parent != -1) { path += requireNotNull(nodes[at].action); at = nodes[at].parent }
                return SearchResult.Solved(path.asReversed(), nodes.size)
            }
        }
        cursor++
    }
    return SearchResult.Unsolvable
}

/** Dijkstra for positive action costs. A goal is optimal only when removed from the priority queue. */
fun lowestCost(engine: PuzzleEngine, start: BoardState, maxStates: Int = 50_000): SearchResult {
    require(engine.validate(start) && maxStates > 0)
    data class Node(val board: BoardState, val cost: Int, val parent: Int, val move: Move?)
    val nodes = mutableListOf(Node(start,0,-1,null))
    val best = mutableMapOf(engine.searchKey(start) to 0)
    // The bridge graph is small; this binary heap also bounds queue work for future weighted puzzles.
    val heap = mutableListOf(0)
    fun push(index: Int) {
        heap.add(index)
        var child=heap.lastIndex
        while(child>0) {
            val parent=(child-1)/2
            if(nodes[heap[parent]].cost<=nodes[heap[child]].cost) break
            val swap=heap[parent];heap[parent]=heap[child];heap[child]=swap;child=parent
        }
    }
    fun pop():Int {
        val answer=heap[0];val last=heap.removeAt(heap.lastIndex)
        if(heap.isNotEmpty()) {
            heap[0]=last;var parent=0
            while(parent*2+1<heap.size) {
                var child=parent*2+1
                if(child+1<heap.size && nodes[heap[child+1]].cost<nodes[heap[child]].cost) child++
                if(nodes[heap[parent]].cost<=nodes[heap[child]].cost) break
                val swap=heap[parent];heap[parent]=heap[child];heap[child]=swap;parent=child
            }
        }
        return answer
    }
    while(heap.isNotEmpty()) {
        val at=pop();val node=nodes[at]
        if(best[engine.searchKey(node.board)]!=node.cost) continue
        if(engine.isGoal(node.board)) {
            val route=mutableListOf<Move>();var cursor=at
            while(nodes[cursor].parent!=-1) {route+=requireNotNull(nodes[cursor].move);cursor=nodes[cursor].parent}
            return SearchResult.Solved(route.asReversed(),nodes.size)
        }
        for(move in engine.legalMoves(node.board)) {
            val cost=engine.cost(node.board,move);require(cost>0 && node.cost<=Int.MAX_VALUE-cost)
            val next=requireNotNull(engine.apply(node.board,move));val score=node.cost+cost;val key=engine.searchKey(next)
            if((best[key]?:Int.MAX_VALUE)<=score) continue
            if(nodes.size>=maxStates) return SearchResult.LimitReached(nodes.size)
            best[key]=score;nodes+=Node(next,score,at,move);push(nodes.lastIndex)
        }
    }
    return SearchResult.Unsolvable
}

sealed interface Hint {
    data class Next(val move: Move) : Hint
    data class Recover(val undoCount: Int, val nextMove: Move) : Hint
    data object Unavailable : Hint
}

fun hintFor(level: LevelDefinition, attempt: Attempt, maxStates: Int = 10_000): Hint {
    require(attempt.levelId == level.id && attempt.levelRevision == level.revision)
    if (attempt.completed) return Hint.Unavailable
    val engine = engineFor(level.puzzle)
    // Every known continuation was verified all the way to the goal.
    verifyReference(level)
    val route = mutableMapOf<BoardState, Move>()
    var state = level.initial
    for (move in level.reference) {
        route[state] = move
        state = requireNotNull(engine.apply(state, move))
    }
    route[attempt.board]?.let { return Hint.Next(it) }
    engine.continuation(attempt.board)?.takeIf { it.isNotEmpty() }?.let { moves ->
        if (engine.isGoal(replay(engine, attempt.board, moves).final)) return Hint.Next(moves.first())
    }
    val search = engine.search(attempt.board, maxStates)
    if (search is SearchResult.Solved && search.moves.isNotEmpty()) return Hint.Next(search.moves.first())
    for ((index, entry) in attempt.history.asReversed().withIndex()) {
        route[entry.before]?.let { return Hint.Recover(index + 1, it) }
    }
    return Hint.Unavailable
}
