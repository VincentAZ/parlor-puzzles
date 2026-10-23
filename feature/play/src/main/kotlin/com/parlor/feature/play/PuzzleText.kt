package com.parlor.feature.play

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.parlor.core.model.*

@Composable
fun puzzleTitle(puzzle: Puzzle): String = stringResource(when (puzzle) {
    Puzzle.TRAFFIC_JAM -> R.string.play_traffic_jam
    Puzzle.SLIDING_TILES -> R.string.play_sliding_tiles
    Puzzle.BLOCK_ESCAPE -> R.string.play_block_escape
    Puzzle.BOX_PUSHER -> R.string.play_box_pusher
    Puzzle.HANOI -> R.string.play_hanoi
    Puzzle.SWITCH_GRID -> R.string.play_switch_grid
    Puzzle.WATER_JUGS -> R.string.play_water_jugs
    Puzzle.PANCAKE_FLIP -> R.string.play_pancake_flip
    Puzzle.PEG_SOLITAIRE -> R.string.play_peg_solitaire
    Puzzle.QUEENS -> R.string.play_queens
    Puzzle.KNIGHTS_TOUR -> R.string.play_knights_tour
    Puzzle.CROSSINGS -> R.string.play_crossings
})

@Composable
internal fun puzzleGoal(board: BoardState): String = when (board) {
    is PegBoard -> com.parlor.core.game.pegGeometry(board.layout).let {stringResource(R.string.play_pegs_goal,board.goal/it.width+1,board.goal%it.width+1)}
    is QueensBoard -> stringResource(R.string.play_queens_goal,board.side)
    is KnightBoard -> stringResource(if(board.closed) R.string.play_knight_closed_goal else R.string.play_knight_open_goal)
    is CrossingBoard -> stringResource(R.string.play_crossings_goal)
    is TilesBoard -> stringResource(R.string.play_tiles_goal)
    is BlockEscapeBoard -> stringResource(R.string.play_blocks_goal, board.targetId, board.exitColumn + 1, board.exitColumn + 2)
    is BoxPusherBoard -> stringResource(R.string.play_boxes_goal)
    is HanoiBoard -> stringResource(R.string.play_hanoi_goal, board.goalPeg + 1)
    is SwitchBoard -> stringResource(R.string.play_switch_goal)
    is JugsBoard -> board.targetJug?.let { pluralStringResource(R.plurals.play_jugs_goal_specific, board.target, board.target, it + 1) }
        ?: pluralStringResource(R.plurals.play_jugs_goal, board.target, board.target)
    is PancakeBoard -> stringResource(R.string.play_pancake_goal)
    else -> stringResource(R.string.play_board_goal)
}

@Composable
internal fun moveDescription(board: BoardState, move: Move): String = when (move) {
    is JumpPeg -> com.parlor.core.game.pegGeometry((board as PegBoard).layout).let {stringResource(R.string.play_jump_peg,move.from/it.width+1,move.from%it.width+1,move.to/it.width+1,move.to%it.width+1)}
    is PlaceQueen -> stringResource(R.string.play_place_queen,move.cell/(board as QueensBoard).side+1,move.cell%board.side+1)
    is RemoveQueen -> stringResource(R.string.play_remove_queen,move.cell/(board as QueensBoard).side+1,move.cell%board.side+1)
    is KnightStep -> stringResource(R.string.play_knight_step,move.cell/(board as KnightBoard).side+1,move.cell%board.side+1)
    is CrossRiver -> {
        board as CrossingBoard
        val party=move.passengers.map {id->crossingActorName(board.actors.single {it.id==id})}.joinToString(", ")
        val destination=if(board.boatRight) stringResource(R.string.play_left_bank) else stringResource(R.string.play_right_bank)
        if(move.passengers.isEmpty()) stringResource(R.string.play_cross_alone,destination) else stringResource(R.string.play_cross_party,destination,party)
    }
    is Translate -> {
        val piece = (board as TrafficJamBoard).pieces.first { it.id == move.pieceId }
        if (move.to == 6) stringResource(R.string.play_hint_exit, move.pieceId)
        else stringResource(R.string.play_hint_next, move.pieceId, stringResource(if (piece.axis == Axis.HORIZONTAL) R.string.play_column else R.string.play_row), move.to + 1)
    }
    is SlideTile -> stringResource(R.string.play_slide_tile, move.tile)
    is SlideBlock -> if (move.y == (board as BlockEscapeBoard).height) stringResource(R.string.play_block_exit, move.id)
        else stringResource(R.string.play_block_move, move.id, move.y + 1, move.x + 1)
    is WalkTo -> stringResource(R.string.play_walk_to, move.cell / (board as BoxPusherBoard).width + 1, move.cell % board.width + 1)
    is WorkerStep -> stringResource(if (com.parlor.core.game.BoxPusherEngine.cost(board, move) == 1) R.string.play_push_direction else R.string.play_walk_direction,
        stringResource(when(move.direction) { Direction.UP -> R.string.play_up; Direction.DOWN -> R.string.play_down; Direction.LEFT -> R.string.play_left; Direction.RIGHT -> R.string.play_right }))
    is TransferDisk -> stringResource(R.string.play_hanoi_move, move.from + 1, move.to + 1)
    is Toggle -> stringResource(R.string.play_switch_move, move.index / (board as SwitchBoard).side + 1, move.index % board.side + 1)
    is FillJug -> stringResource(R.string.play_jug_fill_hint, move.jug + 1)
    is EmptyJug -> stringResource(R.string.play_jug_empty_hint, move.jug + 1)
    is PourJug -> stringResource(R.string.play_jug_pour_hint, move.from + 1, move.to + 1)
    is FlipPrefix -> stringResource(R.string.play_flip_hint, move.count)
}

@Composable
internal fun scoredCount(level: LevelDefinition, count: Int): String = when(level.scoringUnit) {
    ScoringUnit.SCENARIO_MINUTES -> stringResource(R.string.play_scored_minutes,count)
    ScoringUnit.TIME, ScoringUnit.TIME_AND_BACKTRACKS -> stringResource(R.string.play_actions_count,count)
    else -> pluralStringResource(if(level.scoringUnit==ScoringUnit.PUSHES) R.plurals.play_pushes_count else R.plurals.play_moves_count,count,count)
}

@Composable
internal fun parCount(level: LevelDefinition): String = when(level.scoringUnit) {
    ScoringUnit.SCENARIO_MINUTES -> stringResource(R.string.play_minutes_par,level.par)
    ScoringUnit.TIME,ScoringUnit.TIME_AND_BACKTRACKS -> stringResource(R.string.play_time_par,level.targetTimeMs/1000)
    else -> stringResource(if(level.scoringUnit==ScoringUnit.PUSHES) R.string.play_push_par else R.string.play_par_count,level.par)
}
