package com.parlor.feature.play

import com.parlor.core.design.LocalBoardSkin
import com.parlor.core.design.roomTexture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parlor.core.model.*

@Composable
internal fun SwitchBoardView(board: SwitchBoard, viewport: Viewport, enabled: Boolean, onViewport: (Viewport) -> Unit, onMove: (Move) -> Unit) {
    val skin = LocalBoardSkin.current
    val fills = board.lights.mapIndexed { index, on -> key(index) { boardColor(if (on) skin.gold else skin.tile) } }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val canFitWholeBoard = maxWidth / board.side >= 48.dp
        val magnified = !canFitWholeBoard || viewport.zoom > 1
        val cells = if (magnified) ((maxWidth - 108.dp) / 48.dp).toInt().coerceIn(1, minOf(4, board.side)) else board.side
        val cell = minOf((maxWidth - if (magnified) 108.dp else 0.dp) / cells, 64.dp)
        val row = if (magnified) viewport.row.coerceIn(0, board.side - cells) else 0
        val column = if (magnified) viewport.column.coerceIn(0, board.side - cells) else 0
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (magnified) {
                    val overview = stringResource(R.string.play_switch_overview, board.side, board.lights.count { it })
                    Canvas(Modifier.size(96.dp).testTag("switch-overview")
                        .semantics { contentDescription = overview }
                        .pointerInput(board.side, cells) { detectTapGestures { tap ->
                            onViewport(viewport.copy(column = (tap.x / size.width * board.side).toInt().coerceIn(0, board.side - cells),
                                row = (tap.y / size.height * board.side).toInt().coerceIn(0, board.side - cells)))
                        } }) {
                        val unit = size.width / board.side
                        board.lights.forEachIndexed { index, on ->
                            val origin = Offset(index % board.side * unit, index / board.side * unit)
                            drawRect(fills[index].value, origin, Size(unit - 1, unit - 1))
                            if (on) drawCircle(skin.ink, unit * .18f, origin + Offset(unit / 2, unit / 2))
                        }
                        drawRect(skin.ink, Offset(column * unit, row * unit), Size(cells * unit, cells * unit), style = Stroke(2.dp.toPx()))
                    }
                    Spacer(Modifier.width(12.dp))
                }
                BoardCoordinates {
                    Column {
                        for (y in row until row + cells) Row {
                            for (x in column until column + cells) {
                                val index = y * board.side + x
                                val on = board.lights[index]
                                val label = stringResource(R.string.play_switch_cell, y + 1, x + 1)
                                val status = stringResource(if (on) R.string.play_light_on else R.string.play_light_off)
                                Box(Modifier.size(cell).testTag("switch-cell-$index")
                                    .semantics { contentDescription = label; stateDescription = status }
                                    .clickable(enabled = enabled, role = Role.Button, onClickLabel = stringResource(R.string.play_toggle)) { onMove(Toggle(index)) }
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(8.dp)).background(fills[index].value).roomTexture(skin)
                                    .border(1.dp, skin.ink, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center) {
                                    Text(if (on) "●" else "○", fontSize = 20.sp, color = skin.ink, modifier = Modifier.clearAndSetSemantics {})
                                }
                            }
                        }
                    }
                }
            }
            if (magnified) {
                Text(stringResource(R.string.play_switch_window, row + 1, row + cells, column + 1, column + cells))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ViewPan("←", stringResource(R.string.play_pan_left), column > 0) { onViewport(viewport.copy(column = column - 1)) }
                    ViewPan("→", stringResource(R.string.play_pan_right), column < board.side - cells) { onViewport(viewport.copy(column = column + 1)) }
                    ViewPan("↑", stringResource(R.string.play_pan_up), row > 0) { onViewport(viewport.copy(row = row - 1)) }
                    ViewPan("↓", stringResource(R.string.play_pan_down), row < board.side - cells) { onViewport(viewport.copy(row = row + 1)) }
                }
            }
            if (canFitWholeBoard) TextButton(onClick = { onViewport(viewport.copy(zoom = if (magnified) 1 else 2)) }) {
                Text(stringResource(if (magnified) R.string.play_zoom_out else R.string.play_zoom_in))
            }
        }
    }
}

@Composable
private fun ViewPan(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        .semantics { contentDescription = label }) { Text(symbol, modifier = Modifier.clearAndSetSemantics {}) }
}
