package io.github.fershibli.megagames.games.battleship

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.theme.Palette

private val SEA = Color(0xFF4F9FD8)
private val SHIP = Color(0xFF78909C)
private val FIRE = Color(0xFFE53935)

/**
 * One sea of [n] × [n] squares. [ships] are drawn as grey hulls (only the ones that may be seen);
 * [shots] are the squares fired at, a white splash for water and a red cross for a hit.
 * [hint] rings one square.
 */
fun DrawScope.drawSea(n: Int, ships: List<Ship>, shots: Set<Int>, p: Palette, last: Int = -1, sunk: Set<Int> = emptySet(), side: Float = size.width) {
    val cell = side / n
    val box = Size(side, side)
    val sea = if (p.isDark) SEA.darker(0.35f) else SEA
    drawRoundRect(sea, Offset.Zero, box, CornerRadius(cell * 0.25f))
    // Gentle waves.
    for (r in 0 until n) for (c in 0 until n) if ((r + c) % 2 == 0) drawRect(sea.lighter(0.06f), Offset(c * cell, r * cell), Size(cell, cell))
    // Ships: a rounded hull along their squares.
    for (ship in ships) {
        val rows = ship.cells.map { it / n }
        val cols = ship.cells.map { it % n }
        val tl = Offset(cols.min() * cell + cell * 0.12f, rows.min() * cell + cell * 0.12f)
        val sz = Size((cols.max() - cols.min() + 1) * cell - cell * 0.24f, (rows.max() - rows.min() + 1) * cell - cell * 0.24f)
        val gone = ship.cells.all { it in sunk }
        val hull = if (gone) SHIP.darker(0.45f) else SHIP
        drawRoundRect(hull, tl, sz, CornerRadius(cell * 0.4f))
        drawRoundRect(if (p.strongOutlines) Color.Black else hull.darker(0.4f), tl, sz, CornerRadius(cell * 0.4f), style = Stroke(cell * 0.06f))
        // Portholes.
        for (id in ship.cells) drawCircle(Color.White.copy(alpha = 0.7f), cell * 0.1f, Offset((id % n + 0.5f) * cell, (id / n + 0.5f) * cell))
    }
    val line = if (p.strongOutlines) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.35f)
    for (k in 1 until n) {
        drawLine(line, Offset(k * cell, 0f), Offset(k * cell, side), cell * 0.03f)
        drawLine(line, Offset(0f, k * cell), Offset(side, k * cell), cell * 0.03f)
    }
    val hitCells = ships.flatMap { it.cells }.toSet()
    for (id in shots) {
        val c = Offset((id % n + 0.5f) * cell, (id / n + 0.5f) * cell)
        if (id in hitCells || id in sunk) {
            val r = cell * 0.3f
            drawCircle(FIRE.copy(alpha = 0.3f), cell * 0.45f, c)
            drawLine(FIRE, c - Offset(r, r), c + Offset(r, r), cell * 0.14f, StrokeCap.Round)
            drawLine(FIRE, c - Offset(r, -r), c + Offset(r, -r), cell * 0.14f, StrokeCap.Round)
        } else {
            // A splash: two white rings.
            drawCircle(Color.White, cell * 0.13f, c)
            drawCircle(Color.White.copy(alpha = 0.6f), cell * 0.28f, c, style = Stroke(cell * 0.05f))
        }
    }
    if (last >= 0) {
        val tl = Offset(last % n * cell, last / n * cell)
        drawRect(p.highlight, tl + Offset(cell * 0.04f, cell * 0.04f), Size(cell * 0.92f, cell * 0.92f), style = Stroke(cell * 0.1f))
    }
    drawRoundRect(if (p.strongOutlines) Color.Black else sea.darker(0.45f), Offset.Zero, box, CornerRadius(cell * 0.25f), style = Stroke(cell * 0.06f))
}

/** Home-screen picture: a small sea with two ships, a hit and two splashes. */
fun battleshipThumbnail(scope: DrawScope, p: Palette) = scope.run {
    val side = minOf(size.width, size.height)
    translate((size.width - side) / 2, (size.height - side) / 2) {
        drawSea(5, listOf(Ship(listOf(6, 7, 8)), Ship(listOf(15, 20))), setOf(7, 2, 18, 22), p, side = side)
    }
}
