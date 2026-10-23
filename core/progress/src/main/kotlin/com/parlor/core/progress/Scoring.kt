package com.parlor.core.progress

import com.parlor.core.model.*

object Scoring {
    fun result(level: LevelDefinition, attempt: Attempt): Result? {
        require(attempt.levelId == level.id && attempt.levelRevision == level.revision)
        if (!attempt.completed || attempt.mode == AttemptMode.DEMONSTRATION || attempt.mode == AttemptMode.PRACTICE) return null
        if (attempt.mode == AttemptMode.DAILY && attempt.dailyEligibility == DailyEligibility.PRACTICE) return null
        val timeBased = level.scoringUnit == ScoringUnit.TIME || level.scoringUnit == ScoringUnit.TIME_AND_BACKTRACKS
        val rawStars = if (timeBased) {
            when {
                attempt.activeTimeMs <= level.targetTimeMs &&
                    (level.scoringUnit != ScoringUnit.TIME_AND_BACKTRACKS || attempt.backtracks == 0) -> 3
                attempt.activeTimeMs <= 2 * level.targetTimeMs -> 2
                else -> 1
            }
        } else {
            when {
                attempt.scoredMoves <= level.par -> 3
                attempt.scoredMoves.toLong() * 2 <= level.par.toLong() * 3 -> 2
                else -> 1
            }
        }
        val stars = minOf(rawStars, if (attempt.hintsUsed) 2 else 3)
        val bonus = (50 * (3 * level.targetTimeMs - attempt.activeTimeMs)
            .coerceIn(0, 2 * level.targetTimeMs) / (2 * level.targetTimeMs)).toInt()
        return Result(stars, stars * 100 + bonus, attempt.activeTimeMs, attempt.scoredMoves)
    }

    fun improve(levelId: String, previous: LevelBest?, result: Result, date: String) = LevelBest(
        levelId = levelId,
        stars = maxOf(previous?.stars ?: 0, result.stars),
        points = maxOf(previous?.points ?: 0, result.points),
        timeMs = minOf(previous?.timeMs ?: Long.MAX_VALUE, result.timeMs),
        moves = minOf(previous?.moves ?: Int.MAX_VALUE, result.moves),
        firstCompletedDate = previous?.firstCompletedDate ?: date,
    )
}
