package com.parlor.core.progress

import com.parlor.core.model.*
import kotlinx.datetime.LocalDate

object RewardRules {
    fun canOffer(ledger: RewardLedger, date: String, reward: Reward, level: LevelDefinition? = null): Boolean {
        val current = DailyRules.rollover(ledger, date)
        return current.latestResetDate == date && current.rewardedViews < 10 && when (reward) {
            Reward.Hints -> true
            is Reward.LevelAccess -> level?.id == reward.levelId && level.tier >= Tier.HARD
            is Reward.DailyRetry -> reward.date == date && current.dailyClaims[date]?.firstCompletionClaimed == true
        }
    }

    /** Call only for a verified completed rewarded view. All reward kinds share one cap. */
    fun grant(ledger: RewardLedger, date: String, rewardId: String, reward: Reward, nowMs: Long,
              level: LevelDefinition? = null): RewardLedger? {
        val current = DailyRules.rollover(ledger, date)
        if (current.latestResetDate != date) return null
        return grantEarned(current, date, EarnedReward(rewardId, reward, date, nowMs), level)
    }

    /** Recovery uses the callback's date/time, even after a later allowance reset or Pro upgrade. */
    fun grantEarned(ledger: RewardLedger, today: String, receipt: EarnedReward, level: LevelDefinition? = null): RewardLedger? {
        LocalDate.parse(receipt.earnedDate)
        LocalDate.parse(receipt.offeredDate)
        val current = DailyRules.rollover(DailyRules.rollover(ledger, today), receipt.earnedDate)
        val count = current.rewardedViewsByDate[receipt.earnedDate] ?: 0
        if (count >= 10 || receipt.id in current.claims) return null
        val reward = receipt.reward
        val awarded = when (reward) {
            Reward.Hints -> current.copy(earnedHints = current.earnedHints + 2)
            is Reward.LevelAccess -> {
                if (level?.id != reward.levelId || level.tier < Tier.HARD) return null
                current.copy(temporaryUnlocks = current.temporaryUnlocks +
                    (level.id to maxOf(current.temporaryUnlocks[level.id] ?: 0, receipt.earnedAtMs + 86_400_000)))
            }
            is Reward.DailyRetry -> {
                // The explicit offer was for today's challenge when presentation began.
                // Crossing midnight while watching does not substitute another challenge.
                if (reward.date != receipt.offeredDate) return null
                val claim = current.dailyClaims[reward.date] ?: return null
                if (!claim.firstCompletionClaimed) return null
                current.copy(dailyClaims = current.dailyClaims + (reward.date to claim.copy(earnedRetries = claim.earnedRetries + 1)))
            }
        }
        return awarded.copy(rewardedViews = if (awarded.latestResetDate == receipt.earnedDate) count + 1 else awarded.rewardedViews,
            rewardedViewsByDate = awarded.rewardedViewsByDate + (receipt.earnedDate to count + 1), claims = awarded.claims + receipt.id)
    }
}
