package com.parlor.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable enum class PegLayout { ENGLISH, EUROPEAN, TRIANGLE, DIAMOND }
/** Layout fixes the hole topology. Pegs and the single required final hole are explicit. */
@Serializable @SerialName("peg_solitaire")
data class PegBoard(val layout: PegLayout, val pegs: List<Int>, val goal: Int) : BoardState
@Serializable @SerialName("jump_peg") data class JumpPeg(val from: Int, val to: Int) : Move

/** Fixed queens cannot be removed; blocked cells are part of the level's constraints. */
@Serializable @SerialName("queens")
data class QueensBoard(val side: Int, val queens: List<Int>, val fixed: List<Int> = emptyList(),
    val blocked: List<Int> = emptyList()) : BoardState
@Serializable @SerialName("place_queen") data class PlaceQueen(val cell: Int) : Move
@Serializable @SerialName("remove_queen") data class RemoveQueen(val cell: Int) : Move

/** The first cell is the configured start; only shared undo can shorten the path. */
@Serializable @SerialName("knights_tour")
data class KnightBoard(val side: Int, val path: List<Int>, val closed: Boolean = false) : BoardState
@Serializable @SerialName("knight_step") data class KnightStep(val cell: Int) : Move

@Serializable enum class CrossingScenario { FOOD_FERRY, MISSIONARIES, COUPLES, BRIDGE }
@Serializable enum class CrossingRole { WOLF, GOAT, CABBAGE, MISSIONARY, CANNIBAL, PARTNER_A, PARTNER_B, WALKER }
@Serializable data class CrossingActor(val id: String, val role: CrossingRole,
    val pair: Int? = null, val minutes: Int = 1)
/** The farmer is the food ferry's implicit pilot. Other parties carry the boat/torch themselves. */
@Serializable @SerialName("crossings")
data class CrossingBoard(val scenario: CrossingScenario, val actors: List<CrossingActor>,
    val capacity: Int, val right: List<String> = emptyList(), val boatRight: Boolean = false,
    val elapsedMinutes: Int = 0) : BoardState
@Serializable @SerialName("cross_river") data class CrossRiver(val passengers: List<String>) : Move
