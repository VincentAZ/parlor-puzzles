package com.parlor.feature.library

import com.parlor.core.design.FoldAwareAlertDialog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.DpSize
import com.parlor.core.data.*
import com.parlor.core.model.*
import com.parlor.core.progress.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow

@Composable
fun LibraryScreen(catalog: Catalog, bests: List<LevelBest>, resume: Attempt?, player: PlayerSummary,
                  settings: PlayerSettings, dailies: List<DailyEntry>, history: (HistoryQuery) -> Flow<HistoryPage>, today: String,
                  pro: Boolean, dailyError: Boolean, onDaily: (String) -> Unit, onDailyRetry: () -> Unit,
                  resetProgress: suspend (Puzzle?) -> Unit, updateSettings: ((PlayerSettings) -> PlayerSettings) -> Unit,
                  onPlay: (LevelDefinition) -> Unit, onTutorial: (Puzzle) -> Unit, onPro: () -> Unit,
                  onLockedLevel: ((LevelDefinition) -> Unit)? = null, onRewardDaily: ((String) -> Unit)? = null,
                  rewardRecovery: @Composable () -> Unit = {}, onBackup: (() -> Unit)? = null,
                  onReminders: (() -> Unit)? = null, onPhotos: (() -> Unit)? = null,
                  onThemes: (() -> Unit)? = null, requestedDestination: String? = null,
                  onDestinationOpened: () -> Unit = {}) {
    var backStack by rememberSaveable { mutableStateOf(listOf("home")) }
    val screen = backStack.last()
    var collection by rememberSaveable { mutableStateOf(Puzzle.TRAFFIC_JAM) }
    LaunchedEffect(requestedDestination) {
        if (requestedDestination == "daily") backStack = listOf("home", "daily")
        else if (requestedDestination?.startsWith("levels/") == true) {
            collection = Puzzle.valueOf(requestedDestination.removePrefix("levels/"))
            backStack = listOf("home", "levels")
        }
        if (requestedDestination != null) onDestinationOpened()
    }
    fun navigate(destination: String) { backStack = backStack + destination }
    fun back() { backStack = backStack.dropLast(1).ifEmpty { listOf("home") } }
    var reset by rememberSaveable { mutableStateOf<String?>(null) }
    var resetting by remember { mutableStateOf(false) }
    var resetError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val homeScroll = rememberScrollState()
    val otherScroll = rememberScrollState()
    val scroll = if (screen == "home") homeScroll else otherScroll
    LaunchedEffect(screen) { if (screen != "home") otherScroll.scrollTo(0) }
    LaunchedEffect(screen, today) { if (screen == "daily") onDailyRetry() }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = screen != "home") { back() }
    if (!settings.onboarded) {
        Onboarding { updateSettings { it.copy(onboarded = true) } }
        return
    }
    if (screen == "stats") {
        ProgressScreen(catalog, bests, player, true, history, onPro = { notice = Access.PRO.name },
            onBack = ::back)
        return
    }
    if (screen == "levels") {
        key(collection) {
            LevelSelectScreen(catalog, collection, bests, player.ledger.temporaryUnlocks, true,
                onPlay, onTutorial, onPro, ::back, onLockedLevel)
        }
        return
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)
        .padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            if (screen != "home") TextButton(onClick = { back() }) { Text(stringResource(R.string.library_back)) }
            else Text(stringResource(R.string.library_brand), style = MaterialTheme.typography.headlineMedium)
            if (screen == "home") TextButton(onClick = { navigate("settings") }) { Text(stringResource(R.string.library_settings)) }
        }
        when (screen) {
            "home" -> {
                rewardRecovery()
                Text(stringResource(R.string.library_tagline), style = MaterialTheme.typography.headlineLarge.copy(textDirection = TextDirection.Content))
                Text(stringResource(R.string.library_welcome_body), style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (resume != null && (resume.mode != AttemptMode.DAILY || DailyRules.withinHistory(requireNotNull(resume.challengeDate), today, player.ledger.latestResetDate))) {
                    val resumeLevel = catalog.levels.find { it.id == resume.levelId }
                    FilledTonalButton(onClick = {
                        if (resume.mode == AttemptMode.DAILY) onDaily(requireNotNull(resume.challengeDate))
                        else resumeLevel?.let(onPlay)
                    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.library_resume))
                            val daily=resume.mode==AttemptMode.DAILY
                            val label=if(daily) displayDate(requireNotNull(resume.challengeDate)) else puzzleName(resumeLevel?.puzzle?:Puzzle.TRAFFIC_JAM)
                            val timed=resumeLevel?.scoringUnit in listOf(ScoringUnit.TIME,ScoringUnit.TIME_AND_BACKTRACKS)
                            val resource=when(resumeLevel?.scoringUnit) {
                                ScoringUnit.SCENARIO_MINUTES->if(daily) R.string.library_resume_daily_minutes else R.string.library_resume_minutes
                                ScoringUnit.TIME,ScoringUnit.TIME_AND_BACKTRACKS->if(daily) R.string.library_resume_daily_seconds else R.string.library_resume_seconds
                                ScoringUnit.PUSHES->if(daily) R.string.library_resume_daily_pushes else R.string.library_resume_pushes
                                else->if(daily) R.string.library_resume_daily else R.string.library_resume_puzzle
                            }
                            Text(stringResource(resource,label,if(timed) resume.activeTimeMs/1000 else resume.scoredMoves),style=MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content))
                        }
                    }
                }
                PuzzleRoom(catalog, bests, settings.accessibleHome,
                    onPuzzle = { collection = it; navigate("levels") },
                    onDaily = { navigate("daily") }, onProgress = { navigate("stats") })
            }
            "daily" -> DailyContent(dailies, today, player.ledger, true, dailyError, onDaily, onDailyRetry, onRewardDaily)
            "settings" -> {
                rewardRecovery()
                Text(stringResource(R.string.library_settings_title), style = MaterialTheme.typography.headlineLarge)
                SettingToggle(stringResource(R.string.library_show_timer), stringResource(R.string.library_timer_detail), settings.showTimer) { value -> updateSettings { it.copy(showTimer = value) } }
                SettingToggle(stringResource(R.string.library_reduce_motion), stringResource(R.string.library_motion_detail), settings.reduceMotion) { value -> updateSettings { it.copy(reduceMotion = value) } }
                SettingToggle(stringResource(R.string.library_haptics), stringResource(R.string.library_haptics_detail), settings.haptics) { value -> updateSettings { it.copy(haptics = value) } }
                val sfxLabel = stringResource(R.string.library_sfx_volume)
                val musicLabel = stringResource(R.string.library_music_volume)
                val musicUnavailable = stringResource(R.string.library_music_unavailable)
                Text(sfxLabel, style = MaterialTheme.typography.titleMedium)
                SettingSlider(sfxLabel, settings.sfxVolume, { value -> updateSettings { it.copy(sfxVolume = value) } })
                Text(stringResource(R.string.library_silent_mode))
                Text(musicLabel, style = MaterialTheme.typography.titleMedium)
                SettingSlider(musicLabel, 0f, {}, unavailable = musicUnavailable)
                Text(musicUnavailable)
                SettingToggle(stringResource(R.string.library_simple_home), stringResource(R.string.library_simple_home_detail), settings.accessibleHome) { value -> updateSettings { it.copy(accessibleHome = value) } }
                onThemes?.let { open -> OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.library_themes))
                } }
                onBackup?.let { open -> OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.library_backup))
                } }
                onReminders?.let { open -> OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.library_reminders))
                } }
                onPhotos?.let { open -> OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.library_photos))
                } }
                HorizontalDivider()
                Text(stringResource(R.string.library_reset_heading), style = MaterialTheme.typography.titleLarge)
                catalog.levels.map { it.puzzle }.distinct().forEach { puzzle ->
                    TextButton(onClick = { resetError = false; reset = puzzle.name }) { Text(stringResource(R.string.library_reset_puzzle, puzzleName(puzzle))) }
                }
                OutlinedButton(onClick = { resetError = false; reset = "all" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.library_reset_all)) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    }
    if (reset != null) {
    val resetScroll = rememberScrollState()
    LaunchedEffect(resetError, resetting) { resetScroll.scrollTo(0) }
    FoldAwareAlertDialog(onDismissRequest = { if (!resetting) reset = null },
        title = { Text(stringResource(R.string.library_reset_title, if (reset == "all") stringResource(R.string.library_all_progress) else puzzleName(Puzzle.valueOf(requireNotNull(reset))))) },
        text = { Column(Modifier.verticalScroll(resetScroll).testTag("progress-reset-explanation"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (resetError) Text(stringResource(R.string.library_reset_error), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (resetting) {
                Text(stringResource(R.string.library_reset_busy), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                CircularProgressIndicator()
            }
            Text(stringResource(R.string.library_reset_body))
            Text(stringResource(R.string.library_reset_preserved))
            Text(stringResource(R.string.library_reset_allowances))
        } },
        confirmButton = { TextButton(modifier = Modifier.heightIn(min = 48.dp), enabled = !resetting, onClick = {
            if (resetting) return@TextButton
            val puzzle = reset?.takeIf { it != "all" }?.let(Puzzle::valueOf)
            resetting = true
            resetError = false
            scope.launch {
                try { resetProgress(puzzle); reset = null }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { resetError = true }
                finally { resetting = false }
            }
        }) { Text(stringResource(R.string.library_reset_confirm)) } },
        dismissButton = { TextButton(modifier = Modifier.heightIn(min = 48.dp), enabled = !resetting, onClick = { reset = null }) { Text(stringResource(R.string.library_cancel)) } })
    }

}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SettingSlider(label: String, value: Float, onChange: (Float) -> Unit, unavailable: String? = null) {
    val interaction = remember { MutableInteractionSource() }
    Slider(value = value, onValueChange = onChange, enabled = unavailable == null,
        interactionSource = interaction,
        modifier = Modifier.semantics {
            contentDescription = label
            if (unavailable != null) stateDescription = unavailable
        },
        thumb = { SliderDefaults.Thumb(interactionSource = interaction, enabled = unavailable == null,
            thumbSize = DpSize(4.dp, 48.dp)) })
}

@Composable
private fun SettingToggle(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(body) }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
internal fun tierName(tier: Tier): String = stringResource(when (tier) { Tier.EASY -> R.string.library_easy; Tier.MEDIUM -> R.string.library_medium; Tier.HARD -> R.string.library_hard; Tier.EXPERT -> R.string.library_expert })

@Composable
private fun Onboarding(onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(stringResource(R.string.library_brand), style = MaterialTheme.typography.headlineMedium)
        RoomIllustration(Modifier.fillMaxWidth().height(200.dp))
        Text(stringResource(when (step) { 0 -> R.string.library_onboarding_1_title; 1 -> R.string.library_onboarding_2_title; else -> R.string.library_onboarding_3_title }), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(when (step) { 0 -> R.string.library_onboarding_1_body; 1 -> R.string.library_onboarding_2_body; else -> R.string.library_onboarding_3_body }), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.library_onboarding_step, step + 1, 3), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { if (step < 2) step++ else onDone() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(if (step == 2) R.string.library_begin else R.string.library_continue_label))
        }
    }
}
