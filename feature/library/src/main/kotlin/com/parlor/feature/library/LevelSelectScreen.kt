package com.parlor.feature.library

import com.parlor.core.design.FoldAwareAlertDialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.parlor.core.model.*
import com.parlor.core.progress.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** All four tiers remain in one bounded, saveable list; ads never cover its cards. */
@Composable
fun LevelSelectScreen(catalog: Catalog, puzzle: Puzzle, bests: List<LevelBest>,
                      temporaryUnlocks: Map<String, Long>, pro: Boolean,
                      onPlay: (LevelDefinition) -> Unit, onTutorial: (Puzzle) -> Unit,
                      onPro: () -> Unit, onBack: () -> Unit,
                      onLockedLevel: ((LevelDefinition) -> Unit)? = null,
                      banner: @Composable () -> Unit = {},
                      wallTime: () -> Long = System::currentTimeMillis) {
    val tiers = remember(catalog, puzzle) {
        Tier.entries.associateWith { tier -> catalog.levels.filter { it.puzzle == puzzle && it.tier == tier }.sortedBy { it.order } }
    }
    val bestById = remember(bests) { bests.associateBy { it.levelId } }
    val tierIndices = remember(tiers) {
        var index = 3 // Title, description/count, tutorial.
        Tier.entries.associateWith { tier -> index.also { index += 1 + tiers.getValue(tier).size.coerceAtLeast(1) } }
    }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var chooseTier by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var accessTime by remember { mutableLongStateOf(wallTime()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(temporaryUnlocks, lifecycle, wallTime) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                accessTime = wallTime()
                val nextExpiry = temporaryUnlocks.values.filter { it > accessTime }.minOrNull() ?: awaitCancellation()
                delay((nextExpiry - accessTime).coerceIn(1L, 60_000L))
            }
        }
    }
    fun access(level: LevelDefinition, time: Long) =
        Progression.access(level, catalog.levels, bests, true, temporaryUnlocks[level.id], time)
    fun open(level: LevelDefinition) {
        // A card can remain visible across a 24-hour expiry; never trust its earlier label.
        accessTime = wallTime()
        when (val current = access(level, accessTime)) {
            Access.AVAILABLE -> onPlay(level)
            Access.PRO -> onLockedLevel?.invoke(level) ?: onPro()
            else -> notice = current.name
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.library_back)) }
            OutlinedButton(onClick = { chooseTier = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("level-difficulty")) {
                Text(stringResource(R.string.library_difficulty))
            }
        }
        LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().testTag("level-list"),
            contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item("title", contentType = "heading") {
                Text(if (puzzle == Puzzle.TRAFFIC_JAM) stringResource(R.string.library_level_title) else puzzleName(puzzle),
                    modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
            }
            item("description", contentType = "description") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.library_level_subtitle), style = MaterialTheme.typography.bodyLarge)
                    val count = tiers.values.sumOf { it.size }
                    Text(pluralStringResource(R.plurals.library_level_count, count, count))
                }
            }
            item("tutorial", contentType = "action") {
                TextButton(onClick = { onTutorial(puzzle) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.library_how_to_play_puzzle, puzzleName(puzzle)))
                }
            }
            Tier.entries.forEach { tier ->
                item("tier-$tier", contentType = "heading") {
                    Text(tierName(tier), Modifier.testTag("level-tier-$tier").semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                }
                if (tiers.getValue(tier).isEmpty()) item("empty-$tier", contentType = "description") {
                    Text(stringResource(R.string.library_levels_empty))
                }
                items(tiers.getValue(tier), key = { "level-${it.id}" }, contentType = { "level" }) { level ->
                    LevelCard(level, bestById[level.id], access(level, accessTime)) { open(level) }
                }
            }
        }
        banner()
    }
    if (chooseTier) FoldAwareAlertDialog(onDismissRequest = { chooseTier = false },
        title = { Text(stringResource(R.string.library_choose_difficulty)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Tier.entries.forEach { tier ->
                TextButton(onClick = {
                    chooseTier = false
                    scope.launch { list.scrollToItem(tierIndices.getValue(tier)) }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("level-jump-$tier")) { Text(tierName(tier)) }
            }
        } },
        confirmButton = { TextButton(onClick = { chooseTier = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.library_close)) } })
    if (notice != null) FoldAwareAlertDialog(onDismissRequest = { notice = null },
        title = { Text(stringResource(R.string.library_level_locked)) },
        text = { Text(stringResource(if (notice == Access.PREVIOUS_TIER.name) R.string.library_previous_tier else R.string.library_previous_level),
            modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { notice = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.library_close)) } })
}

@Composable
private fun LevelCard(level: LevelDefinition, best: LevelBest?, access: Access, onClick: () -> Unit) {
    val number = stringResource(R.string.library_level, level.order)
    val label = stringResource(R.string.library_tier_level, tierName(level.tier), number)
    val stars = best?.stars ?: 0
    val starLabel = stringResource(R.string.library_level_stars, stars, 3)
    val availability = stringResource(when (access) {
        Access.PRO -> R.string.library_pro_level
        Access.PREVIOUS_LEVEL -> R.string.library_previous_level
        Access.PREVIOUS_TIER -> R.string.library_previous_tier
        Access.AVAILABLE -> R.string.library_new_level
    })
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .testTag("level-${level.id}").semantics { stateDescription = availability }, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(number, Modifier.weight(1f).semantics { contentDescription = label }, style = MaterialTheme.typography.titleMedium)
                Text("★".repeat(stars) + "☆".repeat(3 - stars), Modifier.clearAndSetSemantics { contentDescription = starLabel }, color = MaterialTheme.colorScheme.primary)
            }
            BoardDescription(level.initial)
            if (best != null) Text(when (level.scoringUnit) {
                ScoringUnit.TIME, ScoringUnit.TIME_AND_BACKTRACKS -> stringResource(R.string.library_best_seconds, best.timeMs / 1000)
                ScoringUnit.SCENARIO_MINUTES -> stringResource(R.string.library_best_minutes, best.moves)
                ScoringUnit.PUSHES -> stringResource(R.string.library_best_pushes, best.moves)
                ScoringUnit.MOVES -> stringResource(R.string.library_best_moves, best.moves)
            })
            Text(availability, Modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
private fun BoardDescription(board: BoardState) {
    when (board) {
        is PegBoard -> Text(stringResource(when (board.layout) { PegLayout.ENGLISH -> R.string.library_peg_english; PegLayout.EUROPEAN -> R.string.library_peg_european; PegLayout.TRIANGLE -> R.string.library_peg_triangle; PegLayout.DIAMOND -> R.string.library_peg_diamond }))
        is QueensBoard -> Text(stringResource(R.string.library_queens_board, board.side, board.fixed.size))
        is KnightBoard -> Text(stringResource(if (board.closed) R.string.library_knight_closed else R.string.library_knight_open, board.side))
        is CrossingBoard -> Text(stringResource(when (board.scenario) { CrossingScenario.FOOD_FERRY -> R.string.library_cross_food; CrossingScenario.MISSIONARIES -> R.string.library_cross_missionaries; CrossingScenario.COUPLES -> R.string.library_cross_couples; CrossingScenario.BRIDGE -> R.string.library_cross_bridge }))
        is TilesBoard -> Text(stringResource(if (board.art == TileArt.NUMBERS) R.string.library_grid_board else R.string.library_tile_picture_board, board.side))
        is BoxPusherBoard -> Text(pluralStringResource(R.plurals.library_boxes_board, board.boxes.size, board.boxes.size, board.width, board.height))
        is BlockEscapeBoard -> Text(stringResource(R.string.library_block_board, board.width, board.height))
        is HanoiBoard -> Text(stringResource(R.string.library_hanoi_board, board.pegs.flatten().size, board.pegs.size))
        is SwitchBoard -> Text(stringResource(R.string.library_grid_board, board.side))
        is JugsBoard -> Text(stringResource(R.string.library_jugs_board, board.capacities.size, board.target))
        is PancakeBoard -> Text(stringResource(R.string.library_pancake_board, board.stack.size))
        else -> Unit
    }
}
