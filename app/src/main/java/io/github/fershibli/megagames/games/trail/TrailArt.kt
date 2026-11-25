package io.github.fershibli.megagames.games.trail

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.fershibli.megagames.games.ludo.drawLudoPiece
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.theme.Palette

/** Soft color of each stage's row, from childhood (bottom) to the quiet years (top). */
private val STAGE_COLORS = listOf(
    Color(0xFFC5E1A5), // childhood: spring green
    Color(0xFFB3E5FC), // youth: sky blue
    Color(0xFFFFE0B2), // work: orange
    Color(0xFFF8BBD0), // family: pink
    Color(0xFFD1C4E9), // travel: lavender
    Color(0xFFFFF59D), // rest: gold
)
private val HEART = Color(0xFFE53935)
private val PATH = Color(0xFF8D6E63)

/** Center of a square, in pixels, for squares [c] pixels wide. */
fun squareCenter(index: Int, c: Float): Offset {
    val (row, col) = Trail.rowCol(index)
    return Offset((col + 0.5f) * c, (Trail.ROWS - 1 - row + 0.5f) * c)
}

fun DrawScope.heart(center: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y + r * 0.85f)
        cubicTo(center.x - r * 1.3f, center.y - r * 0.1f, center.x - r * 0.7f, center.y - r * 1.05f, center.x, center.y - r * 0.4f)
        cubicTo(center.x + r * 0.7f, center.y - r * 1.05f, center.x + r * 1.3f, center.y - r * 0.1f, center.x, center.y + r * 0.85f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.squareMark(square: Square, center: Offset, c: Float, p: Palette) {
    val ink = if (p.strongOutlines) Color.Black else Color(0xFF5D4037)
    val r = c * 0.22f
    when (square) {
        Square.HEART -> heart(center, r, HEART)
        Square.STORY -> {
            // An open book.
            val w = r * 1.1f
            drawRect(Color.White, Offset(center.x - w, center.y - r * 0.7f), Size(w * 2, r * 1.4f))
            drawLine(ink, Offset(center.x, center.y - r * 0.7f), Offset(center.x, center.y + r * 0.7f), c * 0.025f)
            drawRect(ink, Offset(center.x - w, center.y - r * 0.7f), Size(w * 2, r * 1.4f), style = Stroke(c * 0.025f))
            for (k in 0 until 3) {
                val y = center.y - r * 0.35f + k * r * 0.35f
                drawLine(ink.copy(alpha = 0.5f), Offset(center.x - w * 0.8f, y), Offset(center.x - w * 0.15f, y), c * 0.015f)
                drawLine(ink.copy(alpha = 0.5f), Offset(center.x + w * 0.15f, y), Offset(center.x + w * 0.8f, y), c * 0.015f)
            }
        }
        Square.CHOICE -> {
            // A signpost with two arms.
            drawLine(ink, Offset(center.x, center.y - r), Offset(center.x, center.y + r), c * 0.04f, StrokeCap.Round)
            val left = Path().apply { moveTo(center.x, center.y - r * 0.9f); lineTo(center.x - r, center.y - r * 0.9f); lineTo(center.x - r * 1.3f, center.y - r * 0.6f); lineTo(center.x - r, center.y - r * 0.3f); lineTo(center.x, center.y - r * 0.3f); close() }
            val right = Path().apply { moveTo(center.x, center.y - r * 0.1f); lineTo(center.x + r, center.y - r * 0.1f); lineTo(center.x + r * 1.3f, center.y + r * 0.2f); lineTo(center.x + r, center.y + r * 0.5f); lineTo(center.x, center.y + r * 0.5f); close() }
            drawPath(left, Color(0xFF42A5F5))
            drawPath(right, Color(0xFFFFA726))
        }
        Square.START -> {
            drawLine(ink, Offset(center.x - r * 0.6f, center.y + r), Offset(center.x - r * 0.6f, center.y - r), c * 0.04f, StrokeCap.Round)
            val flag = Path().apply { moveTo(center.x - r * 0.6f, center.y - r); lineTo(center.x + r, center.y - r * 0.55f); lineTo(center.x - r * 0.6f, center.y - r * 0.1f); close() }
            drawPath(flag, Color(0xFF43A047))
        }
        Square.FINISH -> {
            // A little house with a heart on the door.
            val roof = Path().apply { moveTo(center.x - r * 1.2f, center.y - r * 0.1f); lineTo(center.x, center.y - r * 1.2f); lineTo(center.x + r * 1.2f, center.y - r * 0.1f); close() }
            drawPath(roof, Color(0xFFC0392B))
            drawRect(Color(0xFFFFF8E1), Offset(center.x - r * 0.9f, center.y - r * 0.1f), Size(r * 1.8f, r * 1.2f))
            drawRect(ink, Offset(center.x - r * 0.9f, center.y - r * 0.1f), Size(r * 1.8f, r * 1.2f), style = Stroke(c * 0.02f))
            heart(Offset(center.x, center.y + r * 0.45f), r * 0.4f, HEART)
        }
        Square.PLAIN -> drawCircle(ink.copy(alpha = 0.25f), c * 0.05f, center)
    }
}

/** The whole path: six colored rows that snake up the board, joined at the ends. */
fun DrawScope.drawTrailBoard(c: Float, p: Palette) {
    val dim = if (p.isDark) 0.35f else 0f
    // The road behind the squares.
    for (i in 0 until Trail.FINISH) {
        drawLine(PATH.darker(dim), squareCenter(i, c), squareCenter(i + 1, c), c * 0.28f, StrokeCap.Round)
    }
    for (i in 0 until Trail.SQUARES) {
        val center = squareCenter(i, c)
        val color = STAGE_COLORS[Trail.stage(i).ordinal].darker(dim)
        val tl = center - Offset(c * 0.43f, c * 0.43f)
        val sz = Size(c * 0.86f, c * 0.86f)
        val special = i == 0 || i == Trail.FINISH
        drawRoundRect(if (special) color.lighter(0.4f) else color, tl, sz, CornerRadius(c * 0.18f))
        drawRoundRect(if (p.strongOutlines) Color.Black else color.darker(0.4f), tl, sz, CornerRadius(c * 0.18f), style = Stroke(c * if (p.strongOutlines) 0.05f else 0.03f))
        squareMark(Trail.square(i), center, c, p)
    }
}

/** A pawn: Ludo's glossy piece with its color's symbol. */
fun DrawScope.drawTrailPawn(center: Offset, radius: Float, p: Palette, color: Int, ring: Color?) =
    drawLudoPiece(center, radius, p, color, ring)

/** Home-screen picture: three rows of the path with a heart, a book and two pawns. */
fun trailThumbnail(scope: DrawScope, p: Palette) = scope.run {
    val c = size.width / 4f
    val top = (size.height - c * 3) / 2
    val dim = if (p.isDark) 0.35f else 0f
    fun at(row: Int, col: Int) = Offset((col + 0.5f) * c, top + (2 - row + 0.5f) * c)
    val order = (0 until 12).map { k -> val row = k / 4; val col = if (row % 2 == 0) k % 4 else 3 - k % 4; row to col }
    for (k in 0 until 11) drawLine(PATH.darker(dim), at(order[k].first, order[k].second), at(order[k + 1].first, order[k + 1].second), c * 0.28f, StrokeCap.Round)
    val marks = listOf(Square.START, Square.PLAIN, Square.HEART, Square.STORY, Square.PLAIN, Square.CHOICE, Square.HEART, Square.PLAIN, Square.STORY, Square.HEART, Square.PLAIN, Square.FINISH)
    order.forEachIndexed { k, (row, col) ->
        val center = at(row, col)
        val color = STAGE_COLORS[row * 2].darker(dim)
        drawRoundRect(color, center - Offset(c * 0.43f, c * 0.43f), Size(c * 0.86f, c * 0.86f), CornerRadius(c * 0.18f))
        drawRoundRect(color.darker(0.4f), center - Offset(c * 0.43f, c * 0.43f), Size(c * 0.86f, c * 0.86f), CornerRadius(c * 0.18f), style = Stroke(c * 0.03f))
        squareMark(marks[k], center, c, p)
    }
    drawTrailPawn(at(0, 1), c * 0.28f, p, 0, null)
    drawTrailPawn(at(1, 2), c * 0.28f, p, 3, null)
}
