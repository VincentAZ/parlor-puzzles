package com.parlor.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.parlor.core.design.BoardSkin
import com.parlor.core.design.LocalBoardSkin
import com.parlor.core.model.Puzzle

/** Original vector room and puzzle objects. Decorative; adjacent native controls carry all semantics. */
@Composable
internal fun RoomIllustration(modifier: Modifier) {
    val skin = LocalBoardSkin.current
    Canvas(modifier.clearAndSetSemantics {}) {
        val unit = minOf(size.width / 340f, size.height / 200f)
        translate((size.width - 340f * unit) / 2, (size.height - 200f * unit) / 2) {
            scale(unit, Offset.Zero) {
                box(27f, 15f, 89f, 91f, skin.wood, 42f)
                box(33f, 21f, 77f, 78f, skin.waterEmpty, 36f)
                drawCircle(skin.gold, 14f, Offset(83f, 47f))
                line(71f, 22f, 71f, 100f, skin.wood, 4f)
                line(32f, 67f, 111f, 67f, skin.wood, 4f)
                line(12f, 129f, 328f, 129f, skin.woodAlternate, 2f)
                line(285f, 66f, 285f, 145f, skin.ink, 3f)
                box(271f, 141f, 29f, 5f, skin.woodAlternate)
                polygon(listOf(268f to 29f, 302f to 29f, 312f to 70f, 257f to 70f), skin.gold)
                box(16f, 116f, 30f, 31f, skin.target, 7f)
                line(31f, 119f, 31f, 85f, skin.ink, 2f)
                drawOval(skin.tile, Offset(13f, 80f), Size(19f, 13f))
                drawOval(skin.tile, Offset(31f, 94f), Size(18f, 12f))
                drawOval(skin.tile, Offset(25f, 71f), Size(12f, 21f))
                drawOval(skin.ink.copy(alpha = .06f), Offset(28f, 184f), Size(280f, 13f))
                box(41f, 153f, 9f, 36f, skin.woodAlternate)
                box(290f, 153f, 9f, 36f, skin.woodAlternate)
                box(28f, 145f, 284f, 13f, skin.wood, 6f)
                box(35f, 157f, 270f, 6f, skin.woodAlternate, 2f)
                translate(74f, 30f) { scale(1.18f, Offset.Zero) { puzzleObject(Puzzle.TRAFFIC_JAM, skin) } }
            }
        }
    }
}

@Composable
internal fun PuzzleObject(puzzle: Puzzle, modifier: Modifier) {
    val skin = LocalBoardSkin.current
    Canvas(modifier.clearAndSetSemantics {}) {
        val unit = minOf(size.width / 160f, size.height / 112f)
        translate((size.width - 160f * unit) / 2, size.height - 112f * unit) {
            scale(unit, Offset.Zero) { puzzleObject(puzzle, skin) }
        }
    }
}

private fun DrawScope.box(x: Float, y: Float, w: Float, h: Float, color: Color, radius: Float = 4f) {
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(radius))
}
private fun DrawScope.line(x: Float, y: Float, x2: Float, y2: Float, color: Color, width: Float = 2f) {
    drawLine(color, Offset(x, y), Offset(x2, y2), width)
}
private fun DrawScope.polygon(points: List<Pair<Float, Float>>, color: Color) {
    drawPath(Path().apply {
        moveTo(points.first().first, points.first().second)
        points.drop(1).forEach { (x, y) -> lineTo(x, y) }
        close()
    }, color)
}

private fun DrawScope.puzzleObject(puzzle: Puzzle, s: BoardSkin) {
    fun piece(x: Float, y: Float, w: Float, h: Float, color: Color) {
        box(x, y + 3, w, h, s.woodAlternate)
        box(x, y, w, h, color)
        line(x + 4, y + 4, x + w - 4, y + 4, s.ink.copy(alpha = .12f), 1f)
    }
    fun board() { piece(25f, 15f, 110f, 89f, s.wood) }
    fun chess() {
        board()
        for (r in 0..3) for (c in 0..3) box(32f + c * 24, 22f + r * 18, 23f, 17f,
            if ((r + c) % 2 == 0) s.tile else s.empty, 1f)
    }
    when (puzzle) {
        Puzzle.TRAFFIC_JAM -> {
            board()
            piece(33f, 22f, 48f, 15f, s.tile); piece(87f, 22f, 17f, 44f, s.pieces[3])
            piece(33f, 46f, 48f, 17f, s.target); piece(111f, 22f, 16f, 19f, s.gold)
            piece(111f, 47f, 16f, 42f, s.tile); piece(33f, 78f, 46f, 16f, s.pieces[4])
            line(136f, 52f, 147f, 52f, s.target, 3f)
            line(143f, 47f, 148f, 52f, s.target); line(143f, 57f, 148f, 52f, s.target)
        }
        Puzzle.HANOI -> {
            piece(13f, 96f, 134f, 8f, s.wood)
            listOf(47f, 87f, 122f).forEach { box(it, 27f, 5f, 71f, s.woodAlternate) }
            piece(23f, 82f, 54f, 12f, s.target); piece(29f, 67f, 42f, 12f, s.gold)
            piece(35f, 52f, 30f, 12f, s.tile); piece(115f, 83f, 19f, 11f, s.pieces[3])
        }
        Puzzle.SLIDING_TILES -> {
            board()
            for (r in 0..2) for (c in 0..2) if (r != 2 || c != 2) {
                val x = 32f + c * 32; val y = 22f + r * 25
                piece(x, y, 28f, 21f, s.pieces[(r + c) % s.pieces.size])
                repeat(r * 3 + c + 1) { dot -> drawCircle(s.ink, 1.1f, Offset(x + 7 + (dot % 3) * 6, y + 6 + (dot / 3) * 5)) }
            }
        }
        Puzzle.BLOCK_ESCAPE -> {
            board(); piece(58f, 23f, 43f, 37f, s.target)
            piece(32f, 23f, 19f, 59f, s.tile); piece(108f, 23f, 19f, 42f, s.gold)
            piece(58f, 69f, 43f, 17f, s.pieces[3]); piece(108f, 73f, 19f, 20f, s.tile)
        }
        Puzzle.BOX_PUSHER -> {
            board()
            for (i in 0..3) piece(32f + i * 24, 23f, 20f, 13f, s.wall)
            piece(36f, 56f, 31f, 30f, s.gold)
            line(40f, 60f, 63f, 82f, s.ink); line(63f, 60f, 40f, 82f, s.ink)
            drawCircle(s.target, 12f, Offset(105f, 73f), style = Stroke(3f))
            drawCircle(s.ink, 6f, Offset(80f, 49f))
        }
        Puzzle.SWITCH_GRID -> {
            board()
            for (r in 0..2) for (c in 0..2) {
                val x = 34f + c * 32; val y = 24f + r * 25
                piece(x, y, 26f, 19f, if ((r + c) % 2 == 0) s.gold else s.tile)
                line(x + 9, y + 9, x + 17, y + 9, s.ink)
                if ((r + c) % 2 == 0) line(x + 13, y + 5, x + 13, y + 13, s.ink)
            }
        }
        Puzzle.PEG_SOLITAIRE -> {
            drawCircle(s.woodAlternate, 47f, Offset(80f, 59f)); drawCircle(s.wood, 45f, Offset(80f, 56f))
            for (r in 0..4) for (c in 0..4) if (r in 1..3 || c in 1..3) {
                val center = Offset(48f + c * 16, 24f + r * 16)
                drawCircle(s.woodAlternate, 5.5f, center)
                if (r != 2 || c != 2) {
                    drawCircle(s.target, 4.5f, center - Offset(0f, 2f))
                    drawCircle(s.onTarget.copy(alpha = .35f), 1f, center - Offset(1f, 3f))
                }
            }
        }
        Puzzle.QUEENS -> {
            chess()
            polygon(listOf(62f to 75f, 55f to 44f, 70f to 52f, 80f to 34f, 90f to 52f, 105f to 44f, 98f to 75f), s.target)
            piece(61f, 77f, 38f, 8f, s.target)
            listOf(55f to 42f, 80f to 33f, 105f to 42f).forEach { (x, y) -> drawCircle(s.gold, 3f, Offset(x, y)) }
        }
        Puzzle.KNIGHTS_TOUR -> {
            chess()
            polygon(listOf(62f to 82f, 69f to 65f, 77f to 55f, 62f to 58f, 57f to 49f, 76f to 32f,
                79f to 21f, 88f to 30f, 101f to 41f, 104f to 59f, 98f to 82f), s.target)
            drawCircle(s.onTarget, 2f, Offset(80f, 41f)); piece(60f, 83f, 43f, 6f, s.target)
        }
        Puzzle.WATER_JUGS -> {
            listOf(35f to 53f, 83f to 29f).forEach { (x, y) ->
                drawRoundRect(s.woodAlternate, Offset(x + 22, y + 8), Size(24f, 31f), CornerRadius(9f), style = Stroke(4f))
                piece(x, y, 35f, 101f - y, s.waterEmpty)
                box(x + 4, y + 21, 27f, 76f - y, s.water, 2f)
                line(x + 7, y + 11, x + 7, 91f, s.ink.copy(alpha = .3f), 1f)
                line(x - 2, y, x + 37, y, s.ink, 2f)
            }
        }
        Puzzle.CROSSINGS -> {
            drawOval(s.water, Offset(16f, 83f), Size(132f, 22f))
            polygon(listOf(39f to 76f, 125f to 76f, 112f to 93f, 55f to 93f), s.wood)
            line(86f, 32f, 86f, 76f, s.ink)
            polygon(listOf(90f to 33f, 90f to 69f, 117f to 69f), s.tile)
            drawCircle(s.target, 8f, Offset(60f, 58f)); box(52f, 64f, 16f, 12f, s.target)
            line(46f, 76f, 24f, 96f, s.woodAlternate, 4f)
        }
        Puzzle.PANCAKE_FLIP -> {
            drawOval(s.tile, Offset(20f, 89f), Size(120f, 16f))
            listOf(80f, 100f, 55f, 65f, 35f).forEachIndexed { i, width ->
                piece(80f - width / 2, 81f - i * 14, width, 12f, if (i % 2 == 0) s.gold else s.wood)
            }
        }
    }
}
