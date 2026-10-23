package com.parlor.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Disks are stored bottom to top. A disk number is its size, with 1 the smallest. */
@Serializable @SerialName("hanoi")
data class HanoiBoard(val pegs: List<List<Int>>, val goalPeg: Int = pegs.lastIndex) : BoardState
@Serializable @SerialName("transfer_disk")
data class TransferDisk(val from: Int, val to: Int) : Move

/** A toggle changes its own square and the orthogonal neighbours. */
@Serializable @SerialName("switch_grid")
data class SwitchBoard(val side: Int, val lights: List<Boolean>) : BoardState
@Serializable @SerialName("toggle")
data class Toggle(val index: Int) : Move

@Serializable @SerialName("water_jugs")
data class JugsBoard(val capacities: List<Int>, val amounts: List<Int>, val target: Int, val targetJug: Int? = null) : BoardState
@Serializable @SerialName("fill_jug") data class FillJug(val jug: Int) : Move
@Serializable @SerialName("empty_jug") data class EmptyJug(val jug: Int) : Move
@Serializable @SerialName("pour_jug") data class PourJug(val from: Int, val to: Int) : Move

/** Sizes run from 1 to n, stored from the top of the stack to the bottom. */
@Serializable @SerialName("pancake")
data class PancakeBoard(val stack: List<Int>) : BoardState
@Serializable @SerialName("flip_prefix")
data class FlipPrefix(val count: Int) : Move
