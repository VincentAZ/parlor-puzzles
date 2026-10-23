package com.parlor.feature.play

import com.parlor.core.design.rememberDisplayFormats
import com.parlor.core.design.LocalBoardSkin
import com.parlor.core.design.roomTexture

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.parlor.core.game.TrafficJamEngine
import com.parlor.core.model.*
import kotlin.math.roundToInt



@Composable
fun TrafficBoard(board: TrafficJamBoard, viewport: Viewport, enabled: Boolean, onViewport: (Viewport) -> Unit, onMove: (Move) -> Unit) {
    val formats = rememberDisplayFormats()
    val skin = LocalBoardSkin.current
    val pieceColors = skin.pieces
    val selectedPiece = board.pieces.find { it.id == viewport.selectedId }
    val legal = remember(board) { TrafficJamEngine.legalMoves(board).filterIsInstance<Translate>() }
    val selectedMoves = legal.filter { it.pieceId == selectedPiece?.id }
    var dragged by remember(board, enabled, viewport.zoom, viewport.column, viewport.row) { mutableStateOf<String?>(null) }
    var dragOffset by remember(board, enabled) { mutableStateOf(Offset.Zero) }
    val dragMoves = legal.filter { it.pieceId == dragged }
    val positions = board.pieces.associate { piece ->
        piece.id to key(piece.id) { boardPoint(Offset(piece.x.toFloat(), piece.y.toFloat()) + if (dragged == piece.id) dragOffset else Offset.Zero, immediate = dragged == piece.id) }
    }
    val canInteract = enabled
    val description = stringResource(R.string.play_board_description)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val availableWidth = maxWidth
            val magnified = availableWidth / 6 < 48.dp || viewport.zoom > 1
            val sideOverview = magnified && availableWidth >= 384.dp
            val cells = if (magnified) 3 else 6
            val column = if (magnified) viewport.column.coerceIn(0, 3) else 0
            val row = if (magnified) viewport.row.coerceIn(0, 3) else 0
            val cell = minOf((availableWidth - if (sideOverview) 168.dp else 0.dp) / cells, 72.dp)
            val px = with(LocalDensity.current) { cell.toPx() }
            val overview: @Composable () -> Unit = {
                val overviewDescription = stringResource(R.string.play_overview)
                Canvas(Modifier.size(156.dp).clip(RoundedCornerShape(12.dp)).background(skin.base).roomTexture(skin)
                    .semantics { contentDescription = overviewDescription }
                    .pointerInput(viewport) { detectTapGestures { position ->
                        onViewport(viewport.copy(column = (position.x / size.width * 6).toInt().coerceIn(0, 3),
                            row = (position.y / size.height * 6).toInt().coerceIn(0, 3)))
                    } }) {
                    val unit = size.width / 6
                    board.pieces.forEachIndexed { index, p ->
                        val position = positions.getValue(p.id).value
                        if (position.x < 6) drawRoundRect(if (p.id == board.targetId) skin.target else pieceColors[index % pieceColors.size],
                            Offset(position.x * unit + 2, position.y * unit + 2),
                            Size((if (p.axis == Axis.HORIZONTAL) p.length else 1) * unit - 4,
                                (if (p.axis == Axis.VERTICAL) p.length else 1) * unit - 4))
                    }
                    drawRect(skin.ink, Offset(column * unit, row * unit), Size(3 * unit, 3 * unit),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                }
            }
            val pan: @Composable () -> Unit = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onViewport(viewport.copy(column = (column - 1).coerceAtLeast(0))) }, enabled = column > 0) { Text(stringResource(R.string.play_pan_left)) }
                    TextButton(onClick = { onViewport(viewport.copy(column = (column + 1).coerceAtMost(3))) }, enabled = column < 3) { Text(stringResource(R.string.play_pan_right)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onViewport(viewport.copy(row = (row - 1).coerceAtLeast(0))) }, enabled = row > 0) { Text(stringResource(R.string.play_pan_up)) }
                    TextButton(onClick = { onViewport(viewport.copy(row = (row + 1).coerceAtMost(3))) }, enabled = row < 3) { Text(stringResource(R.string.play_pan_down)) }
                }
            }
            val interaction: @Composable () -> Unit = {
                BoardCoordinates {
                    Box(Modifier.size(cell * cells).clip(RoundedCornerShape(16.dp)).background(skin.base).roomTexture(skin)
                        .semantics { contentDescription = description }) {
                        for (y in row until row + cells) for (x in column until column + cells) {
                            val occupied = board.pieces.any { p ->
                                x in p.x until p.x + (if (p.axis == Axis.HORIZONTAL) p.length else 1) &&
                                    y in p.y until p.y + (if (p.axis == Axis.VERTICAL) p.length else 1)
                            }
                            val destination = selectedPiece?.let { p ->
                                selectedMoves.find { m -> m.to == (if (p.axis == Axis.HORIZONTAL) x else y) &&
                                    (if (p.axis == Axis.HORIZONTAL) y == p.y else x == p.x) }
                            }
                            val dragPiece = board.pieces.find { it.id == dragged }
                            val dragDestination = dragPiece?.let { p -> dragMoves.any { m ->
                                if (p.axis == Axis.HORIZONTAL) y == p.y && x in m.to until m.to + p.length
                                else x == p.x && y in m.to until m.to + p.length
                            } } == true
                            val cellDescription = stringResource(R.string.play_cell_description, y + 1, x + 1)
                            Box(Modifier.offset(cell * (x - column), cell * (y - row)).size(cell)
                                .background(if (dragDestination) skin.target.copy(alpha = .28f) else Color.Transparent)
                                .border(if (dragDestination) 2.dp else 0.5.dp, if (dragDestination) skin.ink else skin.ink.copy(alpha = .2f), RoundedCornerShape(4.dp))
                                .then(if (!occupied && destination != null && canInteract) Modifier.clickable { onMove(destination) }
                                    .semantics { contentDescription = cellDescription; role = Role.Button } else Modifier.clearAndSetSemantics {}))
                        }
                        board.pieces.forEachIndexed { index, piece ->
                            val position = positions.getValue(piece.id).value
                            val horizontal = piece.axis == Axis.HORIZONTAL
                            val startX = position.x
                            val startY = position.y
                            val endX = position.x + if (horizontal) piece.length else 1
                            val endY = position.y + if (horizontal) 1 else piece.length
                            if (dragged != piece.id && (endX <= column || endY <= row || startX >= column + cells || startY >= row + cells)) return@forEachIndexed
                            val isTarget = piece.id == board.targetId
                            val isSelected = piece.id == selectedPiece?.id
                            val pieceDescription = stringResource(R.string.play_piece_description, piece.id,
                                stringResource(if (horizontal) R.string.play_horizontal else R.string.play_vertical), piece.y + 1, piece.x + 1, piece.length)
                            val actions = legal.filter { it.pieceId == piece.id }.map { move ->
                                val label = if (move.to == 6) stringResource(R.string.play_exit_description, piece.id) else
                                    stringResource(R.string.play_destination_description, piece.id, stringResource(if (horizontal) R.string.play_column else R.string.play_row), move.to + 1)
                                CustomAccessibilityAction(label) { if (canInteract) { onMove(move); true } else false }
                            }
                            Box(Modifier.offset(cell * (startX - column), cell * (startY - row))
                                .size(cell * (endX - startX), cell * (endY - startY))
                                .zIndex(if (dragged == piece.id) 1f else 0f)
                                .testTag("piece-${piece.id}")
                                .semantics(mergeDescendants = true) { contentDescription = pieceDescription; selected = isSelected; customActions = actions }
                                .clickable(enabled = canInteract, role = Role.Button) { onViewport(viewport.copy(selectedId = piece.id)) }
                                .pointerInput(board, piece, canInteract, px, column, row) {
                                    var displacement = 0f
                                    val moves = legal.filter { it.pieceId == piece.id }
                                    val offsets = moves.map { if (horizontal) Offset((it.to - piece.position).toFloat(), 0f) else Offset(0f, (it.to - piece.position).toFloat()) }
                                    val finish: () -> Unit = {
                                        if (canInteract && dragged == piece.id) {
                                            val end = nearestDestination(dragOffset, offsets)
                                            val to = piece.position + (if (horizontal) end.x else end.y).roundToInt()
                                            moves.find { it.to == to }?.let(onMove)
                                        }
                                        dragged = null
                                        dragOffset = Offset.Zero
                                    }
                                    val start: (Offset) -> Unit = { if (canInteract) { displacement = 0f; dragOffset = Offset.Zero; dragged = piece.id } }
                                    val cancel: () -> Unit = { dragged = null; dragOffset = Offset.Zero }
                                    if (horizontal) detectHorizontalDragGestures(
                                        onDragStart = start, onDragEnd = finish, onDragCancel = cancel,
                                    ) { change, amount -> if (canInteract) {
                                        change.consume(); displacement += amount
                                        dragOffset = constrainedSlide(Offset(displacement / px, 0f), offsets)
                                    } }
                                    else detectVerticalDragGestures(
                                        onDragStart = start, onDragEnd = finish, onDragCancel = cancel,
                                    ) { change, amount -> if (canInteract) {
                                        change.consume(); displacement += amount
                                        dragOffset = constrainedSlide(Offset(0f, displacement / px), offsets)
                                    } }
                                }.padding(4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isTarget) skin.target else pieceColors[index % pieceColors.size])
                                .border(if (isSelected) 3.dp else 0.5.dp, if (isTarget) skin.onTarget else skin.ink, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center) {
                                Text(piece.id, color = if (isTarget) skin.onTarget else skin.ink, style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.clearAndSetSemantics {})
                            }
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (sideOverview) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        overview()
                        interaction()
                    }
                    pan()
                } else {
                    if (magnified) { overview(); pan() }
                    interaction()
                }
                if (availableWidth / 6 >= 48.dp) TextButton(onClick = { onViewport(viewport.copy(zoom = if (magnified) 1 else 2)) }) {
                    Text(stringResource(if (magnified) R.string.play_zoom_out else R.string.play_zoom_in))
                }
            }
        }
        Text(if (selectedPiece == null) stringResource(R.string.play_selection_help) else stringResource(R.string.play_selected_help, selectedPiece.id),
            style = MaterialTheme.typography.bodyMedium)
        if (selectedPiece != null) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                selectedMoves.forEach { move ->
                    val label = if (move.to == 6) stringResource(R.string.play_exit_description, selectedPiece.id) else
                        stringResource(R.string.play_destination_description, selectedPiece.id,
                            stringResource(if (selectedPiece.axis == Axis.HORIZONTAL) R.string.play_column else R.string.play_row), move.to + 1)
                    OutlinedButton(onClick = { onMove(move) }, enabled = canInteract,
                        modifier = Modifier.padding(horizontal = 3.dp).heightIn(min = 48.dp).testTag("destination-${selectedPiece.id}-${move.to}")
                            .semantics { contentDescription = label }) {
                        Text(if (move.to == 6) stringResource(R.string.play_exit) else formats.integer(move.to + 1))
                    }
                }
            }
        }
    }
}
