package com.parlor.core.game

import com.parlor.core.model.*

object CrossingEngine : PuzzleEngine {
    override fun validate(board: BoardState): Boolean {
        if (board !is CrossingBoard || board.actors.size !in 2..10 || board.actors.any { !it.id.matches(Regex("[A-Za-z0-9]{1,8}")) } ||
            board.actors.map { it.id }.distinct().size != board.actors.size || board.right.distinct().size != board.right.size ||
            board.right.any { id -> board.actors.none { it.id==id } } || board.elapsedMinutes !in 0..10_000_000) return false
        if (board.scenario != CrossingScenario.BRIDGE && board.elapsedMinutes != 0) return false
        val roles=board.actors.groupingBy { it.role }.eachCount()
        val definitionValid=when(board.scenario) {
            CrossingScenario.FOOD_FERRY -> board.capacity==1 && roles==mapOf(CrossingRole.WOLF to 1,CrossingRole.GOAT to 1,CrossingRole.CABBAGE to 1)
            CrossingScenario.MISSIONARIES -> board.capacity in 2..3 && roles.keys==setOf(CrossingRole.MISSIONARY,CrossingRole.CANNIBAL) &&
                roles[CrossingRole.MISSIONARY]==roles[CrossingRole.CANNIBAL]
            CrossingScenario.COUPLES -> board.capacity in 2..3 && board.actors.all { it.role in setOf(CrossingRole.PARTNER_A,CrossingRole.PARTNER_B) && it.pair in 1..5 } &&
                board.actors.groupBy { it.pair }.values.all { pair -> pair.size==2 && pair.map { it.role }.toSet()==setOf(CrossingRole.PARTNER_A,CrossingRole.PARTNER_B) }
            CrossingScenario.BRIDGE -> board.capacity==2 && board.actors.all { it.role==CrossingRole.WALKER && it.minutes in 1..120 }
        }
        if(!definitionValid || board.actors.any { (board.scenario!=CrossingScenario.COUPLES && it.pair!=null) ||
                (board.scenario!=CrossingScenario.BRIDGE && it.minutes!=1) }) return false
        val left=board.actors.filter { it.id !in board.right }
        val right=board.actors.filter { it.id in board.right }
        return if(board.scenario==CrossingScenario.FOOD_FERRY) safeGroup(board.scenario,if(board.boatRight) left else right)
            else safeGroup(board.scenario,left)&&safeGroup(board.scenario,right)
    }

    /** Safety is checked on both banks and on the travelling party, as appropriate to the scenario. */
    fun safeGroup(scenario:CrossingScenario,actors:List<CrossingActor>):Boolean = when(scenario) {
        CrossingScenario.FOOD_FERRY -> {
            val roles=actors.map {it.role}.toSet()
            !(CrossingRole.GOAT in roles && (CrossingRole.WOLF in roles||CrossingRole.CABBAGE in roles))
        }
        CrossingScenario.MISSIONARIES -> {
            val missionaries=actors.count {it.role==CrossingRole.MISSIONARY}
            missionaries==0||missionaries>=actors.count {it.role==CrossingRole.CANNIBAL}
        }
        CrossingScenario.COUPLES -> {
            val companions=actors.filter {it.role==CrossingRole.PARTNER_A}.map {it.pair}.toSet()
            companions.isEmpty()||actors.filter {it.role==CrossingRole.PARTNER_B}.all {it.pair in companions}
        }
        CrossingScenario.BRIDGE -> true
    }

    override fun isGoal(board:BoardState):Boolean = board is CrossingBoard && validate(board) &&
        board.boatRight && board.right.size==board.actors.size
    override fun apply(board:BoardState,move:Move):BoardState? {
        if(board !is CrossingBoard || move !is CrossRiver || !validate(board)||isGoal(board)) return null
        if(move.passengers.distinct().size!=move.passengers.size || move.passengers.size>board.capacity ||
            (move.passengers.isEmpty()&&board.scenario!=CrossingScenario.FOOD_FERRY)) return null
        val party=move.passengers.map { id -> board.actors.find { it.id==id } ?: return null }
        if(party.any { (it.id in board.right)!=board.boatRight }) return null
        if(board.scenario!=CrossingScenario.FOOD_FERRY && !safeGroup(board.scenario,party)) return null
        val next=board.copy(right=(if(board.boatRight) board.right-move.passengers.toSet() else board.right+move.passengers).sorted(),
            boatRight=!board.boatRight,elapsedMinutes=board.elapsedMinutes+if(board.scenario==CrossingScenario.BRIDGE) cost(board,move) else 0)
        return next.takeIf(::validate)
    }
    override fun cost(board:BoardState,move:Move):Int {
        board as CrossingBoard;move as CrossRiver
        return if(board.scenario==CrossingScenario.BRIDGE) board.actors.filter {it.id in move.passengers}.maxOfOrNull {it.minutes} ?: 0 else 1
    }
    override fun legalMoves(board:BoardState):List<Move> {
        if(board !is CrossingBoard||!validate(board)||isGoal(board)) return emptyList()
        val available=board.actors.filter {(it.id in board.right)==board.boatRight}.map {it.id}.sorted()
        val moves=mutableListOf<Move>()
        fun choose(offset:Int,party:List<String>) {
            val move=CrossRiver(party)
            if(apply(board,move)!=null) moves+=move
            if(party.size==board.capacity) return
            for(index in offset until available.size) choose(index+1,party+available[index])
        }
        choose(0,emptyList())
        return moves
    }
    override fun searchKey(board:BoardState):Any = (board as CrossingBoard).right.sorted() to board.boatRight
    override fun search(board:BoardState,maxStates:Int):SearchResult = if((board as CrossingBoard).scenario==CrossingScenario.BRIDGE)
        lowestCost(this,board,maxStates) else breadthFirst(this,board,maxStates)
}
