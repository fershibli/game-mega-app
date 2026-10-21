package io.github.fershibli.megagames.games.go

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.LocalSettings
import io.github.fershibli.megagames.ui.theme.Palette

// ---------------------------------------------------------------------------------------------
// The wooden board with lines used by Go and by five in a row, and the black and white stones.
// Stones are black and white in every palette (that is the game); the board, the marks and the
// outlines follow the palette so high contrast and dark modes stay readable.
// ---------------------------------------------------------------------------------------------

private val BlackStone = Color(0xFF1E1E21)
private val WhiteStone = Color(0xFFF6F3EB)
private val GridLine = Color(0xFF2B1D10)

fun goBoardColor(p: Palette): Color = p.wood.lighter(if (p.isDark) 0.25f else 0.3f)

/** A stone of [color] ([Go.BLACK] or [Go.WHITE]) centred at [center]. */
fun DrawScope.drawStone(center: Offset, radius: Float, color: Int, strong: Boolean, alpha: Float = 1f) {
    val black = color == Go.BLACK
    val base = if (black) BlackStone else WhiteStone
    if (alpha < 1f) {
        drawCircle(base.copy(alpha = alpha), radius, center)
        drawCircle((if (black) Color.White else Color.Black).copy(alpha = alpha * 0.6f), radius, center, style = Stroke(radius * 0.08f))
        return
    }
    drawCircle(Color.Black.copy(alpha = 0.28f), radius, center + Offset(radius * 0.08f, radius * 0.12f))
    val shine = if (black) Color(0xFF55555C) else Color.White
    val edge = if (black) Color(0xFF0C0C0E) else Color(0xFFCFC9BC)
    drawCircle(
        Brush.radialGradient(listOf(shine, base, edge), center - Offset(radius * 0.35f, radius * 0.4f), radius * 1.5f),
        radius, center,
    )
    val rim = when {
        strong -> if (black) Color.White else Color.Black
        black -> Color.White.copy(alpha = 0.25f)
        else -> Color.Black.copy(alpha = 0.35f)
    }
    drawCircle(rim, radius, center, style = Stroke(radius * (if (strong) 0.12f else 0.06f)))
}

/** The wooden board: [n] lines each way through the centres of an n×n grid of cells, and the star points. */
fun DrawScope.drawGridBoard(n: Int, p: Palette) {
    val cell = size.width / n
    val wood = goBoardColor(p)
    drawRect(Brush.linearGradient(listOf(wood.lighter(0.12f), wood, wood.darker(0.06f)), Offset.Zero, Offset(size.width, size.height)))
    val half = cell / 2
    val line = if (p.strongOutlines) cell * 0.06f else cell * 0.035f
    for (k in 0 until n) {
        val at = half + k * cell
        drawLine(GridLine, Offset(half, at), Offset(size.width - half, at), line, StrokeCap.Square)
        drawLine(GridLine, Offset(at, half), Offset(at, size.height - half), line, StrokeCap.Square)
    }
    drawRect(GridLine, Offset(half, half), Size(size.width - cell, size.height - cell), style = Stroke(line * 1.8f))
    for (i in Go.starPoints(n)) drawCircle(GridLine, cell * 0.11f, Offset(half + (i % n) * cell, half + (i / n) * cell))
}

/**
 * A square board of [n]×[n] crossings with stones ([stoneAt] gives [Go.BLACK], [Go.WHITE] or 0
 * for `row * n + column`). Marks: the [last] move, a [ghost] stone waiting for confirmation, a
 * [hint], a winning [line], the [territory] and [dead] stones while counting.
 */
@Composable
fun GridBoard(
    n: Int,
    modifier: Modifier,
    stoneAt: (Int) -> Int,
    last: Int = -1,
    ghost: Int = -1,
    ghostColor: Int = Go.BLACK,
    hint: Int = -1,
    line: List<Int> = emptyList(),
    territory: IntArray? = null,
    dead: Set<Int> = emptySet(),
    onTap: ((Int) -> Unit)? = null,
) {
    val p = LocalPalette.current
    val scale = LocalSettings.current.pieceScale
    Canvas(
        modifier.then(
            if (onTap == null) Modifier else Modifier.pointerInput(n, onTap) {
                detectTapGestures { o ->
                    val cell = size.width.toFloat() / n
                    val c = (o.x / cell).toInt()
                    val r = (o.y / cell).toInt()
                    if (o.x >= 0 && o.y >= 0 && c in 0 until n && r in 0 until n) onTap(r * n + c)
                }
            },
        ),
    ) {
        drawGridBoard(n, p)
        val cell = size.width / n
        val radius = cell * (0.46f * scale).coerceIn(0.38f, 0.49f)
        fun center(i: Int) = Offset((i % n + 0.5f) * cell, (i / n + 0.5f) * cell)
        val strong = p.strongOutlines
        for (i in 0 until n * n) {
            val c = stoneAt(i)
            if (c == Go.BLACK || c == Go.WHITE) drawStone(center(i), radius, c, strong, alpha = if (i in dead) 0.4f else 1f)
        }
        if (territory != null) {
            for (i in 0 until n * n) {
                val owner = territory[i]
                if (owner == 0) continue
                val half = cell * 0.17f
                val at = center(i) - Offset(half, half)
                drawRect(if (owner == Go.BLACK) BlackStone else WhiteStone, at, Size(half * 2, half * 2))
                drawRect(if (owner == Go.BLACK) Color.White else Color.Black, at, Size(half * 2, half * 2), style = Stroke(cell * 0.03f))
            }
            for (i in dead) {
                val c = center(i)
                val d = radius * 0.5f
                drawLine(p.danger, c - Offset(d, d), c + Offset(d, d), cell * 0.08f, StrokeCap.Round)
                drawLine(p.danger, c - Offset(d, -d), c + Offset(d, -d), cell * 0.08f, StrokeCap.Round)
            }
        }
        if (last >= 0) {
            val c = stoneAt(last)
            drawCircle(if (c == Go.BLACK) Color.White else Color.Black, radius * 0.42f, center(last), style = Stroke(cell * 0.08f))
        }
        if (line.size >= 2) {
            drawLine(p.highlight.darker(0.2f), center(line.first()), center(line.last()), cell * 0.22f, StrokeCap.Round)
            drawLine(p.highlight, center(line.first()), center(line.last()), cell * 0.13f, StrokeCap.Round)
        }
        if (ghost >= 0) {
            drawStone(center(ghost), radius, ghostColor, strong, alpha = 0.55f)
            drawCircle(p.hint.darker(0.2f), radius * 1.05f, center(ghost), style = Stroke(cell * 0.09f))
        }
        if (hint >= 0) {
            drawCircle(Color.White.copy(alpha = 0.8f), radius * 0.95f, center(hint), style = Stroke(cell * 0.14f))
            drawCircle(p.hint.darker(0.25f), radius * 0.95f, center(hint), style = Stroke(cell * 0.09f))
        }
    }
}

/** Home screen picture: a corner of a Go board with a small fight between black and white. */
fun goThumbnail(scope: DrawScope, p: Palette) = scope.run {
    val n = 6
    val cell = size.width / n
    val wood = goBoardColor(p)
    drawRect(Brush.linearGradient(listOf(wood.lighter(0.12f), wood.darker(0.05f))))
    val half = cell / 2
    for (k in 0 until n) {
        val at = half + k * cell
        drawLine(GridLine, Offset(half, at), Offset(size.width, at), cell * 0.05f)
        drawLine(GridLine, Offset(at, half), Offset(at, size.height), cell * 0.05f)
    }
    drawCircle(GridLine, cell * 0.12f, Offset(half + 3 * cell, half + 3 * cell))
    fun at(r: Int, c: Int) = Offset(half + c * cell, half + r * cell)
    val r = cell * 0.47f
    val black = listOf(2 to 2, 2 to 3, 3 to 4, 4 to 2, 1 to 4)
    val white = listOf(3 to 2, 3 to 3, 4 to 3, 2 to 1, 5 to 4)
    for ((y, x) in black) drawStone(at(y, x), r, Go.BLACK, p.strongOutlines)
    for ((y, x) in white) drawStone(at(y, x), r, Go.WHITE, p.strongOutlines)
}
