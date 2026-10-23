package com.parlor.feature.play

import com.parlor.core.design.rememberDisplayFormats
import com.parlor.core.design.LocalBoardSkin
import com.parlor.core.design.roomTexture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import com.parlor.core.game.*
import com.parlor.core.model.*
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun TilesBoardView(board:TilesBoard,viewport:Viewport,enabled:Boolean,onViewport:(Viewport)->Unit,onMove:(Move)->Unit,onIllegal:()->Unit,photo:ImageBitmap?=null) {
    val formats = rememberDisplayFormats()
    val skin = LocalBoardSkin.current
    val selected=viewport.selectedId?.toIntOrNull()
    var preview by rememberSaveable(board.side,board.art) { mutableStateOf(false) }
    val legal=remember(board) { TilesEngine.legalMoves(board).filterIsInstance<SlideTile>().map { it.tile } }
    val targets = (1 until board.tiles.size).associateWith { tile ->
        val index = board.tiles.indexOf(tile)
        Offset((index % board.side).toFloat(), (index / board.side).toFloat())
    }
    var dragged by remember(board, enabled, viewport.zoom, viewport.column, viewport.row) { mutableStateOf<Int?>(null) }
    var dragOffset by remember(board, enabled) { mutableStateOf(Offset.Zero) }
    val empty = board.tiles.indexOf(0)
    fun destinations(tile: Int) = if (tile in legal) listOf(Offset((empty % board.side).toFloat(), (empty / board.side).toFloat()) - targets.getValue(tile)) else emptyList()
    val positions = targets.mapValues { (tile, point) -> key(tile) { boardPoint(point + if (dragged == tile) dragOffset else Offset.Zero, immediate = dragged == tile) } }
    val canInteract = enabled
    val picture = photo != null || board.art == TileArt.GARDEN
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        PlanningGrid(board.side,board.side,viewport,onViewport,"tiles",overview={ index,origin,unit ->
            if (index == 0) clipRect(origin.x, origin.y, origin.x + board.side * unit, origin.y + board.side * unit) {
                drawRect(skin.empty, origin, Size(board.side * unit, board.side * unit))
                positions.forEach { (tile, state) ->
                    val point = origin + state.value * unit
                    drawRect(skin.tile, point, Size(unit - 1, unit - 1))
                    if (picture) translate(point.x, point.y) { clipRect(0f, 0f, unit, unit) {
                        if (photo != null) photoTile(photo, tile, board.side, unit) else gardenTile(tile, board.side, unit)
                    } }
                }
            }
        }, cellPosition = { index -> positions[board.tiles[index]]?.value ?: Offset((index % board.side).toFloat(), (index / board.side).toFloat()) },
            raisedCell = { board.tiles[it] != 0 }, retainedCell = { board.tiles[it] == dragged }) { index,size ->
            val tile=board.tiles[index]
            val description=if(tile==0) stringResource(R.string.play_tiles_empty,index/board.side+1,index%board.side+1)
                else stringResource(R.string.play_tiles_cell,tile,index/board.side+1,index%board.side+1)
            val action=if(tile in legal) listOf(CustomAccessibilityAction(stringResource(R.string.play_slide_tile,tile)) { if(canInteract) {onMove(SlideTile(tile));true} else false }) else emptyList()
            PlanningCell("tile-$tile",size,description,if(tile==0) "" else if(!picture||viewport.showLabels) formats.integer(tile) else "",
                if(tile==0) skin.empty else skin.tile,canInteract,selected==tile || (tile==0 && dragged in legal),action,
                onClick={ if(tile==0) { if(selected!=null) onMove(SlideTile(selected)) else onIllegal() }
                    else onViewport(viewport.copy(selectedId=if(selected==tile) null else tile.toString())) },
                onDrag=if(tile in legal) ({ _,_ ->
                    if (nearestDestination(dragOffset, destinations(tile)) != Offset.Zero) onMove(SlideTile(tile))
                }) else null,
                dragKey=board,
                onDragPreview={ distance ->
                    dragged = if(distance == null) null else tile
                    dragOffset = if(distance == null) Offset.Zero else constrainedSlide(distance, destinations(tile))
                },artwork=if(tile!=0 && picture) ({
                    if(photo!=null) photoTile(photo,tile,board.side,this.size.width) else gardenTile(tile,board.side,this.size.width)
                }) else null)
        }
        Text(stringResource(R.string.play_tiles_select))
        if(selected in legal) OutlinedButton(onClick={onMove(SlideTile(requireNotNull(selected)))},enabled=canInteract,modifier=Modifier.testTag("tile-slide")) {
            Text(stringResource(R.string.play_slide_tile,requireNotNull(selected)))
        }
        if(picture) {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={onViewport(viewport.copy(showLabels=!viewport.showLabels))}) {Text(stringResource(if(viewport.showLabels) R.string.play_hide_numbers else R.string.play_show_numbers))}
                TextButton(onClick={preview=!preview}) {Text(stringResource(if(preview) R.string.play_hide_picture else R.string.play_show_picture))}
            }
            if(preview) {
                val desc=stringResource(if(photo!=null) R.string.play_photo_description else R.string.play_garden_description)
                Canvas(Modifier.size(180.dp).testTag("tile-picture").semantics { contentDescription=desc }) {
                    if(photo!=null) drawImage(photo,dstSize=IntSize(size.width.roundToInt(),size.height.roundToInt())) else garden(size.width)
                }
            }
        }
    }
}

/** Identity defines the solved crop, regardless of current cell, orientation, zoom or pan. */
private fun DrawScope.photoTile(photo:ImageBitmap,tile:Int,side:Int,unit:Float) {
    clipRect(0f,0f,unit,unit) {
        translate(-((tile-1)%side)*unit,-((tile-1)/side)*unit) {
            drawImage(photo,dstSize=IntSize((unit*side).roundToInt(),(unit*side).roundToInt()))
        }
    }
}

/** Original bundled vector illustration. Tile crops preserve the same coordinates at every board size. */
private fun DrawScope.gardenTile(tile:Int,side:Int,unit:Float) {
    clipRect(0f,0f,unit,unit) { translate(-((tile-1)%side)*unit,-((tile-1)/side)*unit) { garden(unit*side) } }
}
private fun DrawScope.garden(w:Float) {
    drawRect(Color(0xFFEFE2C8),size=Size(w,w))
    drawRect(Color(0xFFB4CFD2),Offset(.08f*w,.05f*w),Size(.84f*w,.60f*w))
    drawCircle(Color(0xFFECCB79),.12f*w,Offset(.68f*w,.22f*w))
    drawRect(Color(0xFF587664),Offset(.08f*w,.48f*w),Size(.84f*w,.17f*w))
    for(x in listOf(.08f,.5f,.92f)) drawLine(Color(0xFF9A714E),Offset(x*w,.05f*w),Offset(x*w,.68f*w),.025f*w)
    drawLine(Color(0xFF9A714E),Offset(.07f*w,.35f*w),Offset(.93f*w,.35f*w),.025f*w)
    drawRect(Color(0xFFD4AF7F),Offset(0f,.68f*w),Size(w,.32f*w))
    for((x,h) in listOf(.24f to .34f,.7f to .47f)) {
        drawRect(Color(0xFFAA6346),Offset((x-.10f)*w,.75f*w),Size(.20f*w,.20f*w))
        drawLine(Color(0xFF425C40),Offset(x*w,.78f*w),Offset(x*w,h*w),.02f*w)
        drawOval(Color(0xFF6F8D54),Offset((x-.18f)*w,(h+.04f)*w),Size(.18f*w,.10f*w))
        drawOval(Color(0xFF486C4A),Offset(x*w,(h+.13f)*w),Size(.18f*w,.10f*w))
    }
}

@Composable
internal fun BlockBoardView(board:BlockEscapeBoard,viewport:Viewport,enabled:Boolean,onViewport:(Viewport)->Unit,onMove:(Move)->Unit,onIllegal:()->Unit) {
    val skin = LocalBoardSkin.current
    val selected=board.blocks.find { it.id==viewport.selectedId }
    val legal=remember(board) { BlockEscapeEngine.legalMoves(board).filterIsInstance<SlideBlock>() }
    var dragged by remember(board, enabled, viewport.zoom, viewport.column, viewport.row) { mutableStateOf<String?>(null) }
    var dragOffset by remember(board, enabled) { mutableStateOf(Offset.Zero) }
    val destinations=legal.filter { it.id==(dragged ?: selected?.id) }
    fun offsets(block: EscapeBlock) = legal.filter { it.id == block.id }.map { Offset((it.x-block.x).toFloat(), (it.y-block.y).toFloat()) }
    val targets = board.blocks.associate { it.id to Offset(it.x.toFloat(), it.y.toFloat()) }
    val positions = targets.mapValues { (id, point) -> key(id) { boardPoint(point + if (dragged == id) dragOffset else Offset.Zero, immediate = dragged == id) } }
    val canInteract = enabled
    fun at(index:Int)=board.blocks.find { index%board.width in it.x until it.x+it.width && index/board.width in it.y until it.y+it.height }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        PlanningGrid(board.width,board.height,viewport,onViewport,"blocks",overview={ index,origin,unit ->
            if (index == 0) clipRect(origin.x, origin.y, origin.x + board.width * unit, origin.y + board.height * unit) {
                drawRect(skin.empty, origin, Size(board.width * unit, board.height * unit))
                board.blocks.forEach { block ->
                    drawRect(if (block.id == board.targetId) skin.target else skin.tile, origin + positions.getValue(block.id).value * unit,
                        Size(block.width * unit - 1, block.height * unit - 1))
                }
            }
        }, cellPosition = { index ->
            val block = at(index)
            val offset = block?.let { positions.getValue(it.id).value - targets.getValue(it.id) } ?: Offset.Zero
            Offset((index % board.width).toFloat(), (index / board.width).toFloat()) + offset
        }, raisedCell = { at(it) != null }, retainedCell = { dragged != null && at(it)?.id == dragged }, overflowRows = (board.blocks.maxOf { it.y + it.height } - board.height).coerceAtLeast(0)) { index,size ->
            val x=index%board.width;val y=index/board.width;val block=at(index)
            val destination=destinations.find { it.x==x&&it.y==y }
            val label=when { destination!=null -> "·";block!=null && x==block.x && y==block.y -> block.id;else -> "" }
            val description=if(destination!=null) moveDescription(board,destination) else if(block==null) stringResource(R.string.play_block_empty,y+1,x+1)
                else stringResource(R.string.play_block_cell,block.id,y+1,x+1,block.width,block.height)
            val actions=legal.filter { it.id==block?.id }.map { move -> CustomAccessibilityAction(moveDescription(board,move)) { if(canInteract) {onMove(move);true} else false } }
            PlanningCell("block-cell-$index",size,description,label,if(block?.id==board.targetId) skin.selected else if(block!=null) skin.tile else skin.empty,canInteract,(block!=null&&block==selected) || (dragged!=null && destination!=null),actions,
                onClick={ when { destination!=null -> onMove(destination);block!=null -> onViewport(viewport.copy(selectedId=if(block==selected) null else block.id));else -> onIllegal() } },
                onDrag=if(block!=null) ({ _,_ ->
                    val end = nearestDestination(dragOffset, offsets(block))
                    if(end != Offset.Zero) onMove(SlideBlock(block.id, block.x+end.x.roundToInt(), block.y+end.y.roundToInt()))
                }) else null, joined=block!=null, dragKey=board,
                onDragPreview={ distance ->
                    dragged = if(distance == null) null else block?.id
                    dragOffset = if(distance == null || block == null) Offset.Zero else constrainedSlide(distance, offsets(block))
                },
                artwork=if(block!=null) ({
                    val edge=if(block==selected) 3.dp.toPx() else 1.dp.toPx()
                    val ink=skin.ink
                    if(x==block.x) drawLine(ink,Offset.Zero,Offset(0f,this.size.height),edge)
                    if(y==block.y) drawLine(ink,Offset.Zero,Offset(this.size.width,0f),edge)
                    if(x==block.x+block.width-1) drawLine(ink,Offset(this.size.width,0f),Offset(this.size.width,this.size.height),edge)
                    if(y==block.y+block.height-1) drawLine(ink,Offset(0f,this.size.height),Offset(this.size.width,this.size.height),edge)
                }) else null)
        }
        Text(stringResource(R.string.play_block_select))
        if(selected!=null) {
            Text(stringResource(R.string.play_block_selected,selected.id))
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                destinations.forEach { move -> OutlinedButton(onClick={onMove(move)},enabled=canInteract,modifier=Modifier.testTag("block-to-${move.id}-${move.x}-${move.y}")) {
                    Text(moveDescription(board,move))
                } }
            }
        }
        if(selected?.id!=board.targetId) legal.find { it.id==board.targetId&&it.y==board.height }?.let { exit ->
            Button(onClick={onMove(exit)},enabled=canInteract,modifier=Modifier.testTag("block-exit")) {Text(moveDescription(board,exit))}
        }
    }
}

@Composable
internal fun BoxBoardView(board:BoxPusherBoard,viewport:Viewport,enabled:Boolean,onViewport:(Viewport)->Unit,onMove:(Move)->Unit,onIllegal:()->Unit) {
    val skin = LocalBoardSkin.current
    val worker = workerPosition(board)
    val boxes = boxPositions(board)
    val settled by remember(board, worker, boxes) { derivedStateOf {
        worker.value == board.point(board.worker) && boxes.all { (cell, point) -> point.value == board.point(cell) }
    } }
    val canInteract = enabled && settled
    val legal=BoxPusherEngine.legalMoves(board)
    val reachable=remember(board) { BoxPusherEngine.walks(board).keys }
    val dead=remember(board) { BoxPusherEngine.deadlocked(board) }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        PlanningGrid(board.width,board.height,viewport,onViewport,"boxes",overview={ index,origin,unit ->
            if (index == 0) {
                for (cell in 0 until board.width * board.height) {
                    val at = origin + board.point(cell) * unit
                    drawRect(if(cell !in board.floor) skin.ink else skin.base,at,Size(unit-1,unit-1))
                    if(cell in board.goals) drawCircle(skin.ink,unit*.36f,at+Offset(unit/2,unit/2),style=Stroke(unit*.12f))
                }
                boxes.values.forEach { point -> drawRect(skin.gold,origin+point.value*unit+Offset(unit*.18f,unit*.18f),Size(unit*.64f,unit*.64f)) }
                drawCircle(skin.ink,unit*.23f,origin+worker.value*unit+Offset(unit/2,unit/2))
            }
        }, cellPosition = { index ->
            if (index == board.worker) worker.value else boxes[index]?.value ?: board.point(index)
        }, raisedCell = { it == board.worker || it in board.boxes }) { index,size ->
            val kind=when { index==board.worker -> R.string.play_worker;index in board.boxes&&index in board.goals -> R.string.play_box_on_goal
                index in board.boxes -> R.string.play_box;index in board.goals -> R.string.play_floor_goal;index in board.floor -> R.string.play_floor;else -> R.string.play_wall }
            val description=stringResource(R.string.play_box_cell,stringResource(kind),index/board.width+1,index%board.width+1)
            val label=when { index==board.worker -> "●";index in board.boxes&&index in board.goals -> "▣";index in board.boxes -> "□";index in board.goals -> "○";else -> "" }
            val actions=if(index==board.worker) legal.map { move -> CustomAccessibilityAction(moveDescription(board,move)) { if(canInteract) {onMove(move);true} else false } } else emptyList()
            PlanningCell("box-cell-$index",size,description,label,if(index !in board.floor) skin.wall else if(index in board.boxes) skin.gold else skin.empty,canInteract,
                actions=actions,onClick=if(index !in board.floor) null else ({
                    when {
                        index==board.worker -> Unit
                        index in reachable -> onMove(WalkTo(index))
                        index in board.boxes -> {
                            val direction=Direction.entries.find { BoxPusherEngine.adjacent(board,board.worker,it)==index }
                            if(direction!=null) onMove(WorkerStep(direction)) else onIllegal()
                        }
                        else -> onIllegal()
                    }
                }))
        }
        Text(stringResource(R.string.play_worker_controls), style=MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            for(direction in Direction.entries) {
                val move=WorkerStep(direction);val label=moveDescription(board,move)
                OutlinedButton(onClick={onMove(move)},enabled=canInteract&&move in legal,modifier=Modifier.size(48.dp).testTag("worker-${direction.name}").semantics { contentDescription=label },contentPadding=PaddingValues(0.dp)) {
                    ArrowMark(when(direction) {Direction.UP->"↑";Direction.DOWN->"↓";Direction.LEFT->"←";Direction.RIGHT->"→"})
                }
            }
        }
        Text(stringResource(R.string.play_box_controls))
        Text(stringResource(R.string.play_box_legend))
        if(dead) Text(stringResource(R.string.play_box_deadlock),color=MaterialTheme.colorScheme.error,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
    }
}
