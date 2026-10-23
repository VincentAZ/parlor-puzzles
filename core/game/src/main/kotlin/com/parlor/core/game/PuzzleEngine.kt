package com.parlor.core.game

import com.parlor.core.model.*

interface PuzzleEngine {
    fun validate(board: BoardState): Boolean
    fun legalMoves(board: BoardState): List<Move>
    fun apply(board: BoardState, move: Move): BoardState?
    fun isGoal(board: BoardState): Boolean
    fun cost(board: BoardState, move: Move): Int = 1
    fun walkingCost(board: BoardState, move: Move): Int = 0
    fun searchKey(board: BoardState): Any = board
    fun search(board: BoardState, maxStates: Int): SearchResult = breadthFirst(this, board, maxStates)
    fun completionFacts(board: BoardState): CompletionFacts = CompletionFacts()
    /** Optional complete route, checked by the shared hint adapter before suggesting its first move. */
    fun continuation(board: BoardState): List<Move>? = null
}

fun engineFor(puzzle: Puzzle): PuzzleEngine = when (puzzle) {
    Puzzle.TRAFFIC_JAM -> TrafficJamEngine
    Puzzle.HANOI -> HanoiEngine
    Puzzle.SWITCH_GRID -> SwitchEngine
    Puzzle.WATER_JUGS -> JugsEngine
    Puzzle.PANCAKE_FLIP -> PancakeEngine
    Puzzle.SLIDING_TILES -> TilesEngine
    Puzzle.BLOCK_ESCAPE -> BlockEscapeEngine
    Puzzle.BOX_PUSHER -> BoxPusherEngine
    Puzzle.PEG_SOLITAIRE -> PegEngine
    Puzzle.QUEENS -> QueensEngine
    Puzzle.KNIGHTS_TOUR -> KnightEngine
    Puzzle.CROSSINGS -> CrossingEngine
}

data class Replay(val final: BoardState, val cost: Int)

fun replay(engine: PuzzleEngine, initial: BoardState, moves: List<Move>): Replay {
    require(engine.validate(initial)) { "Invalid initial board" }
    var state = initial
    var cost = 0
    for ((index, move) in moves.withIndex()) {
        require(!engine.isGoal(state)) { "Reference continues after goal at action $index" }
        cost += engine.cost(state, move)
        state = requireNotNull(engine.apply(state, move)) { "Illegal reference action $index: $move" }
        require(engine.validate(state)) { "Invalid state after action $index" }
    }
    return Replay(state, cost)
}

fun verifyReference(level: LevelDefinition) {
    val engine = engineFor(level.puzzle)
    require(!engine.isGoal(level.initial)) { "Level ${level.id} starts solved" }
    val result = replay(engine, level.initial, level.reference)
    require(engine.isGoal(result.final)) { "Unsolved reference for ${level.id}" }
    require(result.cost == level.par) { "Par mismatch for ${level.id}: ${result.cost} != ${level.par}" }
}
