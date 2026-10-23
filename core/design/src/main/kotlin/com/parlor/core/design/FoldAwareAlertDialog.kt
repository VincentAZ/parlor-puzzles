package com.parlor.core.design

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/** Keep Material's content/buttons while placing its modal window clear of a separating hinge. */
@Composable
fun FoldAwareAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val folds = LocalWindowFolds.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    val dismiss by rememberUpdatedState(onDismissRequest)
    val placement = remember { DialogPlacement() }
    val scroll = rememberScrollState()
    val adapted = if (folds.isEmpty()) modifier else Modifier.fillMaxSize().safeDrawingPadding()
        .onGloballyPositioned {
            val point = it.positionInWindow()
            origin = IntOffset(point.x.roundToInt(), point.y.roundToInt())
        }
        .pointerInput(Unit) {
            // The native dialog now fills the window. Preserve outside-tap dismissal ourselves;
            // child controls consume their own taps before this handler sees them.
            detectTapGestures { point -> if (!placement.bounds.contains(IntOffset(point.x.toInt(), point.y.toInt()))) dismiss() }
        }
        .layout { measurable, constraints ->
            val window = IntRect(origin.x, origin.y, origin.x + constraints.maxWidth, origin.y + constraints.maxHeight)
            val pane = largestPane(foldPanes(window, folds, 8.dp.roundToPx(), rtl))?.translate(-origin)
                ?: IntRect(0, 0, 0, 0)
            val margin = 24.dp.roundToPx()
            val width = minOf(560.dp.roundToPx(), (pane.width - margin * 2).coerceAtLeast(0))
            val height = (pane.height - margin * 2).coerceAtLeast(0)
            val child = measurable.measure(Constraints(maxWidth = width, maxHeight = height))
            val x = pane.left + (pane.width - child.width) / 2
            val y = pane.top + (pane.height - child.height) / 2
            placement.bounds = IntRect(x, y, x + child.width, y + child.height)
            layout(constraints.maxWidth, constraints.maxHeight) { child.place(x, y) }
        }.testTag("fold-dialog-viewport").verticalScroll(scroll)
        // Bound the Material body even when the whole dialog must scroll in a short tabletop
        // pane. Its existing inner text scrollers still receive finite constraints.
        .heightIn(max = 600.dp).then(modifier).testTag("fold-dialog-content")
    AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = adapted,
        dismissButton = dismissButton, title = title, text = text,
        properties = if (folds.isEmpty()) DialogProperties() else DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false))
}

private class DialogPlacement { var bounds = IntRect.Zero }
