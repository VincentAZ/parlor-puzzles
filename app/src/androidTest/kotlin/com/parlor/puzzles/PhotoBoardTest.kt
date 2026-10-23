package com.parlor.puzzles

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.parlor.core.game.TilesEngine
import com.parlor.core.model.*
import com.parlor.feature.library.ParlorTheme
import com.parlor.feature.play.PuzzleBoard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PhotoBoardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyBoardSizeKeepsImageIdentityWhenSlidingZoomingAndPreviewing() {
        var side by mutableIntStateOf(3)
        var board by mutableStateOf(TilesBoard(3, (1..7).toList() + listOf(0, 8)))
        var viewport by mutableStateOf(Viewport(showLabels = false))
        val colors = (0 until 25).map { Color.HSVToColor(floatArrayOf(it * 360f / 25, .8f, .85f)) }
        fun atlas(side: Int) = Bitmap.createBitmap(side * 64, side * 64, Bitmap.Config.ARGB_8888).also { b ->
            val canvas = Canvas(b)
            repeat(side * side) { i -> canvas.drawRect((i % side * 64).toFloat(), (i / side * 64).toFloat(),
                (i % side * 64 + 64).toFloat(), (i / side * 64 + 64).toFloat(), Paint().apply { color = colors[i] }) }
        }
        val images = (3..5).associateWith { atlas(it) }
        compose.setContent { ParlorTheme { Surface {
            Column(Modifier.width(320.dp).height(640.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                PuzzleBoard(board, viewport, true, { viewport = it }, { board = TilesEngine.apply(board, it) as TilesBoard },
                    photo = images.getValue(side).asImageBitmap())
            }
        } } }
        for (n in 3..5) {
            compose.runOnIdle { side = n; board = TilesBoard(n, (1 until n * n - 1).toList() + listOf(0, n * n - 1)); viewport = Viewport(showLabels = false) }
            fun assertTile(tile: Int) {
                val bitmap = compose.onNodeWithTag("tile-$tile").performScrollTo().assertWidthIsAtLeast(48.dp)
                    .assertHeightIsAtLeast(48.dp).captureToImage().asAndroidBitmap()
                val actual = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                fun distance(color: Int) = listOf(Color.red(actual) - Color.red(color), Color.green(actual) - Color.green(color),
                    Color.blue(actual) - Color.blue(color)).sumOf { it * it }
                // Renderer rounding and the fading touch ink may tint pixels, but not change crop identity.
                assertEquals("Tile $tile at size $n", tile - 1, colors.indices.minBy { distance(colors[it]) })
                assertTrue("Tile $tile must retain its source color", distance(colors[tile - 1]) < 900)
            }
            for (tile in 1 until n * n) assertTile(tile)
            compose.onNodeWithTag("tile-${n * n - 1}").performClick()
            compose.onNodeWithTag("tile-0").performClick()
            android.os.SystemClock.sleep(500) // Let the native touch ripple leave the sampled image pixels.
            assertTile(n * n - 1)
            compose.runOnIdle { assertEquals(n * n - 1, board.tiles[n * n - 2]) }
            compose.onNodeWithText("Show complete picture").performScrollTo().performClick()
            val picture = compose.onNodeWithTag("tile-picture").performScrollTo().captureToImage().asAndroidBitmap()
            assertEquals(colors[n * n - 1], picture.getPixel(picture.width - picture.width / n / 2, picture.height - picture.height / n / 2))
            File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "photo-tiles-${n}x$n.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithText("Hide complete picture").performScrollTo().performClick()
            compose.runOnIdle { viewport = viewport.copy(zoom = 2, column = 0, row = 0) }
            compose.onNodeWithTag("tiles-overview").performScrollTo().assertIsDisplayed(); assertTile(1)
            compose.runOnIdle { viewport = viewport.copy(column = 2, row = 1) }
            assertTile(n * n - 1)
        }
    }
}
