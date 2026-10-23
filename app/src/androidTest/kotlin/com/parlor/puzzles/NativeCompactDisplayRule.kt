package com.parlor.puzzles

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.rules.ExternalResource
import java.io.FileInputStream

/** Apply native compact-phone settings before the Activity exists, then restore the original device. */
class NativeCompactDisplayRule : ExternalResource() {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var scale: Float? = null
    private var size: String? = null
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).bufferedReader().readText().trim()
    }
    private fun awaitScale(value: Float) {
        val end = SystemClock.elapsedRealtime() + 5_000
        while (kotlin.math.abs(context.resources.configuration.fontScale - value) > .01f && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50)
        assertEquals(value, context.resources.configuration.fontScale, .01f)
    }
    override fun before() {
        scale = shell("settings get system font_scale").toFloatOrNull()
        size = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        val density = context.resources.displayMetrics.density
        shell("wm size ${(320 * density).toInt()}x${(640 * density).toInt()}")
        shell("settings put system font_scale 2.0"); awaitScale(2f)
    }
    override fun after() {
        shell("wm size ${size ?: "reset"}")
        shell(scale?.let { "settings put system font_scale $it" } ?: "settings delete system font_scale")
        awaitScale(scale ?: 1f)
    }
}
