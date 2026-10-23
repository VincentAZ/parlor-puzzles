package com.parlor.puzzles

import android.content.Context
import android.content.ContextWrapper
import android.content.ContentResolver
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.parlor.core.platform.ads.*
import com.parlor.core.platform.billing.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.io.File

class AdsStartupTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val units = AdUnits(BuildConfig.ADMOB_APP, BuildConfig.ADMOB_BANNER, BuildConfig.ADMOB_INTERSTITIAL, BuildConfig.ADMOB_REWARDED)
    @Test fun unknownProAndPendingPurchasesLeaveTheSdkUninitialized() {
        val state = MutableStateFlow(ProState())
        val untouched = object : ConsentGateway {
            override fun snapshot(): ConsentSnapshot = error("No consent SDK work expected")
            override fun refresh(onFinished: (Boolean) -> Unit) = error("No consent refresh expected")
            override fun load(onFinished: (PrivacyForm?) -> Unit) = error("No consent form expected")
            override fun privacy(onDismissed: (Boolean) -> Unit) = error("No privacy request expected")
        }
        lateinit var ads: AndroidAds
        compose.runOnIdle {
            ads = AndroidAds(context, units, state, AdsWork(context) { false }, untouched)
            ads.foreground(compose.activity, true); ads.placement(AdPlacement.HOME)
        }
        try {
            for (value in listOf(ProState(), ProState(ProAccess.PRO, PurchaseNotice.READY),
                ProState(ProAccess.FREE, PurchaseNotice.PENDING), ProState(ProAccess.FREE, PurchaseNotice.ACKNOWLEDGING))) {
                compose.runOnIdle { state.value = value }
                compose.waitForIdle()
                compose.runOnIdle {
                    assertFalse(MobileAds.isInitialized); assertFalse(ads.enabled.value)
                    assertNull(ads.banner(compose.activity, AdPlacement.HOME, 320))
                    ads.placement(AdPlacement.PLAY)
                    ads.loadRewarded { assertNull(it) }
                    assertFalse(MobileAds.isInitialized)
                    ads.placement(AdPlacement.HOME)
                }
            }
        } finally { compose.runOnIdle { ads.close() } }
    }

    @Test fun mergedManifestAndPersistedWorkerCannotStartAdsOrAcquireWakeLocks() {
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse("android.permission.WAKE_LOCK" in permissions)
        assertFalse("android.permission.FOREGROUND_SERVICE" in permissions)
        assertTrue("android.permission.RECEIVE_BOOT_COMPLETED" in permissions)
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        assertEquals(BuildConfig.ADMOB_APP, appInfo.metaData.getString("com.google.android.gms.ads.APPLICATION_ID"))
        val manager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequest.Builder(UnexpectedAdWorker::class.java).build()
        UnexpectedAdWorker.ran = false
        manager.enqueue(request).result.get(5, TimeUnit.SECONDS)
        compose.waitUntil(10_000) { manager.getWorkInfoById(request.id).get(1, TimeUnit.SECONDS)?.state?.isFinished == true }
        assertEquals(WorkInfo.State.SUCCEEDED, manager.getWorkInfoById(request.id).get().state)
        assertFalse(UnexpectedAdWorker.ran); assertFalse(MobileAds.isInitialized)
        manager.cancelWorkById(request.id).result.get(5, TimeUnit.SECONDS)
    }

    @Test fun monotonicClockIdentityStaysPrivateAndChangesOnAnotherInstallation() {
        val firstFile = "ads-clock-test-one"; val secondFile = "ads-clock-test-two"
        try {
            val first = DeviceAdClock(context, firstFile).bootMarker()
            val other = DeviceAdClock(context, secondFile).bootMarker()
            assertNotNull(first); assertNotNull(other); assertNotEquals(first, other)
            assertEquals(first, DeviceAdClock(context, firstFile).bootMarker())
            assertEquals(36L, File(context.noBackupFilesDir, firstFile).length())
            File(context.noBackupFilesDir, firstFile).writeText("invalid")
            val repaired = DeviceAdClock(context, firstFile).bootMarker()
            assertNotNull(repaired); assertNotEquals(first, repaired)
            val restricted = object : ContextWrapper(context) {
                override fun getContentResolver(): ContentResolver = throw SecurityException("Boot counter unavailable")
            }
            val unavailable = DeviceAdClock(restricted)
            val fallback = unavailable.bootMarker()
            assertTrue(fallback.startsWith("session:")); assertEquals(fallback, unavailable.bootMarker())
            assertNotEquals(fallback, DeviceAdClock(restricted).bootMarker())
        } finally {
            listOf(firstFile, secondFile).forEach { File(context.noBackupFilesDir, it).delete() }
        }
    }
}

class UnexpectedAdWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result { ran = true; return Result.failure() }
    companion object { @Volatile var ran = false }
}
