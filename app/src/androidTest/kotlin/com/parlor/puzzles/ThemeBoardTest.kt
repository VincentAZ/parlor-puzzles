package com.parlor.puzzles

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.data.*
import com.parlor.core.design.roomColors
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

class ThemeBoardTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as ParlorApplication
    private fun capture(name: String) {
        File(context.filesDir, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun everyFamilyRendersInAllSixRoomsAndBothAppearancesAtCompactTextSize() {
        val levels = runBlocking { app.content.load() }.levels
        val samples = Puzzle.entries.map { puzzle -> levels.first { it.puzzle == puzzle && it.tier == Tier.EASY } }
        var level by mutableStateOf(samples.first { it.puzzle == Puzzle.TRAFFIC_JAM })
        var theme by mutableStateOf(RoomTheme.LINEN); var dark by mutableStateOf(false)
        compose.setContent { ParlorTheme(theme, dark) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Surface { key(level.id) { Column(Modifier.requiredSize(320.dp, 640.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    PuzzleBoard(level.initial, Viewport(), true, {}, {})
                } } }
            }
        } }
        for (choice in RoomTheme.entries) for (night in listOf(false, true)) {
            compose.runOnIdle { theme = choice; dark = night }
            for (sample in samples) {
                compose.runOnIdle { level = sample }; compose.waitForIdle()
                assertTrue(compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().isNotEmpty())
                if (sample.puzzle == Puzzle.TRAFFIC_JAM) {
                    compose.onNodeWithTag("piece-A").performScrollTo().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                    capture("theme-${choice.id}-${if (night) "dark" else "light"}-traffic.png")
                } else if (choice == RoomTheme.WALNUT && night) {
                    capture("theme-walnut-dark-${sample.puzzle.name.lowercase()}.png")
                }
            }
        }
    }

    @Test fun themeAndProChangesPreservePausedPhotoBoardHistoryHintsAndSelection() {
        val db = Room.inMemoryDatabaseBuilder(context, ParlorDatabase::class.java).build()
        var pro by mutableStateOf(true); var visible by mutableStateOf(true); var pause by mutableStateOf(true)
        var theme by mutableStateOf(RoomTheme.SAGE); var dark by mutableStateOf(false)
        val level = LevelDefinition("theme-tiles", puzzle = Puzzle.SLIDING_TILES, tier = Tier.EASY, order = 1,
            initial = TilesBoard(3, listOf(1,2,3,4,5,6,0,7,8)), reference = listOf(SlideTile(7), SlideTile(8)),
            par = 2, parKind = ParKind.PROVEN_OPTIMAL, targetTimeMs = 60_000, provenance = Provenance("Parlor", "theme UI fixture", 0, "original"))
        val repo = ProgressRepository(db, today = { "2026-09-28" }, isPro = { pro })
        repo.configure(Catalog(levels = listOf(level)), DailySchedule(listOf(ScheduleEpoch(1, "2026-09-28", listOf(LevelRef(level.id, 1))))))
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val image = try {
            bitmap.eraseColor(android.graphics.Color.BLUE)
            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            PhotoCodec.normalize(bytes.inputStream(), context.cacheDir)
        } finally { bitmap.recycle() }
        val saved = runBlocking {
            val a = repo.startOrResume(level); repo.setPhoto(level, a.id, image)
            repo.move(level, a.id, SlideTile(7), 100); repo.undo(level, a.id, 100); repo.useHint(level, a.id, 50)
            repo.saveViewport(level, a.id, Viewport(zoom = 2, selectedId = "7", showLabels = false), 0)
            requireNotNull(repo.observeAttempt(a.id).first())
        }
        lateinit var vm: PlayViewModel
        compose.setContent { if (visible) ParlorTheme(RoomTheme.displayed(theme.id, pro), dark) {
            Surface {
                vm = viewModel(factory = PlayViewModel.factory(level, repo))
                PlayScreen(vm, PlayerSettings(sfxVolume = 0f, reduceMotion = true), 3, false,
                    app.feedback, {}, null, pro = pro, externalPause = pause)
            }
        } }
        try {
            compose.waitUntil(10_000) { vm.state.value.attempt != null && vm.state.value.paused }
            val before = requireNotNull(vm.state.value.attempt)
            assertEquals(saved.board, before.board)
            for (choice in RoomTheme.entries) for (night in listOf(false, true)) {
                compose.runOnIdle { theme = choice; dark = night }
                compose.waitForIdle()
                assertEquals(before, vm.state.value.attempt)
                assertArrayEquals(PhotoCodec.bytes(image), runBlocking { repo.photo(image.id) })
            }
            compose.runOnIdle { pro = false }; compose.waitForIdle()
            assertEquals(before, vm.state.value.attempt)
            compose.runOnIdle { pro = true }; compose.waitForIdle()
            assertEquals(before, vm.state.value.attempt)
            compose.runOnIdle { pause = false }
            compose.waitUntil { !vm.state.value.paused }
            compose.onNodeWithText("Redo").performScrollTo().performClick()
            compose.waitUntil { vm.state.value.attempt?.history?.size == 1 }
            assertEquals(before.photoId, vm.state.value.attempt?.photoId)
            assertTrue(vm.state.value.attempt!!.hintsUsed)
        } finally {
            compose.runOnIdle { vm.lifecycle(false); visible = false }; compose.waitForIdle(); db.close()
        }
    }
}
