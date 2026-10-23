package com.parlor.core.model

/** Engine-supplied structural facts, emitted only after validating a completed board. */
data class CompletionFacts(val hanoiDisks: Int? = null, val closedKnightSide: Int? = null)

enum class AchievementKind { FIRST_THREE_STARS, PERFECT_TIER, STREAK, HANOI_TEN, BOX_NO_UNDO, KNIGHT_CLOSED_EIGHT,
    FIRST_SOLUTION, CAMPAIGN_LEVELS, CAMPAIGN_STARS, DAILY_DAYS, ALL_PUZZLES }

data class AchievementDefinition(val id: String, val kind: AchievementKind, val puzzle: Puzzle? = null, val target: Int = 1)
