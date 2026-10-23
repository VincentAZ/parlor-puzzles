package com.parlor.tools.levels

import com.parlor.core.game.*
import com.parlor.core.model.*
import java.io.File

fun main(args: Array<String>) {
    if (args.size == 2 && args[0] == "generate-wave-a") { generateWaveA(File(args[1])); return }
    if (args.size == 2 && args[0] == "generate-wave-b") { generateWaveB(File(args[1])); return }
    if (args.size == 2 && args[0] == "generate-wave-c") { generateWaveC(File(args[1])); return }
    require(args.size == 3 && args[0] == "verify") { "Usage: verify <catalog directory> <prototype|release>, or generate-wave-a <directory>" }
    val directory = File(args[1])
    val index = ParlorJson.decodeFromString<CatalogIndex>(File(directory, "catalog-index.json").readText())
    val files = index.files.map { ParlorJson.decodeFromString<Catalog>(File(directory, it).readText()) }
    require(files.all { it.schemaVersion == 1 && it.contentVersion <= index.contentVersion })
    val catalog = Catalog(contentVersion = index.contentVersion, levels = files.flatMap { it.levels })
    require(catalog.levels.map { it.id }.toSet().size == catalog.levels.size) { "Duplicate level IDs" }
    require(catalog.levels.map { it.puzzle to canonicalContent(it.initial) }.toSet().size == catalog.levels.size) { "Duplicate initial boards" }
    require(catalog.levels.map { Triple(it.puzzle,it.tier,it.order) }.distinct().size == catalog.levels.size) { "Duplicate tier/order slot" }
    for (level in catalog.levels) {
        verifyReference(level)
        require((level.puzzle == Puzzle.BOX_PUSHER) == (level.scoringUnit == ScoringUnit.PUSHES))
        require((level.puzzle == Puzzle.QUEENS) == (level.scoringUnit == ScoringUnit.TIME))
        require((level.puzzle == Puzzle.KNIGHTS_TOUR) == (level.scoringUnit == ScoringUnit.TIME_AND_BACKTRACKS))
        require((level.initial is CrossingBoard && (level.initial as CrossingBoard).scenario==CrossingScenario.BRIDGE) == (level.scoringUnit==ScoringUnit.SCENARIO_MINUTES))
        if (level.initial is TilesBoard) require(TilesEngine.solvable(level.initial as TilesBoard))
        require(level.provenance.author.isNotBlank() && level.provenance.method.isNotBlank() && level.provenance.license.isNotBlank())
        if (level.initial is HanoiBoard) {
            val board = level.initial as HanoiBoard
            require(board.pegs.flatten().size in 3..10)
            require(level.proVariant == (board.pegs.size == 4))
        }
        if (level.parKind == ParKind.PROVEN_OPTIMAL) {
            val proven = when (val board = level.initial) {
                is PegBoard -> board.pegs.size-1==level.par
                is QueensBoard, is KnightBoard -> false // Time targets are calibrated references, never minimum-time claims.
                is CrossingBoard -> (CrossingEngine.search(board,300_000) as? SearchResult.Solved)?.let {replay(CrossingEngine,board,it.moves).cost==level.par}==true
                is HanoiBoard -> if (board.pegs.size == 3 && board.pegs[0] == (board.pegs.flatten().size downTo 1).toList() && board.goalPeg != 0)
                    (1 shl board.pegs[0].size) - 1 == level.par else (breadthFirst(HanoiEngine, board, 300_000) as? SearchResult.Solved)?.moves?.size == level.par
                is SwitchBoard -> solveSwitch(board)?.let { it.provenOptimal && it.moves.size == level.par } == true
                is TilesBoard -> (solveTiles(board, 300_000) as? SearchResult.Solved)?.moves?.size == level.par
                is BoxPusherBoard -> (solveBoxes(board, 300_000) as? SearchResult.Solved)?.let { replay(BoxPusherEngine, board, it.moves).cost == level.par } == true
                is PancakeBoard -> (solvePancake(board, 300_000) as? SearchResult.Solved)?.moves?.size == level.par
                else -> (breadthFirst(engineFor(level.puzzle), board, 300_000) as? SearchResult.Solved)?.moves?.size == level.par
            }
            require(proven) { "Optimality not proved: ${level.id}" }
        }
        println("Verified ${level.id}: ${level.par} ${level.scoringUnit}, ${level.parKind}")
    }
    val schedule = ParlorJson.decodeFromString<DailySchedule>(File(directory, "daily-schedule.json").readText())
    require(schedule.epochs.all { epoch -> epoch.pool.all { ref -> catalog.levels.any { it.id == ref.id && it.revision == ref.revision } } })
    if (args[2] == "release") {
        for (puzzle in Puzzle.entries) {
            require(catalog.levels.count { it.puzzle == puzzle } == puzzle.campaignCount) { "Release requires ${puzzle.campaignCount} $puzzle levels" }
            for (tier in Tier.entries) {
                val sizes = when (puzzle.campaignCount) { 50 -> listOf(13,12,13,12); 30 -> listOf(8,7,8,7); else -> List(4) { puzzle.campaignCount / 4 } }
                require(catalog.levels.filter { it.puzzle == puzzle && it.tier == tier }.map { it.order }.sorted() == (1..sizes[tier.ordinal]).toList())
            }
        }
        require(catalog.levels.size == 556)
    } else {
        require(args[2] == "prototype")
        val counts = Puzzle.entries.associateWith {if(it==Puzzle.TRAFFIC_JAM) 12 else 4}
        require(catalog.levels.groupingBy { it.puzzle }.eachCount() == counts)
        require(counts.keys.all { p -> Tier.entries.all { t -> catalog.levels.any { it.puzzle == p && it.tier == t } } })
    }
    println("Verified ${catalog.levels.size} levels for ${args[2]}")
}

/** Ignore interchangeable labels and free walking within one region when detecting duplicate content. */
private fun canonicalContent(board: BoardState): Any = when(board) {
    is PegBoard -> board.copy(pegs=board.pegs.sorted())
    is QueensBoard -> board.copy(queens=board.queens.sorted(),fixed=board.fixed.sorted(),blocked=board.blocked.sorted())
    is CrossingBoard -> board.copy(actors=board.actors.sortedBy {it.id},right=board.right.sorted(),elapsedMinutes=0)
    is BlockEscapeBoard -> BlockEscapeEngine.searchKey(board)
    is TilesBoard -> board.copy(art=TileArt.NUMBERS)
    is BoxPusherBoard -> board.copy(floor=board.floor.sorted(),goals=board.goals.sorted(),boxes=board.boxes.sorted(),
        worker=requireNotNull(BoxPusherEngine.walks(board).keys.minOrNull()))
    is TrafficJamBoard -> {
        val target=board.pieces.single {it.id==board.targetId}.copy(id="A")
        val rest=board.pieces.filter {it.id!=board.targetId}.sortedWith(compareBy({it.axis},{it.length},{it.y},{it.x}))
            .mapIndexed {index,piece -> piece.copy(id="piece-$index")}
        board.copy(targetId="A",pieces=listOf(target)+rest)
    }
    else -> board
}
