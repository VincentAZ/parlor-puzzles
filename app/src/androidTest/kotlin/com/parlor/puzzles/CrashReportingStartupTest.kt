package com.parlor.puzzles

import android.content.pm.PackageManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.parlor.core.platform.crashes.AndroidCrashReporting
import com.parlor.core.platform.crashes.CrashReportingStatus
import org.junit.Assert.*
import org.junit.Test

class CrashReportingStartupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun ordinaryLaunchAndRecreationNeverInitializeFirebase() {
        val app = context.applicationContext as ParlorApplication
        assertFalse(BuildConfig.CRASH_REPORTING_ENABLED)
        assertEquals(CrashReportingStatus.DISABLED, app.crashReportingStatus)
        assertTrue(FirebaseApp.getApps(context).isEmpty())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
            assertEquals(CrashReportingStatus.DISABLED, app.crashReportingStatus)
            assertTrue(FirebaseApp.getApps(context).isEmpty())
        }
        val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        for (flag in listOf("firebase_data_collection_default_enabled", "firebase_crashlytics_collection_enabled", "firebase_sessions_enabled")) {
            assertTrue("Collection flag must be explicit: $flag", info.metaData.containsKey(flag))
            assertFalse(info.metaData.getBoolean(flag))
        }
        val providers = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS).providers.orEmpty()
        assertFalse(providers.any { it.name == "com.google.firebase.provider.FirebaseInitProvider" })
        assertEquals(0, context.resources.getIdentifier("google_app_id", "string", context.packageName))
        val activities = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES).activities.orEmpty()
        assertFalse(activities.any { it.name == "com.parlor.puzzles.CrashTestActivity" })
    }

    @Test fun missingConfigurationLeavesTheAppUsableWithoutStartingFirebase() {
        assertTrue(FirebaseApp.getApps(context).isEmpty())
        assertEquals(CrashReportingStatus.UNAVAILABLE, AndroidCrashReporting.start(context, enabled = true))
        assertTrue(FirebaseApp.getApps(context).isEmpty())
        assertEquals(CrashReportingStatus.DISABLED, AndroidCrashReporting.start(context, enabled = false))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
        assertTrue(FirebaseApp.getApps(context).isEmpty())
    }
}
