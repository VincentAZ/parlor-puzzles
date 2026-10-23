package com.parlor.core.data

import com.parlor.core.model.*
import com.parlor.core.progress.Scoring
import java.time.LocalDate

enum class HistoryKind { ALL, CAMPAIGN, DAILY }
data class HistoryCursor(val updatedAt: Long, val id: String)
data class HistoryQuery(val puzzle: Puzzle? = null, val kind: HistoryKind = HistoryKind.ALL, val before: HistoryCursor? = null)
data class CompletedAttempt(val attempt: Attempt, val puzzle: Puzzle, val completedDate: String?,
                            val level: LevelDefinition?, val result: Result?)
data class HistoryPage(val entries: List<CompletedAttempt> = emptyList(), val next: HistoryCursor? = null,
                       val proRequired: Boolean = false) {
    companion object { const val SIZE = 20 }
}

/** A historical Daily uses its saved definition, including its original scoring targets. */
internal fun CompletedAttemptRow.detail(catalog: Catalog): CompletedAttempt {
    val saved = ParlorJson.decodeFromString<Attempt>(attempt.json)
    check(saved.completed && saved.id == attempt.id)
    completionDate?.let(LocalDate::parse)
    saved.challengeDate?.let(LocalDate::parse)
    val level = if (saved.mode == AttemptMode.DAILY) dailyJson?.let {
        ParlorJson.decodeFromString<DailyEntry>(it).level
    } else catalog.levels.find { it.id == saved.levelId && it.revision == saved.levelRevision }
    val matching = level?.takeIf { it.id == saved.levelId && it.revision == saved.levelRevision }
    return CompletedAttempt(saved, Puzzle.valueOf(attempt.puzzle), completionDate, matching,
        matching?.let { Scoring.result(it, saved) })
}
