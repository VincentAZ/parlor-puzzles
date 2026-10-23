package com.parlor.feature.play

import com.parlor.core.design.rememberDisplayFormats
import com.parlor.core.design.LocalBoardSkin
import com.parlor.core.design.roomTexture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import com.parlor.core.game.*
import com.parlor.core.model.*
import kotlin.math.roundToInt

@Composable
fun PuzzleBoard(board: BoardState, viewport: Viewport, enabled: Boolean, onViewport: (Viewport) -> Unit, onMove: (Move) -> Unit,
                onIllegal: () -> Unit = {}, photo: androidx.compose.ui.graphics.ImageBitmap? = null, reduceMotion: Boolean = false) {
    CompositionLocalProvider(LocalReducedMotion provides reduceMotion) {
    when (board) {
        is TrafficJamBoard -> TrafficBoard(board, viewport, enabled, onViewport, onMove)
        is HanoiBoard -> HanoiBoardView(board, viewport, enabled, onViewport, onMove, onIllegal)
        is SwitchBoard -> SwitchBoardView(board, viewport, enabled, onViewport, onMove)
        is JugsBoard -> JugsBoardView(board, viewport, enabled, onViewport, onMove, onIllegal)
        is PancakeBoard -> PancakeBoardView(board, viewport, enabled, onViewport, onMove)
        is TilesBoard -> TilesBoardView(board, viewport, enabled, onViewport, onMove, onIllegal, photo)
        is BlockEscapeBoard -> BlockBoardView(board, viewport, enabled, onViewport, onMove, onIllegal)
        is BoxPusherBoard -> BoxBoardView(board, viewport, enabled, onViewport, onMove, onIllegal)
        is PegBoard -> PegBoardView(board, viewport, enabled, onViewport, onMove, onIllegal)
        is QueensBoard -> QueensBoardView(board, viewport, enabled, onViewport, onMove, onIllegal)
        is KnightBoard -> KnightBoardView(board, viewport, enabled, onViewport, onMove, onIllegal)
        is CrossingBoard -> CrossingBoardView(board, viewport, enabled, onViewport, onMove)
    }
    }
}

@Composable
private fun HanoiBoardView(board: HanoiBoard, viewport: Viewport, enabled: Boolean, onViewport: (Viewport) -> Unit,
                           onMove: (Move) -> Unit, onIllegal: () -> Unit) {
    val formats = rememberDisplayFormats()
    val skin = LocalBoardSkin.current
    val selected = viewport.selectedId?.toIntOrNull()?.takeIf { it in board.pegs.indices }
    val allDisks = board.pegs.flatten().size
    val targets = (1..allDisks).associateWith { disk ->
        val peg = board.pegs.indexOfFirst { disk in it }
        Offset(peg.toFloat(), board.pegs[peg].indexOf(disk).toFloat())
    }
    var dragged by remember(board, enabled) { mutableStateOf<Int?>(null) }
    var dragOffset by remember(board, enabled) { mutableFloatStateOf(0f) }
    val legal = remember(board) { HanoiEngine.legalMoves(board).filterIsInstance<TransferDisk>() }
    val draggedDisk = dragged?.let { board.pegs[it].lastOrNull() }
    val positions = targets.mapValues { (disk, point) -> key(disk) {
        boardPoint(point + if (disk == draggedDisk) Offset(dragOffset, 0f) else Offset.Zero, immediate = disk == draggedDisk)
    } }
    val canInteract = enabled
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (allDisks == 10) Text(stringResource(R.string.play_hanoi_long))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val pegWidth = (maxWidth - 4.dp * (board.pegs.size - 1)) / board.pegs.size
            val pegPixels = with(LocalDensity.current) { (pegWidth + 4.dp).toPx() }
            BoardCoordinates {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        board.pegs.indices.forEach { index ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.play_peg, index + 1), style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.clearAndSetSemantics {})
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            board.pegs.forEachIndexed { index, peg ->
                                val contents = if (peg.isEmpty()) stringResource(R.string.play_empty_peg) else peg.reversed().joinToString { formats.integer(it) }
                                val description = stringResource(R.string.play_peg_description, index + 1, contents)
                                val actions = HanoiEngine.legalMoves(board).filterIsInstance<TransferDisk>().filter { it.from == index }
                                    .map { move -> CustomAccessibilityAction(moveDescription(board, move)) { if (canInteract) { onMove(move); true } else false } }
                                val selection = selected == index || legal.any { it.from == dragged && it.to == index }
                                Box(Modifier.weight(1f).fillMaxHeight().testTag("hanoi-peg-$index")
                                    .semantics { contentDescription = description; this.selected = selection; customActions = actions }
                                    .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).roomTexture(skin)
                                    .border(if (selection) 3.dp else 1.dp, if (selection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                    .clickable(enabled = canInteract, onClickLabel = stringResource(R.string.play_select_top_disk), role = Role.Button) {
                                        when {
                                            selected == null -> if (peg.isEmpty()) onIllegal() else onViewport(viewport.copy(selectedId = index.toString()))
                                            selected == index -> onViewport(viewport.copy(selectedId = null))
                                            else -> onMove(TransferDisk(selected, index))
                                        }
                                    }.pointerInput(board, canInteract, pegPixels) {
                                        var delta = 0f
                                        detectHorizontalDragGestures(onDragStart = {
                                            delta = 0f
                                            if(canInteract && peg.isNotEmpty()) { dragged = index; dragOffset = 0f }
                                        }, onDragCancel = { dragged = null; dragOffset = 0f }, onDragEnd = {
                                            if (canInteract && dragged == index) {
                                                val target = index + dragOffset.roundToInt()
                                                legal.find { it.from == index && it.to == target }?.let(onMove)
                                            }
                                            dragged = null; dragOffset = 0f
                                        }) { change, amount -> if (canInteract && dragged == index) {
                                            change.consume(); delta += amount
                                            dragOffset = (delta / pegPixels).coerceIn(-index.toFloat(), (board.pegs.lastIndex-index).toFloat())
                                        } }
                                    })
                            }
                        }
                        // One drawing plane lets a disk travel between pegs without clipping at each column.
                        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                            val width = pegWidth.toPx()
                            val stride = width + 4.dp.toPx()
                            val height = minOf(18.dp.toPx(), (size.height - 24.dp.toPx()) / allDisks)
                            board.pegs.indices.forEach { index ->
                                drawRoundRect(skin.ink, Offset(index * stride + width / 2 - 3.dp.toPx(), 12.dp.toPx()), Size(6.dp.toPx(), size.height - 24.dp.toPx()))
                            }
                            positions.entries.sortedBy { (disk, point) -> disk == draggedDisk || point.value != targets.getValue(disk) }.forEach { (disk, point) ->
                                val position = point.value
                                val diskWidth = (width - 12.dp.toPx()) * (0.28f + 0.72f * disk / allDisks)
                                drawRoundRect(if (disk % 2 == 0) skin.wood else skin.woodAlternate,
                                    Offset(position.x * stride + (width - diskWidth) / 2, size.height - (position.y + 1) * height),
                                    Size(diskWidth, height - 2.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        board.pegs.indices.forEach { index ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(if (index == board.goalPeg) stringResource(R.string.play_goal_peg) else "",
                                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clearAndSetSemantics {})
                            }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.play_hanoi_counts, board.pegs.joinToString { formats.integer(it.size) }))
        Text(if (selected == null) stringResource(R.string.play_hanoi_select) else stringResource(R.string.play_hanoi_selected, selected + 1))
        if (selected != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            board.pegs.indices.filter { it != selected }.forEach { target ->
                OutlinedButton(onClick = { onMove(TransferDisk(selected, target)) }, enabled = canInteract,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("hanoi-destination-$target")) { Text(stringResource(R.string.play_to_peg, target + 1)) }
            }
        }
    }
}

@Composable
private fun JugsBoardView(board: JugsBoard, viewport: Viewport, enabled: Boolean, onViewport: (Viewport) -> Unit,
                          onMove: (Move) -> Unit, onIllegal: () -> Unit) {
    val formats = rememberDisplayFormats()
    val skin = LocalBoardSkin.current
    val selected = viewport.selectedId?.toIntOrNull()?.takeIf { it in board.capacities.indices }
    val levels = board.amounts.mapIndexed { index, amount -> key(index) { boardAmount(amount.toFloat() / board.capacities[index]) } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoardCoordinates {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                board.capacities.forEachIndexed { i, capacity ->
                    val label = stringResource(R.string.play_jug_description, i + 1, board.amounts[i], capacity)
                    val selection = selected == i
                    val actions = JugsEngine.legalMoves(board).filter { move -> when (move) { is FillJug -> move.jug == i; is EmptyJug -> move.jug == i; is PourJug -> move.from == i; else -> false } }
                        .map { move -> CustomAccessibilityAction(moveDescription(board, move)) { if (enabled) { onMove(move); true } else false } }
                    Column(Modifier.weight(1f).testTag("jug-$i").clip(RoundedCornerShape(16.dp))
                        .border(if (selection) 3.dp else 1.dp, if (selection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                        .semantics(mergeDescendants = true) { contentDescription = label; this.selected = selection; customActions = actions }
                        .clickable(enabled = enabled, role = Role.Button) {
                            if (selected == null || selected == i) onViewport(viewport.copy(selectedId = if (selected == i) null else i.toString()))
                            else if (board.amounts[selected] == 0) onIllegal() else onMove(PourJug(selected, i))
                        }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.play_jug, i + 1), modifier = Modifier.clearAndSetSemantics {})
                        Canvas(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(10.dp)).clearAndSetSemantics {}) {
                            val height = size.height * levels[i].value
                            drawRoundRect(skin.waterEmpty, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
                            drawRect(skin.water, topLeft = Offset(0f, size.height - height), size = Size(size.width, height))
                            for (unit in 1 until capacity) drawLine(skin.ink, Offset(0f, size.height * unit / capacity), Offset(8.dp.toPx(), size.height * unit / capacity), 1.dp.toPx())
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics {}) {
                            Text(formats.integer(board.amounts[i]), style = MaterialTheme.typography.titleMedium)
                            HorizontalDivider(Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formats.integer(capacity), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
        Text(if (selected == null) stringResource(R.string.play_jug_select) else stringResource(R.string.play_jug_selected, selected + 1))
        if (selected != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onMove(FillJug(selected)) }, enabled = enabled && board.amounts[selected] < board.capacities[selected], modifier = Modifier.testTag("jug-fill")) { Text(stringResource(R.string.play_fill)) }
            OutlinedButton(onClick = { onMove(EmptyJug(selected)) }, enabled = enabled && board.amounts[selected] > 0, modifier = Modifier.testTag("jug-empty")) { Text(stringResource(R.string.play_empty)) }
            board.capacities.indices.filter { it != selected }.forEach { target ->
                OutlinedButton(onClick = { onMove(PourJug(selected, target)) }, enabled = enabled && board.amounts[selected] > 0 && board.amounts[target] < board.capacities[target],
                    modifier = Modifier.testTag("jug-pour-$target")) { Text(stringResource(R.string.play_pour_to, target + 1)) }
            }
        }
    }
}

@Composable
private fun PancakeBoardView(board: PancakeBoard, viewport: Viewport, enabled: Boolean, onViewport: (Viewport) -> Unit, onMove: (Move) -> Unit) {
    val formats = rememberDisplayFormats()
    val skin = LocalBoardSkin.current
    val count = viewport.selectedId?.toIntOrNull()?.coerceIn(2, board.stack.size) ?: 2
    val order = board.stack.joinToString { formats.integer(it) }
    val description = stringResource(R.string.play_stack_order, order)
    val positions = (1..board.stack.size).associateWith { size -> key(size) { boardAmount(board.stack.indexOf(size).toFloat()) } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(description, style = MaterialTheme.typography.bodyLarge)
        Canvas(Modifier.fillMaxWidth().height(180.dp).clearAndSetSemantics {}) {
            val row = size.height / board.stack.size
            board.stack.forEachIndexed { i, sizeLabel ->
                val width = size.width * (0.20f + 0.75f * sizeLabel / board.stack.size)
                drawRoundRect(if (i < count) skin.woodAlternate else skin.gold, Offset((size.width - width) / 2, positions.getValue(sizeLabel).value * row + 2.dp.toPx()),
                    Size(width, row - 4.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
            }
            val cut = (row * count).coerceAtMost(size.height - 1.dp.toPx())
            drawLine(skin.ink, Offset(0f, cut), Offset(size.width, cut), 3.dp.toPx())
        }
        Text(stringResource(R.string.play_flip_selection, count), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { onViewport(viewport.copy(selectedId = (count - 1).toString())) }, enabled = enabled && count > 2, modifier = Modifier.testTag("flip-less")) { Text(stringResource(R.string.play_fewer)) }
            OutlinedButton(onClick = { onViewport(viewport.copy(selectedId = (count + 1).toString())) }, enabled = enabled && count < board.stack.size, modifier = Modifier.testTag("flip-more")) { Text(stringResource(R.string.play_more)) }
            Button(onClick = { onMove(FlipPrefix(count)) }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag("flip-confirm")) { Text(stringResource(R.string.play_flip)) }
        }
    }
}
