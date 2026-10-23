package com.parlor.core.game

import com.parlor.core.model.*

/** A verified, read-only replay. It has no player attempt or reward state. */
data class Demonstration(val states: List<BoardState>) {
    companion object {
        fun from(level: LevelDefinition): Demonstration {
            verifyReference(level)
            val engine = engineFor(level.puzzle)
            val states = level.reference.runningFold(level.initial) { board, move ->
                requireNotNull(engine.apply(board, move))
            }
            return Demonstration(states)
        }
    }
}
