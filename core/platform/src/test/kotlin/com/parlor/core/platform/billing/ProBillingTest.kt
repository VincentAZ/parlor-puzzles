package com.parlor.core.platform.billing

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.*

class ProBillingTest {
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val key = Base64.getEncoder().encodeToString(pair.public.encoded)
    private val verifier = PlayPurchaseVerifier(key,"com.parlor.test","parlor_pro")
    private fun receipt(state: Int = 0, acknowledged: Boolean = false, token: String = "token-one", changes: Map<String,JsonElement> = emptyMap()): SignedPurchase {
        val fields = mutableMapOf<String,JsonElement>("packageName" to JsonPrimitive("com.parlor.test"),"productId" to JsonPrimitive("parlor_pro"),
            "purchaseToken" to JsonPrimitive(token),"purchaseState" to JsonPrimitive(state),"acknowledged" to JsonPrimitive(acknowledged),"quantity" to JsonPrimitive(1))
        fields.putAll(changes)
        val data = JsonObject(fields).toString()
        val signer = Signature.getInstance("SHA1withRSA").apply { initSign(pair.private);update(data.toByteArray()) }
        return SignedPurchase(data,Base64.getEncoder().encodeToString(signer.sign()))
    }
    private class MemoryStore : PurchaseStore {
        var saved = PurchaseCache();var writes = 0;var failAt = -1;var readFailure = false
        override suspend fun load(): PurchaseCache { if(readFailure) error("disk read");return saved }
        override suspend fun save(cache: PurchaseCache) { writes++;if(writes == failAt) error("disk write");saved = cache }
    }
    private class Gateway : PurchaseGateway {
        var owned = StoreReply(StoreCode.OK,emptyList<SignedPurchase>())
        var offered = ProOffer("$2.99","one")
        var connection = StoreCode.OK;var ack = StoreCode.OK;var acknowledgements = 0;var queries = 0;var connections = 0
        var beforeAck: suspend () -> Unit = {}
        override suspend fun connect(): StoreCode { connections++;return connection }
        override suspend fun purchases(): StoreReply<List<SignedPurchase>> { queries++;return owned }
        override suspend fun offer() = StoreReply(StoreCode.OK,offered)
        override suspend fun acknowledge(token: String): StoreCode { acknowledgements++;beforeAck();return ack }
    }

    @Test fun signaturesAndAllPurchaseFieldsMustValidateBeforeGranting() {
        assertTrue(verifier.configured)
        assertNotNull(verifier.verify(receipt()))
        for(changes in listOf(mapOf("packageName" to JsonPrimitive("wrong.package")),mapOf("productId" to JsonPrimitive("other")),
            mapOf("purchaseToken" to JsonPrimitive("")),mapOf("quantity" to JsonPrimitive(2)),mapOf("quantity" to JsonPrimitive("invalid")),mapOf("purchaseState" to JsonPrimitive(9))))
            assertNull(verifier.verify(receipt(changes = changes)))
        val valid = receipt()
        assertNull(verifier.verify(valid.copy(data = valid.data.replace("token-one","token-two"))))
        assertNull(verifier.verify(valid.copy(signature = "invalid")))
        assertFalse(PlayPurchaseVerifier("","com.parlor.test","parlor_pro").configured)
        assertNull(PlayPurchaseVerifier(key,"other.package","parlor_pro").verify(valid))
        assertNull(verifier.verify(SignedPurchase("x".repeat(65_537),"invalid")))
    }

    @Test fun pendingThenPurchasedIsDurableBeforeAcknowledgementAndSurvivesRestart() = runTest {
        val store = MemoryStore();val gateway = Gateway();var scheduled = 0
        val billing = ProBilling(gateway,store,verifier,true) { scheduled++ }
        gateway.owned = StoreReply(StoreCode.OK,listOf(receipt(state = 4)))
        billing.refresh()
        assertFalse(billing.hasPro);assertFalse(billing.state.value.permitsAds);assertFalse(billing.state.value.canBuy)
        assertEquals(PurchaseNotice.PENDING,billing.state.value.notice);assertEquals(0,gateway.acknowledgements)
        gateway.beforeAck = { assertTrue(store.saved.purchases.isNotEmpty());assertFalse(billing.hasPro);assertTrue(scheduled>0) }
        billing.purchasesUpdated(StoreCode.OK,listOf(receipt()))
        assertTrue(billing.hasPro);assertEquals(1,gateway.acknowledgements)
        val restart = ProBilling(gateway,store,verifier,true)
        restart.loadLocal();assertTrue(restart.hasPro)
        gateway.connection = StoreCode.RETRY;restart.refresh()
        assertTrue(restart.hasPro);assertFalse(restart.state.value.permitsAds)
    }

    @Test fun duplicateAndEmptyCallbacksCannotRevokeOrRepeatAcknowledgement() = runTest {
        val gateway = Gateway();val store = MemoryStore();val billing = ProBilling(gateway,store,verifier,true)
        billing.purchasesUpdated(StoreCode.OK,listOf(receipt()))
        repeat(3) { billing.purchasesUpdated(StoreCode.OK,listOf(receipt())) }
        billing.purchasesUpdated(StoreCode.OK,emptyList())
        billing.purchasesUpdated(StoreCode.OK,listOf(receipt(state = 4))) // delayed pending callback
        assertTrue(billing.hasPro);assertEquals(1,gateway.acknowledgements);assertEquals(1,store.saved.purchases.size)
        gateway.owned = StoreReply(StoreCode.OK,emptyList())
        billing.refresh()
        assertFalse(billing.hasPro);assertEquals(ProAccess.FREE,billing.state.value.access)
        assertTrue(billing.state.value.permitsAds);assertTrue(store.saved.purchases.isEmpty())
        assertNotNull(billing.state.value.offer) // A revoked customer can purchase again.
    }

    @Test fun failedOrInvalidOwnershipQueriesKeepConfirmedPro() = runTest {
        val store = MemoryStore().apply { saved = PurchaseCache(purchases = listOf(receipt(acknowledged = true))) }
        val gateway = Gateway();val billing = ProBilling(gateway,store,verifier,true)
        gateway.owned = StoreReply(StoreCode.RETRY)
        billing.refresh();assertTrue(billing.hasPro)
        gateway.owned = StoreReply(StoreCode.OK,listOf(receipt().copy(signature = "wrong")))
        billing.refresh();assertTrue(billing.hasPro)
        gateway.owned = StoreReply(StoreCode.OK,emptyList());store.failAt = store.writes+1
        billing.refresh();assertTrue(billing.hasPro)
        gateway.owned = StoreReply(StoreCode.OK,listOf(receipt(acknowledged = true)))
        billing.refresh(restoring = true);assertEquals(PurchaseNotice.RESTORED,billing.state.value.notice)
    }

    @Test fun delayedCallbackCannotRestoreARevokedPurchaseAfterRestart() = runTest {
        val store = MemoryStore();val gateway = Gateway();var billing = ProBilling(gateway,store,verifier,true)
        val old = receipt(acknowledged = true)
        billing.purchasesUpdated(StoreCode.OK,listOf(old));assertTrue(billing.hasPro)
        billing.refresh();assertFalse(billing.hasPro)
        assertEquals(1,store.saved.revokedTokenHashes.size)
        billing = ProBilling(gateway,store,verifier,true)
        billing.purchasesUpdated(StoreCode.OK,listOf(old));assertFalse(billing.hasPro)
        assertEquals(0,gateway.acknowledgements)
        gateway.owned = StoreReply(StoreCode.OK,listOf(old))
        billing.refresh();assertTrue(billing.hasPro) // A new complete query is authoritative.
        assertTrue(store.saved.revokedTokenHashes.isEmpty())
        gateway.owned = StoreReply(StoreCode.OK,emptyList());billing.refresh()
        billing.purchasesUpdated(StoreCode.OK,listOf(receipt(token = "new-purchase")))
        assertTrue(billing.hasPro);assertEquals(1,gateway.acknowledgements)
    }

    @Test fun acknowledgementFailureRemainsUnentitledAndRetriesFromDisk() = runTest {
        val store = MemoryStore();val gateway = Gateway().apply { ack = StoreCode.RETRY };var scheduled = 0
        val billing = ProBilling(gateway,store,verifier,true) { scheduled++ }
        billing.purchasesUpdated(StoreCode.OK,listOf(receipt()))
        assertFalse(billing.hasPro);assertEquals(PurchaseNotice.ACKNOWLEDGING,billing.state.value.notice);assertTrue(scheduled>0)
        val restart = ProBilling(gateway,store,verifier,true)
        gateway.owned = StoreReply(StoreCode.OK,store.saved.purchases);gateway.ack = StoreCode.OK
        restart.refresh();assertTrue(restart.hasPro)
        assertEquals(2,gateway.acknowledgements)
    }

    @Test fun neitherPreAckNorPostAckWriteFailureCanGrantUndurableAccess() = runTest {
        for(failedWrite in listOf(1,2)) {
            var scheduled = 0
            val store = MemoryStore().apply { failAt = failedWrite };val gateway = Gateway();val billing = ProBilling(gateway,store,verifier,true) { scheduled++ }
            billing.purchasesUpdated(StoreCode.OK,listOf(receipt()))
            assertFalse(billing.hasPro)
            assertTrue(scheduled>0)
            assertEquals(if(failedWrite==1) 0 else 1,gateway.acknowledgements)
            val restart = ProBilling(gateway,store,verifier,true);restart.loadLocal();assertFalse(restart.hasPro)
            gateway.owned = StoreReply(StoreCode.OK,listOf(receipt(acknowledged = failedWrite == 2)))
            restart.refresh();assertTrue(restart.hasPro)
        }
    }

    @Test fun schedulingFailureDoesNotPreventAcknowledgementAndDuplicateQueryKeepsBestState() = runTest {
        val store = MemoryStore();val gateway = Gateway();val billing = ProBilling(gateway,store,verifier,true) { error("scheduler quota") }
        gateway.owned = StoreReply(StoreCode.OK,listOf(receipt(),receipt(state = 4),receipt(acknowledged = true)))
        billing.refresh()
        assertTrue(billing.hasPro);assertEquals(0,gateway.acknowledgements);assertEquals(1,store.saved.purchases.size)
        gateway.owned = StoreReply(StoreCode.OK,emptyList());billing.refresh()
        billing.purchasesUpdated(StoreCode.OK,listOf(receipt(token = "new-token")))
        assertTrue(billing.hasPro);assertEquals(1,gateway.acknowledgements)
        val restart = ProBilling(gateway,store,verifier,true);restart.loadLocal();assertTrue(restart.hasPro)
    }

    @Test fun unavailableAndCorruptCacheStayUnknownAndDoNotInitializeAdsOrBilling() = runTest {
        val gateway = Gateway();val store = MemoryStore().apply { readFailure = true }
        val disabled = ProBilling(gateway,store,verifier,false)
        disabled.refresh();assertFalse(disabled.hasPro);assertFalse(disabled.state.value.permitsAds);assertEquals(0,gateway.connections)
        val billing = ProBilling(gateway,store,verifier,true)
        billing.loadLocal();assertEquals(ProAccess.UNKNOWN,billing.state.value.access)
        gateway.connection = StoreCode.RETRY;billing.refresh();assertFalse(billing.state.value.permitsAds)
        gateway.connection = StoreCode.OK;billing.refresh(restoring = true)
        assertEquals(PurchaseNotice.NOT_FOUND,billing.state.value.notice);assertTrue(billing.state.value.permitsAds)
    }

    @Test fun checkoutUsesCurrentLocalizedPriceAndSerializesRepeatedTaps() = runTest {
        val gateway = Gateway();val billing = ProBilling(gateway,MemoryStore(),verifier,true)
        billing.refresh();assertEquals("$2.99",billing.state.value.offer!!.price)
        gateway.offered = ProOffer("€3.49","two")
        var launches = 0
        billing.buy { launches++;StoreCode.OK }
        assertEquals(0,launches);assertEquals(PurchaseNotice.PRICE_CHANGED,billing.state.value.notice)
        billing.buy { assertEquals("€3.49",it.price);launches++;StoreCode.OK }
        billing.buy { launches++;StoreCode.OK }
        assertEquals(1,launches);assertFalse(billing.state.value.permitsAds)
        billing.purchasesUpdated(StoreCode.CANCELLED,emptyList())
        assertEquals(PurchaseNotice.CANCELLED,billing.state.value.notice);assertFalse(billing.hasPro)
    }

    @Test fun concurrentCallbacksAndRefreshKeepOneReceiptAndOneGrant() = runTest {
        val gateway = Gateway();val store = MemoryStore();val billing = ProBilling(gateway,store,verifier,true)
        gateway.owned = StoreReply(StoreCode.OK,listOf(receipt()))
        coroutineScope { repeat(12) { index -> launch { if(index%2==0) billing.refresh() else billing.purchasesUpdated(StoreCode.OK,listOf(receipt())) } } }
        assertTrue(billing.hasPro);assertEquals(1,gateway.acknowledgements);assertEquals(1,store.saved.purchases.size)
    }
}
