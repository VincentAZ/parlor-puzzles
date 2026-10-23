package com.parlor.core.data

import com.parlor.core.game.*
import com.parlor.core.model.*
import com.parlor.core.progress.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerializationException
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PlayerSummary(val ledger: RewardLedger = RewardLedger(), val totalPlayTimeMs: Long = 0, val profile: PlayerProfile = PlayerProfile())
data class DailySession(val level: LevelDefinition, val attempt: Attempt)
class AccessDenied(val reason: Access) : IllegalStateException("Campaign access: $reason")
class DailyUnavailable : IllegalStateException("Daily challenge is outside the saved history window")

/** Actions, history, time, reward claims and completion awards share one transaction. */
class ProgressRepository(
    private val database: ProgressDatabase,
    private val today: () -> String = { LocalDate.now().toString() },
    private val wallTime: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val isPro: () -> Boolean = { false },
    private val storageHealth: StorageHealth? = null,
    private val protection: RecoveryProtectionStore? = null,
) {
    constructor(database: ParlorDatabase, today: () -> String = { LocalDate.now().toString() },
                wallTime: () -> Long = System::currentTimeMillis, newId: () -> String = { UUID.randomUUID().toString() },
                isPro: () -> Boolean = { false }, storageHealth: StorageHealth? = null, protection: RecoveryProtectionStore? = null) :
        this(ProgressDatabase { database }, today, wallTime, newId, isPro, storageHealth, protection)

    private val dao get() = database.current.progress()
    private var protectionInitialized = false
    private var catalog: Catalog? = null
    private var schedule: DailySchedule? = null
    private val foregroundDailies = ConcurrentHashMap<String, String>()
    val bests = database.observe { it.progress().observeBests() }.map { rows -> rows.map { it.best() } }.observeStorage(storageHealth, StorageArea.PROGRESS)
    val resume = database.observe { it.progress().observeResume() }.map { it?.attempt() }.observeStorage(storageHealth, StorageArea.PROGRESS)
    val player = database.observe { it.progress().observePlayer() }.map { PlayerSummary(it?.ledger() ?: RewardLedger(), it?.totalPlayTimeMs ?: 0, it?.profile() ?: PlayerProfile()) }
        .observeStorage(storageHealth, StorageArea.PROGRESS)
    val completionCount = database.observe { it.progress().observeCompletionCount() }.observeStorage(storageHealth, StorageArea.PROGRESS)
    val dailies = database.observe { it.progress().observeDailies() }.map { rows -> rows.map { it.entry() } }.observeStorage(storageHealth, StorageArea.PROGRESS)
    val history = database.observe { it.progress().observeHistory() }.map { rows -> rows.map { it.attempt() } }.observeStorage(storageHealth, StorageArea.PROGRESS)
    /** Collected only while Pro history is visible; at most one page of saved boards is decoded. */
    fun historyPage(query: HistoryQuery): Flow<HistoryPage> = flow {
        if (!isPro()) { emit(HistoryPage(proRequired = true)); return@flow }
        val generation = storageHealth?.state?.value?.generation
        emitAll(database.observe { it.progress().observeHistoryPage(query.puzzle?.name,
            query.kind.takeUnless { kind -> kind == HistoryKind.ALL }?.name,
            query.before?.updatedAt ?: 0, query.before?.id, HistoryPage.SIZE + 1) }.map { rows ->
            if (!isPro()) HistoryPage(proRequired = true) else {
                val page = rows.take(HistoryPage.SIZE)
                HistoryPage(page.map { it.detail(requireNotNull(catalog)) },
                    if (rows.size > HistoryPage.SIZE) page.last().attempt.let { HistoryCursor(it.updatedAt, it.id) } else null)
            }
        }.flowOn(Dispatchers.IO).catch { failure ->
            storageHealth?.failed(StorageArea.PROGRESS, failure, requireNotNull(generation))
            throw failure
        })
    }
    fun observeAttempt(id: String) = database.observe { it.progress().observeAttempt(id) }.map { it?.attempt() }.observeStorage(storageHealth, StorageArea.PROGRESS)
    fun localDate(): String = today()
    fun hasPro(): Boolean = isPro()

    suspend fun photo(id: String): ByteArray? = database.transaction { dao.photo(id)?.jpeg }

    /** The bounded image and its reference commit together; no source URI is retained. */
    suspend fun setPhoto(level: LevelDefinition, attemptId: String, photo: PuzzlePhoto?): Attempt = database.transaction {
        check(dao.pendingSettings() == null) { "Finish the earlier import first" }
        val previous = requireNotNull(dao.attempt(attemptId)).attempt()
        require(previous.levelId == level.id && previous.levelRevision == level.revision && previous.board is TilesBoard)
        require(previous.mode == AttemptMode.CAMPAIGN && !previous.completed)
        if (photo != null) {
            check(isPro()) { "Pro is required" }
            dao.savePhoto(PhotoRow(photo.id, PhotoCodec.validate(photo)))
        }
        val next = previous.copy(photoId = photo?.id)
        dao.saveAttempt(next.row(level.puzzle))
        prunePhotos()
        check(dao.photoBytes() <= PhotoLimits.TOTAL_BYTES) { "Saved photo storage is full" }
        check(photo == null || isPro()) { "Pro changed before photo committed" }
        next
    }

    /** Privacy deletion also edits the local recovery copy, without changing moves, scores or claims. */
    suspend fun deleteAllPhotos() = database.transaction {
        check(dao.pendingSettings() == null) { "Finish the earlier import first" }
        dao.allAttempts().forEach { row ->
            val attempt = row.attempt()
            if (attempt.photoId != null) dao.saveAttempt(row.copy(json = ParlorJson.encodeToString(attempt.copy(photoId = null))))
        }
        recoverySnapshot()?.let { before ->
            val cleaned = before.copy(photos = emptyList(), attempts = before.attempts.map { it.copy(photoId = null) })
            dao.replaceRecovery(BackupFile.bytes(cleaned).toString(Charsets.UTF_8))
        }
        dao.prunePhotos(emptyList())
    }

    private suspend fun prunePhotos() = dao.prunePhotos(dao.allAttempts().mapNotNull { it.attempt().photoId }.distinct())

    suspend fun exportProgress(settings: PlayerSettings): ProgressEnvelope = database.transaction {
        check(isPro()) { "Pro is required" }
        check(dao.pendingSettings() == null) { "Finish the earlier import first" }
        snapshot(settings).also(::validateBackup)
    }

    fun validateBackup(file: ProgressEnvelope) {
        BackupValidation.validate(file, requireNotNull(catalog), requireNotNull(schedule))
    }

    /** Keep notification eligibility ordered with a concurrent completion/import/reset transaction. */
    suspend fun withCompletionCredits(action: suspend (Set<String>) -> Unit) = database.transaction {
        action(playerRow().ledger().creditedDates)
    }

    private fun validatePlayerSize(ledger: RewardLedger, profile: PlayerProfile) {
        BackupValidation.playerSize(ledger, profile)
    }

    /** Progress and its recoverable previous snapshot commit together. Settings finish before play resumes. */
    suspend fun importProgress(file: ProgressEnvelope, currentSettings: PlayerSettings, recovery: Boolean = false) = database.transaction {
        check(recovery || isPro()) { "Pro is required" }
        check(dao.pendingSettings() == null) { "Finish the earlier import first" }
        if (recovery) check(recoverySnapshot() == file) { "Recovery snapshot changed" }
        validateBackup(file)
        val before = snapshot(currentSettings)
        validateBackup(before)
        val recoveryBytes = BackupFile.bytes(before)
        val retained = RecoveryProtection(1,
            before.ledger.copy(hintedAttemptIds = before.ledger.hintedAttemptIds + before.attempts.filter { it.hintsUsed }.map { it.id }),
            before.completionDates, before.profile.achievements)
        val protected = if (protection == null) retained else protectStorage {
            protection.preserve(retained, today())
            protection.read()
        }
        val importedLedger = file.ledger.copy(hintedAttemptIds = file.ledger.hintedAttemptIds + file.attempts.filter { it.hintsUsed }.map { it.id })
        val ledger = BackupRules.protect(protected.ledger, importedLedger, today())
        val completions = file.completionDates + protected.completionDates
        val attempts = file.attempts.filterNot { !it.completed && it.id in protected.completionDates }.map { a ->
            val eligibility = if (a.mode == AttemptMode.DAILY && !a.completed) {
                val claim = ledger.dailyClaims[requireNotNull(a.challengeDate)] ?: DailyClaim()
                when (a.dailyEligibility) {
                    DailyEligibility.FIRST_COMPLETION -> if (!claim.firstCompletionClaimed) a.dailyEligibility else DailyEligibility.PRACTICE
                    DailyEligibility.REWARDED -> if (claim.earnedRetries > claim.consumedRetries) a.dailyEligibility else DailyEligibility.PRACTICE
                    DailyEligibility.PRO -> if (isPro()) a.dailyEligibility else DailyRules.eligibility(claim, false)
                    DailyEligibility.PRACTICE -> a.dailyEligibility
                }
            } else a.dailyEligibility
            a.copy(hintsUsed = a.hintsUsed || a.id in ledger.hintedAttemptIds, dailyEligibility = eligibility)
        }
        val levels = requireNotNull(catalog).levels.associateBy { it.id }
        dao.resetAttempts(null); dao.resetBests(null); dao.clearDailies()
        attempts.forEach { a -> dao.saveAttempt(a.row(levels.getValue(a.levelId).puzzle).copy(updatedAt = file.attemptUpdatedAt.getValue(a.id))) }
        file.photos.forEach { dao.savePhoto(PhotoRow(it.id, PhotoCodec.bytes(it))) }
        prunePhotos()
        file.bests.forEach { b -> dao.saveBest(BestRow(b.levelId, ParlorJson.encodeToString(b), levels.getValue(b.levelId).puzzle.name)) }
        file.daily.forEach { d ->
            val record = d.copy(activeAttemptId = d.activeAttemptId?.takeIf { id -> attempts.any { it.id == id && !it.completed } })
            dao.saveDaily(DailyEntry(record, levels.getValue(record.levelId)).row())
        }
        completions.forEach { (id, date) -> dao.recordCompletion(CompletionRow(id, date)) }
        val profile = file.profile.copy(achievements = file.profile.achievements + protected.achievements)
        validatePlayerSize(ledger, profile)
        savePlayer(PlayerRow(ledgerJson = ParlorJson.encodeToString(ledger), totalPlayTimeMs = file.totalPlayTimeMs, profileJson = ParlorJson.encodeToString(profile)))
        // Notification permission/opt-in is device-owned and is never enabled by a progress file.
        val settings = file.settings.copy(notifications = currentSettings.notifications)
        if (recovery) check(dao.restoreBackupSettings(ParlorJson.encodeToString(settings), file.snapshotId) == 1)
        else dao.saveBackupState(BackupStateRow(recoveryJson = recoveryBytes.toString(Charsets.UTF_8),
            pendingSettingsJson = ParlorJson.encodeToString(settings), lastImportId = file.snapshotId))
        check(recovery || isPro()) { "Pro changed before import committed" }
    }

    /** Build only an empty, inactive file. No data from an unreadable database is interpreted as empty. */
    internal suspend fun restoreIntoEmptyDatabase(file: ProgressEnvelope, retained: RecoveryProtection) = database.transaction {
        check(isPro()) { "Pro is required" }
        validateBackup(file)
        BackupRules.validateProtection(retained)
        check(dao.player() == null && dao.allAttempts().isEmpty() && dao.allBests().isEmpty() &&
            dao.allCompletions().isEmpty() && dao.allDailies().isEmpty() && dao.photoIds().isEmpty() && dao.pendingSettings() == null)
        val profile = PlayerProfile(achievements = retained.achievements)
        validatePlayerSize(retained.ledger, profile)
        savePlayer(PlayerRow(ledgerJson = ParlorJson.encodeToString(retained.ledger), profileJson = ParlorJson.encodeToString(profile)))
        retained.completionDates.forEach { (id, date) -> dao.recordCompletion(CompletionRow(id, date)) }
        importProgress(file.copy(settings = file.settings.copy(onboarded = true, notifications = false)), PlayerSettings())
        // The previous damaged database remains in its own slot; there is no invented empty recovery snapshot.
        dao.saveBackupState(BackupStateRow(recoveryJson = null,
            pendingSettingsJson = ParlorJson.encodeToString(requireNotNull(pendingBackupSettings())), lastImportId = file.snapshotId))
        snapshot(requireNotNull(pendingBackupSettings())).also(::validateBackup)
    }

    suspend fun pendingBackupSettings(): PlayerSettings? = database.transaction { dao.pendingSettings()?.let { ParlorJson.decodeFromString(it) } }
    suspend fun hasRecoverySnapshot(): Boolean = database.transaction { dao.hasRecovery() }
    suspend fun finishBackupSettings(expected: PlayerSettings) = database.transaction {
        val stored = checkNotNull(dao.pendingSettings()) { "No settings import is pending" }
        // Older encodings can omit newly defaulted fields or use different JSON ordering/spacing.
        // Compare the decoded value, then clear exactly the row read in this transaction.
        check(ParlorJson.decodeFromString<PlayerSettings>(stored) == expected) { "Pending settings changed" }
        check(dao.finishBackupSettings(stored) == 1)
    }
    suspend fun recoverySnapshot(): ProgressEnvelope? = database.transaction {
        val length = dao.recoveryLength() ?: return@transaction null
        require(length in 1..BackupFile.MAX_BYTES)
        // SQLite substr counts Unicode code points. Advance by the requested count, not UTF-16 length.
        val json = buildString {
            for (start in 1..length step 65_536) append(requireNotNull(dao.recoveryPart(start, 65_536)))
        }
        ParlorJson.decodeFromString<ProgressEnvelope>(json)
    }

    private suspend fun snapshot(settings: PlayerSettings): ProgressEnvelope {
        val row = playerRow(); val profile = row.profile()
        val attempts = dao.allAttempts(); val completions = dao.allCompletions()
        val photos = attempts.mapNotNull { it.attempt().photoId }.distinct().sorted().map { id ->
            PhotoCodec.payload(requireNotNull(dao.photo(id)) { "Saved photo is missing" }.jpeg).also { require(it.id == id) }
        }
        return ProgressEnvelope(contentVersion = requireNotNull(catalog).contentVersion, snapshotId = newId(), createdAtMs = wallTime(),
            attempts = attempts.map { it.attempt() }, bests = dao.allBests().map { it.best() }, daily = dao.allDailies().map { it.entry().record },
            ledger = row.ledger(), settings = settings, completedAttemptIds = completions.map { it.attemptId }.toSet(),
            achievements = profile.achievements, totalPlayTimeMs = row.totalPlayTimeMs, profile = profile,
            attemptUpdatedAt = attempts.associate { it.id to it.updatedAt }, completionDates = completions.associate { it.attemptId to it.completionDate }, photos = photos)
    }
    // A date rollover must not delete the board currently being played. This lease is never persisted.
    fun retainActiveDaily(attemptId: String, date: String?) {
        if (date == null) foregroundDailies.remove(attemptId) else foregroundDailies[attemptId] = date
    }

    fun configure(content: Catalog, dailySchedule: DailySchedule) {
        require(content.levels.map { it.id }.distinct().size == content.levels.size)
        dailySchedule.epochs.forEach { epoch ->
            require(epoch.pool.all { ref -> content.levels.any { it.id == ref.id && it.revision == ref.revision } })
        }
        DailyRules.assignment(today(), dailySchedule)
        catalog = content
        schedule = dailySchedule
    }

    suspend fun refreshDay() = database.transaction {
        val row = playerRow()
        val ledger = DailyRules.rollover(row.ledger(), today())
        savePlayer(row.copy(ledgerJson = ParlorJson.encodeToString(ledger)))
        val anchor = maxOf(LocalDate.parse(today()), LocalDate.parse(requireNotNull(ledger.latestResetDate)))
        val cutoff = anchor.minusDays(29).toString()
        val protectedDates = foregroundDailies.values.toList()
        dao.pruneDailyAttempts(cutoff, protectedDates)
        dao.pruneDaily(cutoff, protectedDates)
    }

    suspend fun startOrResume(level: LevelDefinition): Attempt = database.transaction {
        checkCampaignAccess(level)
        val previous = dao.unfinished(level.id, AttemptMode.CAMPAIGN.name)?.attempt()
        if (previous != null) {
            require(previous.levelRevision == level.revision) { "Saved content revision is unavailable" }
            require(engineFor(level.puzzle).validate(previous.board)) { "Saved board is invalid" }
            dao.saveAttempt(previous.row(level.puzzle))
            previous
        } else Attempt.start(newId(), level).also { dao.saveAttempt(it.row(level.puzzle)) }
    }

    /** Recheck access when an existing Activity returns, as well as after process death. */
    suspend fun checkReturnAccess(level: LevelDefinition, id: String) = database.transaction {
        val attempt = dao.attempt(id)?.attempt() ?: throw DailyUnavailable()
        require(attempt.levelId == level.id && attempt.levelRevision == level.revision)
        if (attempt.completed) return@transaction
        if (attempt.mode == AttemptMode.DAILY) dailyEntry(requireNotNull(attempt.challengeDate))
        else checkCampaignAccess(level)
    }

    /** Merely opening the daily screen does not reserve or consume an attempt. */
    suspend fun dailyEntry(date: String = today()): DailyEntry = database.transaction {
        val ledger = playerRow().ledger()
        if (!DailyRules.withinHistory(date, today(), ledger.latestResetDate)) throw DailyUnavailable()
        dao.daily(date)?.entry()?.let { return@transaction it }
        if (date != today()) throw DailyUnavailable()
        val (version, ref) = DailyRules.assignment(date, requireNotNull(schedule) { "Content not configured" })
        val level = requireNotNull(catalog).levels.single { it.id == ref.id && it.revision == ref.revision }
        DailyEntry(DailyRecord(date, version, ref.id, ref.revision), level).also { dao.saveDaily(it.row()) }
    }

    suspend fun startDaily(date: String = today()): DailySession = database.transaction {
        val entry = dailyEntry(date)
        val previous = entry.record.activeAttemptId?.let { dao.attempt(it)?.attempt() }
        if (previous != null && !previous.completed) {
            require(engineFor(entry.level.puzzle).validate(previous.board))
            dao.saveAttempt(previous.row(entry.level.puzzle))
            return@transaction DailySession(entry.level, previous)
        }
        val claim = playerRow().ledger().dailyClaims[date] ?: DailyClaim()
        val attempt = Attempt.start(newId(), entry.level, AttemptMode.DAILY, date, DailyRules.eligibility(claim, isPro()))
        dao.saveAttempt(attempt.row(entry.level.puzzle))
        dao.saveDaily(entry.copy(record = entry.record.copy(activeAttemptId = attempt.id)).row())
        DailySession(entry.level, attempt)
    }

    /** Explicit "Start scored retry" replaces only an unfinished practice attempt. */
    suspend fun startScoredDailyRetry(date: String): DailySession = database.transaction {
        val session = startDaily(date)
        if (session.attempt.dailyEligibility != DailyEligibility.PRACTICE) return@transaction session
        val claim = playerRow().ledger().dailyClaims[date] ?: DailyClaim()
        check(DailyRules.eligibility(claim, isPro()) != DailyEligibility.PRACTICE) { "No scored retry available" }
        DailySession(session.level, restart(session.level, session.attempt.id))
    }

    suspend fun restart(level: LevelDefinition, attemptId: String): Attempt = database.transaction {
        val previous = requireNotNull(dao.attempt(attemptId)).attempt()
        require(previous.levelId == level.id && previous.levelRevision == level.revision)
        if (previous.mode == AttemptMode.DAILY) {
            val date = requireNotNull(previous.challengeDate)
            val entry = dailyEntry(date)
            if (previous.completed) return@transaction startDaily(date).attempt
            // An explicitly restarted practice board may use a newly earned retry or Pro access.
            val eligibility = if (previous.dailyEligibility == DailyEligibility.PRACTICE)
                DailyRules.eligibility(playerRow().ledger().dailyClaims[date] ?: DailyClaim(), isPro()) else previous.dailyEligibility
            val restarted = Attempt.start(newId(), level, AttemptMode.DAILY, date, eligibility)
            dao.deleteAttempt(previous.id)
            dao.saveAttempt(restarted.row(level.puzzle))
            dao.saveDaily(entry.copy(record = entry.record.copy(activeAttemptId = restarted.id)).row())
            restarted
        } else {
            if (previous.completed) checkCampaignAccess(level)
            if (!previous.completed) dao.deleteAttempt(previous.id)
            Attempt.start(newId(), level).copy(photoId = previous.photoId).also { dao.saveAttempt(it.row(level.puzzle)) }
        }
    }

    suspend fun move(level: LevelDefinition, id: String, action: Move, elapsedMs: Long): Attempt? =
        mutate(level, id, elapsedMs) { Session.move(engineFor(level.puzzle), it, action) }
    suspend fun undo(level: LevelDefinition, id: String, elapsedMs: Long): Attempt? =
        mutate(level, id, elapsedMs) { Session.undo(it, level.scoringUnit) }
    suspend fun redo(level: LevelDefinition, id: String, elapsedMs: Long): Attempt? =
        mutate(level, id, elapsedMs) { Session.redo(engineFor(level.puzzle), it) }
    suspend fun checkpoint(level: LevelDefinition, id: String, elapsedMs: Long): Attempt? = mutate(level, id, elapsedMs) { it }
    suspend fun saveViewport(level: LevelDefinition, id: String, viewport: Viewport, elapsedMs: Long = 0): Attempt? =
        mutate(level, id, elapsedMs) { it.copy(viewport = viewport) }

    suspend fun useHint(level: LevelDefinition, id: String, elapsedMs: Long): Attempt? = database.transaction {
        val original = requireNotNull(dao.attempt(id)).attempt()
        if (original.completed) return@transaction null
        val row = playerRow()
        val spent = DailyRules.spendHint(row.ledger(), today(), isPro()) ?: return@transaction null
        savePlayer(row.copy(ledgerJson = ParlorJson.encodeToString(spent.copy(hintedAttemptIds = spent.hintedAttemptIds + id))))
        mutate(level, id, elapsedMs) { it.copy(hintsUsed = true) }
    }

    /** SDK adapters call this once a completed rewarded view has been confirmed. */
    suspend fun grantReward(rewardId: String, reward: Reward): Boolean = database.transaction {
        val row = playerRow()
        val level = (reward as? Reward.LevelAccess)?.let { r -> requireNotNull(catalog).levels.find { it.id == r.levelId } }
        val ledger = RewardRules.grant(row.ledger(), today(), rewardId, reward, wallTime(), level) ?: return@transaction false
        savePlayer(row.copy(ledgerJson = ParlorJson.encodeToString(ledger)))
        true
    }

    suspend fun canOfferReward(reward: Reward): Boolean = rewardAvailability(reward) == RewardAvailability.AVAILABLE

    suspend fun rewardAvailability(reward: Reward): RewardAvailability = database.transaction {
        val row = playerRow()
        val date = today()
        val ledger = DailyRules.rollover(row.ledger(), date)
        savePlayer(row.copy(ledgerJson = ParlorJson.encodeToString(ledger)))
        val level = (reward as? Reward.LevelAccess)?.let { r -> requireNotNull(catalog).levels.find { it.id == r.levelId } }
        when {
            isPro() -> RewardAvailability.PRO
            ledger.latestResetDate != date -> RewardAvailability.INVALID_DATE
            ledger.rewardedViews >= 10 -> RewardAvailability.DAILY_LIMIT
            RewardRules.canOffer(ledger, date, reward, level) -> RewardAvailability.AVAILABLE
            else -> RewardAvailability.INELIGIBLE
        }
    }

    /** The receipt remains in private recovery storage until this transaction commits. */
    suspend fun applyEarnedReward(receipt: EarnedReward): RewardApplication = database.transaction {
        val row = playerRow()
        if (receipt.id in row.ledger().claims) return@transaction RewardApplication.ALREADY_APPLIED
        val level = (receipt.reward as? Reward.LevelAccess)?.let { r -> requireNotNull(catalog).levels.find { it.id == r.levelId } }
        val ledger = RewardRules.grantEarned(row.ledger(), today(), receipt, level) ?: return@transaction RewardApplication.REJECTED
        savePlayer(row.copy(ledgerJson = ParlorJson.encodeToString(ledger)))
        RewardApplication.APPLIED
    }

    /** Claim a results departure before presenting a ready ad; failures never create a deferred interruption. */
    suspend fun claimInterstitial(attemptId: String, ready: Boolean, elapsedMs: Long, launchedMs: Long, boot: String?): Boolean = database.transaction {
        if (!dao.wasCompleted(attemptId)) return@transaction false
        val row = playerRow(); val ledger = row.ledger(); val key = "ad-results:$attemptId"
        if (key in ledger.claims) return@transaction false
        val count = dao.completionCount()
        val allowed = ready && !isPro() && AdRules.interstitialAllowed(ledger.ads, count, elapsedMs, launchedMs, boot)
        val history = if (allowed) ledger.ads.copy(lastInterstitialCompletion = count,
            lastInterstitialElapsedMs = elapsedMs, lastInterstitialBoot = boot, interstitialUnsettled = true) else ledger.ads
        savePlayer(row.copy(ledgerJson = ParlorJson.encodeToString(ledger.copy(ads = history, claims = ledger.claims + key))))
        allowed
    }

    /** Called only for a shown interstitial's actual close callback; callback duplicates are harmless. */
    suspend fun interstitialDismissed(attemptId: String, elapsedMs: Long, boot: String?): Boolean = database.transaction {
        val row = playerRow(); val ledger = row.ledger(); val key = "ad-dismissed:$attemptId"
        if ("ad-results:$attemptId" !in ledger.claims || key in ledger.claims) return@transaction false
        val count = ledger.ads.dismissedInterstitials + 1
        savePlayer(row.copy(ledgerJson = ParlorJson.encodeToString(ledger.copy(
            ads = ledger.ads.copy(dismissedInterstitials = count, lastInterstitialElapsedMs = elapsedMs,
                lastInterstitialBoot = boot, interstitialUnsettled = false), claims = ledger.claims + key))))
        count % 5 == 0 && !isPro()
    }

    suspend fun claimTenCompletionPrompt(): Boolean = database.transaction {
        val row = playerRow(); val ledger = row.ledger()
        if (ledger.ads.tenCompletionPromptSeen || dao.completionCount() < 10 || isPro()) return@transaction false
        savePlayer(row.copy(ledgerJson = ParlorJson.encodeToString(ledger.copy(ads = ledger.ads.copy(tenCompletionPromptSeen = true)))))
        true
    }

    /** Clear play progress, retaining claims, hints, streaks and achievements. Purchase data is elsewhere. */
    suspend fun resetProgress(puzzle: Puzzle? = null) = database.transaction {
        val row = playerRow()
        val profile = row.profile()
        dao.resetAttempts(puzzle?.name)
        prunePhotos()
        dao.resetBests(puzzle?.name)
        dao.allDailies().filter { puzzle == null || it.puzzle == puzzle.name }.forEach { daily ->
            val entry = daily.entry()
            dao.saveDaily(entry.copy(record = entry.record.copy(best = null, activeAttemptId = null, completions = 0, scoredCompletions = 0)).row())
        }
        val time = if (puzzle == null) emptyMap() else profile.playTimeByPuzzle - puzzle
        val daily = if (puzzle == null) emptyMap() else profile.dailyTotals - puzzle
        savePlayer(row.copy(totalPlayTimeMs = time.values.sum(), profileJson = ParlorJson.encodeToString(profile.copy(playTimeByPuzzle = time, dailyTotals = daily))))
    }

    private suspend fun mutate(level: LevelDefinition, id: String, elapsedMs: Long, transform: (Attempt) -> Attempt?): Attempt? = database.transaction {
        val original = requireNotNull(dao.attempt(id)).attempt()
        require(original.levelId == level.id && original.levelRevision == level.revision)
        if (original.completed) return@transaction null
        val timed = Session.checkpoint(original, elapsedMs)
        val changed = transform(timed)
        var next = changed ?: timed
        val row = playerRow()
        var ledger = DailyRules.rollover(row.ledger(), today())
        var profile = row.profile().let { it.copy(playTimeByPuzzle = it.playTimeByPuzzle + (level.puzzle to ((it.playTimeByPuzzle[level.puzzle] ?: 0) + elapsedMs))) }
        val result = Scoring.result(level, next)
        if (next.completed && next.mode != AttemptMode.DEMONSTRATION) {
            val engine = engineFor(level.puzzle)
            require(engine.isGoal(next.board))
            if (dao.recordCompletion(CompletionRow(next.id, today())) != -1L) {
                if (next.mode == AttemptMode.CAMPAIGN && result != null) {
                    val best = Scoring.improve(level.id, dao.best(level.id)?.best(), result, today())
                    dao.saveBest(BestRow(level.id, ParlorJson.encodeToString(best), level.puzzle.name))
                }
                if (next.mode == AttemptMode.DAILY) {
                    val date = requireNotNull(next.challengeDate)
                    val entry = requireNotNull(dao.daily(date)) { "Missing saved daily" }.entry()
                    var claim = ledger.dailyClaims[date] ?: DailyClaim()
                    if (result != null) {
                        claim = when (next.dailyEligibility) {
                            DailyEligibility.FIRST_COMPLETION -> { check(!claim.firstCompletionClaimed); claim.copy(firstCompletionClaimed = true) }
                            DailyEligibility.REWARDED -> { check(claim.consumedRetries < claim.earnedRetries); claim.copy(consumedRetries = claim.consumedRetries + 1) }
                            DailyEligibility.PRO -> claim
                            DailyEligibility.PRACTICE -> error("Practice cannot be scored")
                        }
                    }
                    ledger = ledger.copy(dailyClaims = ledger.dailyClaims + (date to claim))
                    val best = if (result != null) DailyRules.improveDaily(entry.record.best, result) else entry.record.best
                    val totals = profile.dailyTotals[level.puzzle] ?: ScoreTotals()
                    profile = profile.copy(dailyCompletedDates = profile.dailyCompletedDates + date,
                        dailyTotals = profile.dailyTotals + (level.puzzle to ScoreTotals(
                            totals.stars + (best?.stars ?: 0) - (entry.record.best?.stars ?: 0),
                            totals.points + (best?.points ?: 0) - (entry.record.best?.points ?: 0))))
                    dao.saveDaily(entry.copy(record = entry.record.copy(best = best, activeAttemptId = null,
                        scoredCompletions = entry.record.scoredCompletions + if (result != null) 1 else 0,
                        completions = entry.record.completions + 1)).row())
                }
                ledger = DailyRules.creditStreak(ledger, today())
                profile = profile.copy(completedPuzzles = profile.completedPuzzles + level.puzzle)
                val awards = Achievements.evaluate(level, next, result, requireNotNull(catalog).levels, dao.allBests().map { it.best() }, ledger, profile, engine.completionFacts(next.board))
                profile = profile.copy(achievements = profile.achievements + awards)
            }
            // Reversible histories belong to unfinished play. Completed attempts retain their summary.
            next = next.copy(history = emptyList(), redo = emptyList())
        }
        savePlayer(row.copy(ledgerJson = ParlorJson.encodeToString(ledger), totalPlayTimeMs = row.totalPlayTimeMs + elapsedMs,
            profileJson = ParlorJson.encodeToString(profile)))
        dao.saveAttempt(next.row(level.puzzle))
        if (changed == null) null else next
    }

    private suspend fun checkCampaignAccess(level: LevelDefinition) {
        val levels = requireNotNull(catalog) { "Content not configured" }.levels
        require(levels.any { it == level }) { "Unknown level revision" }
        val ledger = playerRow().ledger()
        val access = Progression.access(level, levels, dao.allBests().map { it.best() }, isPro(), ledger.temporaryUnlocks[level.id], wallTime())
        if (access != Access.AVAILABLE) throw AccessDenied(access)
    }
    private suspend fun savePlayer(row: PlayerRow) {
        if (protection != null) {
            protectStorage {
                // Bootstrap sticky hint flags from readable legacy attempts once, before acknowledging writes.
                val ledger = row.ledger().let { ledger ->
                    if (protectionInitialized) ledger else ledger.copy(hintedAttemptIds = ledger.hintedAttemptIds +
                        dao.allAttempts().map { it.attempt() }.filter { it.hintsUsed }.map { it.id })
                }
                val completions = dao.allCompletions().associate { it.attemptId to it.completionDate }
                // Dated receipts are independently authoritative when bootstrapping an older ledger.
                protection.preserve(RecoveryProtection(1, ledger.copy(creditedDates = ledger.creditedDates + completions.values),
                    completions, row.profile().achievements), today())
                protectionInitialized = true
            }
        }
        dao.savePlayer(row)
    }

    private suspend fun <T> protectStorage(action: suspend () -> T): T {
        val generation = storageHealth?.state?.value?.generation
        try { return action() }
        catch (failure: Exception) {
            if (generation != null) storageHealth?.failed(StorageArea.PROGRESS, failure, generation)
            throw failure
        }
    }

    private suspend fun playerRow() = dao.player() ?: PlayerRow(ledgerJson = ParlorJson.encodeToString(RewardLedger()))
    private fun AttemptRow.attempt() = ParlorJson.decodeFromString<Attempt>(json)
    private fun BestRow.best() = ParlorJson.decodeFromString<LevelBest>(json).also { best ->
        try {
            SavedScoreRules.best(best)
            require(best.levelId == levelId) { "Saved best identity differs from its row" }
        } catch (failure: IllegalArgumentException) {
            // Semantic corruption must reach the same preserved-save gate as malformed JSON.
            throw SerializationException("Saved best is invalid", failure)
        }
    }
    private fun DailyRow.entry() = ParlorJson.decodeFromString<DailyEntry>(json)
    private fun PlayerRow.ledger() = ParlorJson.decodeFromString<RewardLedger>(ledgerJson)
    private fun PlayerRow.profile() = ParlorJson.decodeFromString<PlayerProfile>(profileJson)
    private fun Attempt.row(puzzle: Puzzle) = AttemptRow(id, levelId, mode.name, completed, wallTime(), ParlorJson.encodeToString(this), puzzle.name, challengeDate)
    private fun DailyEntry.row() = DailyRow(record.date, level.puzzle.name, ParlorJson.encodeToString(this))
}
