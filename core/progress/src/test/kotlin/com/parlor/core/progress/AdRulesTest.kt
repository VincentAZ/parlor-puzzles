package com.parlor.core.progress

import com.parlor.core.model.AdHistory
import kotlin.test.*

class AdRulesTest {
    @Test fun firstAdNeedsFourCompletionsAndSixtySecondsOfThisLaunch() {
        for (count in 0..3) assertFalse(AdRules.interstitialAllowed(AdHistory(), count, 1_000_000, 0, "device:1"))
        assertFalse(AdRules.interstitialAllowed(AdHistory(), 4, 60_099, 100, "device:1"))
        assertTrue(AdRules.interstitialAllowed(AdHistory(), 4, 60_100, 100, "device:1"))
    }
    @Test fun bothThreeMoreCompletionsAndMonotonicSpacingAreRequired() {
        val history = AdHistory(4, 70_000, "device:1")
        assertFalse(AdRules.interstitialAllowed(history, 6, 190_000, 0, "device:1"))
        assertFalse(AdRules.interstitialAllowed(history, 7, 189_999, 0, "device:1"))
        assertTrue(AdRules.interstitialAllowed(history, 7, 190_000, 0, "device:1"))
        assertFalse(AdRules.interstitialAllowed(history, 7, 190_000, 150_000, "device:1"))
    }
    @Test fun rebootOrUnknownBootStartsAConservativeSpacingWindow() {
        val history = AdHistory(4, 900_000, "device:1")
        for (boot in listOf("device:2", "other-device:1", null)) {
            assertFalse(AdRules.interstitialAllowed(history, 7, 120_099, 100, boot))
            assertTrue(AdRules.interstitialAllowed(history, 7, 120_100, 100, boot))
        }
        assertFalse(AdRules.interstitialAllowed(history, 7, 50_000, 100, "device:1"))
    }
    @Test fun processDeathDuringAnAdOrFailedCloseWriteCannotShortenTheNextInterval() {
        val history = AdHistory(lastInterstitialCompletion = 4, lastInterstitialElapsedMs = 60_000,
            lastInterstitialBoot = "device:1", interstitialUnsettled = true)
        assertFalse(AdRules.interstitialAllowed(history, 7, 300_000, 240_000, "device:1"))
        assertTrue(AdRules.interstitialAllowed(history, 7, 360_000, 240_000, "device:1"))
    }
}
