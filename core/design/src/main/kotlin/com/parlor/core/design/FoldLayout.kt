package com.parlor.core.design

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

val LocalWindowFolds = compositionLocalOf<List<WindowFold>> { emptyList() }

@Composable
fun ProvideWindowFolds(activity: Activity, content: @Composable () -> Unit) {
    val flow = remember(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).map { info ->
            info.displayFeatures.filterIsInstance<FoldingFeature>().filter {
                it.isSeparating || it.occlusionType == FoldingFeature.OcclusionType.FULL
            }.map { fold ->
                val b = fold.bounds
                WindowFold(IntRect(b.left, b.top, b.right, b.bottom), fold.orientation == FoldingFeature.Orientation.VERTICAL)
            }
        }
    }
    val folds by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    CompositionLocalProvider(LocalWindowFolds provides folds, content = content)
}

/** Consume system/cutout insets once; convert WindowManager bounds into this layout's coordinates. */
@Composable
fun FoldRegions(modifier: Modifier = Modifier, content: @Composable BoxWithConstraintsScope.(List<IntRect>) -> Unit) {
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    val folds = LocalWindowFolds.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(modifier.safeDrawingPadding().onGloballyPositioned {
        val point = it.positionInWindow()
        origin = IntOffset(point.x.roundToInt(), point.y.roundToInt())
    }) {
        val density = LocalDensity.current
        val bounds = IntRect(origin.x, origin.y, origin.x + constraints.maxWidth, origin.y + constraints.maxHeight)
        val panes = foldPanes(bounds, folds, with(density) { 8.dp.roundToPx() }, rtl)
            .map { it.translate(-origin) }
        content(panes)
    }
}

@Composable
fun BoxScope.FoldPane(bounds: IntRect, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val density = LocalDensity.current
    Box(modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset { IntOffset(bounds.left, bounds.top) }
        .requiredSize(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
        .clipToBounds(), content = content)
}

/** Screens with one scroll area stay together on the largest usable side of a separating fold. */
@Composable
fun FoldSafePane(content: @Composable () -> Unit) {
    FoldRegions(Modifier.fillMaxSize()) { panes ->
        largestPane(panes)?.let { FoldPane(it) { content() } }
    }
}
