package com.parlor.puzzles

import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.platform.billing.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class PlayBillingAdapterTest {
    @Test fun realSdkCanConnectOrReportUnavailableWithoutLaunchingCheckout() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(!(context.applicationContext as ParlorApplication).billing.configured)
        val gateway = PlayBillingGateway(context,"parlor_pro",{ _, _ -> },{})
        try {
            val connected = gateway.connect()
            assertTrue(connected in setOf(StoreCode.OK,StoreCode.UNAVAILABLE,StoreCode.RETRY))
            if(connected == StoreCode.OK) {
                val ownership = gateway.purchases()
                if(ownership.code == StoreCode.OK) assertNotNull(ownership.value)
                val offer = gateway.offer()
                if(offer.code == StoreCode.OK) assertTrue(requireNotNull(offer.value).price.isNotBlank())
            }
        } finally { gateway.close() }
        // Read-only SDK smoke only: no launch, acknowledgement, consumption or grant.
    }
}
