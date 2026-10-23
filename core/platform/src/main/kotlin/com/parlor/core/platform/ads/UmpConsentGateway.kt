package com.parlor.core.platform.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.ump.*

class UmpConsentGateway(context: Context, private val activity: () -> Activity?) : ConsentGateway {
    private val context = context.applicationContext
    private val information by lazy { UserMessagingPlatform.getConsentInformation(context) }
    private val main = Handler(Looper.getMainLooper())
    private fun deliver(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action)
    }
    override fun snapshot() = ConsentSnapshot(
        information.canRequestAds(), information.consentStatus == ConsentInformation.ConsentStatus.REQUIRED,
        information.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
    )
    override fun refresh(onFinished: (Boolean) -> Unit) {
        val host = activity()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return onFinished(false)
        information.requestConsentInfoUpdate(host, ConsentRequestParameters.Builder().build(),
            { deliver { onFinished(true) } }, { deliver { onFinished(false) } })
    }
    override fun load(onFinished: (PrivacyForm?) -> Unit) {
        UserMessagingPlatform.loadConsentForm(context, { loaded -> deliver {
            onFinished(object : PrivacyForm {
                override fun show(onDismissed: (Boolean) -> Unit) {
                    val host = activity()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return onDismissed(false)
                    loaded.show(host) { error -> deliver { onDismissed(error == null) } }
                }
            })
        } }, { deliver { onFinished(null) } })
    }
    override fun privacy(onDismissed: (Boolean) -> Unit) {
        val host = activity()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return onDismissed(false)
        UserMessagingPlatform.showPrivacyOptionsForm(host) { error -> deliver { onDismissed(error == null) } }
    }
}
