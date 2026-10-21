package io.github.fershibli.megagames.games.mancala

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.art.pxToSp
import io.github.fershibli.megagames.ui.art.text
import io.github.fershibli.megagames.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where things are on the canvas: a row of numbers above the board (the computer's pits and
 * store), the wooden board with two rows of six pits between the two stores, and a row of numbers
 * below it (your pits and store). The canvas is [HEIGHT_RATIO] times as wide as it is tall.
 */
internal class MancalaGeometry(width: Float) {
    val column = width / 8
    val label = width * LABEL
    val boardTop = label
    val boardHeight = width * BOARD
    val boardBottom = boardTop + boardHeight
    private val pad = column * 0.08f

    /** Column (0..7, left to right) of a hole. */
    fun columnOf(hole: Int) = when {
        hole == 13 -> 0
        hole == 6 -> 7
        hole < 6 -> hole + 1
        else -> 13 - hole
    }

    fun rect(hole: Int): Rect {
        val x = columnOf(hole) * column
        return when {
            Mancala.isStore(hole) -> Rect(x + pad, boardTop + pad * 2, x + column - pad, boardBottom - pad * 2)
            hole < 6 -> Rect(x + pad, boardTop + boardHeight / 2 + pad, x + column - pad, boardBottom - pad * 2)
            else -> Rect(x + pad, boardTop + pad * 2, x + column - pad, boardTop + boardHeight / 2 - pad)
        }
    }

    /** Where the number of seeds of [hole] is written: above the top row, below the bottom row. */
    fun labelCenter(hole: Int): Offset {
        val x = (columnOf(hole) + 0.5f) * column
        val top = hole >= 7
        return Offset(x, if (top) label / 2 else boardBottom + label / 2)
    }

    /** The hole at [o] on the canvas (the number above or below a pit counts), or -1. */
    fun hit(o: Offset): Int {
        val c = (o.x / column).toInt()
        if (c !in 0..7) return -1
        if (c == 0) return 13
        if (c == 7) return 6
        return if (o.y >= boardTop + boardHeight / 2) c - 1 else 13 - c
    }

    companion object {
        const val LABEL = 0.1f
        const val BOARD = 0.75f
        const val HEIGHT_RATIO = LABEL * 2 + BOARD
    }
}

/** Natural stone colors for the seeds. */
private val STONES = arrayOf(Color(0xFFEDE6D3), Color(0xFFB8C9DA), Color(0xFFDDB896), Color(0xFFC4D9AE))

/** The seeds of a hole as small pebbles spread over it, at most 30 of them (the number tells the rest). */
fun DrawScope.drawSeeds(r: Rect, count: Int, scale: Float) {
    val shown = minOf(count, 30)
    if (shown == 0) return
    val base = minOf(r.width, r.height) * 0.2f * scale.coerceIn(0.8f, 1.3f)
    val pebble = when {
        shown > 18 -> base * 0.62f
        shown > 9 -> base * 0.78f
        else -> base
    }
    // A few seeds lie close together in the middle; more of them spread over the whole hole.
    val spread = sqrt(minOf(1f, shown / 14f))
    val rx = (r.width / 2 - pebble * 1.15f) * spread
    // ...in a round heap rather than a column, even in tall holes.
    val ry = minOf((r.height / 2 - pebble * 1.15f) * spread, rx * (1f + shown / 10f))
    val c = r.center
    for (k in 0 until shown) {
        val d = if (shown == 1) 0f else sqrt((k + 0.5f) / shown)
        val a = k * 2.39996f
        val at = Offset(c.x + cos(a) * rx * d, c.y + sin(a) * ry * d)
        val color = STONES[k % STONES.size]
        drawCircle(Color.Black.copy(alpha = 0.25f), pebble, at + Offset(pebble * 0.12f, pebble * 0.18f))
        drawCircle(color, pebble, at)
        drawCircle(color.darker(0.45f), pebble, at, style = Stroke(pebble * 0.18f))
        drawCircle(Color.White.copy(alpha = 0.45f), pebble * 0.3f, at - Offset(pebble * 0.3f, pebble * 0.3f))
    }
}

/** The wooden board, its holes and seeds, and the number of seeds next to each hole. */
internal fun DrawScope.drawMancala(g: MancalaGeometry, pits: List<Int>, p: Palette, measurer: TextMeasurer?, scale: Float) {
    val boardRect = Rect(0f, g.boardTop, size.width, g.boardBottom)
    drawRoundRect(
        Brush.linearGradient(listOf(p.wood.lighter(0.2f), p.wood, p.woodDark), boardRect.topLeft, boardRect.bottomRight),
        boardRect.topLeft, boardRect.size, CornerRadius(g.column * 0.4f),
    )
    if (p.strongOutlines) drawRoundRect(Color.Black, boardRect.topLeft, boardRect.size, CornerRadius(g.column * 0.4f), style = Stroke(3f))
    val hollow = p.woodDark.darker(0.35f)
    for (hole in 0 until 14) {
        val r = g.rect(hole)
        drawRoundRect(Brush.verticalGradient(listOf(hollow.darker(0.3f), hollow), r.top, r.bottom), r.topLeft, r.size, CornerRadius(r.width / 2))
        drawRoundRect(p.wood.lighter(0.35f).copy(alpha = 0.5f), r.topLeft, r.size, CornerRadius(r.width / 2), style = Stroke(r.width * 0.04f))
        drawSeeds(r, pits[hole], scale)
        if (measurer != null) text(measurer, pits[hole].toString(), g.labelCenter(hole), p.text, pxToSp(g.label * 0.8f))
    }
}

/** Home screen picture: the board with seeds in every pit. */
fun mancalaThumbnail(scope: DrawScope, p: Palette) = scope.run {
    val g = MancalaGeometry(size.width)
    translate(0f, (size.height - size.width * MancalaGeometry.HEIGHT_RATIO) / 2) {
        drawMancala(g, listOf(4, 5, 0, 6, 4, 3, 7, 4, 1, 5, 6, 4, 2, 5), p, null, 1f)
    }
}
