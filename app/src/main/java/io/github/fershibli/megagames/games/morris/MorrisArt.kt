package io.github.fershibli.megagames.games.morris

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.fershibli.megagames.games.checkers.drawCheckersPiece
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.theme.Palette

/**
 * Where things are on the canvas: a strip at the top for the computer's pieces still to place,
 * the square board, and a strip at the bottom for yours. The canvas is [HEIGHT_RATIO] times as
 * tall as it is wide.
 */
internal class MorrisGeometry(val width: Float, rules: MorrisRules) {
    val strip = width * STRIP
    val margin = width * 0.08f
    val spacing = (width - margin * 2) / (rules.grid - 1)

    fun point(x: Int, y: Int) = Offset(margin + x * spacing, strip + margin + y * spacing)

    companion object {
        const val STRIP = 0.1f
        const val HEIGHT_RATIO = 1f + STRIP * 2
    }
}

/** The colors of White (the side that places first) and Black. */
fun morrisColor(p: Palette, side: Int): Color = if (side == Morris.WHITE) p.checkerPlayer else p.checkerAi

/** Board with its lines and points, on a wooden square of side [side] whose top-left corner is [origin]. */
fun DrawScope.drawMorrisBoard(origin: Offset, side: Float, margin: Float, spacing: Float, rules: MorrisRules, p: Palette) {
    drawRoundRect(Brush.linearGradient(listOf(p.wood.lighter(0.25f), p.wood), origin, origin + Offset(side, side)), origin, Size(side, side), CornerRadius(side * 0.03f))
    val ink = if (p.strongOutlines) Color.Black else p.woodDark.darker(0.45f)
    fun at(i: Int) = origin + Offset(margin + rules.x(i) * spacing, margin + rules.y(i) * spacing)
    val stroke = spacing * 0.07f
    for (i in 0 until rules.points) for (j in rules.adjacency[i]) {
        if (j > i) drawLine(ink, at(i), at(j), stroke, StrokeCap.Round)
    }
    for (i in 0 until rules.points) drawCircle(ink, spacing * 0.13f, at(i))
}

/** Home screen picture: the nine men's morris board with a few pieces and a mill. */
fun morrisThumbnail(scope: DrawScope, p: Palette) = scope.run {
    val rules = MorrisRules.NINE
    val side = size.minDimension
    val margin = side * 0.1f
    val spacing = (side - margin * 2) / (rules.grid - 1)
    drawMorrisBoard(Offset.Zero, side, margin, spacing, rules, p)
    fun at(i: Int) = Offset(margin + rules.x(i) * spacing, margin + rules.y(i) * spacing)
    for (i in intArrayOf(0, 1, 2, 11, 21)) drawCheckersPiece(at(i), spacing * 0.42f, morrisColor(p, Morris.WHITE), false, p.strongOutlines)
    for (i in intArrayOf(7, 15, 4, 19)) drawCheckersPiece(at(i), spacing * 0.42f, morrisColor(p, Morris.BLACK), false, p.strongOutlines)
}
