package com.parlor.tools.levels

import com.parlor.core.game.*
import com.parlor.core.model.*
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random

fun generateWaveB(directory: File) {
    val seed = 20260929L
    val random = Random(seed)
    val levels = mutableListOf<LevelDefinition>()
    fun add(puzzle: Puzzle, tier: Tier, board: BoardState, route: List<Move>, optimal: Boolean) {
        val prefix = when (puzzle) { Puzzle.SLIDING_TILES -> "tiles"; Puzzle.BLOCK_ESCAPE -> "blocks"; else -> "boxes" }
        val number = (if (puzzle == Puzzle.BLOCK_ESCAPE) listOf(1,14,26,39) else listOf(1,16,31,46))[tier.ordinal]
        val cost = replay(engineFor(puzzle), board, route).cost
        levels += LevelDefinition("$prefix-${number.toString().padStart(3,'0')}", puzzle=puzzle, tier=tier,order=1,initial=board,
            reference=route,par=cost,parKind=if(optimal) ParKind.PROVEN_OPTIMAL else ParKind.REFERENCE,
            targetTimeMs=maxOf(60, cost * if(puzzle==Puzzle.BOX_PUSHER) 14 else 7)*1000L,
            scoringUnit=if(puzzle==Puzzle.BOX_PUSHER) ScoringUnit.PUSHES else ScoringUnit.MOVES,
            proVariant=board is TilesBoard && board.art!=TileArt.NUMBERS,
            variant=if(board is TilesBoard && board.art!=TileArt.NUMBERS) "bundled-image" else "standard",
            provenance=Provenance("Parlor Puzzles","Original fixed-seed Wave B authoring; bounded search and complete reference replay",seed,"Original project content"))
        println("Authored ${levels.last().id}: $cost, ${levels.last().parKind}")
    }
    for (tier in Tier.entries) {
        val side = listOf(3,4,5,5)[tier.ordinal]
        var board = TilesBoard(side,(1 until side*side).toList()+0,if(tier==Tier.EXPERT) TileArt.GARDEN else TileArt.NUMBERS)
        val seen = mutableSetOf(board.tiles)
        val shuffled = mutableListOf<Move>()
        repeat(listOf(14,30,42,60)[tier.ordinal]) {
            val empty = board.tiles.indexOf(0)
            val choices = board.tiles.indices.filter { kotlin.math.abs(it/side-empty/side)+kotlin.math.abs(it%side-empty%side)==1 }
                .map { from -> board.tiles.toMutableList().also { it[empty]=it[from]; it[from]=0 }.toList() }
                .filter { it !in seen }
            require(choices.isNotEmpty()) { "Shuffle trapped; change explicit seed" }
            val next=choices.random(random)
            shuffled += SlideTile(next[empty]); board=board.copy(tiles=next);seen.add(next)
        }
        val solved=solveTiles(board,200_000)
        add(Puzzle.SLIDING_TILES,tier,board,(solved as? SearchResult.Solved)?.moves ?: shuffled.asReversed(),solved is SearchResult.Solved)
    }
    val wanted=listOf(10..18,19..30,31..45,46..150)
    val blocksFound=mutableMapOf<Tier,Pair<BlockEscapeBoard,List<Move>>>()
    // Original random packings, not a reproduction of a named/commercial layout.
    for (candidate in 1..400) {
        val shapes=listOf(2 to 2,1 to 2,1 to 2,1 to 2,1 to 2,2 to 1,1 to 1,1 to 1,1 to 1,1 to 1)
        val occupied=mutableSetOf<Int>();val blocks=mutableListOf<EscapeBlock>()
        for ((i,shape) in shapes.withIndex()) {
            val (w,h)=shape
            val positions=(0..5-h).flatMap { y -> (0..4-w).map { x -> x to y } }.filter { (x,y) ->
                (i!=0 || y<=2) && (y until y+h).all { yy -> (x until x+w).all { xx -> yy*4+xx !in occupied } }
            }
            if(positions.isEmpty()) break
            val (x,y)=positions.random(random)
            blocks += EscapeBlock(('A'.code+i).toChar().toString(),x,y,w,h)
            for(yy in y until y+h) for(xx in x until x+w) occupied += yy*4+xx
        }
        if(blocks.size!=shapes.size) continue
        val board=BlockEscapeBoard(blocks=blocks)
        val solved=breadthFirst(BlockEscapeEngine,board,90_000)
        if(solved is SearchResult.Solved) {
            val tier=Tier.entries.firstOrNull { solved.moves.size in wanted[it.ordinal] && it !in blocksFound }
            if(tier!=null) { blocksFound[tier]=board to solved.moves;println("Block candidate $candidate supplies $tier (${solved.moves.size})") }
        }
        if(blocksFound.size==4) break
    }
    require(blocksFound.size==4) { "Need four distinct block difficulty samples; found ${blocksFound.mapValues { it.value.second.size }}" }
    for(tier in Tier.entries) { val (board,route)=blocksFound.getValue(tier);add(Puzzle.BLOCK_ESCAPE,tier,board,route,true) }

    for(tier in Tier.entries) {
        val width=listOf(6,7,8,9)[tier.ordinal];val height=if(tier.ordinal<2) 6 else 8
        val count=listOf(1,2,4,6)[tier.ordinal]
        val floor=(1 until height-1).flatMap { y -> (1 until width-1).map { x -> y*width+x } }
        var accepted=false
        repeat(80) attempt@ { candidate ->
            if(accepted) return@attempt
            val goals=floor.shuffled(random).take(count).sorted()
            var state=BoxPusherBoard(width,height,floor,goals,goals,(floor-goals.toSet()).random(random))
            val reverseChunks=mutableListOf<List<Move>>()
            val seen=mutableSetOf(state.boxes)
            repeat(listOf(9,18,32,48)[tier.ordinal]) pull@ {
                val reachable=BoxPusherEngine.walks(state).keys
                val options=state.boxes.flatMap { box -> Direction.entries.mapNotNull { d ->
                    val to=BoxPusherEngine.adjacent(state,box,d) ?: return@mapNotNull null
                    val after=BoxPusherEngine.adjacent(state,to,d) ?: return@mapNotNull null
                    val boxes=(state.boxes-box+to).sorted()
                    if(to !in reachable || after !in floor || after in state.boxes || boxes in seen) null else Triple(box,d,boxes)
                } }
                if(options.isEmpty()) return@pull
                val (box,d,boxes)=options.random(random)
                val to=requireNotNull(BoxPusherEngine.adjacent(state,box,d))
                val after=requireNotNull(BoxPusherEngine.adjacent(state,to,d))
                val forward=Direction.entries.single { it.dx == -d.dx && it.dy == -d.dy }
                reverseChunks.add(buildList<Move> { add(WorkerStep(forward));if(to!=state.worker) add(WalkTo(state.worker)) })
                state=state.copy(boxes=boxes,worker=after);seen.add(boxes)
            }
            if(reverseChunks.size<listOf(5,8,14,20)[tier.ordinal] || BoxPusherEngine.isGoal(state)) return@attempt
            var cursor:BoardState=state
            val route=mutableListOf<Move>()
            for(action in reverseChunks.asReversed().flatten()) {
                if(BoxPusherEngine.isGoal(cursor)) break
                cursor=requireNotNull(BoxPusherEngine.apply(cursor,action));route+=action
            }
            require(BoxPusherEngine.isGoal(cursor))
            val solved=solveBoxes(state,30_000)
            val reference=(solved as? SearchResult.Solved)?.moves ?: route
            if(replay(BoxPusherEngine,state,reference).cost<listOf(3,6,10,15)[tier.ordinal]) return@attempt
            add(Puzzle.BOX_PUSHER,tier,state,reference,solved is SearchResult.Solved)
            accepted=true
            println("Box candidate $candidate has $count boxes, ${reference.size} explicit actions")
        }
        require(accepted) { "No suitable box sample for $tier" }
    }
    levels.forEach(::verifyReference)
    val json=Json(ParlorJson) { prettyPrint=true }
    File(directory,"wave-b.json").writeText(json.encodeToString(Catalog(contentVersion=3,levels=levels))+"\n")
    val indexFile=File(directory,"catalog-index.json")
    val old=ParlorJson.decodeFromString<CatalogIndex>(indexFile.readText())
    val index=old.copy(contentVersion=maxOf(old.contentVersion,3),files=(old.files+"wave-b.json").distinct())
    indexFile.writeText(json.encodeToString(index)+"\n")
    // This epoch's pool must not grow when later waves extend the catalog index.
    val all=listOf("traffic-jam.json","wave-a.json","wave-b.json").flatMap {
        ParlorJson.decodeFromString<Catalog>(File(directory,it).readText()).levels
    }
    val scheduleFile=File(directory,"daily-schedule.json")
    val schedule=ParlorJson.decodeFromString<DailySchedule>(scheduleFile.readText())
    val epoch=ScheduleEpoch(3,"2026-10-04",all.map { LevelRef(it.id,it.revision) })
    require(schedule.epochs.find { it.version==3 }?.let { it==epoch } != false) { "Published schedule epoch is immutable" }
    if(schedule.epochs.none { it.version==3 }) scheduleFile.writeText(json.encodeToString(schedule.copy(epochs=schedule.epochs+epoch))+"\n")
    println("Generated ${levels.size} Wave B samples")
}
