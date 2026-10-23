package com.parlor.core.progress

import com.parlor.core.model.AdHistory

object AdRules {
    fun interstitialAllowed(history: AdHistory, completions: Int, elapsedMs: Long, launchedMs: Long, boot: String?): Boolean {
        if (completions < 4 || completions - history.lastInterstitialCompletion < 3) return false
        if (elapsedMs < launchedMs || elapsedMs - launchedMs < 60_000) return false
        val prior = history.lastInterstitialElapsedMs ?: return true
        val sameBoot = boot != null && boot == history.lastInterstitialBoot
        // A reboot or missing boot identifier uses a conservative new 120-second interval.
        val anchor = if (sameBoot && !history.interstitialUnsettled) prior else maxOf(if (sameBoot) prior else 0, launchedMs)
        return elapsedMs >= anchor && elapsedMs - anchor >= 120_000
    }
}
