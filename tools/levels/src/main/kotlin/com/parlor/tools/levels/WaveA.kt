package com.parlor.tools.levels

import com.parlor.core.game.*
import com.parlor.core.model.*
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random

fun generateWaveA(directory: File) {
    val seed = 20260928L
    val random = Random(seed)
    val levels = mutableListOf<LevelDefinition>()
    val ids = mapOf(Puzzle.HANOI to listOf(1,5,11,14), Puzzle.SWITCH_GRID to listOf(1,16,31,46),
        Puzzle.WATER_JUGS to listOf(1,14,26,39), Puzzle.PANCAKE_FLIP to listOf(1,11,21,31))
    fun add(puzzle: Puzzle, tier: Tier, board: BoardState, route: List<Move>, optimal: Boolean, secondsPerMove: Int) {
        val number = ids.getValue(puzzle)[tier.ordinal]
        val fourPegs = board is HanoiBoard && board.pegs.size == 4
        val prefix = when (puzzle) { Puzzle.HANOI -> "hanoi"; Puzzle.SWITCH_GRID -> "switch"; Puzzle.WATER_JUGS -> "jugs"; else -> "pancake" }
        levels += LevelDefinition("$prefix-${number.toString().padStart(3, '0')}", puzzle = puzzle, tier = tier,
            order = if (puzzle == Puzzle.HANOI && tier == Tier.HARD) 3 else if (puzzle == Puzzle.HANOI && tier == Tier.EXPERT) 2 else 1,
            variant = if (fourPegs) "four-pegs" else "standard", initial = board, reference = route,
            par = route.size, parKind = if (optimal) ParKind.PROVEN_OPTIMAL else ParKind.REFERENCE,
            targetTimeMs = maxOf(45, route.size * secondsPerMove) * 1000L, proVariant = fourPegs,
            provenance = Provenance("Parlor Puzzles", "Original deterministic Wave A sample generator; verified reference", seed, "Original project content"))
    }
    for ((tier, spec) in Tier.entries.zip(listOf(3 to 3, 5 to 3, 7 to 4, 10 to 3))) {
        val (n, pegs) = spec
        val board = HanoiBoard(List(pegs) { if (it == 0) (n downTo 1).toList() else emptyList() })
        add(Puzzle.HANOI, tier, board, hanoiReference(n, pegs), pegs == 3, 4)
    }
    for ((tier, side) in Tier.entries.zip(listOf(3, 4, 5, 8))) {
        val toggles = (0 until side * side).shuffled(random).take(listOf(3, 6, 10, 20)[tier.ordinal])
        val lights = MutableList(side * side) { false }
        toggles.forEach { toggle -> SwitchEngine.affected(side, toggle).forEach { lights[it] = !lights[it] } }
        val board = SwitchBoard(side, lights)
        val solved = requireNotNull(solveSwitch(board))
        add(Puzzle.SWITCH_GRID, tier, board, solved.moves, solved.provenOptimal, 8)
    }
    val jugSpecs = listOf(listOf(3,5) to 1, listOf(7,11) to 8, listOf(11,17,23) to 19, listOf(11,17,23) to 20)
    for ((tier, spec) in Tier.entries.zip(jugSpecs)) {
        val board = JugsBoard(spec.first, List(spec.first.size) { 0 }, spec.second)
        val route = breadthFirst(JugsEngine, board, 100_000) as SearchResult.Solved
        add(Puzzle.WATER_JUGS, tier, board, route.moves, true, 10)
    }
    for ((tier, count) in Tier.entries.zip(listOf(4, 6, 9, 12))) {
        var stack = (1..count).shuffled(random)
        if (stack == stack.sorted()) stack = stack.asReversed()
        val board = PancakeBoard(stack)
        val solved = solvePancake(board, 50_000)
        val route = (solved as? SearchResult.Solved)?.moves ?: requireNotNull(PancakeEngine.continuation(board))
        add(Puzzle.PANCAKE_FLIP, tier, board, route, solved is SearchResult.Solved, 8)
    }
    levels.forEach(::verifyReference)
    val json = Json(ParlorJson) { prettyPrint = true }
    directory.mkdirs()
    File(directory, "wave-a.json").writeText(json.encodeToString(Catalog(contentVersion = 2, levels = levels)) + "\n")
    val indexFile = File(directory, "catalog-index.json")
    val previousIndex = indexFile.takeIf { it.exists() }?.let { ParlorJson.decodeFromString<CatalogIndex>(it.readText()) }
    val index = CatalogIndex(contentVersion = maxOf(2, previousIndex?.contentVersion ?: 1),
        files = ((previousIndex?.files ?: listOf("traffic-jam.json")) + "wave-a.json").distinct())
    indexFile.writeText(json.encodeToString(index) + "\n")
    val scheduleFile = File(directory, "daily-schedule.json")
    val schedule = ParlorJson.decodeFromString<DailySchedule>(scheduleFile.readText())
    val traffic = ParlorJson.decodeFromString<Catalog>(File(directory, "traffic-jam.json").readText())
    val next = ScheduleEpoch(2, "2026-10-01", (traffic.levels + levels).map { LevelRef(it.id, it.revision) })
    val existing = schedule.epochs.find { it.version == 2 }
    require(existing == null || existing == next) { "A published schedule epoch is immutable; use a new future epoch" }
    if (existing == null) scheduleFile.writeText(json.encodeToString(schedule.copy(epochs = schedule.epochs + next)) + "\n")
    println("Generated ${levels.size} representative Wave A levels. Full launch ladders remain a later content gate.")
}
