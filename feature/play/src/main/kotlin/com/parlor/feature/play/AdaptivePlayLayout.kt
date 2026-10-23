package com.parlor.feature.play

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.parlor.core.design.FoldRegions
import com.parlor.core.design.FoldPane
import com.parlor.core.design.largestPane

/** One set of content and controls, arranged for the current window rather than the device model. */
@Composable
fun AdaptivePlayLayout(
    header: @Composable ColumnScope.() -> Unit,
    information: @Composable ColumnScope.() -> Unit,
    board: (@Composable () -> Unit)?,
    controls: @Composable ColumnScope.() -> Unit,
    footer: @Composable () -> Unit = {},
) {
    val pageScroll = rememberScrollState()
    val boardScroll = rememberScrollState()
    val controlsScroll = rememberScrollState()
    FoldRegions(Modifier.fillMaxSize()) { panes ->
        val density = LocalDensity.current
        val pair = board != null && panes.size == 2 && panes.all {
            it.width >= with(density) { 288.dp.roundToPx() } && it.height >= with(density) { 240.dp.roundToPx() }
        }
        if (pair) {
            FoldPane(panes[0], Modifier.testTag("play-fold-layout")) {
                Column(Modifier.fillMaxSize().verticalScroll(boardScroll)
                    .padding(horizontal = 16.dp, vertical = 12.dp).testTag("play-board-pane"),
                    verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    header()
                    val boardWidth = with(density) { minOf(panes[0].width, panes[0].height).toDp() }.coerceAtLeast(288.dp) - 32.dp
                    Box(Modifier.widthIn(max = boardWidth).fillMaxWidth()) { board?.invoke() }
                }
            }
            FoldPane(panes[1]) {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(controlsScroll)
                        .padding(horizontal = 16.dp, vertical = 12.dp).testTag("play-controls-pane"),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        information()
                        controls()
                    }
                    footer()
                }
            }
        } else largestPane(panes)?.let { pane -> FoldPane(pane) {
                Column(Modifier.fillMaxSize()) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val wide = board != null && maxWidth >= 840.dp && maxWidth > maxHeight
                        if (wide) {
                            val controlsWidth = (maxWidth * .4f).coerceIn(280.dp, 400.dp)
                            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("play-wide-layout"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                header()
                                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().testTag("play-board-pane")) {
                                        // Do not grow a board beyond the available shorter axis. Extremely short
                                        // windows scroll instead of reducing the renderers' 48-dp interaction cells.
                                        val boardWidth = minOf(maxWidth, maxHeight.coerceAtLeast(288.dp))
                                        Column(Modifier.fillMaxSize().verticalScroll(boardScroll),
                                            horizontalAlignment = Alignment.CenterHorizontally) {
                                            Box(Modifier.width(boardWidth)) { board?.invoke() }
                                        }
                                    }
                                    Column(Modifier.width(controlsWidth).fillMaxHeight().verticalScroll(controlsScroll)
                                        .testTag("play-controls-pane"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        information()
                                        controls()
                                    }
                                }
                            }
                        } else {
                            Column(Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxSize()
                                .verticalScroll(pageScroll).padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("play-single-layout"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                header()
                                information()
                                board?.invoke()
                                controls()
                            }
                        }
                    }
                    footer()
                }
        } }
    }
}
