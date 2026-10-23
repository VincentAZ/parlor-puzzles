package com.parlor.feature.play

import com.parlor.core.design.LocalBoardSkin
import com.parlor.core.design.roomTexture
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import com.parlor.core.model.Viewport

/** A whole planning view beside large interaction cells; the overview never performs a game action. */
@Composable
internal fun PlanningGrid(width: Int, height: Int, viewport: Viewport, onViewport: (Viewport) -> Unit,
    tag: String, overview: DrawScope.(Int, Offset, Float) -> Unit,
    cellPosition: ((Int) -> Offset)? = null, raisedCell: (Int) -> Boolean = { false }, retainedCell: (Int) -> Boolean = { false }, overflowRows: Int = 0,
    cellContent: @Composable (Int, Dp) -> Unit) {
    val skin = LocalBoardSkin.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val canFit = maxWidth / width >= 48.dp && height * 48 <= 384
        val magnified = !canFit || viewport.zoom > 1
        val columns = if(magnified) ((maxWidth-108.dp)/48.dp).toInt().coerceIn(1,minOf(4,width)) else width
        val rows = if(magnified) minOf(4,height) else height
        val cell = minOf((maxWidth-if(magnified) 108.dp else 0.dp)/columns,384.dp/rows,64.dp)
        val x = if(magnified) viewport.column.coerceIn(0,width-columns) else 0
        val y = if(magnified) viewport.row.coerceIn(0,height-rows) else 0
        Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                if(magnified) {
                    val description=stringResource(R.string.play_grid_overview,width,height)
                    val currentView by rememberUpdatedState(viewport)
                    val currentUpdate by rememberUpdatedState(onViewport)
                    Canvas(Modifier.size(96.dp).testTag("$tag-overview").semantics { contentDescription=description }
                        .pointerInput(width,height,columns,rows) { detectTapGestures { point ->
                            val unit=size.width.toFloat()/maxOf(width,height)
                            val left=(size.width-width*unit)/2;val top=(size.height-height*unit)/2
                            currentUpdate(currentView.copy(column=((point.x-left)/unit).toInt().coerceIn(0,width-columns),
                                row=((point.y-top)/unit).toInt().coerceIn(0,height-rows)))
                        } }) {
                        val unit=size.width/maxOf(width,height);val left=(size.width-width*unit)/2;val top=(size.height-height*unit)/2
                        for(row in 0 until height) for(column in 0 until width) overview(row*width+column,Offset(left+column*unit,top+row*unit),unit)
                        drawRect(skin.ink,Offset(left+x*unit,top+y*unit),Size(columns*unit,rows*unit),style=Stroke(2.dp.toPx()))
                    }
                    Spacer(Modifier.width(12.dp))
                }
                BoardCoordinates {
                    Box(Modifier.size(cell * columns, cell * rows).clipToBounds()) {
                        // Siblings share one drawing plane, so a sliding tile can cross a row boundary.
                        for (index in 0 until width * (height + overflowRows)) {
                            val point = cellPosition?.invoke(index) ?: Offset((index % width).toFloat(), (index / width).toFloat())
                            if (retainedCell(index) || (point.x < x + columns && point.x + 1 > x && point.y < y + rows && point.y + 1 > y)) {
                                key(index) {
                                    Box(Modifier.offset(cell * (point.x - x), cell * (point.y - y))
                                        .size(cell).zIndex(if (raisedCell(index)) 1f else 0f)) { cellContent(index, cell) }
                                }
                            }
                        }
                    }
                }
            }
            if(magnified) {
                Text(stringResource(R.string.play_switch_window,y+1,y+rows,x+1,x+columns))
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    GridPan("←",stringResource(R.string.play_pan_left),x>0) { onViewport(viewport.copy(column=x-1)) }
                    GridPan("→",stringResource(R.string.play_pan_right),x<width-columns) { onViewport(viewport.copy(column=x+1)) }
                    GridPan("↑",stringResource(R.string.play_pan_up),y>0) { onViewport(viewport.copy(row=y-1)) }
                    GridPan("↓",stringResource(R.string.play_pan_down),y<height-rows) { onViewport(viewport.copy(row=y+1)) }
                }
            }
            if(canFit) TextButton(onClick={onViewport(viewport.copy(zoom=if(magnified) 1 else 2))}) {
                Text(stringResource(if(magnified) R.string.play_zoom_out else R.string.play_zoom_in))
            }
        }
    }
}

@Composable
private fun GridPan(symbol:String,label:String,enabled:Boolean,onClick:()->Unit) {
    OutlinedButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(48.dp).semantics { contentDescription=label },contentPadding=PaddingValues(0.dp)) {
        ArrowMark(symbol)
    }
}

@Composable
internal fun PlanningCell(tag:String, side:Dp, description:String, label:String, background:Color, enabled:Boolean,
    selected:Boolean=false, actions:List<CustomAccessibilityAction> = emptyList(), onClick:(()->Unit)?=null,
    onDrag:((Int,Int)->Unit)?=null, twoAxisDrag:Boolean=false, joined:Boolean=false,
    artwork:(DrawScope.()->Unit)?=null, decoration:(DrawScope.()->Unit)?=null, animateState:Boolean=false, onDragPreview:((Offset?)->Unit)?=null, dragKey:Any?=null) {
    val skin = LocalBoardSkin.current
    val fill = if (animateState) boardColor(background).value else background
    val drag by rememberUpdatedState(onDrag)
    val preview by rememberUpdatedState(onDragPreview)
    val clickable=if(onClick!=null) Modifier.clickable(enabled=enabled,role=Role.Button,onClick=onClick) else Modifier
    val dragModifier = if(onDrag==null) Modifier else Modifier.pointerInput(enabled,side,twoAxisDrag,dragKey) {
        var distance=Offset.Zero
        detectDragGestures(onDragStart={distance=Offset.Zero; if(enabled) preview?.invoke(Offset.Zero)},onDragCancel={preview?.invoke(null)},onDragEnd={
            if(enabled) {
                val unit=side.toPx()
                val dx=if(twoAxisDrag||abs(distance.x)>=abs(distance.y)) (distance.x/unit).roundToInt() else 0
                val dy=if(twoAxisDrag||abs(distance.y)>abs(distance.x)) (distance.y/unit).roundToInt() else 0
                if(dx!=0||dy!=0) drag?.invoke(dx,dy)
            }
            preview?.invoke(null)
        }) { change,amount -> if(enabled && drag!=null) { change.consume();distance+=amount; preview?.invoke(distance / side.toPx()) } }
    }
    Box(Modifier.size(side).testTag(tag).semantics(mergeDescendants=true) {
        contentDescription=description;this.selected=selected;customActions=actions
    }.then(clickable).then(dragModifier).padding(if(joined) 0.dp else 1.dp).background(fill,RoundedCornerShape(if(joined) 0.dp else 5.dp))
        .then(if(artwork==null) Modifier.roomTexture(skin) else Modifier)
        .then(if(joined) Modifier else Modifier.border(if(selected) 3.dp else 1.dp,skin.ink,RoundedCornerShape(5.dp))),
        contentAlignment=Alignment.Center) {
        if(artwork!=null) Canvas(Modifier.fillMaxSize().clearAndSetSemantics {},onDraw=artwork)
        val content: @Composable (String) -> Unit = { value ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if(value.isNotEmpty()) Text(value,color=skin.ink,fontSize=20.sp,modifier=Modifier
                    .background(if(artwork!=null) skin.empty else Color.Transparent,RoundedCornerShape(4.dp)).clearAndSetSemantics {})
            }
        }
        if (animateState && !LocalReducedMotion.current) Crossfade(label, Modifier.matchParentSize().clearAndSetSemantics {},
            animationSpec = tween(BoardMotionMillis), label = "board marker", content = content)
        else content(label)
        // State marks must remain visible even when an enlarged label covers background artwork.
        if(decoration!=null) Canvas(Modifier.fillMaxSize().clearAndSetSemantics {},onDraw=decoration)
    }
}

@Composable
internal fun ArrowMark(symbol:String) {
    val ink=LocalContentColor.current
    Canvas(Modifier.size(24.dp).clearAndSetSemantics {}) {
        rotate(when(symbol) {"←" -> 180f;"↑" -> -90f;"↓" -> 90f;else -> 0f}) {
            val tip=Offset(size.width*.8f,size.height*.5f)
            drawLine(ink,Offset(size.width*.2f,size.height*.5f),tip,2.dp.toPx(),StrokeCap.Round)
            drawLine(ink,tip,Offset(size.width*.5f,size.height*.2f),2.dp.toPx(),StrokeCap.Round)
            drawLine(ink,tip,Offset(size.width*.5f,size.height*.8f),2.dp.toPx(),StrokeCap.Round)
        }
    }
}
