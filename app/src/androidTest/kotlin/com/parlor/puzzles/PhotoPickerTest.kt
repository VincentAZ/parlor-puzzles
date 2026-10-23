package com.parlor.puzzles

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import android.view.InputDevice
import android.view.MotionEvent
import android.accessibilityservice.AccessibilityService
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.data.*
import com.parlor.core.model.*
import com.parlor.feature.library.ParlorTheme
import com.parlor.feature.play.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.util.UUID

/** Real Android picker / DocumentsUI fallback; local fixture ownership is injected, never purchased. */
class PhotoPickerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).bufferedReader().readText().trim()
    }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val found = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            found += node
            for (i in 0 until node.childCount) visit(node.getChild(i))
        }
        visit(instrumentation.uiAutomation.rootInActiveWindow)
        return found
    }
    private fun tap(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser || !node.isEnabled) return false
        var p: AccessibilityNodeInfo? = node
        while (p != null) {
            if (p.isClickable && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            p = p.parent
        }
        val bounds = Rect(); node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return false
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { if (!instrumentation.uiAutomation.injectInputEvent(event, true)) return false }
            finally { event.recycle() }
        }
        return true
    }
    private fun text(label: String) = nodes().firstOrNull {
        it.text?.toString().equals(label, true) || it.contentDescription?.toString().equals(label, true)
    }
    private fun capture(name: String) {
        instrumentation.uiAutomation.waitForIdle(100, 3000); SystemClock.sleep(500)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }

    @Test fun selectedSourceIsCopiedAfterNativePickerWithoutLibraryPermission() {
        val app = context.applicationContext as ParlorApplication
        val db = Room.inMemoryDatabaseBuilder(context, ParlorDatabase::class.java).build()
        val repo = ProgressRepository(db, isPro = { true })
        val content = runBlocking { app.content.load() }
        repo.configure(content, runBlocking { app.content.dailySchedule() })
        val level = content.levels.single { it.id == "tiles-001" }
        val name = "parlor-photo-${UUID.randomUUID().toString().take(8)}.png"
        val source = File(context.getExternalFilesDir(null), name)
        val bitmap = Bitmap.createBitmap(192, 128, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(38, 110, 180))
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val expected = source.inputStream().use { PhotoCodec.normalize(it, context.cacheDir) }
        var media: Uri? = null
        lateinit var vm: PlayViewModel; var visible by mutableStateOf(true)
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                media = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ParlorFixtures")
                    put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis()); put(MediaStore.Images.Media.IS_PENDING, 1)
                }))
                context.contentResolver.openOutputStream(media)!!.use { output -> source.inputStream().use { it.copyTo(output) } }
                context.contentResolver.update(media, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } else {
                shell("cp ${source.absolutePath} /sdcard/Download/$name")
                shell("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/$name")
            }
            compose.setContent { if (visible) ParlorTheme {
                vm = viewModel(factory = PlayViewModel.factory(level, repo))
                PlayScreen(vm, PlayerSettings(sfxVolume = 0f), 3, false, app.feedback, {}, null, pro = true)
            } }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("photo-choose").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("photo-choose").performScrollTo().performClick()
            SystemClock.sleep(500)
            if (Build.VERSION.SDK_INT < 29) {
                (text("Show roots") ?: text("Open navigation drawer"))?.let(::tap)
                val end = SystemClock.elapsedRealtime() + 10_000
                while (text("Downloads") == null && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100)
                text("Downloads")?.let(::tap)
            }
            var selected = false
            val end = SystemClock.elapsedRealtime() + 15_000
            while (!selected && SystemClock.elapsedRealtime() < end) {
                val candidate = text(name) ?: if (Build.VERSION.SDK_INT >= 29) nodes().firstOrNull {
                    val description = it.contentDescription?.toString().orEmpty()
                    it.isVisibleToUser && (description.startsWith("Photo taken") || description.startsWith("Photo, taken") ||
                        it.viewIdResourceName?.endsWith(":id/icon_thumbnail") == true)
                } else null
                if (candidate != null) { capture("photo-native-picker.png"); selected = tap(candidate) }
                if (!selected) SystemClock.sleep(100)
            }
            if (!selected) {
                capture("photo-native-picker-failed.png")
                File(context.filesDir, "photo-native-picker-failed.txt").writeText(nodes().joinToString("\n") {
                    "${it.text} | ${it.contentDescription} | ${it.viewIdResourceName}"
                })
            }
            assertTrue("Native photo fixture must be selected", selected)
            compose.waitUntil(15_000) { vm.photoEdit.value.preview != null || vm.photoEdit.value.error }
            assertFalse(vm.photoEdit.value.error); assertEquals(expected.id, vm.photoEdit.value.preview!!.id)
            compose.onNodeWithTag("photo-confirm").performClick()
            compose.waitUntil(10_000) { vm.state.value.attempt?.photoId == expected.id }
            media?.let { context.contentResolver.delete(it, null, null); media = null }
            source.delete(); if (Build.VERSION.SDK_INT < 29) shell("rm -f /sdcard/Download/$name")
            assertArrayEquals(PhotoCodec.bytes(expected), runBlocking { repo.photo(expected.id) })
            compose.onNodeWithText("Show complete picture").performScrollTo().performClick()
            compose.onNodeWithTag("tile-picture").performScrollTo().assertIsDisplayed(); capture("photo-native-saved.png")
            val permissions = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
            for (permission in listOf("android.permission.READ_MEDIA_IMAGES", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"))
                assertFalse(permissions.contains(permission))
        } finally {
            if (instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() != context.packageName)
                instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            compose.runOnIdle { visible = false }
            compose.waitForIdle()
            media?.let { context.contentResolver.delete(it, null, null) }
            source.delete(); if (Build.VERSION.SDK_INT < 29) shell("rm -f /sdcard/Download/$name")
            db.close()
        }
    }
}
