package io.github.fershibli.megagames.games.checkers

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import io.github.fershibli.megagames.games.chess.boardSquares
import io.github.fershibli.megagames.ui.art.glossyDisc
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.theme.Palette

/** A three-point crown in a 1×1 box, drawn on top of kings. */
private val CROWN = Path().apply {
    moveTo(0.1f, 0.78f)
    lineTo(0.04f, 0.28f)
    lineTo(0.3f, 0.52f)
    lineTo(0.5f, 0.16f)
    lineTo(0.7f, 0.52f)
    lineTo(0.96f, 0.28f)
    lineTo(0.9f, 0.78f)
    close()
}

private val Gold = Color(0xFFF2B705)

/** Colors of the player's and the computer's pieces. Under English rules the player (who starts) has the dark ones. */
fun checkersColors(p: Palette, darkFirst: Boolean): Pair<Color, Color> =
    if (darkFirst) p.checkerAi to p.checkerPlayer else p.checkerPlayer to p.checkerAi

/** A checkers piece of [color] centred at [center]; kings get a gold crown with a dark outline. */
fun DrawScope.drawCheckersPiece(center: Offset, radius: Float, color: Color, king: Boolean, strong: Boolean, alpha: Float = 1f) {
    val light = color.luminance() > 0.5f
    val rim = when {
        strong -> if (light) Color.Black else Color.White
        light -> Color.Black.copy(alpha = 0.35f)
        else -> Color.White.copy(alpha = 0.35f)
    }
    if (alpha < 1f) {
        drawCircle(color.copy(alpha = alpha), radius, center)
        drawCircle(rim.copy(alpha = alpha), radius, center, style = Stroke(radius * 0.09f))
        return
    }
    glossyDisc(center, radius, color, rim)
    if (king) {
        val s = radius * 1.15f
        withTransform({
            translate(center.x - s / 2, center.y - s / 2)
            scale(s, s, Offset.Zero)
        }) {
            drawPath(CROWN, Color(0xFF3A2A00), style = Stroke(0.14f, join = StrokeJoin.Round))
            drawPath(CROWN, Gold)
        }
    }
}

/** Home screen picture: a corner of a checkers board with men of both colors and a crowned king. */
fun checkersThumbnail(scope: DrawScope, p: Palette) = scope.run {
    val (light, dark) = boardSquares(p)
    val n = 4
    val cell = size.width / n
    drawRect(Brush.linearGradient(listOf(light.lighter(0.1f), light)))
    for (r in 0 until n) for (c in 0 until n) {
        if ((r + c) % 2 == 1) drawRect(dark, Offset(c * cell, r * cell), Size(cell, cell))
    }
    val (mine, theirs) = checkersColors(p, false)
    fun at(r: Int, c: Int) = Offset((c + 0.5f) * cell, (r + 0.5f) * cell)
    drawCheckersPiece(at(0, 1), cell * 0.4f, theirs, false, p.strongOutlines)
    drawCheckersPiece(at(0, 3), cell * 0.4f, theirs, false, p.strongOutlines)
    drawCheckersPiece(at(1, 2), cell * 0.4f, theirs, false, p.strongOutlines)
    drawCheckersPiece(at(2, 1), cell * 0.4f, mine, true, p.strongOutlines)
    drawCheckersPiece(at(3, 0), cell * 0.4f, mine, false, p.strongOutlines)
    drawCheckersPiece(at(3, 2), cell * 0.4f, mine, false, p.strongOutlines)
    drawRoundRect(dark, Offset.Zero, size, CornerRadius(0f), style = Stroke(size.width * 0.03f))
}
