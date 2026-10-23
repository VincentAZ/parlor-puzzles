package com.parlor.feature.play

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.parlor.core.game.*
import com.parlor.core.model.*
import com.parlor.core.platform.*

private data class Lesson(val initial: BoardState, val actions: List<Move>, val selectFirst: Boolean)

@Composable
fun PuzzleTutorial(puzzle: Puzzle, settings: PlayerSettings, feedback: Feedback, onDone: () -> Unit, onBack: () -> Unit) {
    if (puzzle == Puzzle.TRAFFIC_JAM) { TrafficTutorial(settings, feedback, onDone, onBack); return }
    val lesson = remember(puzzle) {
        when (puzzle) {
            Puzzle.PEG_SOLITAIRE -> Lesson(PegBoard(PegLayout.TRIANGLE,listOf(0,11,12,15),10),listOf(JumpPeg(12,10),JumpPeg(15,5),JumpPeg(0,10)),false)
            Puzzle.QUEENS -> Lesson(QueensBoard(4,listOf(1),listOf(1),listOf(0)),listOf(PlaceQueen(7),PlaceQueen(8),PlaceQueen(14)),false)
            Puzzle.KNIGHTS_TOUR -> Lesson(KnightBoard(5,listOf(0)),listOf(KnightStep(7),KnightStep(4),KnightStep(13)),false)
            Puzzle.CROSSINGS -> Lesson(CrossingBoard(CrossingScenario.FOOD_FERRY,listOf(CrossingActor("W",CrossingRole.WOLF),CrossingActor("G",CrossingRole.GOAT),CrossingActor("C",CrossingRole.CABBAGE)),1),
                listOf(CrossRiver(listOf("G")),CrossRiver(emptyList()),CrossRiver(listOf("C"))),false)
            Puzzle.SLIDING_TILES -> Lesson(TilesBoard(3,listOf(1,2,3,0,4,6,7,5,8)),listOf(SlideTile(4),SlideTile(5),SlideTile(8)),false)
            Puzzle.BLOCK_ESCAPE -> Lesson(BlockEscapeBoard(blocks=listOf(EscapeBlock("A",0,0,2,2),EscapeBlock("B",2,2,1,2))),
                listOf(SlideBlock("B",0,2),SlideBlock("A",1,0),SlideBlock("A",1,5)),false)
            Puzzle.BOX_PUSHER -> Lesson(BoxPusherBoard(6,5,(1..3).flatMap { y -> (1..4).map { x -> y*6+x } },listOf(16),listOf(14),7),
                listOf(WalkTo(13),WorkerStep(Direction.RIGHT),WorkerStep(Direction.RIGHT)),false)
            Puzzle.HANOI -> Lesson(HanoiBoard(listOf(listOf(3,2,1), emptyList(), emptyList())), listOf(TransferDisk(0,2), TransferDisk(0,1), TransferDisk(2,1)), true)
            Puzzle.SWITCH_GRID -> {
                val lights = MutableList(9) { false }
                listOf(0,4,8).forEach { i -> SwitchEngine.affected(3,i).forEach { lights[it] = !lights[it] } }
                Lesson(SwitchBoard(3,lights), listOf(Toggle(0), Toggle(4), Toggle(8)), false)
            }
            Puzzle.WATER_JUGS -> Lesson(JugsBoard(listOf(3,2), listOf(0,0), 1), listOf(FillJug(0), PourJug(0,1)), true)
            Puzzle.PANCAKE_FLIP -> Lesson(PancakeBoard(listOf(1,3,4,2)), listOf(FlipPrefix(3), FlipPrefix(4), FlipPrefix(2)), false)
            else -> error("Tutorial not implemented: $puzzle")
        }
    }
    var step by rememberSaveable(puzzle) { mutableIntStateOf(0) }
    var selected by rememberSaveable(puzzle) { mutableStateOf<String?>(null) }
    var zoom by rememberSaveable(puzzle) { mutableIntStateOf(1) }
    var row by rememberSaveable(puzzle) { mutableIntStateOf(0) }
    var column by rememberSaveable(puzzle) { mutableIntStateOf(0) }
    var incorrect by remember { mutableStateOf(false) }
    val offset = if (lesson.selectFirst) 1 else 0
    val total = lesson.actions.size + offset
    val complete = step >= total
    val presentation = completionPresentation("tutorial-${puzzle.name}", complete, settings.reduceMotion)
    val visibleStep = if (presentation.resultsVisible) step else step.coerceAtMost(total - 1)
    val board = remember(step, lesson) {
        lesson.actions.take((step - offset).coerceAtLeast(0)).fold(lesson.initial) { state, move -> requireNotNull(engineFor(puzzle).apply(state, move)) }
    }
    val view = LocalView.current
    fun reject() { incorrect = true; feedback.play(Cue.ILLEGAL, settings, view, puzzle) }
    BackHandler(onBack = onBack)
    AdaptivePlayLayout(header = {
        TextButton(onClick = onBack) { Text(stringResource(R.string.play_back)) }
    }, information = {
        Text(stringResource(R.string.play_tutorial_puzzle, puzzleTitle(puzzle)), style = MaterialTheme.typography.headlineLarge)
        Text(if (presentation.resultsVisible) stringResource(R.string.play_tutorial_ready) else stringResource(R.string.play_lesson_step, visibleStep + 1, total))
        Text(if (presentation.resultsVisible) stringResource(R.string.play_lesson_complete) else when (puzzle) {
            Puzzle.PEG_SOLITAIRE -> (lesson.actions[visibleStep] as JumpPeg).let {stringResource(R.string.play_lesson_peg,it.from/5+1,it.from%5+1,it.to/5+1,it.to%5+1)}
            Puzzle.QUEENS -> (lesson.actions[visibleStep] as PlaceQueen).let {stringResource(R.string.play_lesson_queen,it.cell/4+1,it.cell%4+1)}
            Puzzle.KNIGHTS_TOUR -> (lesson.actions[visibleStep] as KnightStep).let {stringResource(R.string.play_lesson_knight,it.cell/5+1,it.cell%5+1)}
            Puzzle.CROSSINGS -> stringResource(when(visibleStep) {0->R.string.play_lesson_cross_first;1->R.string.play_lesson_cross_return;else->R.string.play_lesson_cross_next})
            Puzzle.SLIDING_TILES -> stringResource(R.string.play_lesson_tile,(lesson.actions[visibleStep] as SlideTile).tile)
            Puzzle.BLOCK_ESCAPE -> stringResource(when(visibleStep) {0 -> R.string.play_lesson_block_first;1 -> R.string.play_lesson_block_second;else -> R.string.play_lesson_block_exit})
            Puzzle.BOX_PUSHER -> stringResource(when(visibleStep) {0 -> R.string.play_lesson_box_walk;1 -> R.string.play_lesson_box_push;else -> R.string.play_lesson_box_goal})
            Puzzle.HANOI -> stringResource(when (visibleStep) { 0 -> R.string.play_lesson_hanoi_select; 1 -> R.string.play_lesson_hanoi_first; 2 -> R.string.play_lesson_hanoi_second; else -> R.string.play_lesson_hanoi_stack })
            Puzzle.SWITCH_GRID -> stringResource(when (visibleStep) { 0 -> R.string.play_lesson_switch_corner; 1 -> R.string.play_lesson_switch_center; else -> R.string.play_lesson_switch_finish })
            Puzzle.WATER_JUGS -> stringResource(when (visibleStep) { 0 -> R.string.play_lesson_jug_select; 1 -> R.string.play_lesson_jug_fill; else -> R.string.play_lesson_jug_pour })
            else -> stringResource(R.string.play_lesson_flip, (lesson.actions[visibleStep] as FlipPrefix).count)
        }, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (!presentation.resultsVisible) Text(puzzleGoal(board))
    }, board = if (!presentation.resultsVisible) { {
            PuzzleBoard(board, Viewport(column,row,zoom,selected), !complete,
                onViewport = { next ->
                    if (lesson.selectFirst && step == 0 && next.selectedId != "0") reject()
                    else {
                        selected = next.selectedId; row = next.row; column = next.column; zoom = next.zoom
                        incorrect = false
                        feedback.play(Cue.PICKUP, settings, view, puzzle)
                        if (lesson.selectFirst && step == 0 && selected == "0") step++
                    }
                }, onMove = { move ->
                    if (step >= offset && move == lesson.actions[step - offset]) {
                        step++
                        if (puzzle != Puzzle.PANCAKE_FLIP) selected = null
                        incorrect = false
                        feedback.play(if (step == total) Cue.COMPLETE else moveCue(move), settings, view, puzzle)
                    } else reject()
                }, onIllegal = ::reject, reduceMotion = settings.reduceMotion)
    } } else null, controls = {
        if (!presentation.resultsVisible) {
            if (incorrect) Text(stringResource(R.string.play_lesson_try_instruction), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        } else Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.play_tutorial_begin)) }
    })
}
