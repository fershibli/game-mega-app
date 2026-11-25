package io.github.fershibli.megagames.games.pinball

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import io.github.fershibli.megagames.core.ColorMode
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The table as a night at the fair, in pixel art: a striped tent over the lanes at the top, a
 * string of flags, a big wheel glowing behind the bumpers, light bulbs along the walls and a
 * chequered floor by the flippers. Everything that never moves is painted once into a picture
 * ([PinballBackdrop]); each frame only adds the lights, targets, plunger, flippers and ball.
 * Sizes are in "dots", a hundredth of the table's width.
 */

private const val DOTS = 100

private val SKY_TOP = Color(0xFF1B1E4D)
private val SKY_BOTTOM = Color(0xFF3A2D74)
private val TENT_A = Color(0xFFB23A48)
private val TENT_B = Color(0xFF5A2C5E)
private val WHEEL = Color(0xFF7E76D8)
private val BULB = Color(0xFFFFD54F)
private val BULB_B = Color(0xFFFF8A65)
private val CABINET = Color(0xFF4A2C2A)
private val CABINET_EDGE = Color(0xFF2B1918)
private val FLOOR_A = Color(0xFF2E2463)
private val FLOOR_B = Color(0xFF3B2F7A)
private val RAIL = Color(0xFFCFD8DC)
private val RAIL_DARK = Color(0xFF546E7A)
private val BUMPER_COLORS = listOf(Color(0xFFE53935), Color(0xFF43A047), Color(0xFF1E88E5))
private val STAR_ON = Color(0xFFFFD54F)
private val KICKER = Color(0xFFFF7043)
private val TARGET = Color(0xFF26C6DA)
private val LANE_ON = Color(0xFFFFEB3B)
private val FLAG_COLORS = listOf(Color(0xFFE53935), Color(0xFFFFD54F), Color(0xFF43A047), Color(0xFF29B6F6), Color(0xFFAB47BC))

/** The colors of the table in [p]: a little dimmer at night, stronger in high contrast. */
private fun Color.inMode(p: Palette): Color {
    var c = this
    if (p.isDark) c = lerp(c, Color(0xFF0B0D1E), 0.25f)
    if (p.mode == ColorMode.HIGH_CONTRAST) {
        fun s(v: Float) = ((v - 0.5f) * 1.3f + 0.5f).coerceIn(0f, 1f)
        c = Color(s(c.red), s(c.green), s(c.blue), c.alpha)
    }
    return c
}

/** Draws whole dots, so neighbours meet without seams. */
private class Dots(val scope: DrawScope) {
    val d = scope.size.width / DOTS
    fun rect(i: Int, j: Int, w: Int, h: Int, color: Color) {
        val l = floor(i * d)
        val t = floor(j * d)
        scope.drawRect(color, Offset(l, t), Size(floor((i + w) * d) - l, floor((j + h) * d) - t))
    }
    fun at(x: Float, y: Float) = (x * DOTS).roundToInt() to (y * DOTS).roundToInt()
}

/** The outline of the playfield, launch lane included. */
private fun fieldPath(u: Float) = Path().apply {
    moveTo(0.03f * u, 1.12f * u)
    lineTo(0.03f * u, 0.3f * u)
    Pinball.walls.drop(Pinball.ARCH_FIRST).take(Pinball.ARCH_PIECES).forEach { lineTo(it.bx * u, it.by * u) }
    lineTo(0.97f * u, Pinball.HEIGHT * u)
    lineTo(Pinball.LANE_LEFT * u, Pinball.HEIGHT * u)
    lineTo(Pinball.LANE_LEFT * u, 1.12f * u)
    lineTo(0.715f * u, 1.33f * u)
    lineTo(0.715f * u, Pinball.HEIGHT * u)
    lineTo(0.285f * u, Pinball.HEIGHT * u)
    lineTo(0.285f * u, 1.33f * u)
    close()
}

/** A five-pointed star, filled or outlined. */
private fun DrawScope.star(center: Offset, r: Float, color: Color, filled: Boolean) {
    val path = Path()
    for (i in 0 until 10) {
        val a = Math.PI * 2 * i / 10 - Math.PI / 2
        val rr = if (i % 2 == 0) r else r * 0.45f
        val x = center.x + (rr * cos(a)).toFloat()
        val y = center.y + (rr * sin(a)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    if (filled) drawPath(path, color) else drawPath(path, color, style = Stroke(r * 0.2f))
}

/** A chrome rail between two points: dark edge, light body and a shine. */
private fun DrawScope.rail(a: Offset, b: Offset, width: Float, body: Color, p: Palette) {
    drawLine(if (p.strongOutlines) Color.Black else RAIL_DARK.inMode(p), a, b, width * 1.5f, StrokeCap.Square)
    drawLine(body, a, b, width, StrokeCap.Square)
    drawLine(Color.White.copy(alpha = 0.55f), a, b, width * 0.3f, StrokeCap.Square)
}

/** Everything that never moves: cabinet, sky, tent, flags, wheel, floor, rails, bumper bases and the ramp. */
private fun DrawScope.drawPinballStatic(p: Palette) {
    val u = size.width
    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    val dots = Dots(this)
    val rows = (Pinball.HEIGHT * DOTS).toInt()
    // The cabinet around the field, with a dark edge and bolts.
    drawRect(CABINET.inMode(p), Offset.Zero, Size(u, Pinball.HEIGHT * u))
    for (j in 4 until rows step 10) {
        dots.rect(1, j, 1, 1, CABINET_EDGE.inMode(p))
        dots.rect(DOTS - 2, j + 5, 1, 1, CABINET_EDGE.inMode(p))
    }
    val field = fieldPath(u)
    clipPath(field) {
        // A night sky, darker at the top, in dithered bands.
        val bands = 8
        for (j in 0 until rows) {
            val f = j.toFloat() / rows * bands
            val band = f.toInt().coerceAtMost(bands - 1)
            val c0 = lerp(SKY_TOP, SKY_BOTTOM, band / (bands - 1f)).inMode(p)
            val c1 = lerp(SKY_TOP, SKY_BOTTOM, (band + 1).coerceAtMost(bands - 1) / (bands - 1f)).inMode(p)
            val mix = f - band
            dots.rect(0, j, DOTS, 1, c0)
            if (mix > 0.6f) for (i in (j % 2) until DOTS step 2) dots.rect(i, j, 1, 1, c1)
        }
        // Twinkling stars in the sky.
        for (n in 0 until 40) {
            val i = (n * 37 + 11) % 92 + 4
            val j = (n * 53 + 7) % 100 + 36
            dots.rect(i, j, 1, 1, Color.White.copy(alpha = if (n % 3 == 0) 0.8f else 0.4f))
        }
        // The big wheel behind the bumpers.
        val wheel = WHEEL.inMode(p).copy(alpha = 0.55f)
        val (wx, wy) = 50 to 88
        val wr = 22
        for (a in 0 until 160) {
            val t = a / 160f * 6.2832f
            dots.rect(wx + (cos(t) * wr).roundToInt(), wy + (sin(t) * wr).roundToInt(), 1, 1, wheel)
            dots.rect(wx + (cos(t) * (wr - 3)).roundToInt(), wy + (sin(t) * (wr - 3)).roundToInt(), 1, 1, wheel.copy(alpha = 0.3f))
        }
        for (k in 0 until 8) {
            val t = k / 8f * 6.2832f
            for (r in 0..wr step 1) dots.rect(wx + (cos(t) * r).roundToInt(), wy + (sin(t) * r).roundToInt(), 1, 1, wheel.copy(alpha = 0.35f))
            val gx = wx + (cos(t) * wr).roundToInt()
            val gy = wy + (sin(t) * wr).roundToInt()
            dots.rect(gx - 1, gy, 3, 3, FLAG_COLORS[k % FLAG_COLORS.size].inMode(p).copy(alpha = 0.7f))
        }
        for (k in 0..12) {
            dots.rect(wx - k / 2, wy + k * 2, 1, 2, wheel.copy(alpha = 0.4f))
            dots.rect(wx + k / 2, wy + k * 2, 1, 2, wheel.copy(alpha = 0.4f))
        }
        // The striped tent over the lanes, with a scalloped edge.
        for (j in 0 until 29) for (i in 0 until DOTS step 6) {
            dots.rect(i, j, 3, 1, TENT_A.inMode(p))
            dots.rect(i + 3, j, 3, 1, TENT_B.inMode(p))
        }
        for (i in 0 until DOTS step 6) {
            dots.rect(i + 1, 29, 4, 1, TENT_A.inMode(p))
            dots.rect(i + 2, 30, 2, 1, TENT_A.inMode(p))
        }
        // A string of little flags under the tent.
        for (i in 4 until DOTS - 4) dots.rect(i, 33 + if (i % 12 < 6) i % 6 / 3 else (5 - i % 6) / 3, 1, 1, Color.White.copy(alpha = 0.5f))
        for ((n, i) in (6 until DOTS - 6 step 6).withIndex()) {
            val c = FLAG_COLORS[n % FLAG_COLORS.size].inMode(p)
            dots.rect(i, 34, 3, 1, c)
            dots.rect(i, 35, 2, 1, c)
            dots.rect(i, 36, 1, 1, c)
        }
        // A chequered floor by the flippers.
        for (j in 112 until rows) for (i in 0 until DOTS) {
            if ((i / 3 + j / 3) % 2 == 0) dots.rect(i, j, 1, 1, FLOOR_A.inMode(p)) else dots.rect(i, j, 1, 1, FLOOR_B.inMode(p))
        }
        // The launch lane, a little darker, with arrows pointing up.
        val laneL = (Pinball.LANE_LEFT * DOTS).toInt()
        dots.rect(laneL, 40, DOTS - laneL, rows - 40, Color.Black.copy(alpha = 0.25f))
        for (j in 60 until 140 step 12) {
            dots.rect(93, j, 2, 1, BULB.inMode(p).copy(alpha = 0.6f))
            dots.rect(92, j + 1, 4, 1, BULB.inMode(p).copy(alpha = 0.6f))
        }
        // Light bulbs along the left wall and the lane's wall.
        for ((n, j) in (40 until 110 step 6).withIndex()) {
            dots.rect(4, j, 1, 1, (if (n % 2 == 0) BULB else BULB_B).inMode(p))
            dots.rect(laneL - 2, j + 3, 1, 1, (if (n % 2 == 0) BULB_B else BULB).inMode(p))
        }
        // The ramp: a raised rail from its mouth, up and round, down the left side.
        val path = Pinball.rampPath
        for (i in 1 until path.size) {
            val a = o(path[i - 1].first, path[i - 1].second)
            val b = o(path[i].first, path[i].second)
            drawLine(Color.Black.copy(alpha = 0.35f), a + Offset(u * 0.006f, u * 0.008f), b + Offset(u * 0.006f, u * 0.008f), u * 0.05f, StrokeCap.Round)
            drawLine(Color(0xFF90A4AE).inMode(p).copy(alpha = 0.45f), a, b, u * 0.045f, StrokeCap.Round)
        }
        for (i in 1 until path.size) {
            val (ax, ay) = path[i - 1]
            val (bx, by) = path[i]
            val len = sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay)).coerceAtLeast(1e-4f)
            val nx = -(by - ay) / len * 0.022f
            val ny = (bx - ax) / len * 0.022f
            rail(o(ax + nx, ay + ny), o(bx + nx, by + ny), u * 0.007f, RAIL.inMode(p), p)
            rail(o(ax - nx, ay - ny), o(bx - nx, by - ny), u * 0.007f, RAIL.inMode(p), p)
        }
        // The mouth of the ramp, with arrows pointing in.
        val mouth = o(Pinball.RAMP_X, Pinball.RAMP_Y)
        drawCircle(Color.Black.copy(alpha = 0.45f), Pinball.RAMP_MOUTH * u * 0.8f, mouth)
        val (mi, mj) = dots.at(Pinball.RAMP_X, Pinball.RAMP_Y + 0.06f)
        for (k in 0..1) {
            dots.rect(mi - 1, mj + k * 3, 3, 1, KICKER.inMode(p))
            dots.rect(mi - 2, mj + k * 3 + 1, 1, 1, KICKER.inMode(p))
            dots.rect(mi + 2, mj + k * 3 + 1, 1, 1, KICKER.inMode(p))
        }
    }
    // Walls: chrome rails; the kickers glow orange.
    Pinball.walls.forEach { w ->
        val a = o(w.ax, w.ay)
        val b = o(w.bx, w.by)
        if (w.kick > 0f) {
            drawLine(if (p.strongOutlines) Color.Black else KICKER.darker(0.4f), a, b, u * 0.03f, StrokeCap.Round)
            drawLine(KICKER.inMode(p), a, b, u * 0.022f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.6f), a, b, u * 0.006f, StrokeCap.Round)
        } else if (w.oneWay) {
            drawLine(BULB.inMode(p), a, b, u * 0.01f, StrokeCap.Round)
        } else {
            rail(a, b, u * 0.012f, RAIL.inMode(p), p)
        }
    }
    // Bumper bases: a white ring and a colored cap with a star.
    Pinball.bumpers.forEachIndexed { i, b ->
        val c = o(b.x, b.y)
        val r = b.r * u
        val color = BUMPER_COLORS[i % BUMPER_COLORS.size].inMode(p)
        drawCircle(Color.Black.copy(alpha = 0.35f), r * 1.02f, c + Offset(r * 0.1f, r * 0.14f))
        drawCircle(Color(0xFFF5F5F5).inMode(p), r, c)
        drawCircle(color.darker(0.3f), r * 0.74f, c)
        drawCircle(color, r * 0.64f, c - Offset(r * 0.04f, r * 0.06f))
        star(c - Offset(r * 0.04f, r * 0.06f), r * 0.36f, Color.White.copy(alpha = 0.9f), filled = true)
        drawCircle(if (p.strongOutlines) Color.Black else color.darker(0.5f), r, c, style = Stroke(u * 0.006f))
    }
}

/** The picture of everything that never moves, painted once per size and color mode. */
class PinballBackdrop {
    internal var bitmap: ImageBitmap? = null
    internal var key: Any? = null
}

/** The table, its lights and targets, the plunger, the flippers and the ball. */
fun DrawScope.drawPinball(table: Table, s: PinballState, p: Palette, backdrop: PinballBackdrop? = null) {
    val u = size.width
    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    if (backdrop != null) {
        val w = size.width.toInt().coerceAtLeast(1)
        val h = size.height.toInt().coerceAtLeast(1)
        val key = listOf(w, h, p.isDark, p.mode)
        if (backdrop.key != key || backdrop.bitmap == null) {
            val bitmap = ImageBitmap(w, h)
            CanvasDrawScope().draw(this, layoutDirection, Canvas(bitmap), Size(w.toFloat(), h.toFloat())) { drawPinballStatic(p) }
            backdrop.bitmap = bitmap
            backdrop.key = key
        }
        drawImage(backdrop.bitmap!!)
    } else {
        drawPinballStatic(p)
    }
    val dots = Dots(this)
    // Lanes at the top: a lamp each, lit once the ball rolled through.
    Pinball.laneCenters.forEachIndexed { i, (x, y) ->
        val lit = s.lanes[i]
        val c = o(x, y)
        if (lit) drawCircle(LANE_ON.copy(alpha = 0.35f), Pinball.LANE_RADIUS * u * 1.5f, c)
        drawCircle(if (lit) LANE_ON else Color.White.copy(alpha = 0.25f), Pinball.LANE_RADIUS * u * 0.55f, c)
        drawCircle(if (p.strongOutlines) Color.Black else Color.Black.copy(alpha = 0.5f), Pinball.LANE_RADIUS * u * 0.55f, c, style = Stroke(u * 0.004f))
    }
    // Stars across the top.
    Pinball.stars.forEachIndexed { i, (x, y) ->
        val lit = s.stars[i]
        if (lit) drawCircle(STAR_ON.copy(alpha = 0.3f), Pinball.STAR_RADIUS * u * 1.6f, o(x, y))
        star(o(x, y), Pinball.STAR_RADIUS * u, if (lit) STAR_ON else Color.White.copy(alpha = 0.6f), lit)
    }
    // Drop targets: standing blocks, or an empty slot once knocked down; they blink as they rise again.
    val rising = table.raising > 0f
    Pinball.targetCenters.forEachIndexed { i, (x, y) ->
        val left = o(x - Pinball.TARGET_HALF, y - 0.012f)
        val sz = Size(Pinball.TARGET_HALF * 2 * u, 0.024f * u)
        val down = s.targets[i] || (rising && (table.raising * 8).toInt() % 2 == 0)
        if (down) {
            drawRect(Color.Black.copy(alpha = 0.55f), left + Offset(0f, sz.height * 0.4f), Size(sz.width, sz.height * 0.35f))
        } else {
            drawRect(Color.Black.copy(alpha = 0.35f), left + Offset(u * 0.004f, u * 0.006f), sz)
            drawRect(TARGET.inMode(p), left, sz)
            drawRect(TARGET.lighter(0.5f), left, Size(sz.width, sz.height * 0.3f))
            drawRect(if (p.strongOutlines) Color.Black else TARGET.darker(0.5f), left, sz, style = Stroke(u * 0.004f))
        }
    }
    // Bumper lights: a ring of bulbs that flash in turn when the bumper is hit.
    Pinball.bumpers.forEachIndexed { i, b ->
        val c = o(b.x, b.y)
        val r = b.r * u
        val flashing = table.glow[i] > 0f
        val phase = (table.glow[i] * 16).toInt() % 2
        if (flashing) drawCircle(Color.White.copy(alpha = 0.3f), r * 1.4f, c)
        for (k in 0 until 8) {
            val a = k / 8f * 6.2832f
            val at = c + Offset(cos(a) * r * 0.87f, sin(a) * r * 0.87f)
            val on = flashing && k % 2 == phase
            drawRect(if (on) BULB else BULB.darker(0.55f).copy(alpha = 0.8f), at - Offset(u * 0.006f, u * 0.006f), Size(u * 0.012f, u * 0.012f))
        }
    }
    // The plunger: a spring pulled down while the Launch button is held.
    val laneL = Pinball.LANE_LEFT * u
    val tipY = (Pinball.PLUNGER_Y + table.charge * 0.035f) * u
    val bottom = Pinball.HEIGHT * u
    val laneW = (0.97f - Pinball.LANE_LEFT) * u
    drawRect(Color(0xFFB0BEC5).inMode(p), Offset(laneL + laneW * 0.2f, tipY), Size(laneW * 0.6f, u * 0.012f))
    var yy = tipY + u * 0.012f
    var k = 0
    while (yy < bottom) {
        drawRect(if (k % 2 == 0) Color(0xFF90A4AE).inMode(p) else Color(0xFF546E7A).inMode(p), Offset(laneL + laneW * 0.35f, yy), Size(laneW * 0.3f, (bottom - tipY) / 6f))
        yy += (bottom - tipY) / 6f
        k++
    }
    // Flippers: red and white bats.
    Pinball.flippers.forEachIndexed { n, f ->
        val a = table.angles[n]
        val tip = o(f.px + f.length * cos(a), f.py + f.length * sin(a))
        val pivot = o(f.px, f.py)
        drawLine(Color.Black.copy(alpha = 0.35f), pivot + Offset(u * 0.006f, u * 0.01f), tip + Offset(u * 0.006f, u * 0.01f), u * 0.05f, StrokeCap.Round)
        drawLine(if (p.strongOutlines) Color.Black else Color(0xFFC62828), pivot, tip, u * 0.05f, StrokeCap.Round)
        drawLine(Color.White, pivot, tip, u * 0.032f, StrokeCap.Round)
        drawLine(Color(0xFFE53935), pivot, tip, u * 0.01f, StrokeCap.Round)
        drawCircle(Color(0xFF9E9E9E), u * 0.012f, pivot)
    }
    // Sparkles: little four-pointed stars that fade.
    for (i in 0 until Table.SPARKS) {
        val life = table.sparks[i * 5 + 4]
        if (life <= 0f) continue
        val alpha = (life / Table.SPARK_LIFE).coerceIn(0f, 1f)
        val (si, sj) = dots.at(table.sparks[i * 5], table.sparks[i * 5 + 1])
        val c = (if (i % 2 == 0) BULB else Color.White).copy(alpha = alpha)
        dots.rect(si, sj - 1, 1, 3, c)
        dots.rect(si - 1, sj, 3, 1, c)
    }
    // The ball: a shiny steel marble, a little bigger while it rides the raised ramp.
    if (table.inPlay) {
        val c = o(table.x, table.y)
        val r = Pinball.BALL * u * if (table.ramp >= 0f) 1.15f else 1f
        drawCircle(Color.Black.copy(alpha = 0.35f), r, c + Offset(r * 0.25f, r * 0.35f))
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFB0BEC5), Color(0xFF546E7A)), c - Offset(r * 0.35f, r * 0.35f), r * 1.6f), r, c)
    }
}

/** Home-screen picture: the fair table with lit stars, a lane, a fallen target and a ball. */
fun pinballThumbnail(scope: DrawScope, p: Palette) = scope.run {
    val t = Table(Difficulty.EASY)
    t.serve()
    t.launch()
    t.x = 0.42f
    t.y = 0.95f
    val h = size.height
    val w = h / Pinball.HEIGHT
    translate((size.width - w) / 2, 0f) {
        scale(w / size.width, w / size.width, Offset.Zero) {
            drawPinball(
                t,
                PinballState(Difficulty.EASY, stars = listOf(true, true, false, false), lanes = listOf(true, false, true), targets = listOf(false, true, false)),
                p,
            )
        }
    }
}
