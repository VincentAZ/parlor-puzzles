package com.parlor.tools.levels

import com.parlor.core.game.*
import com.parlor.core.model.*
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random

fun generateWaveC(directory:File) {
    val seed=20260930L
    val random=Random(seed)
    val levels=mutableListOf<LevelDefinition>()
    fun add(puzzle:Puzzle,tier:Tier,board:BoardState,route:List<Move>,unit:ScoringUnit,optimal:Boolean,seconds:Int) {
        val prefix=when(puzzle) {Puzzle.PEG_SOLITAIRE->"pegs";Puzzle.QUEENS->"queens";Puzzle.KNIGHTS_TOUR->"knight";else->"crossings"}
        val number=(if(puzzle.campaignCount==30) listOf(1,9,16,24) else listOf(1,11,21,31))[tier.ordinal]
        val variant=when(board) {is PegBoard->board.layout.name.lowercase();is KnightBoard->if(board.closed) "closed" else "open"
            is CrossingBoard->board.scenario.name.lowercase();else->"standard"}
        val level=LevelDefinition("$prefix-${number.toString().padStart(3,'0')}",puzzle=puzzle,tier=tier,order=1,
            variant=variant,initial=board,reference=route,par=replay(engineFor(puzzle),board,route).cost,
            parKind=if(optimal) ParKind.PROVEN_OPTIMAL else ParKind.REFERENCE,targetTimeMs=seconds*1000L,scoringUnit=unit,
            provenance=Provenance("Parlor Puzzles","Original fixed-seed Wave C construction and complete reference verification",seed,"Original project content"))
        verifyReference(level);levels+=level
        println("Authored ${level.id}: ${level.par} $unit, $variant")
    }
    for(tier in Tier.entries) {
        val layout=listOf(PegLayout.ENGLISH,PegLayout.TRIANGLE,PegLayout.EUROPEAN,PegLayout.DIAMOND)[tier.ordinal]
        val goal=if(layout==PegLayout.TRIANGLE) 12 else 24
        val desired=listOf(6,10,16,18)[tier.ordinal]
        val geometry=pegGeometry(layout)
        val seen=mutableSetOf<List<Int>>()
        val reverse=mutableListOf<Move>()
        var authored:PegBoard?=null
        fun grow(board:PegBoard):Boolean {
            if(board.pegs.size==desired) {authored=board;return true}
            if(board.pegs in seen || seen.size>=200_000) return false
            seen.add(board.pegs)
            val choices=board.pegs.flatMap { to -> geometry.directions.mapNotNull { (dx,dy) ->
                val x=to%geometry.width-2*dx;val y=to/geometry.width-2*dy
                if(x !in 0 until geometry.width || y !in 0 until geometry.height) return@mapNotNull null
                val from=y*geometry.width+x;val over=(from+to)/2
                if(from !in geometry.holes || over !in geometry.holes || from in board.pegs || over in board.pegs) null
                else JumpPeg(from,to)
            } }.shuffled(random)
            for(move in choices) {
                val expanded=board.copy(pegs=(board.pegs-move.to+move.from+(move.from+move.to)/2).sorted())
                require(PegEngine.apply(expanded,move)==board)
                reverse+=move
                if(grow(expanded)) return true
                reverse.removeAt(reverse.lastIndex)
            }
            return false
        }
        require(grow(PegBoard(layout,listOf(goal),goal))) {"Could not author $layout with $desired pegs"}
        // Every legal jump removes exactly one peg, so every successful route has this length.
        add(Puzzle.PEG_SOLITAIRE,tier,requireNotNull(authored),reverse.asReversed(),ScoringUnit.MOVES,true,listOf(75,120,240,360)[tier.ordinal])
    }
    for(tier in Tier.entries) {
        val side=listOf(4,5,7,8)[tier.ordinal]
        val empty=QueensBoard(side,emptyList())
        val solution=(solveQueens(empty) as SearchResult.Solved).moves.filterIsInstance<PlaceQueen>().map {it.cell}.sorted()
        val fixed=solution.shuffled(random).take(if(tier==Tier.EASY) 1 else if(tier==Tier.MEDIUM) 2 else 1).sorted()
        val blocked=(0 until side*side).filter {it !in solution}.shuffled(random).take(listOf(0,1,3,5)[tier.ordinal]).sorted()
        val board=QueensBoard(side,fixed,fixed,blocked)
        val route=(solveQueens(board) as SearchResult.Solved).moves
        add(Puzzle.QUEENS,tier,board,route,ScoringUnit.TIME,false,listOf(75,100,180,240)[tier.ordinal])
    }
    for(tier in Tier.entries) {
        val side=listOf(5,6,7,8)[tier.ordinal]
        val board=KnightBoard(side,listOf(0),closed=tier==Tier.EXPERT)
        val solved=solveKnight(board,500_000)
        require(solved is SearchResult.Solved) {"Tour authoring budget exhausted: $side closed=${board.closed}"}
        add(Puzzle.KNIGHTS_TOUR,tier,board,solved.moves,ScoringUnit.TIME_AND_BACKTRACKS,false,listOf(180,270,420,600)[tier.ordinal])
    }
    val scenarios=listOf(
        CrossingBoard(CrossingScenario.FOOD_FERRY,listOf(CrossingActor("W",CrossingRole.WOLF),CrossingActor("G",CrossingRole.GOAT),CrossingActor("C",CrossingRole.CABBAGE)),1),
        CrossingBoard(CrossingScenario.MISSIONARIES,(1..3).flatMap { listOf(CrossingActor("M$it",CrossingRole.MISSIONARY),CrossingActor("C$it",CrossingRole.CANNIBAL)) },2),
        CrossingBoard(CrossingScenario.COUPLES,(1..3).flatMap { listOf(CrossingActor("A$it",CrossingRole.PARTNER_A,it),CrossingActor("B$it",CrossingRole.PARTNER_B,it)) },2),
        CrossingBoard(CrossingScenario.BRIDGE,listOf(1,2,5,10,15,20).mapIndexed {i,t -> CrossingActor("P${i+1}",CrossingRole.WALKER,minutes=t)},2),
    )
    for((tier,board) in Tier.entries.zip(scenarios)) {
        val solved=CrossingEngine.search(board,100_000) as SearchResult.Solved
        add(Puzzle.CROSSINGS,tier,board,solved.moves,if(board.scenario==CrossingScenario.BRIDGE) ScoringUnit.SCENARIO_MINUTES else ScoringUnit.MOVES,
            true,listOf(120,180,240,300)[tier.ordinal])
    }
    val json=Json(ParlorJson) {prettyPrint=true}
    val indexFile=File(directory,"catalog-index.json")
    val old=ParlorJson.decodeFromString<CatalogIndex>(indexFile.readText())
    val index=old.copy(contentVersion=maxOf(old.contentVersion,4),files=(old.files+"wave-c.json").distinct())
    val earlier=listOf("traffic-jam.json","wave-a.json","wave-b.json").flatMap {ParlorJson.decodeFromString<Catalog>(File(directory,it).readText()).levels}
    val epoch=ScheduleEpoch(4,"2026-10-07",(earlier+levels).map {LevelRef(it.id,it.revision)})
    val scheduleFile=File(directory,"daily-schedule.json")
    val schedule=ParlorJson.decodeFromString<DailySchedule>(scheduleFile.readText())
    require(schedule.epochs.find {it.version==4}?.let {it==epoch}!=false) {"A published schedule epoch is immutable"}
    File(directory,"wave-c.json").writeText(json.encodeToString(Catalog(contentVersion=4,levels=levels))+"\n")
    indexFile.writeText(json.encodeToString(index)+"\n")
    if(schedule.epochs.none {it.version==4}) scheduleFile.writeText(json.encodeToString(schedule.copy(epochs=schedule.epochs+epoch))+"\n")
    println("Generated ${levels.size} Wave C samples; full launch ladders remain a separate gate.")
}
