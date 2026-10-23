package com.parlor.puzzles

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.FileInputStream
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.data.*
import com.parlor.core.model.*
import com.parlor.feature.library.ParlorTheme
import com.parlor.feature.play.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/** Injected ownership exercises local access rules; this is not a Play purchase test. */
class PhotoFlowTest {
    private val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val compact = object : ExternalResource() {
        private var originalScale: Float? = null
        private var originalSize: String? = null
        private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
            FileInputStream(it.fileDescriptor).bufferedReader().readText().trim()
        }
        private fun awaitScale(value: Float) {
            val end = SystemClock.elapsedRealtime() + 5_000
            while (kotlin.math.abs(context.resources.configuration.fontScale - value) > 0.01f && SystemClock.elapsedRealtime() < end)
                SystemClock.sleep(50)
            assertEquals(value, context.resources.configuration.fontScale, 0.01f)
        }
        override fun before() {
            originalScale = shell("settings get system font_scale").toFloatOrNull()
            originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
            val density = context.resources.displayMetrics.density
            shell("wm size ${(320 * density).toInt()}x${(640 * density).toInt()}")
            shell("settings put system font_scale 2.0"); awaitScale(2f)
        }
        override fun after() {
            shell("wm size ${originalSize ?: "reset"}")
            shell(originalScale?.let { "settings put system font_scale $it" } ?: "settings delete system font_scale")
            awaitScale(originalScale ?: 1f)
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(compact).around(compose)

    private val app get() = context.applicationContext as ParlorApplication
    private val day = "2026-09-28"
    private val level = LevelDefinition("ui-photo", puzzle = Puzzle.SLIDING_TILES, tier = Tier.EASY, order = 1,
        initial = TilesBoard(3, listOf(1,2,3,4,5,6,0,7,8), TileArt.GARDEN), reference = listOf(SlideTile(7), SlideTile(8)),
        par = 2, parKind = ParKind.PROVEN_OPTIMAL, targetTimeMs = 60_000, provenance = Provenance("Parlor", "photo UI fixture", 0, "original"))
    private fun picture(): PuzzlePhoto {
        val b = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888)
        val bytes = try { b.eraseColor(Color.BLUE); ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }
        finally { b.recycle() }
        return bytes.inputStream().use { PhotoCodec.normalize(it, context.cacheDir) }
    }
    private fun capture(name: String) {
        compose.waitForIdle(); SystemClock.sleep(500)
        val b = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.filesDir, name).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
    }

    @Test fun previewNeedsConfirmationThenFreeAccessRetainsPhotoAndAllowsNumberPlay() {
        val db = Room.inMemoryDatabaseBuilder(context, ParlorDatabase::class.java).build()
        var pro by mutableStateOf(true); var visible by mutableStateOf(true)
        val repo = ProgressRepository(db, today = { day }, isPro = { pro })
        repo.configure(Catalog(levels = listOf(level)), DailySchedule(listOf(ScheduleEpoch(1, day, listOf(LevelRef(level.id, 1))))))
        lateinit var vm: PlayViewModel
        compose.setContent { if (visible) ParlorTheme {
            vm = viewModel(factory = PlayViewModel.factory(level, repo))
            PlayScreen(vm, PlayerSettings(sfxVolume = 0f), 3, false, app.feedback, {}, null, pro = pro, onPhotoPro = {})
        } }
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("tile-7").fetchSemanticsNodes().isNotEmpty() }
            val a = vm.state.value.attempt!!; val image = picture()
            compose.runOnIdle { vm.preparePhoto(a.id) { image } }
            compose.waitUntil(10_000) { vm.photoEdit.value.preview != null }
            // A picker/lifecycle callback can precede Compose's latest pause effect.
            compose.runOnIdle { vm.pause(false); vm.move(SlideTile(7)) }
            compose.waitUntil(5_000) { !vm.state.value.busy }
            assertEquals(a.board, runBlocking { repo.observeAttempt(a.id).first()!!.board })
            compose.onNodeWithTag("photo-crop").assertIsDisplayed(); capture("photo-preview.png")
            compose.onNodeWithText("The center of your photo", substring = true).performScrollTo().assertIsDisplayed()
            capture("photo-preview-details.png")
            assertNull(runBlocking { repo.observeAttempt(a.id).first()!!.photoId })
            compose.onNodeWithText("Cancel").performClick()
            compose.waitUntil(5_000) { !vm.state.value.paused }
            assertNull(runBlocking { repo.observeAttempt(a.id).first()!!.photoId })
            compose.runOnIdle { vm.preparePhoto(a.id) { image } }
            compose.waitUntil(10_000) { vm.photoEdit.value.preview != null }
            compose.onNodeWithTag("photo-confirm").assertHeightIsAtLeast(48.dp).performClick()
            compose.waitUntil(10_000) { vm.state.value.attempt?.photoId == image.id && !vm.state.value.paused }
            compose.onNodeWithText("Show complete picture").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Your selected puzzle photo").performScrollTo().assertIsDisplayed()
            capture("photo-saved-board.png")
            compose.runOnIdle { pro = false }
            compose.onNodeWithTag("photo-choose").assertDoesNotExist()
            compose.onNodeWithText("Your saved photo is kept.", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Show complete picture").assertDoesNotExist()
            assertEquals(image.id, runBlocking { repo.observeAttempt(a.id).first()!!.photoId })
            compose.onNodeWithTag("tile-7").performScrollTo().performClick()
            compose.onNodeWithTag("tile-slide").performScrollTo().performClick()
            compose.waitUntil(5_000) { vm.state.value.attempt?.history?.size == 1 }
            compose.onNodeWithText("Undo").performScrollTo().performClick()
            compose.waitUntil(5_000) { vm.state.value.attempt?.history?.isEmpty() == true }
            compose.onNodeWithTag("photo-remove").performScrollTo().performClick()
            compose.waitUntil(5_000) { vm.state.value.attempt?.photoId == null && !vm.state.value.paused }
            assertEquals(a.board, vm.state.value.attempt!!.board); assertEquals(2, vm.state.value.attempt!!.scoredMoves)
            assertNull(runBlocking { repo.photo(image.id) })
        } finally {
            compose.runOnIdle { vm.lifecycle(false); visible = false }; compose.waitForIdle(); db.close()
        }
    }

    @Test fun dailyImageUsesBundledArtAndNeverOffersPersonalPhotos() {
        val db = Room.inMemoryDatabaseBuilder(context, ParlorDatabase::class.java).build()
        val repo = ProgressRepository(db, today = { day }, isPro = { true })
        repo.configure(Catalog(levels = listOf(level)), DailySchedule(listOf(ScheduleEpoch(1, day, listOf(LevelRef(level.id, 1))))))
        lateinit var vm: PlayViewModel; var visible by mutableStateOf(true)
        compose.setContent { if (visible) ParlorTheme {
            vm = viewModel(factory = PlayViewModel.factory(level, repo, day))
            PlayScreen(vm, PlayerSettings(sfxVolume = 0f), 3, false, app.feedback, {}, null, pro = true)
        } }
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("tile-7").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Your puzzle photo").assertDoesNotExist(); compose.onNodeWithTag("photo-choose").assertDoesNotExist()
            compose.onNodeWithText("Show complete picture").performScrollTo().performClick()
            compose.onNodeWithTag("tile-picture").performScrollTo().assertIsDisplayed()
            assertNull(vm.state.value.attempt!!.photoId); assertEquals(AttemptMode.DAILY, vm.state.value.attempt!!.mode)
        } finally {
            compose.runOnIdle { vm.lifecycle(false); visible = false }; compose.waitForIdle(); db.close()
        }
    }
}
