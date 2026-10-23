package com.parlor.feature.play

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import com.parlor.core.game.BoxPusherEngine
import com.parlor.core.model.BoxPusherBoard
import kotlin.math.abs

internal fun BoxPusherBoard.point(cell: Int) = Offset((cell % width).toFloat(), (cell / width).toFloat())

/** Keep the one pushed box's visual identity; unrelated snapshots snap instead of inventing a route. */
@Composable
internal fun boxPositions(board: BoxPusherBoard): Map<Int, State<Offset>> {
    var previous by remember { mutableStateOf(board.boxes.sorted().mapIndexed { id, cell -> cell to id }.toMap()) }
    val identities = remember(board.boxes) {
        val retained = previous.filterKeys { it in board.boxes }
        val available = previous.filterKeys { it !in board.boxes }.values.sorted()
        retained + board.boxes.filter { it !in retained }.sorted().zip(available)
    }
    val ambiguous = remember(board.boxes) { board.boxes.count { it !in previous } > 1 }
    SideEffect { previous = identities }
    return identities.entries.sortedBy { it.value }.associate { (cell, id) ->
        cell to key(id) { boardPoint(board.point(cell), immediate = ambiguous) }
    }
}

/** Automatic walking follows the same verified floor route as the engine, never a line through walls. */
@Composable
internal fun workerPosition(board: BoxPusherBoard): State<Offset> {
    val target = board.point(board.worker)
    val reduced = LocalReducedMotion.current
    val position = remember { mutableStateOf(target) }
    val activeRoute = remember { mutableStateOf(listOf(target)) }
    var previous by remember { mutableStateOf(board) }
    val before = remember(board) { previous }
    SideEffect { previous = board }
    LaunchedEffect(board, reduced) {
        if (reduced || position.value == target) {
            position.value = target
            activeRoute.value = listOf(target)
            return@LaunchedEffect
        }
        val path = BoxPusherEngine.walkingPath(before, board.worker)
        val adjacent = abs(before.worker % board.width - board.worker % board.width) +
            abs(before.worker / board.width - board.worker / board.width) == 1
        if (path == null && !adjacent) {
            position.value = target
            activeRoute.value = listOf(target)
            return@LaunchedEffect
        }
        val route = listOf(before.point(before.worker)) + (path ?: listOf(board.worker)).map(board::point)
        val from = position.value
        // Undo can interrupt a walk. Reverse directly when on that route, or finish the known
        // remaining corridor before joining the next one; no diagonal shortcut is fabricated.
        val remaining = routeSuffix(route, from) ?: routeSuffix(activeRoute.value, from)?.let { it + route.drop(1) }
        if (remaining == null) {
            position.value = target
            activeRoute.value = listOf(target)
            return@LaunchedEffect
        }
        activeRoute.value = remaining
        animate(0f, 1f, animationSpec = tween(BoardMotionMillis, easing = FastOutSlowInEasing)) { fraction, _ ->
            position.value = pointOnRoute(remaining, fraction)
        }
        position.value = target
    }
    return if (reduced) rememberUpdatedState(target) else position
}

private fun routeSuffix(route: List<Offset>, point: Offset): List<Offset>? {
    if (route.lastOrNull() == point) return listOf(point)
    for (index in 0 until route.lastIndex) {
        val a = route[index]; val b = route[index + 1]
        val ab = (b - a).getDistance()
        if (abs((point - a).getDistance() + (b - point).getDistance() - ab) < .001f) {
            return listOf(point) + route.drop(index + 1)
        }
    }
    return null
}

private fun pointOnRoute(route: List<Offset>, fraction: Float): Offset {
    val lengths = route.zipWithNext { a, b -> (b - a).getDistance() }
    var distance = lengths.sum() * fraction
    for (index in lengths.indices) {
        val length = lengths[index]
        if (length > 0f && distance <= length) return route[index] + (route[index + 1] - route[index]) * (distance / length)
        distance -= length
    }
    return route.last()
}
