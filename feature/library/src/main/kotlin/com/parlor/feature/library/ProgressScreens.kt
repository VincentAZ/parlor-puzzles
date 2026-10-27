package com.parlor.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.parlor.core.design.rememberDisplayFormats
import com.parlor.core.data.PlayerSummary
import com.parlor.core.model.*
import com.parlor.core.progress.*

@Composable
internal fun displayDate(date: String): String = rememberDisplayFormats().date(date)

@Composable
internal fun puzzleName(puzzle: Puzzle): String = stringResource(when (puzzle) {
    Puzzle.TRAFFIC_JAM -> R.string.library_traffic_jam
    Puzzle.HANOI -> R.string.library_hanoi
    Puzzle.SLIDING_TILES -> R.string.library_sliding_tiles
    Puzzle.BLOCK_ESCAPE -> R.string.library_block_escape
    Puzzle.BOX_PUSHER -> R.string.library_box_pusher
    Puzzle.SWITCH_GRID -> R.string.library_switch_grid
    Puzzle.PEG_SOLITAIRE -> R.string.library_peg_solitaire
    Puzzle.QUEENS -> R.string.library_queens
    Puzzle.KNIGHTS_TOUR -> R.string.library_knights_tour
    Puzzle.WATER_JUGS -> R.string.library_water_jugs
    Puzzle.CROSSINGS -> R.string.library_crossings
    Puzzle.PANCAKE_FLIP -> R.string.library_pancake_flip
})

@Composable
internal fun ColumnScope.DailyContent(entries: List<DailyEntry>, today: String, ledger: RewardLedger, pro: Boolean,
                                     error: Boolean, onPlay: (String) -> Unit, onRetry: () -> Unit,
                                     onReward: ((String) -> Unit)? = null) {
    Text(stringResource(R.string.library_daily), style = MaterialTheme.typography.headlineLarge)
    Text(stringResource(R.string.library_daily_intro))
    val current = entries.find { it.record.date == today }
    if (current != null && DailyRules.withinHistory(today, today, ledger.latestResetDate)) {
        DailyCard(current, ledger, true, onPlay)
    } else if (error) {
        Text(stringResource(R.string.library_daily_error), color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry) { Text(stringResource(R.string.library_retry)) }
    } else CircularProgressIndicator()
    Text(stringResource(R.string.library_daily_history), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.library_daily_history_body))
    val older = entries.filter { it.record.date != today && DailyRules.withinHistory(it.record.date, today, ledger.latestResetDate) }
    if (older.isEmpty()) Text(stringResource(R.string.library_daily_history_empty))
    older.forEach { DailyCard(it, ledger, true, onPlay) }
}

@Composable
private fun DailyCard(entry: DailyEntry, ledger: RewardLedger, pro: Boolean, onPlay: (String) -> Unit) {
    val record = entry.record
    val eligibility = DailyRules.eligibility(ledger.dailyClaims[record.date] ?: DailyClaim(), true)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(displayDate(record.date), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.library_daily_puzzle, puzzleName(entry.level.puzzle), tierName(entry.level.tier)))
            record.best?.let { Text(stringResource(R.string.library_daily_best, it.stars, it.points)) }
            val label = when {
                record.activeAttemptId != null -> R.string.library_daily_continue
                eligibility == DailyEligibility.PRACTICE -> R.string.library_daily_practice
                else -> R.string.library_daily_start
            }
            if (record.activeAttemptId == null && eligibility == DailyEligibility.PRACTICE) Text(stringResource(R.string.library_daily_practice_body))
            Button(onClick = { onPlay(record.date) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(label)) }
        }
    }
}

@Composable
internal fun achievementText(definition: AchievementDefinition): String = when (definition.kind) {
    AchievementKind.FIRST_THREE_STARS -> stringResource(R.string.library_achievement_three, puzzleName(requireNotNull(definition.puzzle)))
    AchievementKind.PERFECT_TIER -> stringResource(R.string.library_achievement_tier, puzzleName(requireNotNull(definition.puzzle)))
    AchievementKind.STREAK -> stringResource(R.string.library_achievement_streak, definition.target)
    AchievementKind.HANOI_TEN -> stringResource(R.string.library_achievement_hanoi)
    AchievementKind.BOX_NO_UNDO -> stringResource(R.string.library_achievement_box)
    AchievementKind.KNIGHT_CLOSED_EIGHT -> stringResource(R.string.library_achievement_knight)
    AchievementKind.FIRST_SOLUTION -> stringResource(R.string.library_achievement_first)
    AchievementKind.CAMPAIGN_LEVELS -> stringResource(R.string.library_achievement_levels, definition.target)
    AchievementKind.CAMPAIGN_STARS -> stringResource(R.string.library_achievement_stars, definition.target)
    AchievementKind.DAILY_DAYS -> stringResource(R.string.library_achievement_daily, definition.target)
    AchievementKind.ALL_PUZZLES -> stringResource(R.string.library_achievement_all)
}
