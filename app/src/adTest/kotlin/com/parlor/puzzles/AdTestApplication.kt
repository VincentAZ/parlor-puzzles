package com.parlor.puzzles

import com.parlor.core.platform.ads.AdUnits
import com.parlor.core.platform.ads.AdsWork
import com.parlor.core.platform.ads.AndroidAds
import com.parlor.core.platform.ads.ConsentGateway
import com.parlor.core.platform.ads.ConsentSnapshot
import com.parlor.core.platform.ads.PrivacyForm
import com.parlor.core.platform.billing.ProAccess
import com.parlor.core.platform.billing.ProState
import com.parlor.core.platform.billing.PurchaseNotice
import kotlinx.coroutines.flow.MutableStateFlow

/** Only compiled into the separate adTest app; it never establishes real ownership or consent. */
class AdTestApplication : ParlorApplication() {
    override fun createAds(units: AdUnits, work: AdsWork): AndroidAds {
        check(BuildConfig.APPLICATION_ID == "com.parlor.puzzles.adtest")
        check(units == AdUnits(
            "ca-app-pub-3940256099942544~3347511713",
            "ca-app-pub-3940256099942544/9214589741",
            "ca-app-pub-3940256099942544/1033173712",
            "ca-app-pub-3940256099942544/5224354917",
        )) { "The ad preview must use Google's demo units exclusively." }
        val previewFree = MutableStateFlow(ProState(ProAccess.FREE, PurchaseNotice.READY))
        val previewConsent = object : ConsentGateway {
            override fun snapshot() = ConsentSnapshot(canRequestAds = true)
            override fun refresh(onFinished: (Boolean) -> Unit) = onFinished(true)
            override fun load(onFinished: (PrivacyForm?) -> Unit) = onFinished(null)
            override fun privacy(onDismissed: (Boolean) -> Unit) = onDismissed(true)
        }
        return AndroidAds(this, units, previewFree, work, previewConsent)
    }
}
