package com.parlor.core.progress

import com.parlor.core.game.AttemptValidation
import com.parlor.core.model.LevelBest
import com.parlor.core.model.Result
import kotlinx.datetime.LocalDate

/** The same score bounds apply to database reads and portable backup imports. */
object SavedScoreRules {
    fun best(value: LevelBest) {
        require(value.levelId.isNotBlank() && value.levelId.length <= 200 && value.levelId.none { it.code < 32 })
        result(Result(value.stars, value.points, value.timeMs, value.moves))
        require(value.firstCompletedDate.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
        LocalDate.parse(value.firstCompletedDate)
    }

    fun result(value: Result) {
        require(value.stars in 1..3)
        require(value.points in value.stars * 100..value.stars * 100 + 50)
        require(value.moves in 0..100_000_000 && value.timeMs in 0..AttemptValidation.MAX_TIME)
    }
}
