package com.parlor.feature.library

import com.parlor.core.design.FoldAwareAlertDialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.parlor.core.data.*
import com.parlor.core.model.*
import com.parlor.core.progress.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import com.parlor.core.design.rememberDisplayFormats

private sealed interface HistoryLoad {
    data object Loading : HistoryLoad
    data object Failed : HistoryLoad
    data class Ready(val page: HistoryPage) : HistoryLoad
}

@Composable
fun ProgressScreen(catalog: Catalog, bests: List<LevelBest>, player: PlayerSummary, pro: Boolean,
                   history: (HistoryQuery) -> Flow<HistoryPage>, onPro: () -> Unit, onBack: () -> Unit,
                   banner: @Composable () -> Unit = {}) {
    var tab by rememberSaveable { mutableStateOf("summary") }
    var puzzleId by rememberSaveable { mutableStateOf<String?>(null) }
    var kindId by rememberSaveable { mutableStateOf(HistoryKind.ALL.name) }
    var cursorIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var cursorTimes by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val puzzle = puzzleId?.let(Puzzle::valueOf)
    val kind = HistoryKind.valueOf(kindId)
    val query = HistoryQuery(puzzle, kind, cursorIds.lastOrNull()?.let { HistoryCursor(cursorTimes.last(), it) })
    val loaded = remember(query, pro, tab, retry, history) { mutableStateOf<HistoryLoad>(HistoryLoad.Loading) }
    val list = rememberLazyListState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(tab, puzzleId, kindId, cursorIds) { list.scrollToItem(0) }
    LaunchedEffect(pro) { if (!pro) { cursorIds = emptyList(); cursorTimes = emptyList() } }
    LaunchedEffect(loaded, lifecycle) {
        if (tab == "history" && pro) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    history(query).collect { loaded.value = HistoryLoad.Ready(it) }
                    if (loaded.value == HistoryLoad.Loading) loaded.value = HistoryLoad.Failed
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { loaded.value = HistoryLoad.Failed }
            }
        }
    }
    val levels = remember(catalog) { catalog.levels.associateBy { it.id } }
    val puzzles = remember(catalog) { catalog.levels.map { it.puzzle }.distinct() }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 16.dp).heightIn(min = 48.dp)) {
            Text(stringResource(R.string.library_back))
        }
        LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().testTag("progress-list"),
            contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item("title") { Text(stringResource(R.string.library_progress_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge) }
            item("tabs") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("summary" to R.string.library_summary, "achievements" to R.string.library_achievements,
                        "bests" to R.string.library_best_results, "history" to R.string.library_history).forEach { (id, label) ->
                        FilterChip(selected = tab == id, onClick = { tab = id }, label = { Text(stringResource(label)) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("progress-tab-$id"))
                    }
                }
            }
            when (tab) {
                "achievements" -> {
                    item { Text(stringResource(R.string.library_achievement_count, player.profile.achievements.size, Achievements.definitions.size)) }
                    items(Achievements.definitions, key = { "achievement-${it.id}" }) { definition ->
                        val status = stringResource(if (definition.id in player.profile.achievements) R.string.library_earned else R.string.library_to_discover)
                        DetailCard(Modifier.semantics { stateDescription = status }) {
                            Text(achievementText(definition), style = MaterialTheme.typography.titleMedium)
                            Text(status)
                        }
                    }
                }
                "history", "bests" -> {
                    if (!pro) item { ProDetails(onPro) }
                    else {
                        item("filters") {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(if (tab == "history") R.string.library_history_explainer else R.string.library_bests_explainer))
                                OutlinedButton(onClick = { filter = "puzzle" }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("progress-puzzle-filter")) {
                                    Text(stringResource(R.string.library_puzzle_filter, puzzle?.let { puzzleName(it) } ?: stringResource(R.string.library_all_puzzles)))
                                }
                                if (tab == "history") OutlinedButton(onClick = { filter = "kind" }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("progress-kind-filter")) {
                                    Text(stringResource(R.string.library_kind_filter, historyKind(kind)))
                                }
                            }
                        }
                        if (tab == "bests") {
                            val selected = catalog.levels.filter { puzzle == null || it.puzzle == puzzle }.mapNotNull { level -> bests.find { it.levelId == level.id }?.let { level to it } }
                            if (selected.isEmpty()) item { Text(stringResource(R.string.library_best_results_empty)) }
                            items(selected, key = { "best-${it.first.id}" }) { (level, best) ->
                                DetailCard(Modifier.testTag("best-${level.id}")) {
                                    LevelTitle(level.puzzle, level)
                                    Text(stringResource(R.string.library_saved_best, best.stars, best.points))
                                    Text(stringResource(R.string.library_best_seconds, best.timeMs / 1000))
                                    when (level.scoringUnit) {
                                        ScoringUnit.TIME, ScoringUnit.TIME_AND_BACKTRACKS -> Unit
                                        else -> Text(stringResource(when (level.scoringUnit) {
                                            ScoringUnit.PUSHES -> R.string.library_best_pushes
                                            ScoringUnit.SCENARIO_MINUTES -> R.string.library_best_minutes
                                            else -> R.string.library_best_moves
                                        }, best.moves))
                                    }
                                    TargetText(level)
                                    Text(stringResource(R.string.library_first_completion, displayDate(best.firstCompletedDate)))
                                }
                            }
                        } else when (val state = loaded.value) {
                            HistoryLoad.Loading -> item { CircularProgressIndicator(Modifier.testTag("history-loading")) }
                            HistoryLoad.Failed -> item {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(stringResource(R.string.library_history_error), color = MaterialTheme.colorScheme.error)
                                    OutlinedButton(onClick = { retry++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.library_retry)) }
                                }
                            }
                            is HistoryLoad.Ready -> if (state.page.proRequired) item { ProDetails(onPro) } else {
                                if (state.page.entries.isEmpty()) item { Text(stringResource(R.string.library_history_empty)) }
                                items(state.page.entries, key = { "attempt-${it.attempt.id}" }) { HistoryCard(it) }
                                item("history-pages") {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(stringResource(R.string.library_history_page, cursorIds.size + 1))
                                        if (cursorIds.isNotEmpty()) OutlinedButton(onClick = {
                                            cursorIds = cursorIds.dropLast(1); cursorTimes = cursorTimes.dropLast(1)
                                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("history-newer")) { Text(stringResource(R.string.library_history_newer)) }
                                        state.page.next?.let { next -> OutlinedButton(onClick = {
                                            cursorIds = cursorIds + next.id; cursorTimes = cursorTimes + next.updatedAt
                                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("history-older")) { Text(stringResource(R.string.library_history_older)) } }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    item { Text(stringResource(R.string.library_progress_body)) }
                    if (bests.isEmpty()) item { Text(stringResource(R.string.library_empty_progress)) }
                    item { Text(stringResource(R.string.library_campaign), style = MaterialTheme.typography.titleLarge) }
                    item { Metric(stringResource(R.string.library_levels_completed, bests.size)) }
                    item { Metric(stringResource(R.string.library_stars_possible, bests.sumOf { it.stars }, catalog.levels.size * 3)) }
                    item { Metric(stringResource(R.string.library_points_earned, bests.sumOf { it.points.toLong() })) }
                    item { Metric(stringResource(R.string.library_streak, DailyRules.longestStreak(player.ledger.creditedDates))) }
                    item { Metric(stringResource(R.string.library_time_played, player.totalPlayTimeMs / 60_000)) }
                    item { Metric(stringResource(R.string.library_achievement_count, player.profile.achievements.size, Achievements.definitions.size)) }
                    item { AverageTargets(bests, levels) }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.library_daily_totals), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.library_daily_totals_body))
                            Metric(stringResource(R.string.library_daily_best, player.profile.dailyTotals.values.sumOf { it.stars }, player.profile.dailyTotals.values.sumOf { it.points }))
                        }
                    }
                    items(puzzles, key = { "summary-$it" }) { collection ->
                        val definitions = catalog.levels.filter { it.puzzle == collection }.associateBy { it.id }
                        val records = bests.filter { it.levelId in definitions }
                        DetailCard {
                            Text(puzzleName(collection), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                            Text(stringResource(R.string.library_levels_completed, records.size))
                            Text(stringResource(R.string.library_stars_possible, records.sumOf { it.stars }, definitions.size * 3))
                            Text(stringResource(R.string.library_points_earned, records.sumOf { it.points.toLong() }))
                            Text(stringResource(R.string.library_time_played, (player.profile.playTimeByPuzzle[collection] ?: 0) / 60_000))
                            AverageTargets(records, definitions)
                            val daily = player.profile.dailyTotals[collection] ?: ScoreTotals()
                            Text(stringResource(R.string.library_puzzle_daily, daily.stars, daily.points))
                        }
                    }
                }
            }
        }
        banner()
    }
    if (filter != null && pro) {
        val selectingPuzzle = filter == "puzzle"
        val choices = if (selectingPuzzle) listOf(null) + puzzles.map { it.name } else HistoryKind.entries.map { it.name }
        FoldAwareAlertDialog(onDismissRequest = { filter = null }, title = { Text(stringResource(if (selectingPuzzle) R.string.library_choose_puzzle else R.string.library_choose_kind)) },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    choices.forEach { choice ->
                        val label = if (selectingPuzzle) choice?.let { puzzleName(Puzzle.valueOf(it)) } ?: stringResource(R.string.library_all_puzzles)
                            else historyKind(HistoryKind.valueOf(requireNotNull(choice)))
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("progress-filter-${choice ?: "all"}").selectable(
                            selected = choice == if (selectingPuzzle) puzzleId else kindId, role = Role.RadioButton,
                            onClick = {
                                if (selectingPuzzle) puzzleId = choice else kindId = requireNotNull(choice)
                                cursorIds = emptyList(); cursorTimes = emptyList(); filter = null
                            }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = choice == if (selectingPuzzle) puzzleId else kindId, onClick = null)
                            Text(label, Modifier.weight(1f).padding(start = 12.dp))
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { filter = null }) { Text(stringResource(R.string.library_cancel)) } })
    }
}

@Composable
private fun HistoryCard(entry: CompletedAttempt) = DetailCard(Modifier.testTag("history-${entry.attempt.id}")) {
    val attempt = entry.attempt
    LevelTitle(entry.puzzle, entry.level)
    Text(entry.completedDate?.let { stringResource(R.string.library_completed_on, displayDate(it)) }
        ?: stringResource(R.string.library_completion_date_unknown))
    Text(if (attempt.mode == AttemptMode.DAILY) stringResource(R.string.library_daily_date, displayDate(requireNotNull(attempt.challengeDate)))
        else stringResource(R.string.library_campaign))
    val practice = attempt.mode == AttemptMode.PRACTICE || attempt.mode == AttemptMode.DAILY && attempt.dailyEligibility == DailyEligibility.PRACTICE
    val result = entry.result
    when {
        practice -> Text(stringResource(R.string.library_history_practice))
        result != null -> Text(stringResource(R.string.library_attempt_result, result.stars, result.points))
        else -> Text(stringResource(R.string.library_history_details_unavailable))
    }
    Text(when (entry.level?.scoringUnit) {
        ScoringUnit.TIME, null -> stringResource(R.string.library_attempt_time, attempt.activeTimeMs / 1000)
        ScoringUnit.TIME_AND_BACKTRACKS -> stringResource(R.string.library_attempt_tour, attempt.activeTimeMs / 1000, attempt.backtracks)
        ScoringUnit.SCENARIO_MINUTES -> stringResource(R.string.library_attempt_minutes, attempt.scoredMoves, attempt.activeTimeMs / 1000)
        ScoringUnit.PUSHES -> stringResource(R.string.library_attempt_push_detail, attempt.scoredMoves, attempt.activeTimeMs / 1000)
        else -> stringResource(R.string.library_attempt_detail, attempt.scoredMoves, attempt.activeTimeMs / 1000)
    })
    entry.level?.let { TargetText(it) }
    Text(stringResource(R.string.library_attempt_undos, attempt.undoCount))
    if (entry.level?.scoringUnit == ScoringUnit.PUSHES) Text(stringResource(R.string.library_attempt_walking, attempt.walkingSteps))
    if (attempt.hintsUsed) Text(stringResource(R.string.library_hint_used))
}

@Composable
private fun LevelTitle(puzzle: Puzzle, level: LevelDefinition?) {
    Text(if (level == null) puzzleName(puzzle) else stringResource(R.string.library_history_level, puzzleName(puzzle), tierName(level.tier), level.order),
        style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun TargetText(level: LevelDefinition) = Text(stringResource(when (level.scoringUnit) {
    ScoringUnit.TIME, ScoringUnit.TIME_AND_BACKTRACKS -> R.string.library_target_seconds
    ScoringUnit.SCENARIO_MINUTES -> R.string.library_par_minutes
    ScoringUnit.PUSHES -> R.string.library_par_pushes
    else -> R.string.library_par_moves
}, if (level.scoringUnit in listOf(ScoringUnit.TIME, ScoringUnit.TIME_AND_BACKTRACKS)) level.targetTimeMs / 1000 else level.par.toLong()))

@Composable
private fun ProDetails(onPro: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.library_history_pro))
        OutlinedButton(onClick = onPro, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.library_pro_title)) }
    }
}

@Composable
private fun DetailCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
}

@Composable
private fun Metric(text: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(18.dp), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun historyKind(kind: HistoryKind): String = stringResource(when (kind) {
    HistoryKind.ALL -> R.string.library_all_play
    HistoryKind.CAMPAIGN -> R.string.library_campaign
    HistoryKind.DAILY -> R.string.library_daily
})

@Composable
private fun AverageTargets(bests: List<LevelBest>, levels: Map<String, LevelDefinition>) {
    val formats = rememberDisplayFormats()
    val grouped = bests.mapNotNull { best -> levels[best.levelId]?.let { it to best } }.groupBy { it.first.scoringUnit }
    grouped.forEach { (unit, group) ->
        val mean = group.map { (level, best) ->
            if (unit == ScoringUnit.TIME || unit == ScoringUnit.TIME_AND_BACKTRACKS) (best.timeMs - level.targetTimeMs) / 1000.0 else (best.moves - level.par).toDouble()
        }.average()
        val formatted = formats.decimal(mean)
        Text(stringResource(when (unit) {
            ScoringUnit.TIME, ScoringUnit.TIME_AND_BACKTRACKS -> R.string.library_average_time_over_target
            ScoringUnit.SCENARIO_MINUTES -> R.string.library_average_minutes_over_par
            ScoringUnit.PUSHES -> R.string.library_average_pushes_over_par
            else -> R.string.library_average_over_par
        }, formatted))
    }
}
