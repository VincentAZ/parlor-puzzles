package com.parlor.puzzles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.game.*
import com.parlor.core.model.*
import com.parlor.feature.library.ParlorTheme
import com.parlor.feature.play.PuzzleBoard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.abs

class BoardMotionTest {
    @get:Rule val compose = createComposeRule()
    private var board by mutableStateOf<BoardState?>(null)
    private var puzzle by mutableStateOf(Puzzle.TRAFFIC_JAM)
    private var viewport by mutableStateOf(Viewport())
    private var reduced by mutableStateOf(false)
    private var session by mutableIntStateOf(0)
    private var actions = 0
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun show(initial: BoardState, family: Puzzle, rtl: Boolean = false) {
        board = initial; puzzle = family
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                ParlorTheme { Surface {
                    Column(Modifier.width(360.dp).height(640.dp).testTag("motion-scene").verticalScroll(rememberScrollState())) {
                        key(session) { PuzzleBoard(requireNotNull(board), viewport, true, { viewport = it }, {
                            actions++
                            board = requireNotNull(engineFor(puzzle).apply(requireNotNull(board), it))
                        }, reduceMotion = reduced) }
                    }
                } }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun move(action: Move) {
        compose.runOnIdle { board = requireNotNull(engineFor(puzzle).apply(requireNotNull(board), action)) }
        compose.mainClock.advanceTimeByFrame()
    }
    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun finish() = compose.mainClock.advanceTimeBy(400)

    @Test fun trafficUsesPhysicalRightwardIntermediatePositionsAndAcceptsTouchesDuringMotion() {
        show(TrafficJamBoard(pieces = listOf(TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL))), Puzzle.TRAFFIC_JAM, rtl = true)
        val before = bounds("piece-A")
        move(Translate("A", 1))
        compose.mainClock.advanceTimeBy(96)
        val middle = bounds("piece-A")
        assertEquals(1, (board as TrafficJamBoard).pieces.single().x)
        assertTrue(middle.left > before.left && middle.left < before.left + before.width / 2)
        compose.onNodeWithTag("piece-A").assertIsEnabled().performTouchInput { down(center); up() }
        compose.runOnIdle { assertEquals(0, actions) }
        finish()
        assertEquals(before.left + before.width / 2, bounds("piece-A").left, 1f)
        compose.onNodeWithTag("piece-A").assertIsEnabled()
    }

    @Test fun tilesSlideVerticallyAcrossRowsWithoutDuplicateInteractiveTiles() {
        show(TilesBoard(3, listOf(1, 2, 3, 4, 0, 6, 7, 5, 8), TileArt.GARDEN), Puzzle.SLIDING_TILES)
        val before = bounds("tile-5")
        move(SlideTile(5)); compose.mainClock.advanceTimeBy(96)
        val middle = bounds("tile-5")
        assertTrue(middle.top < before.top && middle.top > before.top - before.height)
        assertEquals(before.left, middle.left, 1f)
        compose.onAllNodesWithTag("tile-5").assertCountEquals(1)
        compose.onNodeWithTag("tile-5").assertIsEnabled()
        snapshot("tiles-middle")
        finish()
        assertEquals(before.top - before.height, bounds("tile-5").top, 1f)
        compose.onNodeWithTag("tile-5").assertIsEnabled()
    }

    @Test fun aWholeBlockKeepsItsJoinedCellsTogetherDuringAMove() {
        show(BlockEscapeBoard(blocks = listOf(EscapeBlock("A", 1, 0, 2, 2))), Puzzle.BLOCK_ESCAPE)
        val first = bounds("block-cell-1"); val second = bounds("block-cell-2")
        move(SlideBlock("A", 1, 1)); compose.mainClock.advanceTimeBy(96)
        val a = bounds("block-cell-5"); val b = bounds("block-cell-6")
        assertTrue(a.top > first.top && a.top < first.top + first.height)
        assertEquals(second.left - first.left, b.left - a.left, 1f)
        assertEquals(a.top, b.top, 1f)
        finish()
        assertEquals(first.top + first.height, bounds("block-cell-5").top, 1f)
    }

    @Test fun automaticWorkerWalkingAndAnInterruptedUndoStayInsideTheKnownCorridor() {
        val initial = BoxPusherBoard(4, 4, listOf(0, 1, 2, 6, 10, 14, 15), listOf(14), listOf(15), 0)
        show(initial, Puzzle.BOX_PUSHER)
        val origin = bounds("box-cell-0")
        move(WalkTo(6))
        repeat(2) {
            compose.mainClock.advanceTimeBy(64)
            val current = bounds("box-cell-6")
            assertTrue("The worker follows the corner instead of crossing a wall",
                abs(current.top - origin.top) < 1f || abs(current.left - origin.left - 2 * origin.width) < 1f)
        }
        assertEquals(6, (board as BoxPusherBoard).worker)
        compose.runOnIdle { board = initial }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(64)
        val returning = bounds("box-cell-0")
        assertTrue(abs(returning.top - origin.top) < 1f || abs(returning.left - origin.left - 2 * origin.width) < 1f)
        finish()
        assertEquals(origin.left, bounds("box-cell-0").left, 1f)
        assertEquals(origin.top, bounds("box-cell-0").top, 1f)
    }

    @Test fun enablingReducedMotionFinishesTheCurrentMoveAndLaterChangesAreImmediate() {
        val initial = TrafficJamBoard(pieces = listOf(TrafficPiece("A", 0, 2, 2, Axis.HORIZONTAL)))
        show(initial, Puzzle.TRAFFIC_JAM)
        val origin = bounds("piece-A")
        move(Translate("A", 1)); compose.mainClock.advanceTimeBy(64)
        assertTrue(bounds("piece-A").left < origin.left + origin.width / 2)
        compose.runOnIdle { reduced = true }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(origin.left + origin.width / 2, bounds("piece-A").left, 1f)
        compose.onNodeWithTag("piece-A").assertIsEnabled()
        compose.runOnIdle { board = initial }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(origin.left, bounds("piece-A").left, 1f)
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { reduced = false }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(origin.left, bounds("piece-A").left, 1f)
    }

    @Test fun everyFamilyHasAnIntermediateVisualFrameBeforeSettling() = gallery(false)
    @Test fun everyFamilyRendersTheCommittedResultImmediatelyWithReducedMotion() = gallery(true)

    private fun gallery(reduce: Boolean) {
        val app = context.applicationContext as ParlorApplication
        val catalog = runBlocking { app.content.load() }
        show(catalog.levels.first().initial, catalog.levels.first().puzzle)
        for (family in Puzzle.entries) {
            val level = catalog.levels.first { it.puzzle == family && it.tier == Tier.EASY }
            val action = level.reference.first()
            val view = when (val state = level.initial) {
                is PegBoard -> (action as JumpPeg).let { jump ->
                    val width = pegGeometry(state.layout).width
                    Viewport(column = minOf(jump.from % width, jump.to % width), row = minOf(jump.from / width, jump.to / width))
                }
                is BoxPusherBoard -> Viewport(column = state.worker % state.width, row = state.worker / state.width)
                else -> Viewport()
            }
            compose.runOnIdle { session++; puzzle = family; board = level.initial; viewport = view; reduced = reduce }
            compose.mainClock.advanceTimeBy(32)
            val before = snapshot()
            move(action)
            compose.mainClock.advanceTimeBy(if (reduce) 32 else 96)
            val intermediate = snapshot("${family.name.lowercase()}-${if (reduce) "reduced" else "middle"}")
            finish()
            val settled = snapshot(if (reduce) null else "${family.name.lowercase()}-settled")
            assertFalse("$family must visibly reflect the real move", before.contentEquals(settled))
            assertEquals("$family motion preference must govern its visual transition", reduce, intermediate.contentEquals(settled))
        }
    }

    private fun snapshot(name: String? = null): ByteArray {
        val bitmap = compose.onNodeWithTag("motion-scene").captureToImage().asAndroidBitmap()
        if (name != null) File(context.filesDir, "motion-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val pixels = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(pixels)
        return MessageDigest.getInstance("SHA-256").digest(pixels.array())
    }
}
