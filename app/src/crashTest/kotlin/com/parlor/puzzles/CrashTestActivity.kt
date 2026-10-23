package com.parlor.puzzles

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.parlor.core.platform.crashes.CrashReportingStatus

/** This screen and its deliberate crash are compiled only into the isolated internal variant. */
class CrashTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val started = (application as ParlorApplication).crashReportingStatus == CrashReportingStatus.STARTED
        val spacing = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(spacing, spacing, spacing, spacing)
        }
        fun label(resource: Int) {
            content.addView(TextView(this).apply {
                setText(resource)
                textSize = 18f
                setPadding(0, 0, 0, spacing)
            })
        }
        label(R.string.crash_test_title)
        label(if (started) R.string.crash_test_started else R.string.crash_test_unavailable)
        if (started) label(if (FirebaseCrashlytics.getInstance().didCrashOnPreviousExecution())
            R.string.crash_test_previous_yes else R.string.crash_test_previous_no)
        label(R.string.crash_test_instructions)
        content.addView(Button(this).apply {
            setText(R.string.crash_test_button)
            isEnabled = started
            setOnClickListener { throw IllegalStateException("Parlor Crashlytics qualification crash") }
        })
        content.addView(Button(this).apply {
            setText(R.string.crash_test_open_game)
            setOnClickListener { startActivity(Intent(this@CrashTestActivity, MainActivity::class.java)) }
        })
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }
}
