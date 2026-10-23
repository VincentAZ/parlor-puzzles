package com.parlor.core.game

import com.parlor.core.model.*

/** Validate imported geometry against bundled content before asking an engine to allocate/search. */
object AttemptValidation {
    fun validate(level: LevelDefinition, attempt: Attempt) {
        require(attempt.levelId == level.id && attempt.levelRevision == level.revision)
        require(attempt.mode == AttemptMode.CAMPAIGN || attempt.mode == AttemptMode.DAILY)
        require(attempt.scoredMoves <= 100_000_000 && attempt.undoCount <= 100_000_000 && attempt.walkingSteps <= 100_000_000)
        require(attempt.backtracks == if (level.scoringUnit == ScoringUnit.TIME_AND_BACKTRACKS) attempt.undoCount else 0)
        require(attempt.scoredMoves >= attempt.undoCount && attempt.activeTimeMs <= MAX_TIME)
        require(attempt.viewport.column in 0..64 && attempt.viewport.row in 0..64 && attempt.viewport.zoom in 1..8)
        require((attempt.viewport.selectedId?.length ?: 0) <= 200)
        require(attempt.history.size + attempt.redo.size <= 10_000)
        val engine = engineFor(level.puzzle)
        fun board(value: BoardState) { require(sameLayout(level.initial, value)); require(engine.validate(value)) }
        board(attempt.board)
        require(attempt.completed == engine.isGoal(attempt.board))
        if (attempt.completed) {
            require(attempt.history.isEmpty() && attempt.redo.isEmpty())
            return
        }
        var cursor = level.initial
        var minimumCost = 0L
        fun step(entry: HistoryEntry) {
            board(entry.before); board(entry.after)
            require(cursor == entry.before && !engine.isGoal(cursor))
            require(entry.cost in 0..10_000_000 && engine.cost(cursor, entry.move) == entry.cost)
            require(engine.apply(cursor, entry.move) == entry.after)
            cursor = entry.after
        }
        for (entry in attempt.history) { step(entry); minimumCost += entry.cost }
        require(cursor == attempt.board && minimumCost + attempt.undoCount <= attempt.scoredMoves)
        for (entry in attempt.redo.asReversed()) step(entry)
    }

    private fun sameLayout(initial: BoardState, state: BoardState): Boolean = when (initial) {
        is TrafficJamBoard -> state is TrafficJamBoard && state.pieces.size == initial.pieces.size &&
            state.copy(pieces = state.pieces.mapIndexed { index, p ->
                val original = initial.pieces[index]
                if (p.axis == Axis.HORIZONTAL) p.copy(x = original.x) else p.copy(y = original.y)
            }) == initial
        is HanoiBoard -> state is HanoiBoard && state.pegs.size == initial.pegs.size && state.goalPeg == initial.goalPeg &&
            state.pegs.flatten().sorted() == initial.pegs.flatten().sorted()
        is SwitchBoard -> state is SwitchBoard && state.side == initial.side && state.lights.size == initial.lights.size
        is JugsBoard -> state is JugsBoard && state.amounts.size == initial.amounts.size && state.copy(amounts = initial.amounts) == initial
        is PancakeBoard -> state is PancakeBoard && state.stack.sorted() == initial.stack.sorted()
        is TilesBoard -> state is TilesBoard && state.tiles.size == initial.tiles.size && state.copy(tiles = initial.tiles) == initial
        is BlockEscapeBoard -> state is BlockEscapeBoard && state.blocks.size == initial.blocks.size &&
            state.copy(blocks = state.blocks.mapIndexed { index, p -> p.copy(x = initial.blocks[index].x, y = initial.blocks[index].y) }) == initial
        is BoxPusherBoard -> state is BoxPusherBoard && state.boxes.size == initial.boxes.size && state.copy(boxes = initial.boxes, worker = initial.worker) == initial
        is PegBoard -> state is PegBoard && state.pegs.size <= initial.pegs.size && state.copy(pegs = initial.pegs) == initial
        is QueensBoard -> state is QueensBoard && state.queens.size <= initial.side && state.copy(queens = initial.queens) == initial
        is KnightBoard -> state is KnightBoard && state.path.size <= initial.side * initial.side && state.path.firstOrNull() == initial.path.firstOrNull() && state.copy(path = initial.path) == initial
        is CrossingBoard -> state is CrossingBoard && state.right.size <= initial.actors.size && state.copy(right = initial.right, boatRight = initial.boatRight, elapsedMinutes = initial.elapsedMinutes) == initial
    }
    const val MAX_TIME = 3_153_600_000_000L
}
