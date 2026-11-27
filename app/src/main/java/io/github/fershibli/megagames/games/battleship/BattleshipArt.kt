package io.github.fershibli.megagames.games.battleship

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import io.github.fershibli.megagames.core.ColorMode
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.sin

/*
 * The seas drawn like a sea chart on old paper: a fine blue-grey grid, the letters A–J along the
 * top and the numbers 1–10 down the side (Latin letters in every language, as on the paper
 * game), light watercolour washes and a compass rose. Ships are seen from above, one silhouette
 * per kind; a miss is an ink circle and a hit a red ink cross with a little flame.
 */

/** The width of the coordinate band along the top and the left, in squares. */
private const val LABEL_BAND = 0.62f

/** Where the squares of a sea of [n] × [n] are inside a picture [side] wide. */
class SeaGrid(val side: Float, val n: Int) {
    val cell: Float = side / (n + LABEL_BAND)
    val origin: Float = cell * LABEL_BAND

    /** The square under [at], snapped to the nearest one at the edges. */
    fun cellAt(at: Offset): Int {
        val c = ((at.x - origin) / cell).toInt().coerceIn(0, n - 1)
        val r = ((at.y - origin) / cell).toInt().coerceIn(0, n - 1)
        return r * n + c
    }

    fun center(id: Int) = Offset(origin + (id % n + 0.5f) * cell, origin + (id / n + 0.5f) * cell)
}

/** The inks and paper of the chart in each color mode. */
private class Chart(val paper: Color, val grid: Color, val ink: Color, val wash: Color, val red: Color, val hull: Color) {
    companion object {
        fun of(p: Palette) = when {
            p.mode == ColorMode.HIGH_CONTRAST && p.isDark -> Chart(Color(0xFF111111), Color.White.copy(alpha = 0.7f), Color.White, Color(0xFF4FC3F7).copy(alpha = 0.25f), Color(0xFFFF5252), Color(0xFFB0BEC5))
            p.mode == ColorMode.HIGH_CONTRAST -> Chart(Color.White, Color.Black.copy(alpha = 0.7f), Color.Black, Color(0xFF4FC3F7).copy(alpha = 0.25f), Color(0xFFB71C1C), Color(0xFF546E7A))
            p.isDark -> Chart(Color(0xFF2E2A22), Color(0xFF8FA9BE).copy(alpha = 0.45f), Color(0xFFE8DFC8), Color(0xFF4F7FA0).copy(alpha = 0.35f), Color(0xFFEF5350), Color(0xFF90A4AE))
            else -> Chart(Color(0xFFF2E6CC), Color(0xFF6F8BA3).copy(alpha = 0.55f), Color(0xFF3E3A36), Color(0xFF8EC5E6).copy(alpha = 0.4f), Color(0xFFC62828), Color(0xFF78909C))
        }
    }
}

private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    textAlign = Paint.Align.CENTER
}

private fun DrawScope.label(text: String, at: Offset, size: Float, color: Color) {
    labelPaint.textSize = size
    labelPaint.color = color.toArgb()
    drawContext.canvas.nativeCanvas.drawText(text, at.x, at.y + size * 0.35f, labelPaint)
}

/** Old paper: the sheet, a slightly darker edge and a few fibres; watercolour washes of sea; the compass rose. */
private fun DrawScope.drawPaper(g: SeaGrid, chart: Chart, p: Palette) {
    val side = g.side
    drawRoundRect(chart.paper, Offset.Zero, Size(side, side), CornerRadius(g.cell * 0.15f))
    if (p.mode != ColorMode.HIGH_CONTRAST) {
        // Aged edges.
        drawRoundRect(
            Brush.radialGradient(listOf(Color.Transparent, chart.paper.darker(0.18f)), Offset(side / 2, side / 2), side * 0.75f),
            Offset.Zero, Size(side, side), CornerRadius(g.cell * 0.15f),
        )
        // Fibres and specks in the paper.
        val speck = chart.ink.copy(alpha = 0.06f)
        for (k in 0 until 90) {
            val x = ((k * 37 + 11) % 97) / 97f * side
            val y = ((k * 61 + 29) % 89) / 89f * side
            drawLine(speck, Offset(x, y), Offset(x + g.cell * 0.12f, y + g.cell * 0.03f), g.cell * 0.02f)
        }
    }
    // Watercolour sea: soft blots of blue over the squares.
    clipRect(g.origin, g.origin, side, side) {
        val blots = listOf(0.2f to 0.25f, 0.7f to 0.2f, 0.45f to 0.55f, 0.15f to 0.8f, 0.8f to 0.75f, 0.55f to 0.9f)
        for ((k, b) in blots.withIndex()) {
            val c = Offset(g.origin + b.first * g.n * g.cell, g.origin + b.second * g.n * g.cell)
            val r = g.cell * (2.6f + (k % 3) * 0.6f)
            drawCircle(Brush.radialGradient(listOf(chart.wash, chart.wash.copy(alpha = 0f)), c, r), r, c)
        }
        compassRose(Offset(side - g.cell * 1.3f, side - g.cell * 1.3f), g.cell * 1.05f, chart)
    }
}

/** A compass rose in faint ink: eight points and a ring, north marked. */
private fun DrawScope.compassRose(c: Offset, r: Float, chart: Chart) {
    val ink = chart.ink.copy(alpha = 0.22f)
    drawCircle(ink, r * 0.62f, c, style = Stroke(r * 0.04f))
    for (k in 0 until 8) {
        val a = Math.PI / 4 * k - Math.PI / 2
        val len = if (k % 2 == 0) r else r * 0.6f
        val tip = c + Offset((cos(a) * len).toFloat(), (sin(a) * len).toFloat())
        val left = c + Offset((cos(a - 0.5) * r * 0.16f).toFloat(), (sin(a - 0.5) * r * 0.16f).toFloat())
        val right = c + Offset((cos(a + 0.5) * r * 0.16f).toFloat(), (sin(a + 0.5) * r * 0.16f).toFloat())
        val path = Path().apply { moveTo(left.x, left.y); lineTo(tip.x, tip.y); lineTo(right.x, right.y); close() }
        drawPath(path, if (k == 0) chart.red.copy(alpha = 0.35f) else ink)
    }
    label("N", c - Offset(0f, r * 1.2f), r * 0.32f, ink)
}

/** The kind of ship drawn for each length; the two ships of 3 take turns between cruiser and submarine. */
private enum class Kind { CARRIER, BATTLESHIP, CRUISER, SUBMARINE, PATROL }

private fun kinds(ships: List<Ship>): List<Kind> {
    var threes = 0
    return ships.map {
        when (it.cells.size) {
            5 -> Kind.CARRIER
            4 -> Kind.BATTLESHIP
            3 -> if (threes++ % 2 == 0) Kind.CRUISER else Kind.SUBMARINE
            else -> Kind.PATROL
        }
    }
}

/**
 * A ship seen from above, lying from x = 0 (stern) to x = [len] (bow), centered on y = 0, [w]
 * wide. [hull] is its grey; [sunk] darkens it.
 */
private fun DrawScope.ship(kind: Kind, len: Float, w: Float, hull: Color, outline: Color, sunk: Boolean) {
    val h = w / 2
    val deck = hull.lighter(0.18f)
    val detail = hull.darker(0.35f)
    val body = when (kind) {
        Kind.SUBMARINE -> Path().apply {
            // A long cigar, round at both ends.
            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, -h * 0.8f, len, h * 0.8f, CornerRadius(h * 0.8f)))
        }
        Kind.CARRIER -> Path().apply {
            moveTo(0f, -h); lineTo(len - w * 0.9f, -h); quadraticTo(len - w * 0.15f, -h, len, 0f)
            quadraticTo(len - w * 0.15f, h, len - w * 0.9f, h); lineTo(0f, h); close()
        }
        else -> Path().apply {
            // A pointed bow and a straight stern.
            moveTo(0f, -h * 0.85f); lineTo(len * 0.62f, -h); quadraticTo(len * 0.9f, -h * 0.85f, len, 0f)
            quadraticTo(len * 0.9f, h * 0.85f, len * 0.62f, h); lineTo(0f, h * 0.85f); close()
        }
    }
    drawPath(body, Color.Black.copy(alpha = 0.18f), alpha = 1f)
    translate(w * 0.06f, w * 0.08f) { drawPath(body, Color.Black.copy(alpha = 0.12f)) }
    drawPath(body, hull)
    drawPath(body, outline, style = Stroke(w * 0.07f))
    when (kind) {
        Kind.CARRIER -> {
            // The flight deck: a runway with a dashed middle line, and the island on one side.
            drawRect(deck, Offset(w * 0.25f, -h * 0.72f), Size(len - w * 1.2f, h * 1.44f))
            var x = w * 0.45f
            while (x < len - w * 1.2f) {
                drawLine(Color.White.copy(alpha = 0.85f), Offset(x, 0f), Offset(x + w * 0.35f, 0f), w * 0.07f)
                x += w * 0.7f
            }
            drawRect(detail, Offset(len * 0.55f, -h * 0.98f), Size(w * 0.7f, h * 0.38f))
        }
        Kind.BATTLESHIP, Kind.CRUISER -> {
            val turrets = if (kind == Kind.BATTLESHIP) listOf(0.18f, 0.72f, 0.86f) else listOf(0.2f, 0.78f)
            // Superstructure in the middle.
            drawRoundRect(deck, Offset(len * 0.36f, -h * 0.5f), Size(len * 0.24f, h), CornerRadius(w * 0.08f))
            drawCircle(detail, w * 0.12f, Offset(len * 0.5f, 0f))
            for (t in turrets) {
                val c = Offset(len * t, 0f)
                val forward = t > 0.5f
                drawLine(detail, c, c + Offset(if (forward) w * 0.55f else -w * 0.55f, 0f), w * 0.09f, StrokeCap.Round)
                drawCircle(deck, w * 0.2f, c)
                drawCircle(outline, w * 0.2f, c, style = Stroke(w * 0.05f))
            }
        }
        Kind.SUBMARINE -> {
            drawRoundRect(detail, Offset(len * 0.42f, -h * 0.35f), Size(len * 0.18f, h * 0.7f), CornerRadius(h * 0.35f))
            drawLine(deck, Offset(w * 0.3f, 0f), Offset(len * 0.38f, 0f), w * 0.06f)
        }
        Kind.PATROL -> {
            drawRoundRect(deck, Offset(len * 0.32f, -h * 0.5f), Size(len * 0.32f, h), CornerRadius(w * 0.1f))
            drawRect(detail, Offset(len * 0.56f, -h * 0.4f), Size(len * 0.06f, h * 0.8f))
        }
    }
    if (sunk) drawPath(body, Color.Black.copy(alpha = 0.12f))
}

/** A little flame for a hit: a red-orange drop with a yellow heart. */
private fun DrawScope.flame(base: Offset, s: Float) {
    val outer = Path().apply {
        moveTo(base.x, base.y - s * 1.3f)
        quadraticTo(base.x + s * 0.9f, base.y - s * 0.2f, base.x, base.y)
        quadraticTo(base.x - s * 0.9f, base.y - s * 0.2f, base.x, base.y - s * 1.3f)
        close()
    }
    drawPath(outer, Color(0xFFFF7043))
    val inner = Path().apply {
        moveTo(base.x, base.y - s * 0.75f)
        quadraticTo(base.x + s * 0.45f, base.y - s * 0.1f, base.x, base.y)
        quadraticTo(base.x - s * 0.45f, base.y - s * 0.1f, base.x, base.y - s * 0.75f)
        close()
    }
    drawPath(inner, Color(0xFFFFD54F))
}

/**
 * One sea of [n] × [n] squares on chart paper. [ships] are drawn (only the ones that may be
 * seen); [shots] are the squares fired at: an ink circle for water and a red ink cross for a hit.
 * [last] rings the last shot. While arranging, ship number [lifted] follows the finger, moved by
 * [lift] pixels and drawn on top, with the squares where it would land ([landing]) outlined; ship
 * number [refused] is outlined in red, for a move or a turn it could not make.
 */
fun DrawScope.drawSea(
    n: Int, ships: List<Ship>, shots: Set<Int>, p: Palette, last: Int = -1, sunk: Set<Int> = emptySet(), side: Float = size.width,
    lifted: Int = -1, lift: Offset = Offset.Zero, refused: Int = -1, landing: List<Int> = emptyList(),
) {
    val g = SeaGrid(side, n)
    val chart = Chart.of(p)
    val cell = g.cell
    drawPaper(g, chart, p)
    // The grid and the coordinates, A–J along the top and 1–10 down the side.
    for (k in 0..n) {
        val at = g.origin + k * cell
        val strong = k == 0 || k == n
        drawLine(chart.grid, Offset(at, g.origin), Offset(at, side), cell * if (strong) 0.04f else 0.018f)
        drawLine(chart.grid, Offset(g.origin, at), Offset(side, at), cell * if (strong) 0.04f else 0.018f)
    }
    for (k in 0 until n) {
        label(('A' + k).toString(), Offset(g.origin + (k + 0.5f) * cell, g.origin * 0.5f), cell * 0.42f, chart.ink)
        label((k + 1).toString(), Offset(g.origin * 0.5f, g.origin + (k + 0.5f) * cell), cell * 0.38f, chart.ink)
    }
    // Ships.
    val outline = if (p.strongOutlines) Color.Black else chart.hull.darker(0.5f)
    val shipKinds = kinds(ships)
    // Where the dragged ship would land: a soft wash with a strong outline on each square.
    for (id in landing) {
        val tl = Offset(g.origin + id % n * cell, g.origin + id / n * cell)
        drawRect(p.primary.copy(alpha = 0.22f), tl, Size(cell, cell))
        drawRect(p.primary, tl + Offset(cell * 0.05f, cell * 0.05f), Size(cell * 0.9f, cell * 0.9f), style = Stroke(cell * 0.08f))
    }
    fun drawShip(index: Int) {
        val ship = ships[index]
        val kind = shipKinds[index]
        val rows = ship.cells.map { it / n }
        val cols = ship.cells.map { it % n }
        val horizontal = rows.distinct().size == 1
        val gone = ship.cells.all { it in sunk }
        val hull = if (gone) chart.hull.darker(0.22f) else chart.hull
        val len = ship.cells.size * cell - cell * 0.16f
        val w = cell * if (kind == Kind.CARRIER) 0.7f else 0.58f
        val start = Offset(g.origin + cols.min() * cell + cell * 0.08f, g.origin + rows.min() * cell + cell * 0.08f)
        // Lay the ship along its squares: across from the left, or down from the top.
        val pivot = if (horizontal) Offset(start.x, start.y + cell * 0.42f) else Offset(start.x + cell * 0.42f, start.y)
        val moved = if (index == lifted) lift else Offset.Zero
        if (index == refused) {
            val tl = Offset(g.origin + cols.min() * cell, g.origin + rows.min() * cell) + moved
            drawRoundRect(p.danger, tl, Size((cols.max() - cols.min() + 1) * cell, (rows.max() - rows.min() + 1) * cell), CornerRadius(cell * 0.2f), style = Stroke(cell * 0.1f))
        }
        translate(pivot.x + moved.x, pivot.y + moved.y) {
            rotate(if (horizontal) 0f else 90f, Offset.Zero) {
                rotate(if (gone) 4f else 0f, Offset(len / 2, 0f)) {
                    ship(kind, len, w, hull, outline, gone)
                }
            }
        }
        if (gone) {
            // Smoke rising from the wreck.
            val mid = g.center(ship.cells[ship.cells.size / 2])
            for (k in 0 until 3) drawCircle(Color(0xFF9E9E9E).copy(alpha = 0.45f - k * 0.1f), cell * (0.14f + k * 0.05f), mid + Offset(cell * 0.12f * k, -cell * (0.2f + 0.18f * k)))
        }
    }
    for (index in ships.indices) if (index != lifted) drawShip(index)
    // Shots: an ink circle in the water, a red ink cross with a flame on a ship.
    val shipCells = ships.flatMap { it.cells }.toSet()
    for (id in shots) {
        val c = g.center(id)
        if (id in shipCells || id in sunk) {
            val r = cell * 0.27f
            drawLine(chart.red, c - Offset(r, r), c + Offset(r, r), cell * 0.12f, StrokeCap.Round)
            drawLine(chart.red, c - Offset(r, -r), c + Offset(r, -r), cell * 0.12f, StrokeCap.Round)
            flame(c + Offset(cell * 0.26f, -cell * 0.14f), cell * 0.16f)
        } else {
            drawCircle(chart.ink, cell * 0.22f, c, style = Stroke(cell * 0.07f))
            drawCircle(chart.ink, cell * 0.05f, c)
        }
    }
    if (last >= 0) {
        val tl = Offset(g.origin + last % n * cell, g.origin + last / n * cell)
        drawRect(p.highlight, tl + Offset(cell * 0.04f, cell * 0.04f), Size(cell * 0.92f, cell * 0.92f), style = Stroke(cell * 0.1f))
    }
    drawRoundRect(if (p.strongOutlines) chart.ink else chart.paper.darker(0.35f), Offset.Zero, Size(side, side), CornerRadius(cell * 0.15f), style = Stroke(cell * 0.05f))
    if (lifted in ships.indices) drawShip(lifted)
}

/** Home-screen picture: a small chart with two ships, a hit and two misses. */
fun battleshipThumbnail(scope: DrawScope, p: Palette) = scope.run {
    val side = minOf(size.width, size.height)
    translate((size.width - side) / 2, (size.height - side) / 2) {
        drawSea(5, listOf(Ship(listOf(6, 7, 8)), Ship(listOf(15, 20))), setOf(7, 2, 18, 22), p, side = side)
    }
}
