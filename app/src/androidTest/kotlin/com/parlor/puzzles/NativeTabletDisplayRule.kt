package com.parlor.puzzles

import android.content.res.Configuration
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.rules.ExternalResource
import java.io.FileInputStream

/** Native 600-dp short axis; keep the real device configuration independent of test-host bounds. */
class NativeTabletDisplayRule(private val fontScale: Float = 1f) : ExternalResource() {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var oldScale: String = "1.0"
    private var oldSize: String? = null
    private var oldDensity: String? = null
    private var oldRotation: String = "0"
    private var oldAccelerometer: String = "0"
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).bufferedReader().readText().trim()
    }
    fun orientation(portrait: Boolean) {
        shell("wm size ${if (portrait) "1200x1920" else "1920x1200"}")
        val expected = if (portrait) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
        val end = SystemClock.elapsedRealtime() + 10_000
        while (context.resources.configuration.orientation != expected && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50)
        assertEquals(expected, context.resources.configuration.orientation)
        assertTrue(context.resources.configuration.smallestScreenWidthDp >= 600)
    }
    override fun before() {
        oldScale = shell("settings get system font_scale")
        oldSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        oldDensity = Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1)
        oldRotation = shell("settings get system user_rotation")
        oldAccelerometer = shell("settings get system accelerometer_rotation")
        shell("settings put system accelerometer_rotation 0")
        shell("settings put system user_rotation 0")
        shell("wm density 320")
        shell("settings put system font_scale $fontScale")
        orientation(false)
        val end = SystemClock.elapsedRealtime() + 5_000
        while (kotlin.math.abs(context.resources.configuration.fontScale - fontScale) > .01f && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50)
        assertEquals(fontScale, context.resources.configuration.fontScale, .01f)
    }
    override fun after() {
        shell("wm size ${oldSize ?: "reset"}")
        shell("wm density ${oldDensity ?: "reset"}")
        for ((key, value) in listOf("font_scale" to oldScale, "user_rotation" to oldRotation, "accelerometer_rotation" to oldAccelerometer)) {
            shell(if (value == "null") "settings delete system $key" else "settings put system $key $value")
        }
    }
}
