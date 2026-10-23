package com.parlor.core.progress

import com.parlor.core.model.*
import com.parlor.core.game.AttemptValidation
import kotlinx.datetime.LocalDate

/** File replacement preserves local anti-duplication records; it never adds two balances. */
object BackupRules {
    fun protect(local: RewardLedger, imported: RewardLedger, today: String): RewardLedger {
        val a = local.latestResetDate?.let { DailyRules.rollover(local, it) } ?: local
        val b = imported.latestResetDate?.let { DailyRules.rollover(imported, it) } ?: imported
        val latest = listOfNotNull(a.latestResetDate, b.latestResetDate).maxOrNull()
        val spent = maxOf(a.earnedHintsSpent, b.earnedHintsSpent)
        val allowance = when {
            a.latestResetDate == b.latestResetDate -> minOf(a.dailyHints, b.dailyHints)
            latest == a.latestResetDate -> a.dailyHints
            else -> b.dailyHints
        }
        val counts = (a.rewardedViewsByDate.keys + b.rewardedViewsByDate.keys).associateWith { maxOf(a.rewardedViewsByDate[it] ?: 0, b.rewardedViewsByDate[it] ?: 0) }
        val claims = (a.dailyClaims.keys + b.dailyClaims.keys).associateWith { date ->
            val x = a.dailyClaims[date] ?: DailyClaim(); val y = b.dailyClaims[date] ?: DailyClaim()
            DailyClaim(x.firstCompletionClaimed || y.firstCompletionClaimed, maxOf(x.earnedRetries, y.earnedRetries), maxOf(x.consumedRetries, y.consumedRetries))
        }
        return DailyRules.rollover(b.copy(latestResetDate = latest, dailyHints = allowance,
            earnedHints = (b.earnedHints.toLong() - (spent - b.earnedHintsSpent)).coerceAtLeast(0).toInt(),
            earnedHintsSpent = spent, claims = a.claims + b.claims, creditedDates = a.creditedDates + b.creditedDates,
            hintedAttemptIds = a.hintedAttemptIds + b.hintedAttemptIds,
            rewardedViewsByDate = counts, rewardedViews = counts[latest] ?: 0, dailyClaims = claims,
            ads = a.ads.copy(dismissedInterstitials = maxOf(a.ads.dismissedInterstitials, b.ads.dismissedInterstitials),
                tenCompletionPromptSeen = a.ads.tenCompletionPromptSeen || b.ads.tenCompletionPromptSeen)), today)
    }

    fun validate(file: ProgressEnvelope, catalog: Catalog, schedule: DailySchedule? = null) {
        require(file.format == "parlor-progress" && file.schemaVersion in 2..4) { "Unsupported backup format" }
        val photoIds = file.attempts.mapNotNull { it.photoId }.toSet()
        require(file.schemaVersion >= 3 || (file.photos.isEmpty() && photoIds.isEmpty()))
        require(file.photos.map { it.id }.toSet() == photoIds && file.photos.size == photoIds.size) { "Missing or duplicate puzzle photos" }
        require(file.photos.sumOf { p -> p.jpeg.sumOf { it.length.toLong() } } <= (PhotoLimits.TOTAL_BYTES.toLong() + 2) / 3 * 4 + file.photos.size * 4L)
        file.photos.forEach { p ->
            require(PhotoLimits.validId(p.id) && p.jpeg.size in 1..PhotoLimits.MAX_CHUNKS)
            require(p.jpeg.dropLast(1).all { it.length == PhotoLimits.CHUNK })
            require(p.jpeg.last().length in 4..PhotoLimits.CHUNK && p.jpeg.last().length % 4 == 0)
            require(p.jpeg.all { it.matches(Regex("[A-Za-z0-9+/]*={0,2}")) })
        }
        require(file.contentVersion in 1..catalog.contentVersion) { "Newer content required" }
        id(file.snapshotId); require(file.createdAtMs in 0..AttemptValidation.MAX_TIME)
        require(file.attempts.size <= 20_000 && file.attempts.sumOf { it.history.size.toLong() + it.redo.size } <= 40_000)
        require(file.attempts.map { it.id }.distinct().size == file.attempts.size)
        require(file.attempts.filter { !it.completed }.groupBy { if (it.mode == AttemptMode.DAILY) "daily:${it.challengeDate}" else "campaign:${it.levelId}" }.values.all { it.size == 1 })
        require(file.bests.map { it.levelId }.distinct().size == file.bests.size)
        require(file.daily.size <= 30 && file.daily.map { it.date }.distinct().size == file.daily.size)
        require(file.attemptUpdatedAt.keys == file.attempts.map { it.id }.toSet())
        require(file.attemptUpdatedAt.values.all { it in 0..AttemptValidation.MAX_TIME })
        require(file.completionDates.keys == file.completedAttemptIds)
        file.completedAttemptIds.forEach(::id); file.completionDates.values.forEach(::date)
        val levels = catalog.levels.associateBy { it.id }
        val attempts = file.attempts.associateBy { it.id }
        file.attempts.forEach { a ->
            id(a.id); val level = requireNotNull(levels[a.levelId]) { "Unknown level" }
            AttemptValidation.validate(level, a)
            require(a.photoId == null || (a.mode == AttemptMode.CAMPAIGN && a.board is TilesBoard && PhotoLimits.validId(requireNotNull(a.photoId))))
            if (a.completed) require(a.id in file.completedAttemptIds)
            if (a.mode == AttemptMode.DAILY) {
                date(requireNotNull(a.challengeDate))
                require(file.daily.any { it.date == a.challengeDate && it.levelId == a.levelId && it.levelRevision == a.levelRevision })
            } else require(a.dailyEligibility == DailyEligibility.PRACTICE)
        }
        file.bests.forEach { b ->
            require(b.levelId in levels); SavedScoreRules.best(b)
        }
        file.daily.forEach { d ->
            date(d.date); val level = requireNotNull(levels[d.levelId])
            require(level.revision == d.levelRevision && d.scheduleVersion > 0)
            val epoch = requireNotNull(schedule?.epochs?.singleOrNull { it.version == d.scheduleVersion }) { "Unknown daily schedule" }
            require(DailyRules.assignment(d.date, DailySchedule(listOf(epoch))).second == LevelRef(d.levelId, d.levelRevision))
            require(d.completions >= d.scoredCompletions && d.scoredCompletions >= 0 && d.earnedRetries >= 0)
            d.best?.let(SavedScoreRules::result)
            require((d.best != null) == (d.scoredCompletions > 0))
            d.activeAttemptId?.let { id -> require(attempts[id]?.let { it.mode == AttemptMode.DAILY && it.challengeDate == d.date && !it.completed } == true) }
        }
        val ledger = file.ledger
        validateLedger(ledger)
        require(file.completionDates.values.all { it in ledger.creditedDates })
        ledger.temporaryUnlocks.keys.forEach { require(levels[it]?.tier?.let { tier -> tier >= Tier.HARD } == true) }
        val profile = file.profile
        require(file.achievements == profile.achievements && profile.achievements.all { id -> Achievements.definitions.any { it.id == id } })
        profile.dailyCompletedDates.forEach(::date)
        require(file.totalPlayTimeMs in 0..AttemptValidation.MAX_TIME && profile.playTimeByPuzzle.values.all { it in 0..AttemptValidation.MAX_TIME })
        require(profile.playTimeByPuzzle.values.sum() == file.totalPlayTimeMs)
        require(file.attempts.sumOf { it.activeTimeMs } <= file.totalPlayTimeMs)
        require(profile.dailyTotals.values.all { it.stars >= 0 && it.points in 0..profile.dailyCompletedDates.size.toLong() * 350 })
        require(profile.dailyTotals.values.sumOf { it.stars.toLong() } <= profile.dailyCompletedDates.size.toLong() * 3)
        require(profile.dailyTotals.values.sumOf { it.points } <= profile.dailyCompletedDates.size.toLong() * 350)
        val settings = file.settings
        require(settings.sfxVolume.isFinite() && settings.sfxVolume in 0f..1f && settings.musicVolume.isFinite() && settings.musicVolume in 0f..1f)
        require(RoomTheme.fromId(settings.theme) != null) { "Unsupported theme" }
        require(file.schemaVersion >= 4 || (settings.theme == RoomTheme.LINEN.id && settings.appearance == Appearance.LIGHT)) {
            "Theme settings need a newer backup format"
        }
    }

    /** Union irreversible claims and keep conservative spending/cap high-water values. */
    fun combineProtection(local: RecoveryProtection?, incoming: RecoveryProtection, today: String): RecoveryProtection {
        validateProtection(incoming)
        local?.let(::validateProtection)
        if (local == null) return incoming
        return incoming.copy(ledger = protect(local.ledger, incoming.ledger, today),
            completionDates = incoming.completionDates + local.completionDates,
            achievements = incoming.achievements + local.achievements).also(::validateProtection)
    }

    fun validateProtection(value: RecoveryProtection) {
        require(value.version == 1) { "Unsupported recovery safeguards" }
        validateLedger(value.ledger)
        value.completionDates.forEach { (id, date) -> id(id); date(date); require(date in value.ledger.creditedDates) }
        require(value.achievements.all { id -> Achievements.definitions.any { it.id == id } })
    }

    private fun validateLedger(ledger: RewardLedger) {
        ledger.latestResetDate?.let(::date)
        require(ledger.dailyHints in 0..3 && ledger.earnedHints in 0..10_000_000 && ledger.rewardedViews in 0..10)
        require(ledger.earnedHintsSpent in 0..1_000_000_000)
        if (ledger.latestResetDate == null) require(ledger.dailyHints == 0 && ledger.rewardedViews == 0 && ledger.rewardedViewsByDate.isEmpty())
        ledger.claims.forEach(::id); ledger.hintedAttemptIds.forEach(::id); ledger.creditedDates.forEach(::date)
        ledger.rewardedViewsByDate.forEach { (d, count) -> date(d); require(count in 0..10 && d <= requireNotNull(ledger.latestResetDate)) }
        require(ledger.rewardedViews >= (ledger.rewardedViewsByDate[ledger.latestResetDate] ?: 0))
        ledger.temporaryUnlocks.forEach { (level, expiry) -> id(level); require(expiry in 0..AttemptValidation.MAX_TIME) }
        ledger.dailyClaims.forEach { (d, claim) -> date(d); require(claim.earnedRetries >= claim.consumedRetries && claim.consumedRetries >= 0 && claim.earnedRetries <= 1_000_000) }
        require(ledger.ads.lastInterstitialCompletion >= 0 && ledger.ads.dismissedInterstitials >= 0)
        require(ledger.ads.lastInterstitialElapsedMs?.let { it >= 0 } != false)
        require((ledger.ads.lastInterstitialBoot?.length ?: 0) <= 200)
    }

    private fun id(value: String) { require(value.isNotBlank() && value.length <= 200 && value.none { it.code < 32 }) }
    private fun date(value: String) { require(value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))); LocalDate.parse(value) }
}
