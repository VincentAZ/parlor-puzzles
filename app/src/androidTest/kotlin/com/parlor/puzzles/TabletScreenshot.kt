package com.parlor.puzzles

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileInputStream

/** Android 8's UI Automation can return null after a display resize; native screencap still works. */
fun captureTabletScreen(compose: ComposeTestRule, name: String) {
    compose.waitForIdle()
    SystemClock.sleep(250)
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val bitmap = instrumentation.uiAutomation.takeScreenshot()
        ?: instrumentation.uiAutomation.executeShellCommand("screencap -p").use {
            FileInputStream(it.fileDescriptor).use(BitmapFactory::decodeStream)
        }
    checkNotNull(bitmap) { "Native tablet screenshot unavailable" }
    try {
        File(instrumentation.targetContext.filesDir, name).outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    } finally { bitmap.recycle() }
}
