package com.parlor.feature.play

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.parlor.core.design.LocalBoardSkin

internal data class CompletionPresentation(val resultsVisible: Boolean, val fromActiveBoard: Boolean)

/** Completion is already durable. Only the presentation waits for the last board movement. */
@Composable
internal fun completionPresentation(session: String?, complete: Boolean, reduced: Boolean, loaded: Boolean = true): CompletionPresentation {
    var observedUnfinished by rememberSaveable(session) { mutableStateOf(false) }
    var ready by rememberSaveable(session) { mutableStateOf(complete) }
    if (loaded && !complete) SideEffect { observedUnfinished = true }
    LaunchedEffect(session, complete, reduced) {
        if (complete && !ready) {
            if (observedUnfinished && !reduced) {
                // Uses Compose's animation clock and the system duration scale, including scale zero.
                animate(0f, 1f, animationSpec = tween<Float>(BoardMotionMillis)) { _, _ -> }
            }
            ready = true
        }
    }
    return CompletionPresentation(complete && (ready || reduced || !observedUnfinished), observedUnfinished)
}

/** A short, decorative fall. No input, semantic announcement, repeat loop or reward mutation. */
@Composable
internal fun CompletionConfetti(play: Boolean, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    val colors = LocalBoardSkin.current.pieces
    LaunchedEffect(play) {
        if (play) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(300, easing = LinearOutSlowInEasing))
        } else progress.snapTo(1f)
    }
    if (play && progress.value < 1f) Canvas(modifier.testTag("completion-confetti").clearAndSetSemantics {}) {
        val fraction = progress.value
        val alpha = ((fraction / .15f).coerceAtMost(1f) * (1f - fraction)).coerceIn(0f, 1f)
        repeat(24) { index ->
            val origin = Offset(size.width * (index + .5f) / 24 + ((index % 5) - 2) * 4.dp.toPx() * fraction,
                size.height * .18f + (index % 3) * 5.dp.toPx() + (12 + index % 4 * 4).dp.toPx() * fraction)
            rotate(index * 31f + fraction * 20f, origin) {
                drawRoundRect(colors[index % colors.size], origin, Size(4.dp.toPx(), 2.dp.toPx()),
                    CornerRadius(.75.dp.toPx()), alpha = alpha)
            }
        }
    }
}
