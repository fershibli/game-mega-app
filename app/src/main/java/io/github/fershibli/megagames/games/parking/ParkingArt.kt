package io.github.fershibli.megagames.games.parking

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.theme.Palette

/** Room around the lot (in squares) for the wall and the exit. */
const val LOT_MARGIN = 0.35f

private val ASPHALT = Color(0xFF8A8F94)
private val RED_CAR = Color(0xFFD32F2F)
private val CAR_COLORS = listOf(
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFDD835), Color(0xFF8E24AA), Color(0xFFFB8C00),
    Color(0xFF00ACC1), Color(0xFF6D4C41), Color(0xFFEC407A), Color(0xFF7CB342), Color(0xFF5C6BC0),
    Color(0xFFFFB300), Color(0xFF26A69A),
)

private fun DrawScope.vehicle(tl: Offset, sz: Size, color: Color, horizontal: Boolean, cell: Float, p: Palette, red: Boolean, ring: Color?) {
    val inset = cell * 0.08f
    val a = tl + Offset(inset, inset)
    val b = Size(sz.width - inset * 2, sz.height - inset * 2)
    val corner = CornerRadius(cell * 0.22f)
    drawRoundRect(Color.Black.copy(alpha = 0.28f), a + Offset(cell * 0.04f, cell * 0.06f), b, corner)
    drawRoundRect(color, a, b, corner)
    drawRoundRect(if (p.strongOutlines) Color.Black else color.darker(0.45f), a, b, corner, style = Stroke(cell * if (p.strongOutlines) 0.07f else 0.04f))
    // Windows: a windscreen near the front and a back window.
    val glass = Color(0xFFCFE8FC)
    if (horizontal) {
        drawRoundRect(glass, Offset(a.x + b.width - cell * 0.42f, a.y + cell * 0.14f), Size(cell * 0.18f, b.height - cell * 0.28f), CornerRadius(cell * 0.06f))
        drawRoundRect(glass, Offset(a.x + cell * 0.14f, a.y + cell * 0.18f), Size(cell * 0.14f, b.height - cell * 0.36f), CornerRadius(cell * 0.06f))
    } else {
        drawRoundRect(glass, Offset(a.x + cell * 0.14f, a.y + cell * 0.24f), Size(b.width - cell * 0.28f, cell * 0.18f), CornerRadius(cell * 0.06f))
        drawRoundRect(glass, Offset(a.x + cell * 0.18f, a.y + b.height - cell * 0.3f), Size(b.width - cell * 0.36f, cell * 0.14f), CornerRadius(cell * 0.06f))
    }
    if (red) {
        // A white arrow pointing to the way out.
        val c = Offset(a.x + b.width / 2, a.y + b.height / 2)
        val path = Path().apply {
            moveTo(c.x - cell * 0.3f, c.y - cell * 0.08f); lineTo(c.x, c.y - cell * 0.08f); lineTo(c.x, c.y - cell * 0.2f)
            lineTo(c.x + cell * 0.25f, c.y); lineTo(c.x, c.y + cell * 0.2f); lineTo(c.x, c.y + cell * 0.08f)
            lineTo(c.x - cell * 0.3f, c.y + cell * 0.08f); close()
        }
        drawPath(path, Color.White)
    }
    if (ring != null) drawRoundRect(ring, a - Offset(inset, inset) * 0.5f, Size(b.width + inset, b.height + inset), corner, style = Stroke(cell * 0.1f))
}

/**
 * The lot: grey asphalt with parking lines, a wall all round with a gap on the right of the red
 * car's row, and the vehicles. [dragging] is pulled [pull] squares; [away] drives the red car out.
 */
fun DrawScope.drawParking(s: ParkingState, p: Palette, dragging: Int = -1, pull: Float = 0f, away: Float = 0f) {
    val n = Parking.SIZE
    val cell = size.width / (n + LOT_MARGIN * 2)
    val o = Offset(cell * LOT_MARGIN, cell * LOT_MARGIN)
    val lot = Size(cell * n, cell * n)
    val asphalt = if (p.isDark) ASPHALT.darker(0.35f) else ASPHALT
    // The wall.
    drawRoundRect(if (p.isDark) Color(0xFF5D4037).darker(0.3f) else Color(0xFFBCAAA4), Offset.Zero, size, CornerRadius(cell * 0.3f))
    drawRect(asphalt, o, lot)
    // The exit: the wall opens on the right of the exit row.
    val exitTop = o.y + Parking.EXIT_ROW * cell
    drawRect(asphalt, Offset(o.x + lot.width, exitTop), Size(cell * LOT_MARGIN, cell))
    val arrowX = o.x + lot.width + cell * LOT_MARGIN * 0.5f
    drawLine(Color(0xFF66BB6A), Offset(arrowX - cell * 0.1f, exitTop + cell * 0.3f), Offset(arrowX + cell * 0.1f, exitTop + cell * 0.5f), cell * 0.06f, StrokeCap.Round)
    drawLine(Color(0xFF66BB6A), Offset(arrowX - cell * 0.1f, exitTop + cell * 0.7f), Offset(arrowX + cell * 0.1f, exitTop + cell * 0.5f), cell * 0.06f, StrokeCap.Round)
    // Parking lines.
    val line = Color.White.copy(alpha = 0.35f)
    for (k in 1 until n) {
        drawLine(line, Offset(o.x + k * cell, o.y), Offset(o.x + k * cell, o.y + lot.height), cell * 0.02f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 0.18f, cell * 0.12f)))
        drawLine(line, Offset(o.x, o.y + k * cell), Offset(o.x + lot.width, o.y + k * cell), cell * 0.02f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 0.18f, cell * 0.12f)))
    }
    // The way out along the exit row, faintly.
    drawRect(Color(0xFF66BB6A).copy(alpha = 0.15f), Offset(o.x, exitTop), Size(lot.width, cell))
    s.vehicles.forEachIndexed { i, v ->
        var pos = s.positions[i].toFloat()
        if (i == dragging) pos += pull
        if (i == 0) pos += away
        val tl = if (v.horizontal) Offset(o.x + pos * cell, o.y + v.fixed * cell) else Offset(o.x + v.fixed * cell, o.y + pos * cell)
        val sz = if (v.horizontal) Size(v.length * cell, cell) else Size(cell, v.length * cell)
        val color = if (i == 0) RED_CAR else CAR_COLORS[(i - 1) % CAR_COLORS.size].let { if (v.length == 3) it.darker(0.1f) else it }
        val ring = when {
            i == s.hintVehicle -> p.hint
            i == dragging -> p.highlight
            else -> null
        }
        vehicle(tl, sz, color, v.horizontal, cell, p, i == 0, ring)
    }
    // Where the hint wants the vehicle to go.
    if (s.hintVehicle >= 0) {
        val v = s.vehicles[s.hintVehicle]
        val tl = if (v.horizontal) Offset(o.x + s.hintTo * cell, o.y + v.fixed * cell) else Offset(o.x + v.fixed * cell, o.y + s.hintTo * cell)
        val sz = if (v.horizontal) Size(v.length * cell, cell) else Size(cell, v.length * cell)
        drawRoundRect(p.hint, tl + Offset(cell * 0.06f, cell * 0.06f), Size(sz.width - cell * 0.12f, sz.height - cell * 0.12f), CornerRadius(cell * 0.22f), style = Stroke(cell * 0.06f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 0.15f, cell * 0.1f))))
    }
    drawRect(if (p.strongOutlines) Color.Black else asphalt.darker(0.4f), o, lot, style = Stroke(cell * 0.04f))
}

/** Home-screen picture: a small lot with the red car about to leave. */
fun parkingThumbnail(scope: DrawScope, p: Palette): Unit = scope.run {
    val s = ParkingState(
        listOf(Vehicle(true, 2, 2), Vehicle(false, 4, 3), Vehicle(true, 0, 2), Vehicle(false, 1, 2), Vehicle(true, 5, 3), Vehicle(false, 5, 2)),
        listOf(1, 1, 2, 3, 0, 0), par = 0,
    )
    val side = minOf(size.width, size.height)
    translate((size.width - side) / 2, (size.height - side) / 2) {
        scale(side / size.width, Offset.Zero) { drawParking(s, p) }
    }
}

