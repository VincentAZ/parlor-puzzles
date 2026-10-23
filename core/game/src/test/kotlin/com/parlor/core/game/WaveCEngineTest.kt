package com.parlor.core.game

import com.parlor.core.model.*
import kotlin.math.abs
import kotlin.test.*

class WaveCEngineTest {
    @Test fun pegTopologiesAndTriangleSolverAgreeWithIndependentRetrogradeStates() {
        assertEquals(listOf(33,37,15,25),PegLayout.entries.map {pegGeometry(it).holes.size})
        for((layout,cells) in listOf(PegLayout.ENGLISH to listOf(2,3,4),PegLayout.EUROPEAN to listOf(2,3,4),
            PegLayout.TRIANGLE to listOf(0,5,10),PegLayout.DIAMOND to listOf(3,10,17))) {
            val board=PegBoard(layout,cells.take(2),cells.last())
            val next=PegEngine.apply(board,JumpPeg(cells[0],cells[2]))!!
            assertTrue(PegEngine.isGoal(next))
            assertNull(PegEngine.apply(board,JumpPeg(cells[0],cells[1])))
            assertFalse(PegEngine.validate(board.copy(pegs=board.pegs+board.pegs.first())))
        }
        val holes=(0..4).flatMap { row -> (0..row).map { row*5+it } }
        val jumps=holes.flatMap { from -> holes.mapNotNull { to ->
            val dx=to%5-from%5;val dy=to/5-from/5
            if((abs(dx)==2&&dy==0)||(dx==0&&abs(dy)==2)||(dx==dy&&abs(dx)==2)) {
                val middle=(from+to)/2
                if(middle in holes) Triple(1 shl holes.indexOf(from),1 shl holes.indexOf(middle),1 shl holes.indexOf(to)) else null
            } else null
        } }
        val target=1 shl holes.indexOf(12)
        val winning=mutableSetOf(target);val queue=ArrayDeque<Int>();queue.add(target)
        while(queue.isNotEmpty()) {
            val mask=queue.removeFirst()
            for((from,over,to) in jumps) if(mask and to != 0 && mask and (from or over)==0) {
                val previous=(mask xor to) or from or over
                if(winning.add(previous)) queue.add(previous)
            }
        }
        for(mask in (1 until (1 shl holes.size)).filter {it%103==0 || it==target}) {
            val board=PegBoard(PegLayout.TRIANGLE,holes.filterIndexed { i,_ -> mask and (1 shl i)!=0 },12)
            val result=solvePegs(board,50_000)
            if(mask in winning) {
                result as SearchResult.Solved
                assertEquals(board.pegs.size-1,result.moves.size)
                assertTrue(PegEngine.isGoal(replay(PegEngine,board,result.moves).final))
            } else assertEquals(SearchResult.Unsolvable,result,"$mask")
        }
    }

    @Test fun queensAcceptEveryValidArrangementAndPreserveFixedAndBlockedConstraints() {
        for((side,expected) in listOf(4 to 2,5 to 10,6 to 4,7 to 40,8 to 92)) {
            val solutions=mutableListOf<List<Int>>()
            fun enumerate(columns:List<Int>) {
                if(columns.size==side) {solutions+=columns.mapIndexed {r,c -> r*side+c};return}
                for(c in 0 until side) if(c !in columns && columns.indices.none {r -> abs(columns[r]-c)==columns.size-r}) enumerate(columns+c)
            }
            enumerate(emptyList());assertEquals(expected,solutions.size)
            solutions.forEach {assertTrue(QueensEngine.isGoal(QueensBoard(side,it)))}
            val solution=solutions.first();val fixed=listOf(solution.first())
            val blocked=(0 until side*side).first {it !in solution}
            val board=QueensBoard(side,fixed,fixed,listOf(blocked))
            assertNull(QueensEngine.apply(board,RemoveQueen(fixed.first())))
            assertNull(QueensEngine.apply(board,PlaceQueen(blocked)))
            val route=solveQueens(board) as SearchResult.Solved
            assertTrue(QueensEngine.isGoal(replay(QueensEngine,board,route.moves).final))
        }
        val deadEnd=QueensBoard(4,listOf(0))
        assertEquals(SearchResult.Unsolvable,solveQueens(deadEnd))
        val recovery=QueensEngine.continuation(deadEnd)!!
        assertEquals(RemoveQueen(0),recovery.first())
        assertTrue(QueensEngine.isGoal(replay(QueensEngine,deadEnd,recovery).final))
        assertNull(QueensEngine.continuation(deadEnd.copy(fixed=listOf(0))))
        assertFalse(QueensEngine.isGoal(QueensBoard(4,listOf(0,5,10,15))))
    }

    @Test fun knightRoutesCoverEverySquareAndCloseOnlyOnAnActualKnightEdge() {
        for(side in 5..8) {
            val board=KnightBoard(side,listOf(0),closed=side%2==0)
            val result=solveKnight(board,500_000) as SearchResult.Solved
            val goal=replay(KnightEngine,board,result.moves).final as KnightBoard
            assertEquals(side*side,goal.path.toSet().size)
            val edges=goal.path.zipWithNext()+if(goal.closed) listOf(goal.path.last() to goal.path.first()) else emptyList()
            assertTrue(edges.all { (a,b) -> setOf(abs(a%side-b%side),abs(a/side-b/side))==setOf(1,2) })
            assertTrue(KnightEngine.isGoal(goal.copy(path=goal.path.asReversed())))
            assertEquals(if(goal.closed) side else null,KnightEngine.completionFacts(goal).closedKnightSide)
            assertNull(KnightEngine.apply(board,KnightStep(1)))
            assertFalse(KnightEngine.validate(board.copy(path=listOf(0,side+2,0))))
        }
        assertFalse(KnightEngine.validate(KnightBoard(5,listOf(0),true)))
        assertFalse(KnightEngine.validate(KnightBoard(7,listOf(0),true)))
        assertTrue(solveKnight(KnightBoard(8,listOf(0),true),1) is SearchResult.LimitReached)
    }

    @Test fun ferryScenariosEnforceBankPassengerCapacityAndCompanionSafety() {
        val food=food()
        assertEquals(listOf(CrossRiver(listOf("G"))),CrossingEngine.legalMoves(food))
        assertNull(CrossingEngine.apply(food,CrossRiver(listOf("W","G"))))
        val foodSolution=CrossingEngine.search(food,1_000) as SearchResult.Solved
        assertEquals(7,foodSolution.moves.size)
        assertTrue(CrossingEngine.isGoal(replay(CrossingEngine,food,foodSolution.moves).final))
        val missionaries=CrossingBoard(CrossingScenario.MISSIONARIES,(1..3).flatMap { listOf(
            CrossingActor("M$it",CrossingRole.MISSIONARY),CrossingActor("C$it",CrossingRole.CANNIBAL)) },2)
        val missionSolution=CrossingEngine.search(missionaries,10_000) as SearchResult.Solved
        assertEquals(11,missionSolution.moves.size)
        assertNull(CrossingEngine.apply(missionaries,CrossRiver(listOf("M1"))))
        assertNull(CrossingEngine.apply(missionaries,CrossRiver(emptyList())))
        val couples=CrossingBoard(CrossingScenario.COUPLES,(1..3).flatMap { listOf(
            CrossingActor("A$it",CrossingRole.PARTNER_A,it),CrossingActor("B$it",CrossingRole.PARTNER_B,it)) },2)
        assertFalse(CrossingEngine.safeGroup(CrossingScenario.COUPLES,listOf(couples.actors[0],couples.actors[3])))
        assertTrue(CrossingEngine.safeGroup(CrossingScenario.COUPLES,couples.actors.take(2)))
        val route=CrossingEngine.search(couples,10_000) as SearchResult.Solved
        assertTrue(CrossingEngine.isGoal(replay(CrossingEngine,couples,route.moves).final))
        assertNull(CrossingEngine.apply(couples,CrossRiver(listOf("A1","A1"))))
        assertFalse(CrossingEngine.validate(couples.copy(actors=couples.actors.dropLast(1))))
    }

    @Test fun weightedBridgeSearchMatchesIndependentStateGraphRelaxation() {
        for(times in listOf(listOf(1,2,5,10),listOf(1,3,7,12,20),listOf(2,4,8,9,15,21))) {
            val board=bridge(times);val count=times.size;val all=(1 shl count)-1
            // Independent Bellman-Ford over (right-bank mask, torch side); no shipping move generation.
            val distance=IntArray((1 shl count)*2) {100_000};distance[0]=0
            repeat(distance.size) {
                for(mask in 0..all) for(side in 0..1) {
                    val from=mask*2+side
                    val available=(0 until count).filter { ((mask shr it) and 1)==side }
                    val groups=available.map {listOf(it)}+available.flatMap {a -> available.filter {it>a}.map {b -> listOf(a,b)}}
                    for(group in groups) {
                        var next=mask;group.forEach {next=next xor (1 shl it)}
                        val to=next*2+(1-side)
                        distance[to]=minOf(distance[to],distance[from]+group.maxOf {times[it]})
                    }
                }
            }
            val result=CrossingEngine.search(board,10_000) as SearchResult.Solved
            val replay=replay(CrossingEngine,board,result.moves)
            assertEquals(distance[all*2+1],replay.cost)
            assertEquals(replay.cost,(replay.final as CrossingBoard).elapsedMinutes)
            if(times.size==4) assertEquals(17,replay.cost)
            assertTrue(CrossingEngine.isGoal(replay.final))
        }
        assertTrue(CrossingEngine.search(bridge(listOf(1,2,5,10)),1) is SearchResult.LimitReached)
        assertFailsWith<IllegalArgumentException> {breadthFirst(CrossingEngine,bridge(listOf(1,2,5,10)))}
    }

    @Test fun waveCHistorySerializationRecoveryAndScoringUnitsRetainTheirMeaning() {
        val bridge=bridge(listOf(1,2,5,10));val route=(CrossingEngine.search(bridge,1_000) as SearchResult.Solved).moves
        val level=level(Puzzle.CROSSINGS,bridge,route,ScoringUnit.SCENARIO_MINUTES)
        val first=route.first();val cost=CrossingEngine.cost(bridge,first)
        var attempt=Session.move(CrossingEngine,Attempt.start("bridge",level),first)!!
        attempt=Session.undo(attempt,level.scoringUnit)!!
        assertEquals(cost*2,attempt.scoredMoves);assertEquals(0,(attempt.board as CrossingBoard).elapsedMinutes)
        attempt=Session.redo(CrossingEngine,attempt)!!
        assertEquals(cost*3,attempt.scoredMoves);assertEquals(cost,(attempt.board as CrossingBoard).elapsedMinutes)
        assertEquals(attempt,ParlorJson.decodeFromString<Attempt>(ParlorJson.encodeToString(attempt)))
        val knight=KnightBoard(5,listOf(0));val tour=(solveKnight(knight) as SearchResult.Solved).moves
        val knightLevel=level(Puzzle.KNIGHTS_TOUR,knight,tour,ScoringUnit.TIME_AND_BACKTRACKS)
        var traced=Session.move(KnightEngine,Attempt.start("knight",knightLevel),tour.first())!!
        traced=Session.redo(KnightEngine,Session.undo(traced,knightLevel.scoringUnit)!!)!!
        assertEquals(1,traced.backtracks);assertEquals(1,traced.undoCount)
        val current=(traced.board as KnightBoard).path.last()
        assertEquals((current%5-1).coerceAtLeast(0),traced.viewport.column)
        assertEquals((current/5-1).coerceAtLeast(0),traced.viewport.row)
        assertEquals(traced,ParlorJson.decodeFromString<Attempt>(ParlorJson.encodeToString(traced)))
        for(board in listOf<BoardState>(PegBoard(PegLayout.TRIANGLE,listOf(0,5),10),QueensBoard(4,listOf(1),listOf(1),listOf(0))))
            assertEquals(board,ParlorJson.decodeFromString<BoardState>(ParlorJson.encodeToString(board)))
        assertEquals(Hint.Next(route[1]),hintFor(level,Session.move(CrossingEngine,Attempt.start("hint",level),first)!!))
    }

    private fun food()=CrossingBoard(CrossingScenario.FOOD_FERRY,listOf(CrossingActor("W",CrossingRole.WOLF),
        CrossingActor("G",CrossingRole.GOAT),CrossingActor("C",CrossingRole.CABBAGE)),1)
    private fun bridge(times:List<Int>)=CrossingBoard(CrossingScenario.BRIDGE,times.mapIndexed {i,t -> CrossingActor("P${i+1}",CrossingRole.WALKER,minutes=t)},2)
    private fun level(puzzle:Puzzle,board:BoardState,moves:List<Move>,unit:ScoringUnit)=LevelDefinition("wave-c-test",puzzle=puzzle,tier=Tier.EASY,order=1,
        initial=board,reference=moves,par=replay(engineFor(puzzle),board,moves).cost,parKind=ParKind.REFERENCE,targetTimeMs=60_000,
        scoringUnit=unit,provenance=Provenance("Parlor","test",0,"original"))
}
