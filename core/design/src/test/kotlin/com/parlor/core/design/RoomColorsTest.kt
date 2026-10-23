package com.parlor.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import com.parlor.core.model.RoomTheme
import kotlin.test.*
import kotlin.math.pow

class RoomColorsTest {
    // Full-severity linear-RGB matrices published with Machado, Oliveira & Fernandes (2009).
    // https://www.inf.ufrgs.br/~oliveira/pubs_files/CVD_Simulation/CVD_Simulation.html
    private val simulations = listOf(
        listOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0),
        listOf(.152286, 1.052583, -.204868, .114503, .786281, .099216, -.003882, -.048116, 1.051998),
        listOf(.367322, .860646, -.227968, .280085, .672501, .047413, -.011820, .042940, .968881),
    )
    private fun simulatedLuminance(color: Color, matrix: List<Double>): Double {
        val rgb = listOf(color.red, color.green, color.blue).map { value ->
            val c = value.toDouble()
            if (c <= .04045) c / 12.92 else ((c + .055) / 1.055).pow(2.4)
        }
        val simulated = (0..2).map { row -> (0..2).sumOf { column -> matrix[row * 3 + column] * rgb[column] }.coerceIn(0.0, 1.0) }
        return simulated[0] * .2126 + simulated[1] * .7152 + simulated[2] * .0722
    }
    private fun contrast(a: Color, b: Color, matrix: List<Double>): Double {
        val x = simulatedLuminance(a, matrix); val y = simulatedLuminance(b, matrix)
        return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
    }

    @Test fun allRoomAndBoardForegroundsMeetFourPointFiveInBothAppearances() {
        for (theme in RoomTheme.entries) for (dark in listOf(false, true)) {
            val colors = roomColors(theme, dark); val c = colors.scheme; val b = colors.board
            val pairs = listOf(
                "primary" to (c.primary to c.onPrimary), "primaryContainer" to (c.primaryContainer to c.onPrimaryContainer),
                "secondary" to (c.secondary to c.onSecondary), "secondaryContainer" to (c.secondaryContainer to c.onSecondaryContainer),
                "tertiary" to (c.tertiary to c.onTertiary), "tertiaryContainer" to (c.tertiaryContainer to c.onTertiaryContainer),
                "background" to (c.background to c.onBackground), "surface" to (c.surface to c.onSurface),
                "variant" to (c.surfaceVariant to c.onSurfaceVariant), "container" to (c.surfaceContainerHighest to c.onSurface),
                "inverse" to (c.inverseSurface to c.inverseOnSurface), "inversePrimary" to (c.inverseSurface to c.inversePrimary),
                "error" to (c.error to c.onError), "errorContainer" to (c.errorContainer to c.onErrorContainer),
                "actionText" to (c.surface to c.primary), "errorText" to (c.surface to c.error),
                "outline" to (c.surface to c.outline), "selectedTarget" to (b.target to b.onTarget),
            )
            for ((mode, matrix) in simulations.withIndex()) for ((name, pair) in pairs) {
                val ratio = contrast(pair.first, pair.second, matrix)
                assertTrue(ratio >= 4.5, "$theme dark=$dark simulation=$mode $name $ratio")
            }
            val surfaces = listOf(b.base, b.empty, b.tile, b.alternate, b.selected, b.wood,
                b.woodAlternate, b.gold, b.water, b.waterEmpty, b.wall) + b.pieces
            for ((i, surface) in surfaces.withIndex()) {
                // Include the darkest/lightest point beneath our subtle decorative surface marks.
                val mark = b.ink.copy(alpha = if (dark) .055f else .035f)
                val once = mark.compositeOver(surface)
                val textured = if (theme == RoomTheme.LINEN || theme == RoomTheme.AMBER)
                    mark.compositeOver(once) else once // Woven lines overlap at their intersections.
                for ((mode, matrix) in simulations.withIndex()) {
                    val ratio = contrast(b.ink, textured, matrix)
                    assertTrue(ratio >= 4.5, "$theme dark=$dark simulation=$mode board $i $ratio")
                }
            }
        }
    }

    @Test fun roomsHaveDistinctSkinsAndDarkSurfacesNeverUsePureBlack() {
        for (dark in listOf(false, true)) {
            val rooms = RoomTheme.entries.map { roomColors(it, dark) }
            assertEquals(6, rooms.map { it.scheme.background }.distinct().size)
            assertEquals(6, rooms.map { it.board.tile }.distinct().size)
            assertEquals(6, rooms.map { it.board.wood }.distinct().size)
            if (dark) rooms.forEach {
                assertNotEquals(Color.Black, it.scheme.background)
                assertTrue(it.scheme.background.luminance() < .03f)
            }
        }
    }
}
