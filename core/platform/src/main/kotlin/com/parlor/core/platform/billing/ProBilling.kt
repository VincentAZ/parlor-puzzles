package com.parlor.core.platform.billing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.security.MessageDigest

enum class ProAccess { UNKNOWN, FREE, PRO }
enum class PurchaseNotice { CHECKING, READY, UNAVAILABLE, PENDING, ACKNOWLEDGING, RETRY, CANCELLED, RESTORED, NOT_FOUND, PRICE_CHANGED, OPENING }
data class ProOffer(val price: String, val key: String)
data class ProState(val access: ProAccess = ProAccess.UNKNOWN, val notice: PurchaseNotice = PurchaseNotice.CHECKING,
    val offer: ProOffer? = null, val busy: Boolean = false) {
    val isPro get() = access == ProAccess.PRO
    // Future ads may initialize only after a successful free reconciliation.
    val permitsAds get() = access == ProAccess.FREE && notice !in setOf(PurchaseNotice.PENDING, PurchaseNotice.ACKNOWLEDGING, PurchaseNotice.OPENING)
    val canBuy get() = !isPro && !busy && offer != null && notice !in setOf(PurchaseNotice.PENDING, PurchaseNotice.ACKNOWLEDGING, PurchaseNotice.UNAVAILABLE)
}
@Serializable data class SignedPurchase(val data: String, val signature: String, val acknowledgedLocally: Boolean = false)
@Serializable data class PurchaseCache(val version: Int = 1, val purchases: List<SignedPurchase> = emptyList(),
    val revokedTokenHashes: Set<String> = emptySet())
data class VerifiedPurchase(val token: String, val pending: Boolean, val acknowledged: Boolean)
fun interface PurchaseVerifier { fun verify(purchase: SignedPurchase): VerifiedPurchase? }
interface PurchaseStore { suspend fun load(): PurchaseCache; suspend fun save(cache: PurchaseCache) }
enum class StoreCode { OK, CANCELLED, UNAVAILABLE, RETRY, ALREADY_OWNED }
data class StoreReply<T>(val code: StoreCode, val value: T? = null)
interface PurchaseGateway {
    suspend fun connect(): StoreCode
    suspend fun purchases(): StoreReply<List<SignedPurchase>>
    suspend fun offer(): StoreReply<ProOffer>
    suspend fun acknowledge(token: String): StoreCode
}

/** Serialized purchase reconciliation. No progress or rewards are stored here. */
class ProBilling(private val gateway: PurchaseGateway, private val store: PurchaseStore,
    private val verifier: PurchaseVerifier, private val configured: Boolean,
    private val scheduleRetry: () -> Unit = {}) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(ProState())
    val state = mutable.asStateFlow()
    private var cache = PurchaseCache()
    private var loaded = false
    private var authoritativeFree = false
    val hasPro get() = state.value.isPro
    val needsRetry get() = state.value.notice in setOf(PurchaseNotice.PENDING, PurchaseNotice.ACKNOWLEDGING, PurchaseNotice.RETRY)

    suspend fun loadLocal() = mutex.withLock { load() }
    private suspend fun load() {
        if (loaded) return
        try {
            val saved = store.load()
            require(saved.version == 1 && saved.purchases.size <= 20 && saved.revokedTokenHashes.size <= 8192)
            require(saved.revokedTokenHashes.all { it.matches(Regex("[0-9a-f]{64}")) })
            cache = saved.copy(purchases = saved.purchases.filter { verifier.verify(it) != null })
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { cache = PurchaseCache() }
        loaded = true
        publish(if(configured) PurchaseNotice.CHECKING else PurchaseNotice.UNAVAILABLE)
        if (cache.purchases.any { verifier.verify(it)?.let { p -> p.pending || !p.acknowledged } == true }) queueRetry()
    }

    private fun publish(notice: PurchaseNotice, busy: Boolean = false, offer: ProOffer? = mutable.value.offer) {
        val pro = cache.purchases.any { verifier.verify(it)?.let { p -> !p.pending && p.acknowledged } == true }
        mutable.value = ProState(if(pro) ProAccess.PRO else if(authoritativeFree && cache.purchases.isEmpty()) ProAccess.FREE else ProAccess.UNKNOWN, notice, offer, busy)
    }
    private fun tokenHash(token: String) = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    private suspend fun persist(purchases: List<SignedPurchase>, revoked: Set<String> = cache.revokedTokenHashes) {
        val next = PurchaseCache(purchases = purchases, revokedTokenHashes = revoked)
        // Even if the receipt write fails or the process dies immediately after it,
        // the persisted platform job can query Play and retry the outstanding purchase.
        if(purchases.any { verifier.verify(it)?.let { p -> p.pending || !p.acknowledged } == true }) queueRetry()
        store.save(next) // Entitlement changes only after the durable write succeeds.
        cache = next
    }
    private fun queueRetry() {
        try { scheduleRetry() }
        catch(cancelled: CancellationException) { throw cancelled }
        catch(_: RuntimeException) { /* Reconciliation still retries on foreground/reconnect. */ }
    }
    private suspend fun acknowledge() {
        val waiting = cache.purchases.mapNotNull { receipt -> verifier.verify(receipt)?.takeIf { !it.pending && !it.acknowledged }?.let { receipt to it } }
        if (waiting.isNotEmpty()) queueRetry() // Queue before the network call; survive process death.
        for ((receipt, purchase) in waiting) {
            publish(PurchaseNotice.ACKNOWLEDGING, busy = true)
            if (gateway.acknowledge(purchase.token) == StoreCode.OK) {
                persist(cache.purchases.map { if(it == receipt) it.copy(acknowledgedLocally = true) else it })
            }
        }
    }
    private fun settled(restoring: Boolean): PurchaseNotice = when {
        cache.purchases.any { verifier.verify(it)?.let { p -> !p.pending && p.acknowledged } == true } -> if(restoring) PurchaseNotice.RESTORED else PurchaseNotice.READY
        cache.purchases.any { verifier.verify(it)?.pending == false } -> PurchaseNotice.ACKNOWLEDGING
        cache.purchases.any { verifier.verify(it)?.pending == true } -> PurchaseNotice.PENDING
        restoring -> PurchaseNotice.NOT_FOUND
        else -> PurchaseNotice.READY
    }

    suspend fun refresh(restoring: Boolean = false) = mutex.withLock {
        load()
        if (!configured) { publish(PurchaseNotice.UNAVAILABLE); return@withLock }
        publish(PurchaseNotice.CHECKING, busy = true)
        try {
            if (gateway.connect() != StoreCode.OK) { publish(PurchaseNotice.RETRY, offer = null); return@withLock }
            val response = gateway.purchases()
            if (response.code != StoreCode.OK || response.value == null) { publish(PurchaseNotice.RETRY, offer = null); return@withLock }
            val purchases = response.value
            // Invalid data cannot establish free status or revoke a previously confirmed entitlement.
            require(purchases.size <= 20 && purchases.all { verifier.verify(it) != null })
            val unique = purchases.groupBy { requireNotNull(verifier.verify(it)).token }.values.map { group ->
                val receipt = group.maxBy { candidate -> requireNotNull(verifier.verify(candidate)).let { if(it.pending) 0 else if(it.acknowledged) 2 else 1 } }
                val token = requireNotNull(verifier.verify(receipt)).token
                val prior = cache.purchases.any { verifier.verify(it)?.let { p -> p.token == token && p.acknowledged } == true }
                receipt.copy(acknowledgedLocally = receipt.acknowledgedLocally || prior)
            }
            val returnedTokens = unique.map { tokenHash(requireNotNull(verifier.verify(it)).token) }.toSet()
            val priorTokens = cache.purchases.mapNotNull { verifier.verify(it)?.token?.let(::tokenHash) }.toSet()
            // A delayed partial callback must not resurrect a purchase that this full
            // query removed. Only a later complete query can restore such a token.
            // Bound history well beyond normal refund use without retaining raw tokens.
            val revoked = ((cache.revokedTokenHashes + priorTokens) - returnedTokens).toList().takeLast(8192).toSet()
            persist(unique, revoked) // Only a successful full query may revoke cached Pro.
            authoritativeFree = unique.isEmpty()
            acknowledge()
            val ownsPro = cache.purchases.any { verifier.verify(it)?.let { p -> !p.pending && p.acknowledged } == true }
            val offer = if(!ownsPro) gateway.offer().takeIf { it.code == StoreCode.OK }?.value else null
            publish(settled(restoring), offer = offer)
            if(needsRetry) queueRetry()
        } catch (cancelled: CancellationException) { publish(PurchaseNotice.RETRY); throw cancelled }
        catch (_: Exception) { publish(PurchaseNotice.RETRY, offer = null) }
    }

    /** Store callbacks are partial lists; an empty callback must never revoke ownership. */
    suspend fun purchasesUpdated(code: StoreCode, receipts: List<SignedPurchase>) = mutex.withLock {
        load()
        if(!configured) return@withLock
        if(code != StoreCode.OK) { publish(if(code == StoreCode.CANCELLED) PurchaseNotice.CANCELLED else PurchaseNotice.RETRY); return@withLock }
        try {
            require(receipts.size <= 20 && receipts.all { verifier.verify(it) != null })
            val currentReceipts = receipts.filter { tokenHash(requireNotNull(verifier.verify(it)).token) !in cache.revokedTokenHashes }
            if(currentReceipts.isNotEmpty()) authoritativeFree = false
            val merged = cache.purchases.toMutableList()
            for(receipt in currentReceipts) {
                val token = requireNotNull(verifier.verify(receipt)).token
                val prior = merged.firstOrNull { verifier.verify(it)?.token == token }
                merged.removeAll { verifier.verify(it)?.token == token }
                merged += if(prior != null && verifier.verify(prior)?.let { !it.pending && it.acknowledged } == true && verifier.verify(receipt)?.pending == true) prior
                    else receipt.copy(acknowledgedLocally = receipt.acknowledgedLocally || prior?.let { verifier.verify(it)?.acknowledged } == true)
            }
            require(merged.size <= 20)
            persist(merged)
            acknowledge()
            publish(settled(false))
            if(needsRetry) queueRetry()
        } catch(cancelled: CancellationException) { publish(PurchaseNotice.RETRY); throw cancelled }
        catch(_: Exception) { publish(PurchaseNotice.RETRY) }
    }

    suspend fun buy(launch: suspend (ProOffer) -> StoreCode) = mutex.withLock {
        if(!configured || !mutable.value.canBuy) return@withLock
        val shown = requireNotNull(mutable.value.offer)
        publish(PurchaseNotice.OPENING, busy = true)
        try {
            if(gateway.connect() != StoreCode.OK) { publish(PurchaseNotice.RETRY); return@withLock }
            val current = gateway.offer().takeIf { it.code == StoreCode.OK }?.value
            if(current == null) { publish(PurchaseNotice.RETRY, offer = null); return@withLock }
            if(current != shown) { publish(PurchaseNotice.PRICE_CHANGED, offer = current); return@withLock }
            when(launch(current)) {
                StoreCode.OK -> publish(PurchaseNotice.OPENING, busy = true)
                StoreCode.CANCELLED -> publish(PurchaseNotice.CANCELLED)
                else -> publish(PurchaseNotice.RETRY)
            }
        } catch(cancelled: CancellationException) { publish(PurchaseNotice.RETRY); throw cancelled }
        catch(_: Exception) { publish(PurchaseNotice.RETRY) }
    }
}
