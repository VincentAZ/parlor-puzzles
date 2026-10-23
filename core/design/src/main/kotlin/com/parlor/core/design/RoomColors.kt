package com.parlor.core.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.parlor.core.model.RoomTheme

private fun color(rgb: Long) = Color(0xFF000000 or rgb)

/** Every theme keeps a terracotta action accent and explicit light/dark contrast roles. */
@Immutable
data class RoomColors(val theme: RoomTheme, val dark: Boolean, val scheme: ColorScheme, val board: BoardSkin)

@Immutable
data class BoardSkin(
    val theme: RoomTheme, val dark: Boolean,
    val base: Color, val empty: Color, val tile: Color, val alternate: Color,
    val ink: Color, val target: Color, val onTarget: Color, val selected: Color,
    val wood: Color, val woodAlternate: Color, val gold: Color,
    val water: Color, val waterEmpty: Color, val wall: Color,
    val pieces: List<Color>,
)

val LocalBoardSkin = staticCompositionLocalOf { roomColors(RoomTheme.LINEN, false).board }

private data class Tones(val wall: Long, val panel: Long, val secondary: Long, val tile: Long,
                         val darkWall: Long, val darkPanel: Long, val darkTile: Long)

private val tones = mapOf(
    RoomTheme.LINEN to Tones(0xF8F2E8, 0xEFE4D6, 0x516047, 0xB0C9CC, 0x24211E, 0x36312C, 0x354747),
    RoomTheme.SAGE to Tones(0xF2F3E8, 0xDFE5D5, 0x455A3C, 0xC6D6B9, 0x23261F, 0x333B2E, 0x3C4A32),
    RoomTheme.ROSE to Tones(0xFAF0EC, 0xEEDFD9, 0x74504F, 0xE2C4BC, 0x29211F, 0x40312F, 0x533B39),
    RoomTheme.AMBER to Tones(0xFAF3E3, 0xEEE1C5, 0x68552F, 0xE3D09B, 0x28231D, 0x3C3327, 0x514326),
    RoomTheme.SLATE to Tones(0xF3F2ED, 0xDEE3E1, 0x435F68, 0xBDCDD2, 0x242625, 0x323B3C, 0x334953),
    RoomTheme.WALNUT to Tones(0xF7EFE5, 0xE9DAC8, 0x6B5039, 0xD8BDA0, 0x29221C, 0x403227, 0x523C2C),
)

fun roomColors(theme: RoomTheme, dark: Boolean): RoomColors {
    val t = tones.getValue(theme)
    val surface = color(if (dark) t.darkWall else t.wall)
    val panel = color(if (dark) t.darkPanel else t.panel)
    val ink = color(if (dark) 0xFFF4E3 else 0x29241E)
    val mutedInk = color(if (dark) 0xD8C9B8 else 0x5C5045)
    val accent = color(if (dark) 0xF0B59D else 0x91432F)
    val onAccent = color(if (dark) 0x382019 else 0xFFFFFF)
    val secondary = if (dark) color(t.tile) else color(t.secondary)
    val secondaryContainer = if (dark) color(t.darkTile) else color(t.tile)
    val scheme = if (dark) darkColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = color(0x613B2D), onPrimaryContainer = ink,
        secondary = secondary, onSecondary = color(0x29241E),
        secondaryContainer = secondaryContainer, onSecondaryContainer = ink,
        tertiary = secondary, onTertiary = color(0x29241E),
        tertiaryContainer = secondaryContainer, onTertiaryContainer = ink,
        background = surface, onBackground = ink, surface = surface, onSurface = ink,
        surfaceVariant = panel, onSurfaceVariant = mutedInk,
        surfaceTint = accent, outline = mutedInk, outlineVariant = mutedInk,
        surfaceDim = surface, surfaceBright = panel,
        surfaceContainerLowest = surface, surfaceContainerLow = surface,
        surfaceContainer = panel, surfaceContainerHigh = panel, surfaceContainerHighest = panel,
        inverseSurface = color(t.wall), inverseOnSurface = color(0x29241E), inversePrimary = color(0x91432F),
        error = color(0xFFBAB0), onError = color(0x4C1511),
        errorContainer = color(0x6E2821), onErrorContainer = ink,
    ) else lightColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = color(0xF0DBCB), onPrimaryContainer = color(0x442318),
        secondary = secondary, onSecondary = Color.White,
        secondaryContainer = secondaryContainer, onSecondaryContainer = ink,
        tertiary = secondary, onTertiary = Color.White,
        tertiaryContainer = secondaryContainer, onTertiaryContainer = ink,
        background = surface, onBackground = ink, surface = surface, onSurface = ink,
        surfaceVariant = panel, onSurfaceVariant = mutedInk,
        surfaceTint = accent, outline = mutedInk, outlineVariant = mutedInk,
        surfaceDim = panel, surfaceBright = surface,
        surfaceContainerLowest = surface, surfaceContainerLow = surface,
        surfaceContainer = panel, surfaceContainerHigh = panel, surfaceContainerHighest = panel,
        inverseSurface = color(t.darkWall), inverseOnSurface = color(0xFFF4E3), inversePrimary = color(0xF0B59D),
        error = color(0x9D332B), onError = Color.White,
        errorContainer = color(0xFFDAD4), onErrorContainer = color(0x4C1511),
    )
    val tile = color(if (dark) t.darkTile else t.tile)
    val pieces = if (dark) listOf(tile, color(0x494331), color(0x36464B), color(0x513C37), color(0x464832))
        else listOf(tile, color(0xD8C6B0), color(0xC8D9DE), color(0xE8D1C7), color(0xE3D7AF))
    val skin = BoardSkin(theme, dark, panel, surface, tile, lerp(panel, tile, .35f), ink,
        accent, onAccent, color(if (dark) 0x613B2D else 0xE5BA9B),
        lerp(color(if (dark) 0x503D2E else 0xE2C6A1), tile, .18f), lerp(color(if (dark) 0x453327 else 0xCFAC80), tile, .12f),
        lerp(color(if (dark) 0x594625 else 0xE1BE80), tile, .12f),
        lerp(color(if (dark) 0x36515A else 0x99C8D1), tile, .12f), color(if (dark) 0x293C3B else 0xDDE9E6),
        color(if (dark) 0x403B35 else 0xD3C9B8), pieces)
    return RoomColors(theme, dark, scheme, skin)
}

/** Original, quiet surface marks. They carry no state and never cover labels or photographs. */
fun Modifier.roomTexture(skin: BoardSkin): Modifier = drawWithCache {
    val mark = skin.ink.copy(alpha = if (skin.dark) .055f else .035f)
    val step = 12f * density
    onDrawBehind {
        when (skin.theme) {
            RoomTheme.LINEN, RoomTheme.AMBER -> {
                var x = 0f
                while (x < size.width) { drawLine(mark, Offset(x, 0f), Offset(x, size.height), density * .5f); x += step }
                var y = 0f
                while (y < size.height) { drawLine(mark, Offset(0f, y), Offset(size.width, y), density * .5f); y += step }
            }
            RoomTheme.SAGE, RoomTheme.ROSE -> {
                var y = step / 2
                while (y < size.height) {
                    var x = if ((y / step).toInt() % 2 == 0) step / 2 else step
                    while (x < size.width) { drawCircle(mark, density, Offset(x, y)); x += step }
                    y += step
                }
            }
            RoomTheme.SLATE -> {
                var x = -size.height
                while (x < size.width) { drawLine(mark, Offset(x, 0f), Offset(x + size.height, size.height), density * .6f); x += step * 2 }
            }
            RoomTheme.WALNUT -> {
                var y = step / 2
                while (y < size.height) { drawLine(mark, Offset(0f, y), Offset(size.width, y + step / 4), density); y += step }
            }
        }
    }
}
