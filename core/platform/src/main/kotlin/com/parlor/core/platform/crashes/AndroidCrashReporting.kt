package com.parlor.core.platform.crashes

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

enum class CrashReportingStatus { DISABLED, STARTED, UNAVAILABLE }

/** SDK fatal/ANR reporting only: no player IDs, gameplay events, custom logs or attachments. */
object AndroidCrashReporting {
    fun start(context: Context, enabled: Boolean): CrashReportingStatus {
        if (!enabled) return CrashReportingStatus.DISABLED
        return try {
            checkNotNull(FirebaseApp.initializeApp(context.applicationContext))
            FirebaseCrashlytics.getInstance()
            CrashReportingStatus.STARTED
        } catch (_: RuntimeException) {
            // Reporting availability must not prevent offline play. Do not log exception data.
            Log.w("ParlorCrashReporting", "Crash reporting could not start")
            CrashReportingStatus.UNAVAILABLE
        }
    }
}
