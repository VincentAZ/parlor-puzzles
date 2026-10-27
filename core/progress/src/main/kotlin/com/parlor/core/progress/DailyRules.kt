package com.parlor.core.progress

import com.parlor.core.model.*
import kotlinx.datetime.LocalDate

object DailyRules {
    fun rollover(ledger: RewardLedger, date: String): RewardLedger {
        val today = LocalDate.parse(date)
        val resetDate = ledger.latestResetDate
        val latest = resetDate?.let(LocalDate::parse)
        // Migrate the previous preview's single-day count before moving its reset date.
        val current = if (resetDate != null && ledger.rewardedViews >
            (ledger.rewardedViewsByDate[resetDate] ?: 0)) ledger.copy(rewardedViewsByDate =
            ledger.rewardedViewsByDate + (resetDate to ledger.rewardedViews)) else ledger
        if (latest != null && today <= latest) return current
        return current.copy(latestResetDate = date, dailyHints = 3, rewardedViews = current.rewardedViewsByDate[date] ?: 0)
    }

    fun spendHint(ledger: RewardLedger, date: String, pro: Boolean): RewardLedger? {
        return rollover(ledger, date) // Unlimited hints; never consume a saved allowance.
    }

    fun earnHints(ledger: RewardLedger, date: String, rewardId: String): RewardLedger? {
        return RewardRules.grant(ledger, date, rewardId, Reward.Hints, 0)
    }

    fun creditStreak(ledger: RewardLedger, date: String): RewardLedger {
        LocalDate.parse(date)
        return ledger.copy(creditedDates = ledger.creditedDates + date)
    }

    fun longestStreak(dates: Set<String>): Int {
        val days = dates.map { LocalDate.parse(it).toEpochDays() }.sorted()
        var longest = 0
        var current = 0
        var previous: Long? = null
        for (day in days) {
            current = if (previous != null && day.toLong() == previous + 1) current + 1 else 1
            longest = maxOf(longest, current)
            previous = day.toLong()
        }
        return longest
    }

    fun currentStreak(dates: Set<String>, today: String): Int {
        val days = dates.map { LocalDate.parse(it).toEpochDays().toLong() }.toSet()
        var cursor = LocalDate.parse(today).toEpochDays().toLong()
        if (cursor !in days) cursor--
        var count = 0
        while (cursor in days) { count++; cursor-- }
        return count
    }

    /** Frozen pool order + version define a schedule for any representable local date. */
    fun assignment(date: String, pool: List<LevelDefinition>, scheduleVersion: Int = 1): LevelDefinition {
        require(pool.isNotEmpty() && scheduleVersion > 0)
        val epoch = LocalDate.parse(date).toEpochDays().toLong()
        val index = indexFor(epoch, pool.size, scheduleVersion)
        return pool[index]
    }

    private fun indexFor(day: Long, size: Int, version: Int): Int {
        fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
        var step = 1_103_515_245 % size
        while (gcd(step, size) != 1) step++
        val reducedDay = (day % size + size) % size
        return ((reducedDay * step + version.toLong() * 12_345 % size) % size).toInt()
    }

    fun assignment(date: String, schedule: DailySchedule): Pair<Int, LevelRef> {
        require(schedule.epochs.isNotEmpty())
        require(schedule.epochs.map { it.version }.distinct().size == schedule.epochs.size)
        require(schedule.epochs.map { it.effectiveFrom }.distinct().size == schedule.epochs.size)
        require(schedule.epochs.all { it.version > 0 && it.pool.isNotEmpty() && it.pool.distinct().size == it.pool.size })
        val day = LocalDate.parse(date)
        val epochs = schedule.epochs.sortedBy { LocalDate.parse(it.effectiveFrom) }
        val epoch = epochs.lastOrNull { LocalDate.parse(it.effectiveFrom) <= day } ?: epochs.first()
        return epoch.version to epoch.pool[indexFor(day.toEpochDays().toLong(), epoch.pool.size, epoch.version)]
    }

    // PRO is the existing serialized value for an unlimited scored retry, including migrated saves.
    fun eligibility(claim: DailyClaim, pro: Boolean): DailyEligibility =
        if (!claim.firstCompletionClaimed) DailyEligibility.FIRST_COMPLETION else DailyEligibility.PRO

    fun withinHistory(date: String, today: String, latest: String?): Boolean {
        val anchor = maxOf(LocalDate.parse(today), latest?.let(LocalDate::parse) ?: LocalDate.parse(today)).toEpochDays()
        val age = anchor - LocalDate.parse(date).toEpochDays()
        return age in 0..29
    }

    fun improveDaily(previous: Result?, candidate: Result): Result = when {
        previous == null -> candidate
        candidate.points > previous.points -> candidate
        candidate.points == previous.points && candidate.timeMs < previous.timeMs -> candidate
        else -> previous
    }
}
