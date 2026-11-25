package io.github.fershibli.megagames.games.parking

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import io.github.fershibli.megagames.core.ColorMode
import io.github.fershibli.megagames.ui.art.PixelArt
import io.github.fershibli.megagames.ui.art.PixelColors
import io.github.fershibli.megagames.ui.art.adaptedTo
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.art.pixelArt
import io.github.fershibli.megagames.ui.theme.Palette
import kotlin.math.floor
import kotlin.math.min

/*
 * The car park in pixel art, seen from above: textured asphalt with painted bays, a pavement all
 * round with plants and cones, and a guard's booth with a barrier at the way out on the right.
 * Every vehicle is a little pixel picture (sedan, hatchback, pick-up, van; lorry, bus, removal
 * van). Sizes are in "dots": a square of the lot is [CELL_DOTS] dots wide.
 */

/** Room around the lot (in squares) for the pavement and the exit. */
const val LOT_MARGIN = 0.5f
private const val CELL_DOTS = 16
private const val MARGIN_DOTS = 8
private const val TOTAL_DOTS = Parking.SIZE * CELL_DOTS + 2 * MARGIN_DOTS

/** Where the lot is on the screen, for drawing and for touches alike. */
class ParkingView(val left: Float, val top: Float, val dot: Float) {
    val cell: Float get() = dot * CELL_DOTS
    val lotLeft: Float get() = left + MARGIN_DOTS * dot
    val lotTop: Float get() = top + MARGIN_DOTS * dot

    /** The square under [at] as (column, row); either may be outside 0 until [Parking.SIZE]. */
    fun cellAt(at: Offset): Pair<Int, Int> = floor((at.x - lotLeft) / cell).toInt() to floor((at.y - lotTop) / cell).toInt()

    /** The middle of square ([col], [row]) on the screen. */
    fun center(col: Float, row: Float) = Offset(lotLeft + (col + 0.5f) * cell, lotTop + (row + 0.5f) * cell)

    companion object {
        /** The largest lot that fits in [width] × [height], in whole screen pixels per dot when it can. */
        fun of(width: Float, height: Float = width): ParkingView {
            val raw = min(width, height) / TOTAL_DOTS
            val dot = if (raw >= 1f) floor(raw) else raw
            return ParkingView(floor((width - TOTAL_DOTS * dot) / 2), floor((height - TOTAL_DOTS * dot) / 2), dot)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Vehicles.
// ---------------------------------------------------------------------------------------------

/** The kinds of vehicle: four cars (2 squares) and three big ones (3 squares). */
enum class VehicleKind(val long: Boolean) { SEDAN(false), HATCH(false), PICKUP(false), VAN(false), TRUCK(true), BUS(true), MOVING(true) }

/** The kind of vehicle [i] of a puzzle: the red car is always a sedan; the others take turns. */
fun vehicleKind(i: Int, length: Int): VehicleKind = when {
    i == 0 -> VehicleKind.SEDAN
    length >= 3 -> listOf(VehicleKind.TRUCK, VehicleKind.BUS, VehicleKind.MOVING)[(i - 1) % 3]
    else -> listOf(VehicleKind.SEDAN, VehicleKind.HATCH, VehicleKind.PICKUP, VehicleKind.VAN)[(i - 1) % 4]
}

private class Grid(val w: Int, val h: Int) {
    val g = Array(h) { CharArray(w) { '.' } }
    fun fill(x0: Int, y0: Int, x1: Int, y1: Int, c: Char) {
        for (y in y0..y1) for (x in x0..x1) if (x in 0 until w && y in 0 until h) g[y][x] = c
    }
    fun at(x: Int, y: Int) = if (x in 0 until w && y in 0 until h) g[y][x] else '.'
}

/** The rows of a vehicle seen from above, facing right (its front on the right). */
private fun vehicleRows(kind: VehicleKind): List<String> {
    val w = if (kind.long) 46 else 30
    val h = 14
    val g = Grid(w, h)
    val axles = if (kind.long) listOf(4, 16, w - 9) else listOf(4, w - 9)
    for (ax in axles) {
        g.fill(ax, 0, ax + 3, 0, 'k')
        g.fill(ax, h - 1, ax + 3, h - 1, 'k')
    }
    g.fill(0, 1, w - 1, h - 2, 'b')
    when (kind) {
        VehicleKind.SEDAN -> {
            g.fill(6, 3, 8, 10, 'w'); g.fill(9, 2, 17, 11, 'r'); g.fill(18, 2, 21, 11, 'w'); g.fill(19, 3, 19, 5, 'h')
            g.fill(24, 6, 27, 7, 'd')
        }
        VehicleKind.HATCH -> {
            g.fill(2, 3, 3, 10, 'w'); g.fill(4, 2, 15, 11, 'r'); g.fill(16, 2, 19, 11, 'w'); g.fill(17, 3, 17, 5, 'h')
            g.fill(22, 5, 26, 8, 'd')
        }
        VehicleKind.PICKUP -> {
            g.fill(2, 3, 11, 10, 'd'); g.fill(3, 4, 10, 4, 'e'); g.fill(13, 3, 13, 10, 'w'); g.fill(14, 2, 19, 11, 'r')
            g.fill(20, 2, 22, 11, 'w'); g.fill(21, 3, 21, 5, 'h')
        }
        VehicleKind.VAN -> {
            g.fill(1, 3, 2, 10, 'w'); g.fill(3, 2, 22, 11, 'r'); g.fill(5, 4, 20, 4, 'd'); g.fill(5, 9, 20, 9, 'd')
            g.fill(23, 2, 25, 11, 'w'); g.fill(24, 3, 24, 5, 'h')
        }
        VehicleKind.TRUCK -> {
            g.fill(0, 1, 31, h - 2, 'c'); for (x in 3 until 31 step 4) g.fill(x, 2, x, h - 3, 'e')
            g.fill(32, 1, 32, h - 2, '.'); g.fill(32, 5, 32, 8, 'k')
            g.fill(34, 2, 39, 11, 'r'); g.fill(40, 2, 42, 11, 'w'); g.fill(41, 3, 41, 5, 'h')
        }
        VehicleKind.BUS -> {
            g.fill(2, 2, 42, 11, 'r'); g.fill(18, 4, 26, 9, 'd'); g.fill(19, 5, 25, 8, 'e'); g.fill(7, 5, 9, 8, 'd'); g.fill(33, 5, 35, 8, 'd')
            g.fill(1, 3, 1, 10, 'w'); g.fill(43, 2, 44, 11, 'w'); g.fill(43, 3, 43, 5, 'h')
        }
        VehicleKind.MOVING -> {
            g.fill(0, 1, 32, h - 2, 'c'); g.fill(2, 6, 30, 7, 'b')
            g.fill(33, 1, 33, h - 2, '.'); g.fill(33, 5, 33, 8, 'k')
            g.fill(35, 2, 40, 11, 'r'); g.fill(41, 2, 43, 11, 'w'); g.fill(42, 3, 42, 5, 'h')
        }
    }
    // Round the corners a little, then outline every edge.
    for ((x, y) in listOf(0 to 1, w - 1 to 1, 0 to h - 2, w - 1 to h - 2)) g.g[y][x] = '.'
    val out = Array(h) { y -> CharArray(w) { x -> g.g[y][x] } }
    for (y in 0 until h) for (x in 0 until w) {
        val c = g.g[y][x]
        if (c == '.' || c == 'k') continue
        val edge = listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1).any { (a, b) -> g.at(a, b) == '.' || g.at(a, b) == 'k' }
        if (edge) out[y][x] = 'o'
    }
    // Headlights at the front, red lights at the back.
    for (y in listOf(2, 3, h - 4, h - 3)) {
        out[y][w - 1] = 'l'
        out[y][0] = 't'
    }
    return out.map { String(it) }
}

/** The same picture turned a quarter turn clockwise: facing down. */
private fun turned(rows: List<String>): List<String> {
    val h = rows.size
    val w = rows[0].length
    return List(w) { r -> String(CharArray(h) { c -> rows[h - 1 - c][r] }) }
}

private val vehicleCache = HashMap<String, PixelArt>()

private fun vehicleArt(kind: VehicleKind, color: Color, horizontal: Boolean, p: Palette): PixelArt {
    val key = "$kind ${color.value} $horizontal ${p.isDark} ${p.mode}"
    return synchronized(vehicleCache) {
        vehicleCache.getOrPut(key) {
            val outline = if (p.mode == ColorMode.HIGH_CONTRAST) Color.Black else Color(0xFF2B2B2B)
            val palette = mapOf(
                'b' to color, 'r' to color.lighter(0.22f), 'd' to color.darker(0.3f),
                'w' to Color(0xFF9ED3F0), 'h' to Color(0xFFE3F4FD), 'c' to Color(0xFFECEFF1), 'e' to Color(0xFFB0BEC5),
                'k' to Color(0xFF263238), 'l' to Color(0xFFFFF59D), 't' to Color(0xFFE53935), 'o' to outline,
            )
            val rows = vehicleRows(kind)
            pixelArt(if (horizontal) rows else turned(rows), palette).adaptedTo(p)
        }
    }
}

private val RED_CAR = Color(0xFFD32F2F)
private val CAR_COLORS = listOf(
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFDD835), Color(0xFF8E24AA), Color(0xFFFB8C00),
    Color(0xFF00ACC1), Color(0xFF6D4C41), Color(0xFFEC407A), Color(0xFF7CB342), Color(0xFF5C6BC0),
    Color(0xFFFFB300), Color(0xFF26A69A),
)

// ---------------------------------------------------------------------------------------------
// Scenery.
// ---------------------------------------------------------------------------------------------

private object LotSprites {
    val bush = pixelArt(
        "o=2E5E2A g=4E9A3E l=7CC35E f=F06292 p=8D6E63",
        ".oooo.",
        "ogllgo",
        "olfglo",
        "oglglo",
        ".oppo.",
        ".pppp.",
    )
    val cone = pixelArt(
        "o=6D3A1E r=FB8C00 w=FFFFFF d=E65100",
        "..r..",
        ".rwr.",
        ".rrr.",
        "rwwwr",
        "ddddd",
    )
    val booth = pixelArt(
        "o=3B2A20 r=C0503A w=FFF3D6 g=9ED3F0 d=8D6E63",
        "oooooooo",
        "orrrrrro",
        "orrrrrro",
        "oooooooo",
        "owggggwo",
        "owggggwo",
        "owwwwwwo",
        "owwddwwo",
        "owwddwwo",
        "oooooooo",
    )
    val arrow = pixelArt(
        "o=3B2A20 w=FFFFFF",
        "....o...",
        "....oo..",
        "ooooowo.",
        "owwwwwwo",
        "ooooowo.",
        "....oo..",
        "....o...",
    )
}

private fun DrawScope.dots(v: ParkingView, x: Float, y: Float, w: Float, h: Float, color: Color) =
    drawRect(color, Offset(v.left + x * v.dot, v.top + y * v.dot), Size(w * v.dot, h * v.dot))

private fun DrawScope.sprite(v: ParkingView, art: PixelArt, x: Float, y: Float, p: Palette) =
    art.adaptedTo(p).draw(this, v.left + x * v.dot, v.top + y * v.dot, v.dot)

/** The pavement, the asphalt with its bays, the plants, the booth and the barrier ([lift]: 0 closed, 1 open). */
private fun DrawScope.drawLot(v: ParkingView, p: Palette, lift: Float) {
    val c = PixelColors.of(p)
    val n = Parking.SIZE
    val lot = (n * CELL_DOTS).toFloat()
    val m = MARGIN_DOTS.toFloat()
    val total = TOTAL_DOTS.toFloat()
    // The pavement, in slabs.
    val slab = PixelColors.adapt(Color(0xFFCFC8BD), p)
    dots(v, 0f, 0f, total, total, slab)
    val joint = slab.darker(0.12f)
    for (k in 0..TOTAL_DOTS step 8) {
        dots(v, k.toFloat(), 0f, 1f, total, joint)
        dots(v, 0f, k.toFloat(), total, 1f, joint)
    }
    // The asphalt, with a speckled texture.
    val asphalt = PixelColors.adapt(Color(0xFF6E7378), p)
    dots(v, m - 1, m - 1, lot + 2, lot + 2, if (p.strongOutlines) Color.Black else PixelColors.adapt(Color(0xFFE0B341), p))
    dots(v, m, m, lot, lot, asphalt)
    // The way out: the asphalt goes on through the pavement on the right of the exit row.
    val exitTop = m + Parking.EXIT_ROW * CELL_DOTS
    dots(v, m + lot, exitTop, m, CELL_DOTS.toFloat(), asphalt)
    val light = asphalt.lighter(0.12f)
    val dark = asphalt.darker(0.15f)
    for (k in 0 until 260) {
        val x = (k * 37 + 5) % (TOTAL_DOTS - 2 * MARGIN_DOTS)
        val y = (k * 53 + 11) % (TOTAL_DOTS - 2 * MARGIN_DOTS)
        dots(v, m + x, m + y, 1f, 1f, if (k % 3 == 0) light else dark)
    }
    // Painted bays: short white marks where the squares meet.
    val paint = Color.White.copy(alpha = if (p.isDark) 0.4f else 0.6f)
    for (col in 0..n) for (row in 0 until n) {
        val x = m + col * CELL_DOTS
        val y = m + row * CELL_DOTS
        dots(v, x - 0.5f, y + 1, 1f, 4f, paint)
        dots(v, x - 0.5f, y + CELL_DOTS - 5, 1f, 4f, paint)
    }
    // Arrows painted along the exit row, pointing to the way out.
    val arrowPaint = Color.White.copy(alpha = 0.35f)
    for (col in 0 until n) {
        val x = m + col * CELL_DOTS + 6
        val y = exitTop + 8
        dots(v, x, y - 3, 1f, 7f, arrowPaint)
        dots(v, x + 1, y - 2, 1f, 5f, arrowPaint)
        dots(v, x + 2, y - 1, 1f, 3f, arrowPaint)
        dots(v, x + 3, y, 1f, 1f, arrowPaint)
    }
    // Plants and cones on the pavement.
    for ((x, y) in listOf(1f to 1f, total - 7 to 1f, 1f to total - 7, total - 7 to total - 7, 40f to 1f, 72f to total - 7, 1f to 56f)) {
        sprite(v, LotSprites.bush, x, y, p)
    }
    for ((x, y) in listOf(24f to 1.5f, 88f to 1.5f, 1.5f to 30f, 56f to total - 6.5f)) sprite(v, LotSprites.cone, x, y, p)
    // The guard's booth above the exit, and the barrier across it.
    sprite(v, LotSprites.booth, m + lot, exitTop - 11, p)
    val pivot = Offset(v.left + (m + lot + 4) * v.dot, v.top + (exitTop + CELL_DOTS) * v.dot)
    dots(v, m + lot + 3, exitTop + CELL_DOTS - 1, 3f, 3f, c.outline)
    val arm = (CELL_DOTS - 1) * v.dot
    rotate(lift.coerceIn(0f, 1f) * 150f, pivot) {
        for (k in 0 until CELL_DOTS - 1) {
            val stripe = if ((k / 3) % 2 == 0) Color(0xFFE53935) else Color.White
            drawRect(PixelColors.adapt(stripe, p), Offset(pivot.x - v.dot, pivot.y - (k + 2) * v.dot), Size(2 * v.dot, v.dot))
        }
        drawRect(c.outline, Offset(pivot.x - v.dot, pivot.y - arm - v.dot), Size(2 * v.dot, 2 * v.dot), style = Stroke(v.dot * 0.5f))
    }
}

/**
 * The car park and its vehicles. [dragging] is pulled [pull] squares, [slide] moves the vehicle
 * [sliding] that was just let go, and [away] drives the red car out through the lifted barrier.
 */
fun DrawScope.drawParking(s: ParkingState, p: Palette, dragging: Int = -1, pull: Float = 0f, away: Float = 0f, sliding: Int = -1, slide: Float = 0f) {
    val v = ParkingView.of(size.width, size.height)
    drawLot(v, p, lift = (away * 2.5f).coerceIn(0f, 1f).let { if (s.solved) maxOf(it, 0.6f) else it })
    val cell = v.cell
    clipRect(v.left, v.top, v.left + TOTAL_DOTS * v.dot, v.top + TOTAL_DOTS * v.dot) {
        s.vehicles.forEachIndexed { i, veh ->
            var pos = s.positions[i].toFloat()
            if (i == dragging) pos += pull
            if (i == sliding) pos += slide
            if (i == 0) pos += away
            val tl = if (veh.horizontal) Offset(v.lotLeft + pos * cell, v.lotTop + veh.fixed * cell) else Offset(v.lotLeft + veh.fixed * cell, v.lotTop + pos * cell)
            val sz = if (veh.horizontal) Size(veh.length * cell, cell) else Size(cell, veh.length * cell)
            val color = if (i == 0) RED_CAR else CAR_COLORS[(i - 1) % CAR_COLORS.size]
            val art = vehicleArt(vehicleKind(i, veh.length), color, veh.horizontal, p)
            // Centered in its squares, with a soft shadow under it.
            val x = tl.x + (sz.width - art.width * v.dot) / 2
            val y = tl.y + (sz.height - art.height * v.dot) / 2
            drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(x + v.dot, y + v.dot * 1.5f), Size(art.width * v.dot, art.height * v.dot), CornerRadius(v.dot * 2))
            art.draw(this, x, y, v.dot)
            if (i == 0) {
                val a = LotSprites.arrow.adaptedTo(p)
                a.draw(this, tl.x + (sz.width - a.width * v.dot) / 2, tl.y + (sz.height - a.height * v.dot) / 2, v.dot)
            }
            val ring = when {
                i == s.hintVehicle -> p.hint
                i == dragging -> p.highlight
                else -> null
            }
            if (ring != null) drawRoundRect(ring, tl + Offset(v.dot, v.dot), Size(sz.width - 2 * v.dot, sz.height - 2 * v.dot), CornerRadius(cell * 0.2f), style = Stroke(cell * 0.09f))
        }
    }
    // Where the hint wants the vehicle to go.
    if (s.hintVehicle >= 0) {
        val veh = s.vehicles[s.hintVehicle]
        val tl = if (veh.horizontal) Offset(v.lotLeft + s.hintTo * cell, v.lotTop + veh.fixed * cell) else Offset(v.lotLeft + veh.fixed * cell, v.lotTop + s.hintTo * cell)
        val sz = if (veh.horizontal) Size(veh.length * cell, cell) else Size(cell, veh.length * cell)
        drawRoundRect(p.hint, tl + Offset(cell * 0.06f, cell * 0.06f), Size(sz.width - cell * 0.12f, sz.height - cell * 0.12f), CornerRadius(cell * 0.22f), style = Stroke(cell * 0.06f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 0.15f, cell * 0.1f))))
    }
}

/** Home-screen picture: a small lot with the red car about to leave. */
fun parkingThumbnail(scope: DrawScope, p: Palette): Unit = scope.run {
    val s = ParkingState(
        listOf(Vehicle(true, 2, 2), Vehicle(false, 4, 3), Vehicle(true, 0, 2), Vehicle(false, 1, 2), Vehicle(true, 5, 3), Vehicle(false, 5, 2)),
        listOf(1, 1, 2, 3, 0, 0), par = 0,
    )
    drawParking(s, p)
}
