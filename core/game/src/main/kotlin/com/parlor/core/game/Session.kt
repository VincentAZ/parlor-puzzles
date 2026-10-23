package com.parlor.core.game

import com.parlor.core.model.*

/** Pure transitions. The repository must commit these before exposing the new board. */
object Session {
    fun move(engine: PuzzleEngine, attempt: Attempt, move: Move): Attempt? {
        if (attempt.completed) return null
        val board = engine.apply(attempt.board, move) ?: return null
        val cost = engine.cost(attempt.board, move)
        return attempt.copy(
            board = board,
            history = attempt.history + HistoryEntry(attempt.board, board, move, cost),
            redo = emptyList(),
            scoredMoves = attempt.scoredMoves + cost,
            walkingSteps = attempt.walkingSteps + engine.walkingCost(attempt.board, move),
            completed = engine.isGoal(board),
            viewport = when {
                move is KnightStep && board is KnightBoard -> attempt.viewport.copy(column=(move.cell%board.side-1).coerceAtLeast(0),row=(move.cell/board.side-1).coerceAtLeast(0))
                move is TransferDisk || move is FillJug || move is EmptyJug || move is PourJug || move is SlideTile || move is JumpPeg || move is CrossRiver -> attempt.viewport.copy(selectedId=null)
                else -> attempt.viewport
            },
        )
    }

    fun undo(attempt: Attempt, unit: ScoringUnit): Attempt? {
        if (attempt.completed) return null
        val entry = attempt.history.lastOrNull() ?: return null
        return attempt.copy(
            board = entry.before,
            history = attempt.history.dropLast(1),
            redo = attempt.redo + entry,
            scoredMoves = attempt.scoredMoves + if (unit == ScoringUnit.SCENARIO_MINUTES) entry.cost else 1,
            backtracks = attempt.backtracks + if (unit == ScoringUnit.TIME_AND_BACKTRACKS) 1 else 0,
            undoCount = attempt.undoCount + 1,
            viewport = if(entry.before is KnightBoard) followKnight(attempt.viewport,entry.before as KnightBoard) else if(entry.before is CrossingBoard)
                attempt.viewport.copy(selectedId=null) else attempt.viewport,
        )
    }

    fun redo(engine: PuzzleEngine, attempt: Attempt): Attempt? {
        if (attempt.completed) return null
        val entry = attempt.redo.lastOrNull() ?: return null
        require(attempt.board == entry.before)
        val after = engine.apply(attempt.board, entry.move) ?: return null
        require(after == entry.after)
        return attempt.copy(
            board = after,
            history = attempt.history + entry,
            redo = attempt.redo.dropLast(1),
            scoredMoves = attempt.scoredMoves + entry.cost,
            walkingSteps = attempt.walkingSteps + engine.walkingCost(attempt.board, entry.move),
            completed = engine.isGoal(after),
            viewport = if(after is KnightBoard) followKnight(attempt.viewport,after) else if(after is CrossingBoard)
                attempt.viewport.copy(selectedId=null) else attempt.viewport,
        )
    }

    fun checkpoint(attempt: Attempt, elapsedMs: Long): Attempt {
        require(elapsedMs >= 0)
        return if (attempt.completed) attempt else attempt.copy(activeTimeMs = attempt.activeTimeMs + elapsedMs)
    }

    private fun followKnight(viewport:Viewport,board:KnightBoard):Viewport = viewport.copy(
        column=(board.path.last()%board.side-1).coerceAtLeast(0),row=(board.path.last()/board.side-1).coerceAtLeast(0))
}
