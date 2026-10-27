package com.parlor.puzzles

import com.parlor.core.design.FoldAwareAlertDialog
import com.parlor.core.design.ProvideWindowFolds
import com.parlor.core.design.FoldSafePane

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.parlor.core.data.PlayerSummary
import com.parlor.core.data.DailyUnavailable
import com.parlor.core.data.BackupPhase
import com.parlor.core.data.StorageArea
import com.parlor.core.data.StorageIssue
import com.parlor.core.data.StorageProblem
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import com.parlor.core.model.*
import com.parlor.core.progress.*
import com.parlor.core.platform.reminders.AndroidReminderDevice
import com.parlor.feature.library.*
import com.parlor.feature.play.*
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {
    var reminderRequest by mutableStateOf<String?>(null)
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reminderRequest = savedInstanceState?.getString("reminderRequest")
        if (savedInstanceState == null) takeReminderIntent(intent)
        requestedOrientation = if (resources.configuration.smallestScreenWidthDp < 600) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        enableEdgeToEdge()

        setContent {
            val app = application as ParlorApplication
            val storage by app.storageHealth.state.collectAsStateWithLifecycle()
            key(storage.generation) {
                val saved by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
                val dark = (saved?.appearance ?: Appearance.LIGHT).isDark(isSystemInDarkTheme())
                SideEffect {
                    val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                }
                ParlorTheme(RoomTheme.displayed(saved?.theme ?: RoomTheme.LINEN.id, true), dark) {
                    ProvideWindowFolds(this) {
                        Surface(Modifier.fillMaxSize().testTag("app-surface")) {
                            val issue = storage.issue
                            if (issue == null) ParlorApp(app, this)
                            else FoldSafePane {

                                val reset by app.settingsReset.collectAsStateWithLifecycle()
                                if (issue == StorageIssue(StorageArea.PROGRESS, StorageProblem.DAMAGED))
                                    DatabaseRecoveryRoute(app.databaseRecovery, true, app.storageHealth::retry, this::finish)
                                else StorageRecoveryScreen(issue, reset, app.storageHealth::retry,
                                    app::resetUnreadableSettings, this::finish)
                            }
                        }
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); takeReminderIntent(intent) }
    private fun takeReminderIntent(incoming: Intent) {
        if (incoming.action == AndroidReminderDevice.OPEN_DAILY) {
            reminderRequest = UUID.randomUUID().toString()
        }
    }
    fun consumeReminder(request: String) { if (reminderRequest == request) reminderRequest = null }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("reminderRequest", reminderRequest); super.onSaveInstanceState(outState) }
    override fun onStart() { super.onStart(); (application as ParlorApplication).reminderVisibility(this, true) }
    override fun onStop() { (application as ParlorApplication).reminderVisibility(this, false); super.onStop() }

}

@Composable
private fun ParlorApp(app: ParlorApplication, activity: MainActivity) {
    var catalog by remember { mutableStateOf<Catalog?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var retries by remember { mutableIntStateOf(0) }
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val bests by app.progress.bests.collectAsStateWithLifecycle(initialValue = emptyList())
    val resume by app.progress.resume.collectAsStateWithLifecycle(initialValue = null)
    val player by app.progress.player.collectAsStateWithLifecycle(initialValue = PlayerSummary())
    val dailies by app.progress.dailies.collectAsStateWithLifecycle(initialValue = emptyList())
    val backup by app.backups.state.collectAsStateWithLifecycle()
    var today by remember { mutableStateOf(app.progress.localDate()) }
    LaunchedEffect(player.ledger.creditedDates, today) { app.reminders.refresh() }
    var dailyError by remember { mutableStateOf(false) }
    var dayError by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    fun updateSettings(transform: (PlayerSettings) -> PlayerSettings) {
        val generation = app.storageHealth.state.value.generation
        scope.launch {
            try { app.settings.update(transform) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { app.storageHealth.failed(StorageArea.SETTINGS, failure, generation) }
        }
    }
    val nav = rememberNavController()
    LaunchedEffect(retries) {
        loadError = false
        try {
            val loaded = app.content.load()
            app.progress.configure(loaded, app.content.dailySchedule())
            app.backups.finishPendingSettings()
            app.progress.refreshDay()
            catalog = loaded
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { loadError = true }
    }
    LaunchedEffect(catalog, lifecycle) {
        if (catalog == null) return@LaunchedEffect
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var refreshedDate: String? = null
            while (true) {
                val current = app.progress.localDate()
                today = current
                if (current != refreshedDate) {
                    try {
                        app.progress.refreshDay()
                        dayError = false
                        refreshedDate = current
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { dayError = true }
                }
                delay(30_000)
            }
        }
    }
    val content = catalog
    val preferences = settings
    ReportDrawnWhen { content != null && preferences != null }
    if (backup.phase == BackupPhase.FINISH_REQUIRED || backup.phase == BackupPhase.IMPORTING) {
        FoldSafePane { BackupFinishScreen(working = backup.phase == BackupPhase.IMPORTING) { retries++ } }
        return
    }
    if (content == null || preferences == null) {
        FoldSafePane { Column(Modifier.safeDrawingPadding().padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
            if (loadError) {
                Text(stringResource(R.string.open_error))
                Button(onClick = { retries++ }) { Text(stringResource(R.string.retry)) }
            } else CircularProgressIndicator()
        } }
        return
    }
    NavHost(navController = nav, startDestination = "library") {
        composable("themes") { entry ->
            FoldSafePane {

                ThemesScreen(preferences, true, isSystemInDarkTheme(),
                    onTheme = { app.settings.selectTheme(it) { true } },
                    onAppearance = { value -> app.settings.update { it.copy(appearance = value) } },
                    onPro = {}, onBack = { nav.popBackStack() })
            }
        }
        composable("photos") { entry ->
            FoldSafePane {

                PhotosScreen(onDelete = app.backups::deletePhotos, onBack = { nav.popBackStack() })
            }
        }
        composable("reminders") { entry ->
            FoldSafePane {

                RemindersRoute(app.reminders) { nav.popBackStack() }
            }
        }
        composable("backup") { entry ->
            FoldSafePane {

                BackupRoute(app.backups, true, onBack = { nav.popBackStack() }, onPro = {})
            }
        }
        composable("demonstration/{mode}/{key}") { entry ->

            val key = entry.arguments?.getString("key")
            val daily = entry.arguments?.getString("mode") == "daily"
            val level = if (daily) dailies.find { it.record.date == key }?.level else content.levels.find { it.id == key }
            if (level == null) {
                LaunchedEffect(Unit) { nav.popBackStack() }
                return@composable
            }
            DemonstrationScreen(level, preferences, onBack = { nav.popBackStack() })
        }
        composable("library") { entry ->
            FoldSafePane {
                val requestedDestination by entry.savedStateHandle.getStateFlow<String?>("openDestination", null).collectAsStateWithLifecycle()
                LibraryScreen(content, bests, resume, player, preferences, dailies, remember(app.progress) { app.progress::historyPage }, today, true, dailyError || dayError,
                    onDaily = { date -> nav.navigate("daily/$date") },
                    onDailyRetry = {
                        scope.launch {
                            dailyError = false
                            try { app.progress.refreshDay(); app.progress.dailyEntry(today); dayError = false }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { dailyError = true }
                        }
                    },
                    resetProgress = { puzzle -> app.progress.resetProgress(puzzle) },
                    updateSettings = ::updateSettings,
                    onPlay = { level -> nav.navigate("play/${level.id}") },
                    onTutorial = { puzzle -> nav.navigate("tutorial/${puzzle.name}") },
                    onPro = {},
                    onBackup = { nav.navigate("backup") }, onReminders = { nav.navigate("reminders") }, onPhotos = { nav.navigate("photos") },
                    onThemes = { nav.navigate("themes") }, requestedDestination = requestedDestination,
                    onDestinationOpened = { entry.savedStateHandle["openDestination"] = null })
            }
        }
        composable("daily/{date}") { entry ->
            val date = requireNotNull(entry.arguments?.getString("date"))
            var daily by remember(date) { mutableStateOf<DailyEntry?>(null) }
            var error by remember(date) { mutableStateOf(false) }
            var expired by remember(date) { mutableStateOf(false) }
            var retry by remember(date) { mutableIntStateOf(0) }
            LaunchedEffect(date, retry) {
                error = false
                try { daily = app.progress.dailyEntry(date) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: DailyUnavailable) { expired = true }
                catch (_: Exception) { error = true }
            }
            val challenge = daily
            if (challenge == null) {
                FoldSafePane { Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick = { nav.popBackStack() }) { Text(stringResource(R.string.back)) }
                    if (error || expired) {
                        Text(stringResource(if (expired) R.string.daily_expired else R.string.open_error))
                        if (!expired) Button(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
                    } else CircularProgressIndicator()
                } }
                return@composable
            }
            val level = challenge.level
            if (level.puzzle !in preferences.tutorialsSeen) {

                PuzzleTutorial(level.puzzle, preferences, app.feedback,
                    onDone = { updateSettings { it.copy(tutorialsSeen = it.tutorialsSeen + level.puzzle) } },
                    onBack = { nav.popBackStack() })
                return@composable
            }
            val vm: PlayViewModel = viewModel(viewModelStoreOwner = entry, factory = PlayViewModel.factory(level, app.progress, date))
            PlayScreen(vm, preferences, player.ledger.dailyHints + player.ledger.earnedHints,
                dailies.any { it.record.date == date && it.record.completions > 0 }, app.feedback,
                onLeave = { nav.popBackStack() }, onNext = null, pro = true,
                onResultsBack = {
                    nav.getBackStackEntry("library").savedStateHandle["openDestination"] = "daily"
                    nav.popBackStack("library", false)
                },
                onDemonstrate = { nav.navigate("demonstration/daily/$date") },
                onPhotoPro = {})
        }
        composable("tutorial/{puzzle}") { entry ->

            val puzzle = Puzzle.valueOf(requireNotNull(entry.arguments?.getString("puzzle")))
            PuzzleTutorial(puzzle, preferences, app.feedback, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
        }
        composable("play/{levelId}") { entry ->
            val level = content.levels.firstOrNull { it.id == entry.arguments?.getString("levelId") }
            if (level == null) { LaunchedEffect(Unit) { nav.popBackStack() }; return@composable }
            if (level.puzzle !in preferences.tutorialsSeen) {

                PuzzleTutorial(level.puzzle, preferences, app.feedback,
                    onDone = { updateSettings { it.copy(tutorialsSeen = it.tutorialsSeen + level.puzzle) } },
                    onBack = { nav.popBackStack() })
                return@composable
            }
            val vm: PlayViewModel = viewModel(viewModelStoreOwner = entry, factory = PlayViewModel.factory(level, app.progress))
            val next = content.levels.filter { it.puzzle == level.puzzle }.sortedWith(compareBy({ it.tier }, { it.order }))
                .dropWhile { it.id != level.id }.drop(1).firstOrNull()?.takeIf {
                    Progression.access(it, content.levels, bests, true, player.ledger.temporaryUnlocks[it.id], System.currentTimeMillis()) == Access.AVAILABLE
                }
            PlayScreen(vm, preferences, player.ledger.dailyHints + player.ledger.earnedHints,
                bests.any { it.levelId == level.id }, app.feedback,
                onLeave = { nav.popBackStack() }, onNext = next?.let { nextLevel -> {
                    nav.navigate("play/${nextLevel.id}") { popUpTo("play/{levelId}") { inclusive = true } }
                } }, pro = true, onDemonstrate = { nav.navigate("demonstration/campaign/${level.id}") },
                onResultsBack = {
                    nav.getBackStackEntry("library").savedStateHandle["openDestination"] = "levels/${level.puzzle.name}"
                    nav.popBackStack("library", false)
                },
                onPhotoPro = {})
        }
    }
    LaunchedEffect(activity.reminderRequest, preferences.onboarded) {
        val request = activity.reminderRequest
        if (request != null && preferences.onboarded) {
            nav.navigate("daily/${app.progress.localDate()}") { launchSingleTop = true }
            activity.consumeReminder(request)
        }
    }
}
