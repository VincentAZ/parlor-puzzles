package com.parlor.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable enum class TileArt { NUMBERS, GARDEN }
/** Zero is the empty square; tile identities always refer to their solved positions. */
@Serializable @SerialName("sliding_tiles")
data class TilesBoard(val side: Int, val tiles: List<Int>, val art: TileArt = TileArt.NUMBERS) : BoardState
@Serializable @SerialName("slide_tile") data class SlideTile(val tile: Int) : Move

@Serializable data class EscapeBlock(val id: String, val x: Int, val y: Int, val width: Int, val height: Int)
/** The target's top-left reaches y == height only after it slides through the bottom opening. */
@Serializable @SerialName("block_escape")
data class BlockEscapeBoard(val width: Int = 4, val height: Int = 5, val exitColumn: Int = 1,
    val targetId: String = "A", val blocks: List<EscapeBlock>) : BoardState
@Serializable @SerialName("slide_block") data class SlideBlock(val id: String, val x: Int, val y: Int) : Move

@Serializable enum class Direction(val dx: Int, val dy: Int) { UP(0,-1), RIGHT(1,0), DOWN(0,1), LEFT(-1,0) }
/** Floor, goals and boxes are cell indices. Box identities are interchangeable. */
@Serializable @SerialName("box_pusher")
data class BoxPusherBoard(val width: Int, val height: Int, val floor: List<Int>, val goals: List<Int>,
    val boxes: List<Int>, val worker: Int) : BoardState
@Serializable @SerialName("worker_step") data class WorkerStep(val direction: Direction) : Move
/** A shortest walk through empty floor, with no automatic pushes. */
@Serializable @SerialName("walk_to") data class WalkTo(val cell: Int) : Move
