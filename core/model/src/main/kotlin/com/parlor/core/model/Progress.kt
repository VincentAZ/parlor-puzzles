package com.parlor.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/** Strict wire format shared by bundled content, persistence, and portable backups. */
val ParlorJson = Json {
    encodeDefaults = true
    classDiscriminator = "type"
    ignoreUnknownKeys = false
}

@Serializable enum class AttemptMode { CAMPAIGN, DAILY, PRACTICE, DEMONSTRATION }
@Serializable enum class DailyEligibility { PRACTICE, FIRST_COMPLETION, REWARDED, PRO }

@Serializable
data class Viewport(val column: Int = 0, val row: Int = 0, val zoom: Int = 1, val selectedId: String? = null, val showLabels: Boolean = true)

@Serializable
data class HistoryEntry(val before: BoardState, val after: BoardState, val move: Move, val cost: Int)

@Serializable
data class Attempt(
    val id: String,
    val levelId: String,
    val levelRevision: Int,
    val mode: AttemptMode,
    val board: BoardState,
    val history: List<HistoryEntry> = emptyList(),
    val redo: List<HistoryEntry> = emptyList(),
    val scoredMoves: Int = 0,
    val backtracks: Int = 0,
    val hintsUsed: Boolean = false,
    val activeTimeMs: Long = 0,
    val completed: Boolean = false,
    val viewport: Viewport = Viewport(),
    val challengeDate: String? = null,
    val dailyEligibility: DailyEligibility = DailyEligibility.PRACTICE,
    val undoCount: Int = 0,
    val walkingSteps: Int = 0,
    /** Presentation only; tile identities, histories and bundled Daily artwork never change. */
    val photoId: String? = null,
) {
    init {
        require(id.isNotBlank() && scoredMoves >= 0 && backtracks >= 0 && activeTimeMs >= 0 && undoCount >= 0 && walkingSteps >= 0)
        require((mode == AttemptMode.DAILY) == (challengeDate != null))
    }
    companion object {
        fun start(id: String, level: LevelDefinition, mode: AttemptMode = AttemptMode.CAMPAIGN, challengeDate: String? = null,
                  dailyEligibility: DailyEligibility = DailyEligibility.PRACTICE) =
            Attempt(id, level.id, level.revision, mode, level.initial,
                viewport = if (level.initial is BoxPusherBoard) Viewport(
                    column = ((level.initial.worker % level.initial.width) - 1).coerceAtLeast(0),
                    row = ((level.initial.worker / level.initial.width) - 1).coerceAtLeast(0)) else Viewport(),
                challengeDate = challengeDate, dailyEligibility = dailyEligibility)
    }
}

@Serializable
data class Result(val stars: Int, val points: Int, val timeMs: Long, val moves: Int)

@Serializable
data class LevelBest(
    val levelId: String,
    val stars: Int,
    val points: Int,
    val timeMs: Long,
    val moves: Int,
    val firstCompletedDate: String,
)

@Serializable
data class DailyRecord(
    val date: String,
    val scheduleVersion: Int,
    val levelId: String,
    val levelRevision: Int,
    val scoredCompletions: Int = 0,
    val earnedRetries: Int = 0,
    val best: Result? = null,
    val completions: Int = 0,
    val activeAttemptId: String? = null,
)

@Serializable
data class DailyEntry(val record: DailyRecord, val level: LevelDefinition)

/** Small claim records survive the 30-day display window and progress resets. */
@Serializable
data class DailyClaim(val firstCompletionClaimed: Boolean = false, val earnedRetries: Int = 0, val consumedRetries: Int = 0)

@Serializable
data class ScoreTotals(val stars: Int = 0, val points: Long = 0)

@Serializable
data class PlayerProfile(
    val achievements: Set<String> = emptySet(),
    val completedPuzzles: Set<Puzzle> = emptySet(),
    val dailyCompletedDates: Set<String> = emptySet(),
    val dailyTotals: Map<Puzzle, ScoreTotals> = emptyMap(),
    val playTimeByPuzzle: Map<Puzzle, Long> = emptyMap(),
)

@Serializable
sealed interface Reward {
    @Serializable @SerialName("hints")
    data object Hints : Reward
    @Serializable @SerialName("level_access")
    data class LevelAccess(val levelId: String) : Reward
    @Serializable @SerialName("daily_retry")
    data class DailyRetry(val date: String) : Reward
}

/** Created only by the SDK's earned callback, never by load, show, click or close. */
@Serializable
data class EarnedReward(
    val id: String,
    val reward: Reward,
    val earnedDate: String,
    val earnedAtMs: Long,
    val schemaVersion: Int = 1,
    val offeredDate: String = earnedDate,
) {
    init {
        require(schemaVersion == 1)
        require(id.isNotBlank() && id.length <= 200)
        require(earnedDate.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
        require(offeredDate.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
        require(earnedAtMs in 0..Long.MAX_VALUE - 86_400_000)
        when (reward) {
            Reward.Hints -> Unit
            is Reward.LevelAccess -> require(reward.levelId.isNotBlank() && reward.levelId.length <= 200)
            is Reward.DailyRetry -> require(reward.date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
        }
    }
}

enum class RewardApplication { APPLIED, ALREADY_APPLIED, REJECTED }
enum class RewardAvailability { AVAILABLE, PRO, DAILY_LIMIT, INVALID_DATE, INELIGIBLE }

@Serializable
data class RewardLedger(
    val latestResetDate: String? = null,
    val dailyHints: Int = 0,
    val earnedHints: Int = 0,
    val rewardedViews: Int = 0,
    val claims: Set<String> = emptySet(),
    val creditedDates: Set<String> = emptySet(),
    val temporaryUnlocks: Map<String, Long> = emptyMap(),
    val dailyClaims: Map<String, DailyClaim> = emptyMap(),
    val ads: AdHistory = AdHistory(),
    val rewardedViewsByDate: Map<String, Int> = emptyMap(),
    val earnedHintsSpent: Long = 0,
    val hintedAttemptIds: Set<String> = emptySet(),
)

/** Frequency records survive progress resets. Elapsed times are meaningful only within the recorded boot. */
@Serializable
data class AdHistory(
    val lastInterstitialCompletion: Int = 0,
    val lastInterstitialElapsedMs: Long? = null,
    val lastInterstitialBoot: String? = null,
    val interstitialUnsettled: Boolean = false,
    val dismissedInterstitials: Int = 0,
    val tenCompletionPromptSeen: Boolean = false,
)

@Serializable
data class PlayerSettings(
    val showTimer: Boolean = false,
    val reduceMotion: Boolean = false,
    val haptics: Boolean = true,
    val sfxVolume: Float = 0.65f,
    val musicVolume: Float = 0f,
    val theme: String = "linen",
    val accessibleHome: Boolean = false,
    // Legacy wire field only. Delivery opt-in/configuration is device-owned and never restored from this model.
    val notifications: Boolean = false,
    val onboarded: Boolean = false,
    val tutorialsSeen: Set<Puzzle> = emptySet(),
    val appearance: Appearance = Appearance.LIGHT,
)

/** Entitlement, tokens and device consent deliberately have no fields in this envelope. */
@Serializable
data class ProgressEnvelope(
    val schemaVersion: Int = 4,
    val contentVersion: Int = 1,
    val attempts: List<Attempt> = emptyList(),
    val bests: List<LevelBest> = emptyList(),
    val daily: List<DailyRecord> = emptyList(),
    val ledger: RewardLedger = RewardLedger(),
    val settings: PlayerSettings = PlayerSettings(),
    val completedAttemptIds: Set<String> = emptySet(),
    val achievements: Set<String> = emptySet(),
    val totalPlayTimeMs: Long = 0,
    val profile: PlayerProfile = PlayerProfile(),
    val format: String = "parlor-progress",
    val snapshotId: String = "",
    val createdAtMs: Long = 0,
    val attemptUpdatedAt: Map<String, Long> = emptyMap(),
    val completionDates: Map<String, String> = emptyMap(),
    val photos: List<PuzzlePhoto> = emptyList(),
)
