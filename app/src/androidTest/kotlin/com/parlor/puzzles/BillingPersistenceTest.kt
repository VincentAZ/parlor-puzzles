package com.parlor.puzzles

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.data.*
import com.parlor.core.model.*
import com.parlor.core.platform.billing.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class BillingPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fileName = "billing-instrumentation.json"
    private fun fixture(): Pair<PlayPurchaseVerifier,SignedPurchase> {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val verifier = PlayPurchaseVerifier(Base64.getEncoder().encodeToString(pair.public.encoded),context.packageName,"parlor_pro")
        val data = """{"packageName":"${context.packageName}","productId":"parlor_pro","purchaseToken":"instrumentation-token","purchaseState":0,"acknowledged":false}"""
        val signature = Signature.getInstance("SHA1withRSA").apply { initSign(pair.private);update(data.toByteArray()) }.sign()
        return verifier to SignedPurchase(data,Base64.getEncoder().encodeToString(signature))
    }
    private class Gateway(var purchases: List<SignedPurchase>) : PurchaseGateway {
        var online = true;var ack = StoreCode.RETRY
        override suspend fun connect() = if(online) StoreCode.OK else StoreCode.RETRY
        override suspend fun purchases() = StoreReply(StoreCode.OK,purchases)
        override suspend fun offer() = StoreReply(StoreCode.OK,ProOffer("$2.99","test"))
        override suspend fun acknowledge(token: String) = ack
    }

    @Test fun durablePurchaseRestoresOfflineAndRevocationPreservesProgress() = runBlocking {
        val file = File(context.noBackupFilesDir,fileName);file.delete()
        val db = Room.inMemoryDatabaseBuilder(context,ParlorDatabase::class.java).build()
        try {
            val (verifier,receipt) = fixture();val gateway = Gateway(listOf(receipt))
            val first = ProBilling(gateway,DevicePurchaseStore(context,fileName),verifier,true)
            first.refresh();assertFalse(first.hasPro)
            assertEquals(PurchaseNotice.ACKNOWLEDGING,first.state.value.notice)
            assertTrue(file.isFile);assertEquals(context.noBackupFilesDir,file.parentFile)
            var billing = ProBilling(gateway,DevicePurchaseStore(context,fileName),verifier,true)
            gateway.ack = StoreCode.OK;billing.refresh();assertTrue(billing.hasPro)
            val content = ContentRepository(context);val catalog = content.load()
            val repo = ProgressRepository(db,today = { "2026-09-28" },isPro = { billing.hasPro })
            repo.configure(catalog,content.dailySchedule())
            val level = catalog.levels.first { it.tier == Tier.EXPERT }
            val attempt = repo.startOrResume(level)
            repo.move(level,attempt.id,level.reference.first(),100)
            val saved = repo.observeAttempt(attempt.id).first()!!
            billing = ProBilling(gateway,DevicePurchaseStore(context,fileName),verifier,true)
            billing.loadLocal();gateway.online = false;billing.refresh()
            assertTrue(billing.hasPro);assertEquals(saved,repo.startOrResume(level))
            gateway.online = true;gateway.purchases = emptyList();billing.refresh()
            assertFalse(billing.hasPro)
            // The authorized foreground board may finish; future entry must recheck.
            assertNotNull(repo.move(level,attempt.id,level.reference[1],100))
            try { repo.checkReturnAccess(level,attempt.id);fail("Revoked Pro must gate a return") } catch(_: AccessDenied) {}
            assertEquals(2,repo.observeAttempt(attempt.id).first()!!.history.size)
            assertTrue(repo.bests.first().isEmpty())
            repo.resetProgress();assertFalse(billing.hasPro)
            assertTrue(DevicePurchaseStore(context,fileName).load().purchases.isEmpty())
            assertEquals(1,DevicePurchaseStore(context,fileName).load().revokedTokenHashes.size)
            billing = ProBilling(gateway,DevicePurchaseStore(context,fileName),verifier,true)
            billing.purchasesUpdated(StoreCode.OK,listOf(receipt));assertFalse(billing.hasPro)
        } finally { db.close();file.delete() }
    }

    @Test fun boundedPrivateStoreRejectsCorruptionAndFutureSchemasWithoutGranting() = runBlocking {
        val file = File(context.noBackupFilesDir,fileName)
        val (verifier,receipt) = fixture();val gateway = Gateway(listOf(receipt))
        try {
            for(bytes in listOf("{".toByteArray(),"""{"version":2,"purchases":[]}""".toByteArray(),ByteArray(1_048_577))) {
                file.writeBytes(bytes)
                val billing = ProBilling(gateway,DevicePurchaseStore(context,fileName),verifier,true)
                billing.loadLocal();assertFalse(billing.hasPro);assertFalse(billing.state.value.permitsAds)
                assertArrayEquals(bytes,file.readBytes())
            }
            val repaired = ProBilling(gateway,DevicePurchaseStore(context,fileName),verifier,true)
            gateway.ack = StoreCode.OK;repaired.refresh();assertTrue(repaired.hasPro)
            assertEquals(1,DevicePurchaseStore(context,fileName).load().purchases.size)
        } finally { file.delete() }
        val info = context.packageManager.getServiceInfo(ComponentName(context,BillingRetryJobService::class.java),0)
        assertEquals("android.permission.BIND_JOB_SERVICE",info.permission);assertTrue(info.exported)
        val permissions = context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue("com.android.vending.BILLING" in permissions)
        assertTrue("android.permission.RECEIVE_BOOT_COMPLETED" in permissions)
        assertFalse("android.permission.WAKE_LOCK" in permissions)
        assertFalse(permissions.any { it.contains("LOCATION") })
    }
}
