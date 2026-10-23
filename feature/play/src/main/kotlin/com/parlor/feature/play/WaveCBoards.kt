package com.parlor.feature.play

import com.parlor.core.design.rememberDisplayFormats
import com.parlor.core.design.LocalBoardSkin
import com.parlor.core.design.roomTexture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parlor.core.game.*
import com.parlor.core.model.*

@Composable
internal fun PegBoardView(board:PegBoard,viewport:Viewport,enabled:Boolean,onViewport:(Viewport)->Unit,onMove:(Move)->Unit,onIllegal:()->Unit) {
    val skin = LocalBoardSkin.current
    val geometry=remember(board.layout) {pegGeometry(board.layout)}
    val selected=viewport.selectedId?.toIntOrNull()
    val legal=remember(board) {PegEngine.legalMoves(board).filterIsInstance<JumpPeg>()}
    var dragged by remember(board, enabled) { mutableStateOf<Int?>(null) }
    val occupancy = geometry.holes.sorted().associateWith { index -> key(index) { boardAmount(if (index in board.pegs) 1f else 0f) } }
    val cell:@Composable (Int,Dp)->Unit = {index,side ->
        if(index !in geometry.holes) Spacer(Modifier.size(side))
        else {
            val occupied=index in board.pegs
            val state=stringResource(if(occupied) R.string.play_peg_occupied else R.string.play_peg_empty)
            val desc=stringResource(R.string.play_peg_cell,state,index/geometry.width+1,index%geometry.width+1)+
                if(index==board.goal) " "+stringResource(R.string.play_peg_goal_hole) else ""
            val actions=legal.filter {it.from==index}.map {move -> CustomAccessibilityAction(moveDescription(board,move)) {if(enabled) {onMove(move);true} else false}}
            PlanningCell("peg-$index",side,desc,"",skin.wood,enabled,selected==index || legal.any { it.from==dragged && it.to==index },actions,
                onClick={if(occupied) onViewport(viewport.copy(selectedId=if(selected==index) null else index.toString()))
                    else if(selected!=null) onMove(JumpPeg(selected,index)) else onIllegal()},
                onDrag=if(occupied) ({dx,dy ->
                    // Triangular rows are offset by half a cell in the full-board view.
                    val x=index%geometry.width+dx+if(board.layout==PegLayout.TRIANGLE) dy/2 else 0
                    val y=index/geometry.width+dy
                    if(x in 0 until geometry.width&&y in 0 until geometry.height) onMove(JumpPeg(index,y*geometry.width+x)) else onIllegal()
                }) else null,twoAxisDrag=board.layout==PegLayout.TRIANGLE, dragKey=board,
                onDragPreview={ dragged=if(it==null) null else index },
                artwork={
                    val radius=this.size.minDimension*.28f
                    drawCircle(skin.ink,radius,center,style=Stroke(1.5.dp.toPx()))
                    drawCircle(skin.ink,radius,center,alpha=occupancy.getValue(index).value)
                    if(index==board.goal) drawCircle(skin.ink,radius*1.35f,center,style=Stroke(2.dp.toPx()))
                })
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(board.layout==PegLayout.TRIANGLE) BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Fifteen triangular holes fit the minimum supported 320-dp layout at full target size.
            val side=minOf(maxWidth/5,64.dp).coerceAtLeast(48.dp)
            BoardCoordinates {
                Column(Modifier.align(Alignment.Center)) {
                    for(row in 0..4) Row {
                        Spacer(Modifier.width(side*(4-row)/2))
                        for(column in 0..row) cell(row*5+column,side)
                    }
                }
            }
        } else PlanningGrid(geometry.width,geometry.height,viewport,onViewport,"pegs",overview={index,origin,unit ->
            if(index in geometry.holes) {
                val center=origin+Offset(unit/2,unit/2)
                drawCircle(skin.ink,unit*.32f,center,style=Stroke(unit*.06f))
                drawCircle(skin.ink,unit*.32f,center,alpha=occupancy.getValue(index).value)
                if(index==board.goal) drawCircle(skin.ink,unit*.45f,center,style=Stroke(unit*.08f))
            }
        },cellContent=cell)
        Text(stringResource(R.string.play_peg_controls))
        if(selected!=null) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            legal.filter {it.from==selected}.forEach {move ->
                OutlinedButton(onClick={onMove(move)},enabled=enabled,modifier=Modifier.testTag("peg-to-${move.to}")) {Text(moveDescription(board,move))}
            }
        }
        if(legal.isEmpty()&&!PegEngine.isGoal(board)) Text(stringResource(R.string.play_peg_no_jumps),modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite})
    }
}

@Composable
internal fun QueensBoardView(board:QueensBoard,viewport:Viewport,enabled:Boolean,onViewport:(Viewport)->Unit,onMove:(Move)->Unit,onIllegal:()->Unit) {
    val skin = LocalBoardSkin.current
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        PlanningGrid(board.side,board.side,viewport,onViewport,"queens",overview={index,origin,unit ->
            drawRect(if((index/board.side+index%board.side)%2==0) skin.alternate else skin.empty,origin,Size(unit-1,unit-1))
            if(index in board.blocked) {drawLine(skin.ink,origin,origin+Offset(unit,unit),unit*.15f);drawLine(skin.ink,origin+Offset(unit,0f),origin+Offset(0f,unit),unit*.15f)}
            if(index in board.queens) drawCircle(skin.ink,unit*.32f,origin+Offset(unit/2,unit/2))
            if(index in board.fixed) drawLine(skin.ink,origin+Offset(unit*.1f,unit*.88f),origin+Offset(unit*.9f,unit*.88f),unit*.1f)
        }) {index,side ->
            val fixed=index in board.fixed;val occupied=index in board.queens;val blocked=index in board.blocked
            val kind=stringResource(when {fixed->R.string.play_fixed_queen;occupied->R.string.play_queen;blocked->R.string.play_blocked_square;else->R.string.play_empty_square})
            val desc=stringResource(R.string.play_chess_cell,kind,index/board.side+1,index%board.side+1)
            val move=if(occupied) RemoveQueen(index) else PlaceQueen(index)
            val actions=if(!fixed&&QueensEngine.apply(board,move)!=null) listOf(CustomAccessibilityAction(moveDescription(board,move)) {if(enabled) {onMove(move);true} else false}) else emptyList()
            PlanningCell("queen-$index",side,desc,if(occupied) "Q" else if(blocked) "×" else "",
                if((index/board.side+index%board.side)%2==0) skin.alternate else skin.empty,enabled,
                actions=actions,onClick={if(fixed||blocked) onIllegal() else onMove(move)},
                decoration=if(fixed) ({drawLine(skin.ink,Offset(this.size.width*.18f,this.size.height*.88f),Offset(this.size.width*.82f,this.size.height*.88f),3.dp.toPx())}) else null, animateState=true)
        }
        Text(stringResource(R.string.play_queens_controls))
        Text(stringResource(R.string.play_queens_placed,board.queens.size,board.side))
    }
}

@Composable
internal fun KnightBoardView(board:KnightBoard,viewport:Viewport,enabled:Boolean,onViewport:(Viewport)->Unit,onMove:(Move)->Unit,onIllegal:()->Unit) {
    val formats = rememberDisplayFormats()
    val skin = LocalBoardSkin.current
    val legal=remember(board) {KnightEngine.legalMoves(board).filterIsInstance<KnightStep>()}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        PlanningGrid(board.side,board.side,viewport,onViewport,"knight",overview={index,origin,unit ->
            drawRect(if(index in board.path) skin.tile else if((index/board.side+index%board.side)%2==0) skin.alternate else skin.empty,origin,Size(unit-1,unit-1))
            val cx=origin.x+unit/2;val cy=origin.y+unit/2
            if(index==board.path.last()) drawCircle(skin.ink,unit*.32f,Offset(cx,cy))
            if(index==board.path.first()) drawCircle(skin.ink,unit*.43f,Offset(cx,cy),style=Stroke(unit*.12f))
            val previous=board.path.indexOf(index).takeIf {it>0}?.let {board.path[it-1]}
            if(previous!=null) drawLine(skin.ink,Offset(cx,cy),Offset(cx+(previous%board.side-index%board.side)*unit,cy+(previous/board.side-index/board.side)*unit),unit*.06f)
        }) {index,side ->
            val step=board.path.indexOf(index)
            val desc=stringResource(R.string.play_knight_cell,index/board.side+1,index%board.side+1,
                if(step>=0) stringResource(R.string.play_knight_visited,step+1) else stringResource(R.string.play_knight_unvisited))+
                if(index==board.path.last()) " "+stringResource(R.string.play_knight_here) else ""
            val actions=if(index==board.path.last()) legal.map {move -> CustomAccessibilityAction(moveDescription(board,move)) {if(enabled) {onMove(move);true} else false}} else emptyList()
            PlanningCell("knight-$index",side,desc,if(step>=0) formats.integer(step+1) else if(legal.any {it.cell==index}) "·" else "",
                if(step>=0) skin.tile else skin.empty,enabled,index==board.path.last(),actions,
                onClick={if(index !in board.path) onMove(KnightStep(index)) else if(index!=board.path.last()) onIllegal()}, animateState=true)
        }
        Text(stringResource(R.string.play_knight_controls))
        Text(stringResource(R.string.play_knight_progress,board.path.size,board.side*board.side))
        if(legal.isEmpty()&&!KnightEngine.isGoal(board)) Text(stringResource(R.string.play_knight_dead_end),modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite})
    }
}

@Composable
internal fun CrossingBoardView(board:CrossingBoard,viewport:Viewport,enabled:Boolean,onViewport:(Viewport)->Unit,onMove:(Move)->Unit) {
    val formats = rememberDisplayFormats()
    val skin = LocalBoardSkin.current
    val selected=viewport.selectedId?.split(',')?.filter {it.isNotEmpty()}?.toSet()?:emptySet()
    val move=CrossRiver(selected.sorted())
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val bankWidth = (maxWidth - 8.dp) / 2
    val across = ((bankWidth + 4.dp) / 68.dp).toInt().coerceAtLeast(1)
    val targets = board.actors.associate { actor ->
        val right = actor.id in board.right
        val slot = board.actors.filter { (it.id in board.right) == right }.indexOf(actor)
        actor.id to Offset(((if (right) bankWidth + 8.dp else 0.dp) + 68.dp * (slot % across)).value, (slot / across) * 68f)
    }
    val positions = targets.mapValues { (id, point) -> key(id, maxWidth) { boardPoint(point) } }
    val settled by remember(targets, positions) { derivedStateOf { targets.all { (id, point) -> positions.getValue(id).value == point } } }
    val canInteract = enabled && settled
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(stringResource(when(board.scenario) {CrossingScenario.FOOD_FERRY->R.string.play_food_rules;CrossingScenario.MISSIONARIES->R.string.play_mission_rules
            CrossingScenario.COUPLES->R.string.play_couple_rules;CrossingScenario.BRIDGE->R.string.play_bridge_rules}))
        BoardCoordinates {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (right in listOf(false, true)) Text(stringResource(if (right) R.string.play_right_short else R.string.play_left_short),
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                }
                // Match the moving tokens' extent, so lower controls never jump into their path.
                val height = positions.values.maxOf { it.value.y } + 64f
                Box(Modifier.fillMaxWidth().height(height.dp)) {
                    for (actor in board.actors) key(actor.id) {
                        val right = actor.id in board.right
                        val chosen = actor.id in selected
                        val position = positions.getValue(actor.id).value
                        val description = stringResource(R.string.play_crossing_actor, crossingActorName(actor),
                            stringResource(if (right) R.string.play_right_bank else R.string.play_left_bank))
                        Column(Modifier.offset(position.x.dp, position.y.dp).width(64.dp).heightIn(min = 64.dp).testTag("actor-${actor.id}")
                            .semantics(mergeDescendants = true) { contentDescription = description; this.selected = chosen }
                            .background(if (chosen) skin.selected else skin.alternate, RoundedCornerShape(8.dp))
                            .border(if (chosen) 3.dp else 1.dp, skin.ink, RoundedCornerShape(8.dp))
                            .clickable(enabled = canInteract && board.boatRight == right, role = Role.Checkbox) {
                                val next = if (chosen) selected - actor.id else selected + actor.id
                                onViewport(viewport.copy(selectedId = next.sorted().joinToString(",")))
                            }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(if (board.scenario == CrossingScenario.BRIDGE) formats.integer(actor.minutes) else actor.id,
                                fontSize = 16.sp, modifier = Modifier.clearAndSetSemantics {})
                        }
                    }
                    if (settled) for (right in listOf(false, true)) {
                        if (board.actors.none { (it.id in board.right) == right }) Text(stringResource(R.string.play_bank_empty),
                            modifier = Modifier.offset(if (right) bankWidth + 8.dp else 0.dp, 0.dp).width(bankWidth))
                    }
                }
            }
        }
        Text(stringResource(if(board.scenario==CrossingScenario.BRIDGE) R.string.play_torch_bank else R.string.play_boat_bank,
            stringResource(if(board.boatRight) R.string.play_right_bank else R.string.play_left_bank)))
        Text(stringResource(R.string.play_crossing_selection,selected.size,board.capacity))
        if(board.scenario==CrossingScenario.BRIDGE) Text(stringResource(R.string.play_travelled_minutes,board.elapsedMinutes))
        Button(onClick={onMove(move)},enabled=canInteract&&CrossingEngine.apply(board,move)!=null,modifier=Modifier.testTag("cross-confirm").heightIn(min=52.dp)) {
            Text(stringResource(if(board.boatRight) R.string.play_cross_left else R.string.play_cross_right))
        }
        if(selected.isNotEmpty()&&CrossingEngine.apply(board,move)==null) Text(stringResource(R.string.play_crossing_unsafe),modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite})
        Text(stringResource(R.string.play_crossing_controls))
    }
    }
}

@Composable
internal fun crossingActorName(actor:CrossingActor):String = when(actor.role) {
    CrossingRole.WOLF->stringResource(R.string.play_wolf)
    CrossingRole.GOAT->stringResource(R.string.play_goat)
    CrossingRole.CABBAGE->stringResource(R.string.play_cabbage)
    CrossingRole.MISSIONARY->stringResource(R.string.play_missionary,actor.id)
    CrossingRole.CANNIBAL->stringResource(R.string.play_cannibal,actor.id)
    CrossingRole.PARTNER_A,CrossingRole.PARTNER_B->stringResource(R.string.play_partner,actor.id)
    CrossingRole.WALKER->stringResource(R.string.play_walker,actor.id,actor.minutes)
}
