package io.github.fershibli.megagames.games.chess

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import io.github.fershibli.megagames.core.ColorMode
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.theme.Palette

// ---------------------------------------------------------------------------------------------
// Chess pieces drawn as plain, bold silhouettes in a 1×1 box (no fonts, no image files). White
// pieces are light with a dark outline, Black pieces dark with a light outline, so the two sides
// differ by brightness and outline in every palette, color-blind ones included.
// ---------------------------------------------------------------------------------------------

private fun unit(build: Path.() -> Unit) = Path().apply(build)

/** Common round base of every piece. */
private fun Path.base() {
    moveTo(0.2f, 0.9f)
    lineTo(0.8f, 0.9f)
    cubicTo(0.82f, 0.84f, 0.8f, 0.79f, 0.74f, 0.78f)
    lineTo(0.26f, 0.78f)
    cubicTo(0.2f, 0.79f, 0.18f, 0.84f, 0.2f, 0.9f)
    close()
}

private val PAWN_PATH = unit {
    base()
    moveTo(0.3f, 0.78f)
    cubicTo(0.33f, 0.62f, 0.4f, 0.52f, 0.43f, 0.47f)
    lineTo(0.57f, 0.47f)
    cubicTo(0.6f, 0.52f, 0.67f, 0.62f, 0.7f, 0.78f)
    close()
    addOval(Rect(Offset(0.5f, 0.35f), 0.14f))
}

private val ROOK_PATH = unit {
    base()
    moveTo(0.32f, 0.78f)
    lineTo(0.35f, 0.38f)
    lineTo(0.65f, 0.38f)
    lineTo(0.68f, 0.78f)
    close()
    // Battlements.
    moveTo(0.27f, 0.38f)
    lineTo(0.27f, 0.16f)
    lineTo(0.37f, 0.16f)
    lineTo(0.37f, 0.24f)
    lineTo(0.45f, 0.24f)
    lineTo(0.45f, 0.16f)
    lineTo(0.55f, 0.16f)
    lineTo(0.55f, 0.24f)
    lineTo(0.63f, 0.24f)
    lineTo(0.63f, 0.16f)
    lineTo(0.73f, 0.16f)
    lineTo(0.73f, 0.38f)
    close()
}

private val KNIGHT_PATH = unit {
    base()
    moveTo(0.28f, 0.78f)
    cubicTo(0.3f, 0.66f, 0.4f, 0.6f, 0.46f, 0.52f) // chest up to the jaw
    cubicTo(0.4f, 0.5f, 0.34f, 0.54f, 0.28f, 0.56f) // under the jaw
    cubicTo(0.2f, 0.56f, 0.16f, 0.5f, 0.2f, 0.44f) // muzzle
    lineTo(0.36f, 0.26f) // forehead
    lineTo(0.38f, 0.14f) // ear
    lineTo(0.46f, 0.2f)
    cubicTo(0.62f, 0.2f, 0.76f, 0.32f, 0.78f, 0.52f) // mane
    cubicTo(0.79f, 0.62f, 0.76f, 0.72f, 0.74f, 0.78f)
    close()
}

private val BISHOP_PATH = unit {
    base()
    moveTo(0.32f, 0.78f)
    cubicTo(0.36f, 0.7f, 0.42f, 0.66f, 0.42f, 0.62f)
    lineTo(0.58f, 0.62f)
    cubicTo(0.58f, 0.66f, 0.64f, 0.7f, 0.68f, 0.78f)
    close()
    // Mitre.
    moveTo(0.5f, 0.2f)
    cubicTo(0.66f, 0.3f, 0.7f, 0.46f, 0.62f, 0.6f)
    lineTo(0.38f, 0.6f)
    cubicTo(0.3f, 0.46f, 0.34f, 0.3f, 0.5f, 0.2f)
    close()
    addOval(Rect(Offset(0.5f, 0.15f), 0.055f))
}

private val QUEEN_PATH = unit {
    base()
    moveTo(0.27f, 0.78f)
    lineTo(0.2f, 0.3f)
    lineTo(0.36f, 0.5f)
    lineTo(0.4f, 0.24f)
    lineTo(0.5f, 0.46f)
    lineTo(0.6f, 0.24f)
    lineTo(0.64f, 0.5f)
    lineTo(0.8f, 0.3f)
    lineTo(0.73f, 0.78f)
    close()
    for (x in floatArrayOf(0.2f, 0.4f, 0.6f, 0.8f)) addOval(Rect(Offset(x, if (x == 0.2f || x == 0.8f) 0.27f else 0.2f), 0.055f))
}

private val KING_PATH = unit {
    base()
    moveTo(0.28f, 0.78f)
    cubicTo(0.2f, 0.6f, 0.2f, 0.4f, 0.34f, 0.38f)
    cubicTo(0.42f, 0.37f, 0.48f, 0.42f, 0.5f, 0.48f)
    cubicTo(0.52f, 0.42f, 0.58f, 0.37f, 0.66f, 0.38f)
    cubicTo(0.8f, 0.4f, 0.8f, 0.6f, 0.72f, 0.78f)
    close()
    // Cross.
    moveTo(0.46f, 0.42f)
    lineTo(0.46f, 0.24f)
    lineTo(0.39f, 0.24f)
    lineTo(0.39f, 0.17f)
    lineTo(0.46f, 0.17f)
    lineTo(0.46f, 0.08f)
    lineTo(0.54f, 0.08f)
    lineTo(0.54f, 0.17f)
    lineTo(0.61f, 0.17f)
    lineTo(0.61f, 0.24f)
    lineTo(0.54f, 0.24f)
    lineTo(0.54f, 0.42f)
    close()
}

private val PATHS = arrayOf(PAWN_PATH, PAWN_PATH, KNIGHT_PATH, BISHOP_PATH, ROOK_PATH, QUEEN_PATH, KING_PATH)

private val WhiteFill = Color(0xFFFBF8F1)
private val WhiteLine = Color(0xFF1C1C1C)
private val BlackFill = Color(0xFF262626)
private val BlackLine = Color(0xFFF2F2F2)

/** Draws [piece] (positive White, negative Black) filling the square at [topLeft] with side [cell]. */
fun DrawScope.drawChessPiece(piece: Int, topLeft: Offset, cell: Float, strong: Boolean = false) {
    if (piece == 0) return
    val path = PATHS[kotlin.math.abs(piece)]
    val white = piece > 0
    val fill = if (white) WhiteFill else BlackFill
    val line = if (white) WhiteLine else BlackLine
    withTransform({
        translate(topLeft.x, topLeft.y)
        scale(cell, cell, Offset.Zero)
    }) {
        // A soft shadow, the dark rim (always, so Black pieces stand out on dark squares), then the fill.
        shifted(0.02f, 0.03f) { drawPath(path, Color.Black.copy(alpha = 0.25f)) }
        drawPath(path, if (white) WhiteLine else Color(0xFF000000), style = Stroke(if (strong) 0.09f else 0.07f, join = StrokeJoin.Round))
        drawPath(path, fill)
        drawPath(path, line, style = Stroke(if (strong) 0.04f else 0.028f, join = StrokeJoin.Round))
        if (kotlin.math.abs(piece) == Chess.KNIGHT) drawCircle(line, 0.035f, Offset(0.4f, 0.32f))
        if (kotlin.math.abs(piece) == Chess.BISHOP) drawLine(line, Offset(0.5f, 0.32f), Offset(0.5f, 0.48f), 0.035f)
    }
}

private inline fun DrawScope.shifted(x: Float, y: Float, block: DrawScope.() -> Unit) = withTransform({ translate(x, y) }, block)

/** Light and dark squares for chess and checkers boards: wood tones taken from the palette. */
fun boardSquares(p: Palette): Pair<Color, Color> = when (p.mode) {
    ColorMode.HIGH_CONTRAST -> Color(0xFFFFF4D6) to Color(0xFF6B4A1E)
    ColorMode.COLORBLIND -> (if (p.isDark) Color(0xFFD9D4C7) else Color(0xFFF2EEE4)) to p.felt.lighter(0.15f)
    else -> p.wood.lighter(if (p.isDark) 0.45f else 0.6f) to p.wood.darker(if (p.isDark) 0.05f else 0.12f)
}

/** Home screen picture: a corner of a chess board with a knight, a king and a pawn. */
fun chessThumbnail(scope: DrawScope, p: Palette) = scope.run {
    val (light, dark) = boardSquares(p)
    val n = 3
    val cell = size.width / n
    for (r in 0 until n) for (c in 0 until n) {
        drawRect(if ((r + c) % 2 == 0) light else dark, Offset(c * cell, r * cell), Size(cell, cell))
    }
    drawChessPiece(-Chess.KING, Offset(cell * 2, 0f), cell, p.strongOutlines)
    drawChessPiece(-Chess.PAWN, Offset(0f, 0f), cell, p.strongOutlines)
    drawChessPiece(Chess.KNIGHT, Offset(cell * 0.5f, cell * 0.85f), cell * 1.5f, p.strongOutlines)
    drawChessPiece(Chess.PAWN, Offset(cell * 2, cell * 2), cell, p.strongOutlines)
    drawRect(dark.darker(0.4f), Offset.Zero, size, style = Stroke(size.width * 0.03f))
}
