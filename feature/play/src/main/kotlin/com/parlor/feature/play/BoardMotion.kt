package com.parlor.feature.play

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

internal const val BoardMotionMillis = 140
internal val LocalReducedMotion = staticCompositionLocalOf { false }

/** Presentation values only. Engines, saved history and accessibility descriptions use the committed board. */
@Composable
internal fun boardPoint(target: Offset, immediate: Boolean = false): State<Offset> {
    val reduced = LocalReducedMotion.current || immediate
    val animated = animateOffsetAsState(target, if (reduced) snap() else tween(BoardMotionMillis, easing = FastOutSlowInEasing), label = "board position")
    // A preference change removes movement in this frame, including an already-running transition.
    return if (reduced) rememberUpdatedState(target) else animated
}

@Composable
internal fun boardAmount(target: Float): State<Float> {
    val reduced = LocalReducedMotion.current
    val animated = animateFloatAsState(target, if (reduced) snap() else tween(BoardMotionMillis, easing = FastOutSlowInEasing), label = "board amount")
    return if (reduced) rememberUpdatedState(target) else animated
}

@Composable
internal fun boardColor(target: Color): State<Color> {
    val reduced = LocalReducedMotion.current
    val animated = animateColorAsState(target, if (reduced) snap() else tween(BoardMotionMillis, easing = FastOutSlowInEasing), label = "board color")
    return if (reduced) rememberUpdatedState(target) else animated
}

/** Direct manipulation uses only immediate legal moves, never a solver or a reference path. */
internal fun constrainedSlide(distance: Offset, destinations: List<Offset>): Offset {
    val horizontal = kotlin.math.abs(distance.x) >= kotlin.math.abs(distance.y)
    val axis = destinations.filter { if (horizontal) it.y == 0f else it.x == 0f } + Offset.Zero
    return if (horizontal) Offset(distance.x.coerceIn(axis.minOf { it.x }, axis.maxOf { it.x }), 0f)
    else Offset(0f, distance.y.coerceIn(axis.minOf { it.y }, axis.maxOf { it.y }))
}

internal fun nearestDestination(position: Offset, destinations: List<Offset>): Offset =
    (listOf(Offset.Zero) + destinations).minBy { (it - position).getDistanceSquared() }
