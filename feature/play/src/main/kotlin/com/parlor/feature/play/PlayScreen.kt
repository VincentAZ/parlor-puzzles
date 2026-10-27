package com.parlor.feature.play

import com.parlor.core.design.FoldAwareAlertDialog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parlor.core.game.*
import com.parlor.core.model.*
import com.parlor.core.platform.*
import com.parlor.core.progress.Scoring
import kotlinx.coroutines.delay
import com.parlor.core.design.rememberDisplayFormats

@Composable
fun PlayScreen(viewModel: PlayViewModel, settings: PlayerSettings, hints: Int, previouslyCompleted: Boolean,
               feedback: Feedback, onLeave: () -> Unit, onNext: (() -> Unit)?, pro: Boolean = true,
               onDemonstrate: (() -> Unit)? = null,
               banner: @Composable () -> Unit = {}, externalPause: Boolean = false,
               onLevelStarted: (String) -> Unit = {}, onCompleted: () -> Unit = {},
               onResultsLeave: (Attempt, () -> Unit) -> Unit = { _, navigate -> navigate() },
               onRewardHints: (() -> Unit)? = null, onPhotoPro: (() -> Unit)? = null,
               onResultsBack: () -> Unit = onLeave) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val photoEdit by viewModel.photoEdit.collectAsStateWithLifecycle()
    val attempt = state.attempt
    val photo = savedPhoto(viewModel, attempt?.photoId, true)
    val puzzle = viewModel.level.puzzle
    var illegalCount by remember { mutableIntStateOf(0) }
    var illegalVisible by remember { mutableStateOf(false) }
    LaunchedEffect(illegalCount) { if (illegalCount > 0) { illegalVisible = true; delay(900); illegalVisible = false } }
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val view = LocalView.current
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val reducedMotion = settings.reduceMotion || !foreground || state.paused || externalPause || dialog != null || photoEdit.pausesPlay
    val presentation = completionPresentation(attempt?.id, attempt?.completed == true, reducedMotion, loaded = attempt != null)
    val showResults = presentation.resultsVisible
    var completionAnnounced by rememberSaveable(attempt?.id) { mutableStateOf(false) }
    var celebration by remember(attempt?.id) { mutableStateOf(false) }
    LaunchedEffect(reducedMotion) { if (reducedMotion) celebration = false }
    val currentDialog by rememberUpdatedState(dialog)
    val currentExternalPause by rememberUpdatedState(externalPause || photoEdit.pausesPlay)
    LaunchedEffect(attempt?.id, attempt?.completed, showResults) {
        attempt?.let {
            if (showResults) onCompleted()
            else if (!it.completed) onLevelStarted(it.id)
        }
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { foreground = true; viewModel.lifecycle(true); viewModel.pause(currentDialog != null || currentExternalPause) }
                Lifecycle.Event.ON_PAUSE -> { foreground = false; viewModel.lifecycle(false) }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        foreground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        viewModel.lifecycle(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { viewModel.lifecycle(false); lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // A fast photo write can start and finish between frames; its revision still releases its pause.
    LaunchedEffect(dialog, externalPause, photoEdit.revision) { viewModel.pause(dialog != null || externalPause || photoEdit.pausesPlay) }
    fun back() {
        if (state.unavailable) onLeave()
        else if (attempt?.completed == true) onResultsLeave(attempt, onResultsBack)
        else if (attempt != null && attempt.history.isNotEmpty() && !attempt.completed) dialog = "leave"
        else viewModel.leave(onLeave)
    }
    BackHandler { back() }
    LaunchedEffect(attempt?.id, showResults, foreground) {
        if (attempt != null && showResults && presentation.fromActiveBoard && !completionAnnounced) {
            completionAnnounced = true
            if (!foreground) return@LaunchedEffect
            celebration = !reducedMotion && Scoring.result(viewModel.level, attempt) != null
            feedback.play(Cue.COMPLETE, settings, view, puzzle)
            repeat(Scoring.result(viewModel.level, attempt)?.stars ?: 0) { delay(220); feedback.play(Cue.STAR, settings, view, puzzle) }
        }
    }
    val header: @Composable ColumnScope.() -> Unit = {
        FlowRow(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { back() }) { Text(stringResource(R.string.play_back)) }
            Text(puzzleTitle(puzzle), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { dialog = "pause" }, enabled = attempt?.completed == false) { Text(stringResource(R.string.play_pause)) }
        }
    }
    val information: @Composable ColumnScope.() -> Unit = {
        if (attempt != null && !state.unavailable) {
            if (attempt.mode == AttemptMode.DAILY) {
                Text(stringResource(R.string.play_daily_date, rememberDisplayFormats().date(requireNotNull(attempt.challengeDate))),
                    style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (attempt.dailyEligibility == DailyEligibility.PRACTICE) R.string.play_daily_practice else R.string.play_daily_scored))
            }
            if (!showResults) {
                Text(puzzleGoal(attempt.board), style = MaterialTheme.typography.headlineSmall)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(scoredCount(viewModel.level, attempt.scoredMoves), style = MaterialTheme.typography.titleMedium)
                    if(puzzle==Puzzle.KNIGHTS_TOUR) Text(stringResource(R.string.play_backtracks,attempt.backtracks))
                    if (puzzle == Puzzle.BOX_PUSHER) Text(pluralStringResource(R.plurals.play_walking_count, attempt.walkingSteps, attempt.walkingSteps))
                    if (previouslyCompleted) Text(parCount(viewModel.level))
                    Text(stringResource(R.string.play_unlimited_hints))
                    if (settings.showTimer) Text(stringResource(R.string.play_time_count, attempt.activeTimeMs / 60_000, attempt.activeTimeMs / 1000 % 60))
                }
            }
        }
    }
    val board: (@Composable () -> Unit)? = if (attempt != null && !showResults && !state.unavailable) {
        {
            val actualBoard = attempt.board
            val displayBoard = if (actualBoard is TilesBoard && attempt.photoId != null && photo.bitmap == null)
                actualBoard.copy(art = TileArt.NUMBERS) else actualBoard
            key(attempt.id) { PuzzleBoard(displayBoard, attempt.viewport, !attempt.completed && dialog == null && !externalPause && !photoEdit.pausesPlay && !state.paused && !state.checkingAccess,
                onViewport = { viewport -> feedback.play(Cue.PICKUP, settings, view, puzzle); viewModel.viewport(viewport) },
                onMove = { move ->
                    val legal = engineFor(puzzle).apply(attempt.board, move) != null
                    feedback.play(if (legal) moveCue(move) else Cue.ILLEGAL, settings, view, puzzle)
                    if (!legal) illegalCount++
                    viewModel.move(move)
                }, onIllegal = { illegalCount++; feedback.play(Cue.ILLEGAL, settings, view, puzzle) }, photo = photo.bitmap,
                reduceMotion = reducedMotion) }
        }
    } else null
    AdaptivePlayLayout(header = header, information = information, board = board, controls = {
        when {
            state.unavailable -> {
                Text(stringResource(R.string.play_access_unavailable), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onLeave) { Text(stringResource(R.string.play_back)) }
            }
            attempt == null -> {
                if (state.error) {
                    Text(stringResource(R.string.play_open_error))
                    Button(onClick = { viewModel.open() }) { Text(stringResource(R.string.play_retry)) }
                } else {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    Text(stringResource(R.string.play_loading))
                }
            }
            attempt.completed && !showResults -> Text(stringResource(R.string.play_saved), style = MaterialTheme.typography.bodySmall)
            showResults -> {
                val result = Scoring.result(viewModel.level, attempt)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(if (puzzle == Puzzle.TRAFFIC_JAM) R.string.play_completed_title else R.string.play_puzzle_completed), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.play_completed_body), style = MaterialTheme.typography.bodyLarge)
                if (result != null) {
                    val starDescription = stringResource(R.string.play_stars_description, result.stars, 3)
                    Box(Modifier.fillMaxWidth()) {
                        Text("★".repeat(result.stars) + "☆".repeat(3 - result.stars), style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { contentDescription = starDescription })
                        CompletionConfetti(celebration && !reducedMotion, Modifier.matchParentSize())
                    }
                    Text(stringResource(R.string.play_points_count, result.points), style = MaterialTheme.typography.headlineSmall)
                    if (attempt.hintsUsed) Text(stringResource(R.string.play_hint_cap))
                } else Text(stringResource(R.string.play_practice_complete), style = MaterialTheme.typography.titleLarge)
                val timed=viewModel.level.scoringUnit==ScoringUnit.TIME||viewModel.level.scoringUnit==ScoringUnit.TIME_AND_BACKTRACKS
                Text((if(timed) stringResource(R.string.play_result_seconds,attempt.activeTimeMs/1000) else scoredCount(viewModel.level,attempt.scoredMoves)) + " · " + parCount(viewModel.level))
                if (settings.showTimer&&!timed) Text(stringResource(R.string.play_time_count, attempt.activeTimeMs / 60_000, attempt.activeTimeMs / 1000 % 60))
                if(puzzle==Puzzle.KNIGHTS_TOUR) Text(stringResource(R.string.play_backtracks,attempt.backtracks))
                if (onNext != null) Button(onClick = { onResultsLeave(attempt, onNext) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.play_next_level)) }
                OutlinedButton(onClick = { viewModel.restart() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.play_play_again)) }
                TextButton(onClick = { onResultsLeave(attempt, onResultsBack) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (attempt.mode == AttemptMode.DAILY) R.string.play_back_to_daily else R.string.play_back_to_levels)) }
                if (puzzle == Puzzle.BOX_PUSHER) Text(pluralStringResource(R.plurals.play_walking_count, attempt.walkingSteps, attempt.walkingSteps))
                if (state.error) Text(stringResource(R.string.play_save_error), color = MaterialTheme.colorScheme.error)
            }
            else -> {
                if (illegalVisible) Text(stringResource(R.string.play_illegal_move), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    OutlinedButton(onClick = { feedback.play(Cue.UNDO, settings, view, puzzle); viewModel.undo() }, enabled = !state.busy && attempt.history.isNotEmpty()) { Text(stringResource(R.string.play_undo)) }
                    OutlinedButton(onClick = { feedback.play(Cue.PLACE, settings, view, puzzle); viewModel.redo() }, enabled = !state.busy && attempt.redo.isNotEmpty()) { Text(stringResource(R.string.play_redo)) }
                    Button(onClick = { feedback.play(Cue.HINT, settings, view, puzzle); viewModel.hint() }, enabled = !state.busy) { Text(stringResource(R.string.play_hint)) }
                }
                state.hint?.let { hint ->
                    val message = when (hint) {
                        is Hint.Next -> moveDescription(attempt.board, hint.move)
                        is Hint.Recover -> pluralStringResource(R.plurals.play_hint_recover, hint.undoCount, hint.undoCount)
                        Hint.Unavailable -> stringResource(R.string.play_hint_unavailable)
                    }
                    Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.primary)
                }
                if (state.error) {
                    Text(stringResource(R.string.play_save_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::checkpoint) { Text(stringResource(R.string.play_retry)) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.play_saved), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { dialog = "restart" }) { Text(stringResource(R.string.play_restart)) }
                }
                if (onDemonstrate != null) OutlinedButton(onClick = { viewModel.leave(onDemonstrate) }, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.play_solve_for_me)) }
                if (attempt.board is TilesBoard && attempt.mode == AttemptMode.CAMPAIGN) PhotoControls(attempt, photoEdit, viewModel,
                    true, !state.busy && !state.checkingAccess && !externalPause && !state.unavailable && !photoEdit.pausesPlay,
                    photo.failed, onPhotoPro)
            }
        }
    }, footer = { if (showResults) banner() })
    if (dialog != null) FoldAwareAlertDialog(onDismissRequest = { dialog = null },
        title = { Text(stringResource(when (dialog) { "leave" -> R.string.play_leave_title; "restart" -> R.string.play_restart_title; else -> R.string.play_pause_title })) },
        text = { Text(stringResource(when (dialog) { "leave" -> R.string.play_leave_body; "restart" -> R.string.play_restart_body; else -> R.string.play_paused_body })) },
        confirmButton = { TextButton(onClick = {
            when (dialog) { "leave" -> viewModel.leave(onLeave); "restart" -> viewModel.restart() }
            dialog = null
        }) { Text(stringResource(when (dialog) { "leave" -> R.string.play_leave; "restart" -> R.string.play_restart; else -> R.string.play_resume })) } },
        dismissButton = { if (dialog != "pause") TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.play_cancel)) } })
}
