package com.parlor.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextDirection
import com.parlor.core.design.LocalBoardSkin
import com.parlor.core.model.*
import java.text.NumberFormat

/** Home reads campaign bests only. Daily play and repeated attempts never inflate these counts. */
@Composable
fun PuzzleRoom(catalog: Catalog, bests: List<LevelBest>, simple: Boolean,
               onPuzzle: (Puzzle) -> Unit, onDaily: () -> Unit, onProgress: () -> Unit) {
    val levels = remember(catalog) { catalog.levels.groupBy { it.puzzle } }
    val completed = remember(bests) { bests.map { it.levelId }.toSet() }
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    fun summary(puzzle: Puzzle?): Pair<Int, Int> {
        val group = if (puzzle == null) catalog.levels else levels[puzzle].orEmpty()
        return group.count { it.id in completed } to group.size
    }
    @Composable fun progress(puzzle: Puzzle?) {
        val (done, total) = summary(puzzle)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier
            .testTag(if (puzzle == null) "home-progress" else "home-progress-$puzzle")) {
            Text(stringResource(R.string.library_home_solved, numbers.format(done), numbers.format(total)),
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
            // The adjacent text gives the same information without relying on colour or a second focus stop.
            LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total },
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        progress(null)
        if (Puzzle.TRAFFIC_JAM in levels) {
            Card(modifier = Modifier.fillMaxWidth().testTag("home-traffic-card"), shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface)) {
                if (!simple) RoomIllustration(Modifier.fillMaxWidth().height(200.dp).testTag("home-room-scene"))
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(puzzleName(Puzzle.TRAFFIC_JAM), style = MaterialTheme.typography.headlineMedium.copy(textDirection = TextDirection.Content),
                        modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.library_traffic_description), style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content))
                    progress(Puzzle.TRAFFIC_JAM)
                    Button(onClick = { onPuzzle(Puzzle.TRAFFIC_JAM) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.library_play))
                    }
                }
            }
        }
        FilledTonalButton(onClick = onDaily, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.library_daily)) }
        OutlinedButton(onClick = onProgress, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.library_stats)) }
        val puzzles = levels.keys.filter { it != Puzzle.TRAFFIC_JAM }
        if (puzzles.isNotEmpty()) {
            Text(stringResource(if (simple) R.string.library_home_collection else R.string.library_home_shelves), style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Content),
                modifier = Modifier.semantics { heading() })
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Large type gets a full-width object. Wider phones/tablets form shelves of two or three.
                val columns = (maxWidth.value / (168f * LocalDensity.current.fontScale)).toInt().coerceIn(1, 3)
                val skin = LocalBoardSkin.current
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    puzzles.chunked(columns).forEach { shelf ->
                        Row(Modifier.fillMaxWidth().then(if (simple) Modifier else Modifier.drawBehind {
                            val y = 107.dp.toPx()
                            drawRoundRect(skin.woodAlternate, Offset(0f, y + 3.dp.toPx()), Size(size.width, 8.dp.toPx()), CornerRadius(3.dp.toPx()))
                            drawRoundRect(skin.wood, Offset(0f, y), Size(size.width, 5.dp.toPx()), CornerRadius(3.dp.toPx()))
                        }), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            shelf.forEach { puzzle ->
                                val content: @Composable ColumnScope.() -> Unit = {
                                    if (!simple) PuzzleObject(puzzle, Modifier.fillMaxWidth().height(112.dp).testTag("home-object-$puzzle"))
                                    Column(Modifier.padding(if (simple) 16.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(puzzleName(puzzle), style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content), modifier = Modifier.fillMaxWidth())
                                        Text(stringResource(puzzleDescription(puzzle)), style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content), modifier = Modifier.fillMaxWidth())
                                        progress(puzzle)
                                    }
                                }
                                val modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("home-puzzle-$puzzle")
                                if (simple) OutlinedCard(onClick = { onPuzzle(puzzle) }, modifier = modifier, content = content)
                                else Card(onClick = { onPuzzle(puzzle) }, modifier = modifier,
                                    colors = CardDefaults.cardColors(containerColor = Color.Transparent,
                                        contentColor = MaterialTheme.colorScheme.onSurface), content = content)
                            }
                            repeat(columns - shelf.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.library_prototype_note), style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun puzzleDescription(puzzle: Puzzle) = when (puzzle) {
    Puzzle.TRAFFIC_JAM -> R.string.library_traffic_description
    Puzzle.SLIDING_TILES -> R.string.library_tiles_description
    Puzzle.BLOCK_ESCAPE -> R.string.library_blocks_description
    Puzzle.BOX_PUSHER -> R.string.library_boxes_description
    Puzzle.PEG_SOLITAIRE -> R.string.library_pegs_description
    Puzzle.QUEENS -> R.string.library_queens_description
    Puzzle.KNIGHTS_TOUR -> R.string.library_knight_description
    Puzzle.CROSSINGS -> R.string.library_crossings_description
    Puzzle.HANOI -> R.string.library_hanoi_description
    Puzzle.SWITCH_GRID -> R.string.library_switch_description
    Puzzle.WATER_JUGS -> R.string.library_jugs_description
    Puzzle.PANCAKE_FLIP -> R.string.library_pancake_description
}
