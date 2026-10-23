package com.parlor.core.platform.ads

import kotlinx.coroutines.*

interface ResultsAd {
    fun show(onShown: () -> Unit, onClosed: () -> Unit)
    fun close()
}

/** Only a user departure from completed Results can present a ready interstitial. */
class ResultsAds(
    private val scope: CoroutineScope,
    private val takeReady: () -> ResultsAd?,
    private val mayPresent: () -> Boolean,
    private val claim: suspend (String, Boolean) -> Boolean,
    private val dismissed: suspend (String) -> Boolean,
    private val mayOfferPro: () -> Boolean = mayPresent,
) {
    private var departing = false

    fun leave(attemptId: String, navigate: () -> Unit, offerPro: (finish: (continueNavigation: Boolean) -> Unit) -> Unit) {
        if (departing) return
        departing = true
        val ad = if (mayPresent()) takeReady() else null
        scope.launch {
            val allowed = try { withTimeoutOrNull(250) { claim(attemptId, ad != null) } == true }
                catch (cancelled: CancellationException) { ad?.close(); departing = false; throw cancelled }
                catch (_: Exception) { false }
            var finished = false
            fun proceed(continueNavigation: Boolean = true) {
                if (!finished) { finished = true; departing = false; if (continueNavigation) navigate() }
            }
            if (!allowed || ad == null || !mayPresent()) { ad?.close(); proceed(); return@launch }
            var shown = false
            var closed = false
            try { ad.show(onShown = { shown = true }, onClosed = {
                if (!closed) {
                    closed = true; ad.close()
                    scope.launch {
                        val prompt = if (shown) try { withTimeoutOrNull(250) { dismissed(attemptId) } == true }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { false } else false
                        if (prompt && mayOfferPro()) offerPro(::proceed) else proceed()
                    }
                }
            }) } catch (_: Exception) { ad.close(); proceed() }
        }
    }
}
