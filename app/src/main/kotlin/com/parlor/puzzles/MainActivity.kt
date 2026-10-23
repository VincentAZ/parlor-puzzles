package com.parlor.puzzles

import com.parlor.core.design.FoldAwareAlertDialog
import com.parlor.core.design.ProvideWindowFolds
import com.parlor.core.design.FoldSafePane

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.SystemClock
import android.content.Intent
import androidx.core.net.toUri
import android.content.ActivityNotFoundException
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
import com.parlor.core.platform.ads.AdPlacement
import com.parlor.core.platform.ads.ResultsAds
import com.parlor.core.platform.ads.RewardAds
import com.parlor.core.platform.ads.RewardMoment
import com.parlor.core.platform.reminders.AndroidReminderDevice
import com.parlor.feature.library.*
import com.parlor.feature.play.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus
import java.time.Instant
import java.time.ZoneId
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
        (application as ParlorApplication).ads.placement(AdPlacement.RECOVERY)
        setContent {
            val app = application as ParlorApplication
            val storage by app.storageHealth.state.collectAsStateWithLifecycle()
            key(storage.generation) {
                val saved by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
                val owner by app.billing.state.collectAsStateWithLifecycle()
                val dark = (saved?.appearance ?: Appearance.LIGHT).isDark(isSystemInDarkTheme())
                SideEffect {
                    val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                }
                ParlorTheme(RoomTheme.displayed(saved?.theme ?: RoomTheme.LINEN.id, owner.isPro), dark) {
                    ProvideWindowFolds(this) {
                        Surface(Modifier.fillMaxSize().testTag("app-surface")) {
                            val issue = storage.issue
                            if (issue == null) ParlorApp(app, this)
                            else FoldSafePane {
                                SideEffect { app.ads.placement(AdPlacement.RECOVERY) }
                                val reset by app.settingsReset.collectAsStateWithLifecycle()
                                if (issue == StorageIssue(StorageArea.PROGRESS, StorageProblem.DAMAGED))
                                    DatabaseRecoveryRoute(app.databaseRecovery, owner.isPro, app.storageHealth::retry, this::finish)
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
    override fun onResume() {
        super.onResume()
        (application as ParlorApplication).let { it.billing.foreground(true); it.ads.foreground(this, true) }
    }
    override fun onPause() {
        (application as ParlorApplication).let { it.ads.foreground(this, false); it.billing.foreground(false) }
        super.onPause()
    }
    override fun onDestroy() { (application as ParlorApplication).ads.detach(this); super.onDestroy() }
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
    val billing by app.billing.state.collectAsStateWithLifecycle()
    val consent by app.ads.consent.collectAsStateWithLifecycle()
    val adsReady by app.ads.enabled.collectAsStateWithLifecycle()
    val rewardDelivery by app.rewardDelivery.state.collectAsStateWithLifecycle()
    val backup by app.backups.state.collectAsStateWithLifecycle()
    LaunchedEffect(backup.phase) { if (backup.phase == BackupPhase.IMPORTED) app.billing.requestRefresh() }
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
    val serviceUiScope = remember(scope) { scope + Dispatchers.Main.immediate }
    val nav = rememberNavController()
    val rewards = remember(app, serviceUiScope) {
        RewardAds(serviceUiScope, app.rewardDelivery, app.ads::loadRewarded, { app.ads.mayPresentReward },
            app.progress::rewardAvailability, moment = {
                val at = System.currentTimeMillis()
                RewardMoment(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate().toString(), at)
            }, newId = { "ad-reward:${UUID.randomUUID()}" })
    }
    val rewardState by rewards.state.collectAsStateWithLifecycle()
    var lockedLevelId by rememberSaveable { mutableStateOf<String?>(null) }
    var rewardActionBusy by remember { mutableStateOf(false) }
    var rewardActionError by remember { mutableStateOf(false) }
    DisposableEffect(rewards) { onDispose { rewards.cancel() } }
    LaunchedEffect(rewardState?.reward) { rewardActionError = false }
    var finishUpsell by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val resultsAds = remember(app, serviceUiScope) { ResultsAds(serviceUiScope, app.ads::takeInterstitial,
        mayPresent = { app.ads.mayPresentResultsAd },
        claim = { id, ready -> app.progress.claimInterstitial(id, ready, SystemClock.elapsedRealtime(), app.ads.launchedElapsedMs, app.ads.bootMarker) },
        dismissed = { app.progress.interstitialDismissed(it, SystemClock.elapsedRealtime(), app.ads.bootMarker) },
        mayOfferPro = { app.billing.state.value.permitsAds && app.ads.currentPlacement.value == AdPlacement.RESULTS && !app.ads.consent.value.showing }) }
    fun completed() {
        serviceUiScope.launch {
            if (app.ads.currentPlacement.value != AdPlacement.RESULTS || !app.billing.state.value.permitsAds || app.ads.consent.value.showing) return@launch
            val claimed = try { app.progress.claimTenCompletionPrompt() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { false }
            if (claimed && app.ads.currentPlacement.value == AdPlacement.RESULTS && app.billing.state.value.permitsAds && !app.ads.consent.value.showing)
                finishUpsell = { }
        }
    }
    LaunchedEffect(retries) {
        loadError = false
        try {
            app.billing.awaitLocalState()
            val loaded = app.content.load()
            app.progress.configure(loaded, app.content.dailySchedule())
            app.backups.finishPendingSettings()
            app.progress.refreshDay()
            app.rewardDelivery.retry()
            catalog = loaded
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { loadError = true }
    }
    LaunchedEffect(catalog, lifecycle) {
        if (catalog == null) return@LaunchedEffect
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            app.rewardDelivery.retry()
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
                SideEffect { if (nav.currentBackStackEntry == entry) app.ads.placement(AdPlacement.THEMES) }
                ThemesScreen(preferences, billing.isPro, isSystemInDarkTheme(),
                    onTheme = { app.settings.selectTheme(it) { app.billing.hasPro } },
                    onAppearance = { value -> app.settings.update { it.copy(appearance = value) } },
                    onPro = { nav.navigate("pro") }, onBack = { nav.popBackStack() })
            }
        }
        composable("photos") { entry ->
            FoldSafePane {
                SideEffect { if (nav.currentBackStackEntry == entry) app.ads.placement(AdPlacement.PHOTOS) }
                PhotosScreen(onDelete = app.backups::deletePhotos, onBack = { nav.popBackStack() })
            }
        }
        composable("reminders") { entry ->
            FoldSafePane {
                SideEffect { if (nav.currentBackStackEntry == entry) app.ads.placement(AdPlacement.REMINDERS) }
                RemindersRoute(app.reminders) { nav.popBackStack() }
            }
        }
        composable("backup") { entry ->
            FoldSafePane {
                SideEffect { if (nav.currentBackStackEntry == entry) app.ads.placement(AdPlacement.BACKUP) }
                BackupRoute(app.backups, billing.isPro, onBack = { nav.popBackStack() }, onPro = { nav.navigate("pro") })
            }
        }
        composable("pro") { entry ->
            FoldSafePane {
                SideEffect { if (nav.currentBackStackEntry == entry) app.ads.placement(AdPlacement.PRO) }
                var redeemError by remember { mutableStateOf(false) }
                ProScreen(billing,onBack = { nav.popBackStack() },onBuy = { app.billing.buy(activity) },
                    onRestore = { app.billing.requestRefresh(restoring = true) },onRedeem = {
                        try { activity.startActivity(Intent(Intent.ACTION_VIEW,"https://play.google.com/redeem".toUri())) }
                        catch(_: ActivityNotFoundException) { redeemError = true }
                    })
                if(redeemError) FoldAwareAlertDialog(onDismissRequest = { redeemError = false },title = { Text(stringResource(R.string.redeem_unavailable)) },
                    confirmButton = { TextButton(onClick = { redeemError = false }) { Text(stringResource(R.string.close)) } })
            }
        }
        composable("demonstration/{mode}/{key}") { entry ->
            SideEffect { if (nav.currentBackStackEntry == entry) app.ads.placement(AdPlacement.TUTORIAL) }
            if (!billing.isPro) {
                LaunchedEffect(Unit) { nav.popBackStack() }
                return@composable
            }
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
                LibraryScreen(content, bests, resume, player, preferences, dailies, remember(app.progress) { app.progress::historyPage }, today, billing.isPro, dailyError || dayError,
                    onDaily = { date -> app.ads.placement(AdPlacement.PLAY); nav.navigate("daily/$date") },
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
                    onPlay = { level -> app.ads.placement(AdPlacement.PLAY); nav.navigate("play/${level.id}") },
                    onTutorial = { puzzle -> app.ads.placement(AdPlacement.TUTORIAL); nav.navigate("tutorial/${puzzle.name}") },
                    onPro = { app.ads.placement(AdPlacement.PRO); nav.navigate("pro") },
                    onPlacement = { if (nav.currentBackStackEntry == entry) app.ads.placement(it) }, banner = { AdBanner(app.ads, activity, it) },
                    onPrivacyOptions = app.ads::privacyOptions, privacyRequired = consent.snapshot.privacyRequired,
                    privacyBusy = consent.refreshing || consent.showing, privacyError = consent.error,
                    onLockedLevel = { level ->
                        if (level.tier >= Tier.HARD) lockedLevelId = level.id
                        else { app.ads.placement(AdPlacement.PRO); nav.navigate("pro") }
                    },
                    onRewardDaily = { date -> rewards.offer(Reward.DailyRetry(date)) },
                    rewardRecovery = { RewardRecoveryCard(rewardDelivery, rewards::retryDelivery) },
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
                SideEffect { if (nav.currentBackStackEntry == entry) app.ads.placement(AdPlacement.TUTORIAL) }
                PuzzleTutorial(level.puzzle, preferences, app.feedback,
                    onDone = { updateSettings { it.copy(tutorialsSeen = it.tutorialsSeen + level.puzzle) } },
                    onBack = { nav.popBackStack() })
                return@composable
            }
            val vm: PlayViewModel = viewModel(viewModelStoreOwner = entry, factory = PlayViewModel.factory(level, app.progress, date))
            PlayScreen(vm, preferences, player.ledger.dailyHints + player.ledger.earnedHints,
                dailies.any { it.record.date == date && it.record.completions > 0 }, app.feedback,
                onLeave = { nav.popBackStack() }, onNext = null, pro = billing.isPro,
                onResultsBack = {
                    nav.getBackStackEntry("library").savedStateHandle["openDestination"] = "daily"
                    nav.popBackStack("library", false)
                },
                onDemonstrate = { nav.navigate("demonstration/daily/$date") },
                onPlacement = { if (nav.currentBackStackEntry == entry) app.ads.placement(it) }, banner = { AdBanner(app.ads, activity, AdPlacement.RESULTS) },
                externalPause = consent.showing || rewardState != null, onLevelStarted = app.ads::levelStarted, onCompleted = ::completed,
                onResultsLeave = { attempt, navigate -> resultsAds.leave(attempt.id, navigate) { finishUpsell = it } },
                onRewardHints = { rewards.offer(Reward.Hints) }, onPhotoPro = { nav.navigate("pro") })
        }
        composable("tutorial/{puzzle}") { entry ->
            SideEffect { if (nav.currentBackStackEntry == entry) app.ads.placement(AdPlacement.TUTORIAL) }
            val puzzle = Puzzle.valueOf(requireNotNull(entry.arguments?.getString("puzzle")))
            PuzzleTutorial(puzzle, preferences, app.feedback, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
        }
        composable("play/{levelId}") { entry ->
            val level = content.levels.firstOrNull { it.id == entry.arguments?.getString("levelId") }
            if (level == null) { LaunchedEffect(Unit) { nav.popBackStack() }; return@composable }
            if (level.puzzle !in preferences.tutorialsSeen) {
                SideEffect { if (nav.currentBackStackEntry == entry) app.ads.placement(AdPlacement.TUTORIAL) }
                PuzzleTutorial(level.puzzle, preferences, app.feedback,
                    onDone = { updateSettings { it.copy(tutorialsSeen = it.tutorialsSeen + level.puzzle) } },
                    onBack = { nav.popBackStack() })
                return@composable
            }
            val vm: PlayViewModel = viewModel(viewModelStoreOwner = entry, factory = PlayViewModel.factory(level, app.progress))
            val next = content.levels.filter { it.puzzle == level.puzzle }.sortedWith(compareBy({ it.tier }, { it.order }))
                .dropWhile { it.id != level.id }.drop(1).firstOrNull()?.takeIf {
                    Progression.access(it, content.levels, bests, billing.isPro, player.ledger.temporaryUnlocks[it.id], System.currentTimeMillis()) == Access.AVAILABLE
                }
            PlayScreen(vm, preferences, player.ledger.dailyHints + player.ledger.earnedHints,
                bests.any { it.levelId == level.id }, app.feedback,
                onLeave = { nav.popBackStack() }, onNext = next?.let { nextLevel -> {
                    app.ads.placement(AdPlacement.PLAY)
                    nav.navigate("play/${nextLevel.id}") { popUpTo("play/{levelId}") { inclusive = true } }
                } }, pro = billing.isPro, onDemonstrate = { nav.navigate("demonstration/campaign/${level.id}") },
                onResultsBack = {
                    nav.getBackStackEntry("library").savedStateHandle["openDestination"] = "levels/${level.puzzle.name}"
                    nav.popBackStack("library", false)
                },
                onPlacement = { if (nav.currentBackStackEntry == entry) app.ads.placement(it) }, banner = { AdBanner(app.ads, activity, AdPlacement.RESULTS) },
                externalPause = consent.showing || rewardState != null, onLevelStarted = app.ads::levelStarted, onCompleted = ::completed,
                onResultsLeave = { attempt, navigate -> resultsAds.leave(attempt.id, navigate) { finishUpsell = it } },
                onRewardHints = { rewards.offer(Reward.Hints) }, onPhotoPro = { nav.navigate("pro") })
        }
    }
    LaunchedEffect(activity.reminderRequest, preferences.onboarded) {
        val request = activity.reminderRequest
        if (request != null && preferences.onboarded) {
            app.ads.placement(AdPlacement.PLAY)
            nav.navigate("daily/${app.progress.localDate()}") { launchSingleTop = true }
            activity.consumeReminder(request)
        }
    }
    lockedLevelId?.let { id ->
        val level = content.levels.find { it.id == id }
        if (level == null || billing.isPro) { LaunchedEffect(id, billing.isPro) { lockedLevelId = null } }
        else FoldAwareAlertDialog(onDismissRequest = { lockedLevelId = null },
            title = { Text(stringResource(R.string.reward_locked_title, puzzleTitle(level.puzzle), level.order)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.reward_locked_body))
                    OutlinedButton(onClick = { lockedLevelId = null; rewards.offer(Reward.LevelAccess(id)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.reward_locked_offer))
                    }
                }
            },
            confirmButton = { TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
                lockedLevelId = null; app.ads.placement(AdPlacement.PRO); nav.navigate("pro")
            }) { Text(stringResource(R.string.pro_prompt_explore)) } },
            dismissButton = { TextButton(onClick = { lockedLevelId = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.close)) } })
    }
    rewardState?.let { offered ->
        RewardDialog(offered, rewardDelivery, adsReady && app.ads.mayPresentReward,
            player.ledger.rewardedViews.takeIf { player.ledger.latestResetDate == today } ?: 0,
            rewardActionBusy, rewardActionError, rewards::watch, rewards::cancel, rewards::retryDelivery, onContinue = {
                if (!rewardActionBusy) {
                    rewardActionBusy = true; rewardActionError = false
                    serviceUiScope.launch {
                        try {
                            when (val reward = offered.reward) {
                                Reward.Hints -> rewards.cancel()
                                is Reward.LevelAccess -> {
                                    check(content.levels.any { it.id == reward.levelId })
                                    rewards.cancel(); app.ads.placement(AdPlacement.PLAY); nav.navigate("play/${reward.levelId}")
                                }
                                is Reward.DailyRetry -> {
                                    app.progress.startScoredDailyRetry(reward.date)
                                    rewards.cancel(); app.ads.placement(AdPlacement.PLAY); nav.navigate("daily/${reward.date}")
                                }
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { rewardActionError = true }
                        finally { rewardActionBusy = false }
                    }
                }
            })
    }
    finishUpsell?.let { finish ->
        if (billing.isPro) { LaunchedEffect(Unit) { finishUpsell = null; finish(true) } }
        else FoldAwareAlertDialog(onDismissRequest = { finishUpsell = null; finish(true) },
            title = { Text(stringResource(R.string.pro_prompt_title)) },
            text = { Text(stringResource(R.string.pro_prompt_body)) },
            confirmButton = { TextButton(onClick = {
                finishUpsell = null; finish(false); app.ads.placement(AdPlacement.PRO); nav.navigate("pro")
            }) { Text(stringResource(R.string.pro_prompt_explore)) } },
            dismissButton = { TextButton(onClick = { finishUpsell = null; finish(true) }) { Text(stringResource(R.string.pro_prompt_continue)) } })
    }
}
