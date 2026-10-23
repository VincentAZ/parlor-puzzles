package com.parlor.feature.play

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.parlor.core.game.Demonstration
import com.parlor.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun DemonstrationScreen(level: LevelDefinition, settings: PlayerSettings, onBack: () -> Unit) {
    var tape by remember(level.id, level.revision) { mutableStateOf<Demonstration?>(null) }
    var failed by remember { mutableStateOf(false) }
    var step by rememberSaveable(level.id, level.revision) { mutableIntStateOf(0) }
    var playing by rememberSaveable { mutableStateOf(!settings.reduceMotion) }
    var foreground by remember { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(Viewport()) }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(settings.reduceMotion) { if (settings.reduceMotion) playing = false }
    LaunchedEffect(level) {
        try { tape = withContext(Dispatchers.Default) { Demonstration.from(level) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.lifecycle.addObserver(observer)
        foreground = lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(playing, foreground, tape, step) {
        val last = tape?.states?.lastIndex ?: return@LaunchedEffect
        if (playing && foreground && step < last) { delay(800); step++ }
    }
    BackHandler(onBack = onBack)
    val replay = tape
    val position = replay?.let { step.coerceIn(it.states.indices) } ?: 0
    AdaptivePlayLayout(header = {
        TextButton(onClick = onBack) { Text(stringResource(R.string.play_return_to_puzzle)) }
    }, information = {
        Text(stringResource(R.string.play_demonstration), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.play_demonstration_body))
        if (replay != null) Text(stringResource(R.string.play_demonstration_step, position, replay.states.lastIndex),
            Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }, board = if (replay != null) { {
        key(level.id, level.revision) {
            PuzzleBoard(replay.states[position], viewport, enabled = false, onViewport = { viewport = it }, onMove = {},
                reduceMotion = settings.reduceMotion || !foreground)
        }
    } } else null, controls = {
        if (replay == null) {
            if (failed) Text(stringResource(R.string.play_demonstration_error)) else CircularProgressIndicator()
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { playing = false; step = (position - 1).coerceAtLeast(0) }, enabled = position > 0) { Text(stringResource(R.string.play_previous_step)) }
                Button(onClick = { playing = !playing }, enabled = position < replay.states.lastIndex) { Text(stringResource(if (playing) R.string.play_pause else R.string.play_play_demonstration)) }
                OutlinedButton(onClick = { playing = false; step = (position + 1).coerceAtMost(replay.states.lastIndex) }, enabled = position < replay.states.lastIndex) { Text(stringResource(R.string.play_next_step)) }
            }
            if (position == replay.states.lastIndex) Text(stringResource(if (level.puzzle == Puzzle.TRAFFIC_JAM)
                R.string.play_demonstration_complete else R.string.play_demonstration_puzzle_complete))
            TextButton(onClick = { playing = false; step = 0 }) { Text(stringResource(R.string.play_watch_again)) }
        }
    })
}
