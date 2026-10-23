package com.parlor.core.platform.billing

import kotlinx.serialization.json.*
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Client-only verification, deliberately without a backend or Play Integrity. */
class PlayPurchaseVerifier(publicKey: String, private val packageName: String, private val productId: String) : PurchaseVerifier {
    private val key = runCatching { KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(
        Base64.getDecoder().decode(publicKey.filterNot(Char::isWhitespace)))) }.getOrNull()
    val configured get() = key != null && productId.matches(Regex("[a-z0-9][a-z0-9._]{0,99}"))
    override fun verify(purchase: SignedPurchase): VerifiedPurchase? = runCatching {
        if(!configured || purchase.data.length !in 1..65_536 || purchase.signature.length !in 1..4096) return null
        // Play's signed purchase-data format uses RSA PKCS#1 with SHA-1.
        val signature = Signature.getInstance("SHA1withRSA")
        signature.initVerify(key);signature.update(purchase.data.toByteArray(Charsets.UTF_8))
        if(!signature.verify(Base64.getDecoder().decode(purchase.signature))) return null
        val json = Json.parseToJsonElement(purchase.data).jsonObject
        if(json["packageName"]?.jsonPrimitive?.content != packageName) return null
        val products = json["productIds"]?.jsonArray?.map { it.jsonPrimitive.content }
            ?: listOfNotNull(json["productId"]?.jsonPrimitive?.content)
        if(products != listOf(productId)) return null
        if(json["quantity"]?.let { it.jsonPrimitive.intOrNull != 1 } == true) return null
        val token = (json["token"] ?: json["purchaseToken"])?.jsonPrimitive?.content ?: return null
        if(token.isBlank() || token.length > 4096) return null
        val purchaseState = json["purchaseState"]?.jsonPrimitive?.intOrNull ?: return null
        if(purchaseState !in setOf(0,4)) return null
        VerifiedPurchase(token, pending = purchaseState == 4,
            acknowledged = purchase.acknowledgedLocally || json["acknowledged"]?.jsonPrimitive?.booleanOrNull == true)
    }.getOrNull()
}
