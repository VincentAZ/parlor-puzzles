package com.parlor.core.platform.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** One application-scoped BillingClient; never consumes the non-consumable Pro product. */
class PlayBillingGateway(context: Context, private val productId: String,
    private val updated: (StoreCode, List<SignedPurchase>) -> Unit,
    private val disconnected: () -> Unit) : PurchaseGateway {
    private val client by lazy {
        BillingClient.newBuilder(context.applicationContext)
            .setListener { result, purchases -> updated(code(result), purchases.orEmpty().filter { productId in it.products }
                .map { SignedPurchase(it.originalJson,it.signature) }) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection().build()
    }
    private var details: ProductDetails? = null
    private var chosenToken: String? = null
    private var lastOffer: ProOffer? = null
    private var connectionStarted = false
    override suspend fun connect(): StoreCode = withContext(Dispatchers.Main.immediate) {
        if(client.isReady || connectionStarted) return@withContext StoreCode.OK
        withTimeoutOrNull(15_000) { suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connectionStarted = result.responseCode == BillingClient.BillingResponseCode.OK
                    if(continuation.isActive) continuation.resume(code(result))
                }
                override fun onBillingServiceDisconnected() { disconnected() }
            })
        } } ?: StoreCode.RETRY
    }
    override suspend fun purchases(): StoreReply<List<SignedPurchase>> = withContext(Dispatchers.Main.immediate) {
        withTimeoutOrNull(15_000) { suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
                if(continuation.isActive) continuation.resume(StoreReply(code(result), if(result.responseCode == BillingClient.BillingResponseCode.OK)
                    purchases.filter { productId in it.products }.map { SignedPurchase(it.originalJson,it.signature) } else null))
            }
        } } ?: StoreReply(StoreCode.RETRY)
    }
    override suspend fun acknowledge(token: String): StoreCode = withContext(Dispatchers.Main.immediate) {
        withTimeoutOrNull(15_000) { suspendCancellableCoroutine { continuation ->
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()) {
                if(continuation.isActive) continuation.resume(code(it))
            }
        } } ?: StoreCode.RETRY
    }
    override suspend fun offer(): StoreReply<ProOffer> = withContext(Dispatchers.Main.immediate) {
        withTimeoutOrNull(15_000) { suspendCancellableCoroutine { continuation ->
            val product = QueryProductDetailsParams.Product.newBuilder().setProductId(productId).setProductType(BillingClient.ProductType.INAPP).build()
            client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { result, response ->
                if(continuation.isActive) {
                    val found = response.productDetailsList.singleOrNull { it.productId == productId }
                    // V1 is one permanent buy option, not a rental, preorder or quantity offer.
                    val option = found?.oneTimePurchaseOfferDetailsList?.filter { it.rentalDetails == null && it.preorderDetails == null && it.limitedQuantityInfo == null }?.singleOrNull()
                    details = found;chosenToken = option?.offerToken
                    lastOffer = option?.let { ProOffer(it.formattedPrice,"${it.offerToken}:${it.priceCurrencyCode}:${it.priceAmountMicros}") }
                    continuation.resume(StoreReply(if(result.responseCode == BillingClient.BillingResponseCode.OK && lastOffer == null) StoreCode.UNAVAILABLE else code(result),lastOffer))
                }
            }
        } } ?: StoreReply(StoreCode.RETRY)
    }
    suspend fun launch(activity: Activity, offer: ProOffer): StoreCode = withContext(Dispatchers.Main.immediate) {
        if(activity.isFinishing || activity.isDestroyed || offer != lastOffer || details == null || chosenToken == null) return@withContext StoreCode.RETRY
        val product = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(requireNotNull(details)).setOfferToken(requireNotNull(chosenToken)).build()
        code(client.launchBillingFlow(activity,BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build()))
    }
    suspend fun close() = withContext(Dispatchers.Main.immediate) {
        client.endConnection()
        connectionStarted = false;details = null;chosenToken = null;lastOffer = null
    }
    private fun code(result: BillingResult): StoreCode = when(result.responseCode) {
        BillingClient.BillingResponseCode.OK -> StoreCode.OK
        BillingClient.BillingResponseCode.USER_CANCELED -> StoreCode.CANCELLED
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> StoreCode.ALREADY_OWNED
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE, BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED, BillingClient.BillingResponseCode.DEVELOPER_ERROR -> StoreCode.UNAVAILABLE
        else -> StoreCode.RETRY
    }
}
