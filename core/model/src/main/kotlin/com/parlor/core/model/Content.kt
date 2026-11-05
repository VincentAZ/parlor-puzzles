package com.parlor.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Puzzle(val campaignCount: Int) {
    TRAFFIC_JAM(12), HANOI(4), SLIDING_TILES(4), BLOCK_ESCAPE(4), BOX_PUSHER(4),
    SWITCH_GRID(4), PEG_SOLITAIRE(4), QUEENS(4), KNIGHTS_TOUR(4), WATER_JUGS(4),
    CROSSINGS(4), PANCAKE_FLIP(4),
}

@Serializable enum class Tier { EASY, MEDIUM, HARD, EXPERT }
@Serializable enum class ParKind { PROVEN_OPTIMAL, REFERENCE }
@Serializable enum class ScoringUnit { MOVES, PUSHES, SCENARIO_MINUTES, TIME, TIME_AND_BACKTRACKS }
@Serializable enum class Axis { HORIZONTAL, VERTICAL }

@Serializable
sealed interface BoardState

@Serializable
@SerialName("traffic_jam")
data class TrafficJamBoard(
    val width: Int = 6,
    val height: Int = 6,
    val exitRow: Int = 2,
    val targetId: String = "A",
    val pieces: List<TrafficPiece>,
) : BoardState

@Serializable
data class TrafficPiece(val id: String, val x: Int, val y: Int, val length: Int, val axis: Axis) {
    val position: Int get() = if (axis == Axis.HORIZONTAL) x else y
}

@Serializable
sealed interface Move

@Serializable
@SerialName("translate")
data class Translate(val pieceId: String, val to: Int) : Move

@Serializable
data class Provenance(val author: String, val method: String, val seed: Long, val license: String)

@Serializable
data class LevelDefinition(
    val id: String,
    val revision: Int = 1,
    val puzzle: Puzzle,
    val variant: String = "standard",
    val tier: Tier,
    val order: Int,
    val initial: BoardState,
    val reference: List<Move>,
    val par: Int,
    val parKind: ParKind,
    val targetTimeMs: Long,
    val scoringUnit: ScoringUnit = ScoringUnit.MOVES,
    val proVariant: Boolean = false,
    val provenance: Provenance,
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9_-]{2,79}")))
        require(revision > 0 && order > 0)
        require(par in 1..10_000_000)
        require(targetTimeMs in 1..86_400_000)
    }
}

@Serializable
data class Catalog(val schemaVersion: Int = 1, val contentVersion: Int = 1, val levels: List<LevelDefinition>)

@Serializable
data class CatalogIndex(val schemaVersion: Int = 1, val contentVersion: Int, val files: List<String>) {
    init {
        require(schemaVersion == 1 && contentVersion > 0 && files.isNotEmpty())
        require(files.distinct().size == files.size && files.all { it.matches(Regex("[a-z0-9-]+\\.json")) })
    }
}

@Serializable data class LevelRef(val id: String, val revision: Int)
@Serializable data class ScheduleEpoch(val version: Int, val effectiveFrom: String, val pool: List<LevelRef>)
@Serializable data class DailySchedule(val epochs: List<ScheduleEpoch>)
