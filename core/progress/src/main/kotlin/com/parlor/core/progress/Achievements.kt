package com.parlor.core.progress

import com.parlor.core.model.*

object Achievements {
    val definitions: List<AchievementDefinition> = buildList {
        Puzzle.entries.forEach { p -> add(AchievementDefinition("three-stars-${p.name.lowercase()}", AchievementKind.FIRST_THREE_STARS, p)) }
        Puzzle.entries.forEach { p -> add(AchievementDefinition("perfect-tier-${p.name.lowercase()}", AchievementKind.PERFECT_TIER, p)) }
        listOf(7, 30, 100, 365).forEach { add(AchievementDefinition("streak-$it", AchievementKind.STREAK, target = it)) }
        add(AchievementDefinition("hanoi-ten", AchievementKind.HANOI_TEN, Puzzle.HANOI))
        add(AchievementDefinition("box-no-undo", AchievementKind.BOX_NO_UNDO, Puzzle.BOX_PUSHER))
        add(AchievementDefinition("knight-closed-eight", AchievementKind.KNIGHT_CLOSED_EIGHT, Puzzle.KNIGHTS_TOUR))
        add(AchievementDefinition("first-solution", AchievementKind.FIRST_SOLUTION))
        listOf(10, 50, 100, 556).forEach { add(AchievementDefinition("campaign-levels-$it", AchievementKind.CAMPAIGN_LEVELS, target = it)) }
        listOf(100, 500).forEach { add(AchievementDefinition("campaign-stars-$it", AchievementKind.CAMPAIGN_STARS, target = it)) }
        add(AchievementDefinition("daily-days-7", AchievementKind.DAILY_DAYS, target = 7))
        add(AchievementDefinition("all-puzzles", AchievementKind.ALL_PUZZLES, target = 12))
    }.also { require(it.size == 40 && it.map { a -> a.id }.distinct().size == 40) }

    fun evaluate(level: LevelDefinition, attempt: Attempt, result: Result?, catalog: List<LevelDefinition>,
                 bests: List<LevelBest>, ledger: RewardLedger, profile: PlayerProfile,
                 facts: CompletionFacts = CompletionFacts()): Set<String> {
        if (!attempt.completed || attempt.mode == AttemptMode.DEMONSTRATION) return emptySet()
        val bestById = bests.associateBy { it.levelId }
        return definitions.filter { definition ->
            if (definition.id in profile.achievements) return@filter false
            when (definition.kind) {
                AchievementKind.FIRST_THREE_STARS -> level.puzzle == definition.puzzle && result?.stars == 3
                AchievementKind.PERFECT_TIER -> Tier.entries.any { tier ->
                    val levels = catalog.filter { it.puzzle == definition.puzzle && it.tier == tier }
                    // A partial development catalog cannot award a full-tier achievement.
                    levels.size == tierSize(requireNotNull(definition.puzzle), tier) &&
                        levels.all { bestById[it.id]?.stars == 3 }
                }
                AchievementKind.STREAK -> DailyRules.longestStreak(ledger.creditedDates) >= definition.target
                AchievementKind.HANOI_TEN -> level.puzzle == Puzzle.HANOI && facts.hanoiDisks == 10
                AchievementKind.BOX_NO_UNDO -> level.puzzle == Puzzle.BOX_PUSHER && attempt.undoCount == 0
                AchievementKind.KNIGHT_CLOSED_EIGHT -> level.puzzle == Puzzle.KNIGHTS_TOUR && facts.closedKnightSide == 8
                AchievementKind.FIRST_SOLUTION -> true
                AchievementKind.CAMPAIGN_LEVELS -> bests.size >= definition.target
                AchievementKind.CAMPAIGN_STARS -> bests.sumOf { it.stars } >= definition.target
                AchievementKind.DAILY_DAYS -> profile.dailyCompletedDates.size >= definition.target
                AchievementKind.ALL_PUZZLES -> profile.completedPuzzles.size == Puzzle.entries.size
            }
        }.map { it.id }.toSet()
    }

    fun tierSize(puzzle: Puzzle, tier: Tier): Int = when (puzzle.campaignCount) {
        50 -> listOf(13, 12, 13, 12)[tier.ordinal]
        30 -> listOf(8, 7, 8, 7)[tier.ordinal]
        else -> puzzle.campaignCount / 4
    }
}
