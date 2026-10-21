package io.github.fershibli.megagames.games.wordsearch

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.pxToSp
import io.github.fershibli.megagames.ui.art.text
import io.github.fershibli.megagames.ui.theme.Palette

/** Number of band patterns; together with the colors they tell every found word apart. */
const val BAND_PATTERNS = 4

/** Color of the band of the [k]-th word found. */
fun bandColor(p: Palette, k: Int): Color = p.pieces[k % p.pieces.size]

/**
 * Draws a found-word band from the centre [a] to the centre [b], [width] thick. Each band has a
 * color and one of four patterns, so the bands stay apart without relying on color alone:
 * plain, with solid edges, with dashed edges, or hollow with a thick rim.
 */
fun DrawScope.drawBand(a: Offset, b: Offset, width: Float, color: Color, pattern: Int, strong: Boolean) {
    val edge = color.darker(0.45f)
    when (pattern % BAND_PATTERNS) {
        3 -> {
            drawLine(edge, a, b, width, StrokeCap.Round)
            drawLine(color.copy(alpha = 0.22f), a, b, width * 0.66f, StrokeCap.Round)
        }
        else -> drawLine(color.copy(alpha = 0.55f), a, b, width, StrokeCap.Round)
    }
    if (pattern % BAND_PATTERNS == 1 || pattern % BAND_PATTERNS == 2) {
        val d = b - a
        val len = d.getDistance().coerceAtLeast(0.001f)
        // Unit vector across the band, to draw its two edges.
        val n = Offset(-d.y / len, d.x / len) * (width * 0.42f)
        val effect = if (pattern % BAND_PATTERNS == 2) PathEffect.dashPathEffect(floatArrayOf(width * 0.22f, width * 0.16f)) else null
        val stroke = width * if (strong) 0.12f else 0.09f
        drawLine(edge, a + n, b + n, stroke, StrokeCap.Round, effect)
        drawLine(edge, a - n, b - n, stroke, StrokeCap.Round, effect)
    }
    if (strong) drawLine(edge.copy(alpha = 0.6f), a, b, width * 0.06f, StrokeCap.Round)
}

/** Home-screen picture: a small grid of letters with three words circled by bands. */
fun wordSearchThumbnail(scope: DrawScope, p: Palette, m: TextMeasurer) = with(scope) {
    drawRect(p.boardLight)
    val n = 5
    val pad = size.width * 0.06f
    val cell = (size.width - pad * 2) / n
    val rows = listOf("SUNXA", "TOEKB", "ARWMD", "RKILE", "WINDS")
    fun center(r: Int, c: Int) = Offset(pad + (c + 0.5f) * cell, pad + (r + 0.5f) * cell)
    drawBand(center(0, 0), center(0, 2), cell * 0.8f, bandColor(p, 0), 0, p.strongOutlines)
    drawBand(center(0, 0), center(3, 0), cell * 0.8f, bandColor(p, 1), 1, p.strongOutlines)
    drawBand(center(1, 1), center(3, 3), cell * 0.8f, bandColor(p, 2), 2, p.strongOutlines)
    for (r in 0 until n) for (c in 0 until n) {
        text(m, rows[r][c].toString(), center(r, c), p.text, pxToSp(cell * 0.55f))
    }
}
