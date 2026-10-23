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
import com.parlor.core.game.TrafficJamEngine
import com.parlor.core.model.*
import com.parlor.core.platform.*

/** A separate teaching board. It cannot spend hints, affect an attempt or award progress. */
@Composable
fun TrafficTutorial(settings: PlayerSettings, feedback: Feedback, onDone: () -> Unit, onBack: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var zoom by rememberSaveable { mutableIntStateOf(1) }
    var column by rememberSaveable { mutableIntStateOf(0) }
    var row by rememberSaveable { mutableIntStateOf(0) }
    val view = LocalView.current
    val initial = remember { TrafficJamBoard(pieces = listOf(
        TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL), TrafficPiece("B", 2, 0, 3, Axis.VERTICAL),
    )) }
    val board = remember(step) {
        val moved = if (step >= 3) TrafficJamEngine.apply(initial, Translate("B", 3)) as TrafficJamBoard else initial
        if (step >= 4) TrafficJamEngine.apply(moved, Translate("A", 6)) as TrafficJamBoard else moved
    }
    val presentation = completionPresentation("traffic-tutorial", step == 4, settings.reduceMotion)
    val visibleStep = if (presentation.resultsVisible) step else step.coerceAtMost(3)
    BackHandler(onBack = onBack)
    AdaptivePlayLayout(header = {
        TextButton(onClick = onBack) { Text(stringResource(R.string.play_back)) }
    }, information = {
        Text(stringResource(R.string.play_tutorial_title), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(if (visibleStep == 4) R.string.play_tutorial_ready else R.string.play_tutorial_step, minOf(visibleStep + 1, 4)),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(when (visibleStep) {
            0 -> R.string.play_tutorial_select_target
            1 -> R.string.play_tutorial_select_blocker
            2 -> R.string.play_tutorial_move_blocker
            3 -> R.string.play_tutorial_exit
            else -> R.string.play_tutorial_finished
        }), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }, board = if (!presentation.resultsVisible) { {
        CompositionLocalProvider(LocalReducedMotion provides settings.reduceMotion) {
        TrafficBoard(board, Viewport(column, row, zoom, selected), enabled = step < 4,
            onViewport = { next ->
                selected = next.selectedId; column = next.column; row = next.row; zoom = next.zoom
                feedback.play(Cue.PICKUP, settings, view)
                if (step == 0 && selected == "A") step = 1
                else if (step == 1 && selected == "B") step = 2
            }, onMove = { move ->
                when {
                    step == 2 && move == Translate("B", 3) -> { step = 3; selected = null; feedback.play(Cue.SLIDE, settings, view) }
                    step == 3 && move == Translate("A", 6) -> { step = 4; feedback.play(Cue.COMPLETE, settings, view) }
                    else -> feedback.play(Cue.ILLEGAL, settings, view)
                }
            })
        }
    } } else null, controls = {
        if (presentation.resultsVisible) Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.play_tutorial_begin))
        }
    })
}
