package io.github.fershibli.megagames.games.pinball

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.sin

private val TABLE = Color(0xFF1E4D7B)
private val RAIL = Color(0xFFB0BEC5)
private val BUMPER_COLORS = listOf(Color(0xFFE53935), Color(0xFF43A047), Color(0xFFFB8C00))
private val STAR_ON = Color(0xFFFFD54F)
private val KICKER = Color(0xFFFF7043)

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
    if (filled) drawPath(path, color) else drawPath(path, color, style = Stroke(r * 0.18f))
}

/** The table, its bumpers, stars, kickers and flippers, and the ball when it is in play. */
fun DrawScope.drawPinball(table: Table, s: PinballState, p: Palette) {
    val u = size.width // one table unit in pixels
    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    val bg = if (p.isDark) TABLE.darker(0.35f) else TABLE
    // The playfield, with its round top.
    val field = Path().apply {
        moveTo(0.03f * u, 1.12f * u)
        lineTo(0.03f * u, 0.3f * u)
        Pinball.walls.drop(2).take(14).forEach { lineTo(it.bx * u, it.by * u) }
        lineTo(0.97f * u, 1.12f * u)
        lineTo(0.715f * u, 1.33f * u)
        lineTo(0.715f * u, Pinball.HEIGHT * u)
        lineTo(0.285f * u, Pinball.HEIGHT * u)
        lineTo(0.285f * u, 1.33f * u)
        close()
    }
    drawPath(field, Brush.verticalGradient(listOf(bg.lighter(0.12f), bg.darker(0.2f))))
    // A few guide lines on the felt, for depth.
    for (k in 1..4) drawCircle(Color.White.copy(alpha = 0.05f), u * 0.12f * k, o(0.5f, 0.75f), style = Stroke(u * 0.004f))
    // Walls.
    val rail = if (p.strongOutlines) Color.White else RAIL
    Pinball.walls.forEach { w ->
        drawLine(if (w.kick > 0f) KICKER else rail, o(w.ax, w.ay), o(w.bx, w.by), u * (if (w.kick > 0f) 0.022f else 0.016f), StrokeCap.Round)
    }
    // Stars across the top.
    Pinball.stars.forEachIndexed { i, (x, y) ->
        val lit = s.stars[i]
        if (lit) drawCircle(STAR_ON.copy(alpha = 0.3f), Pinball.STAR_RADIUS * u * 1.6f, o(x, y))
        star(o(x, y), Pinball.STAR_RADIUS * u, if (lit) STAR_ON else Color.White.copy(alpha = 0.55f), lit)
    }
    // Bumpers: a colored cap on a white ring; they glow when hit.
    Pinball.bumpers.forEachIndexed { i, b ->
        val c = o(b.x, b.y)
        val r = b.r * u
        val color = BUMPER_COLORS[i % BUMPER_COLORS.size]
        if (table.glow[i] > 0f) drawCircle(Color.White.copy(alpha = 0.5f), r * 1.35f, c)
        drawCircle(Color.Black.copy(alpha = 0.3f), r, c + Offset(r * 0.08f, r * 0.12f))
        drawCircle(Color.White, r, c)
        drawCircle(if (table.glow[i] > 0f) color.lighter(0.35f) else color, r * 0.78f, c)
        drawCircle(Color.White.copy(alpha = 0.45f), r * 0.25f, c - Offset(r * 0.25f, r * 0.3f))
        drawCircle(if (p.strongOutlines) Color.Black else color.darker(0.4f), r, c, style = Stroke(u * 0.006f))
    }
    // Flippers.
    Pinball.flippers.forEachIndexed { k, f ->
        val a = table.angles[k]
        val tip = o(f.px + f.length * cos(a), f.py + f.length * sin(a))
        val pivot = o(f.px, f.py)
        drawLine(Color(0xFFC62828), pivot, tip, u * 0.05f, StrokeCap.Round)
        drawLine(Color.White, pivot, tip, u * 0.034f, StrokeCap.Round)
        drawCircle(Color(0xFF9E9E9E), u * 0.012f, pivot)
    }
    // The ball: a shiny steel marble.
    if (table.inPlay) {
        val c = o(table.x, table.y)
        val r = Pinball.BALL * u
        drawCircle(Color.Black.copy(alpha = 0.3f), r, c + Offset(r * 0.25f, r * 0.35f))
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFB0BEC5), Color(0xFF546E7A)), c - Offset(r * 0.35f, r * 0.35f), r * 1.6f), r, c)
    }
}

/** Home-screen picture: the table with its bumpers and a ball. */
fun pinballThumbnail(scope: DrawScope, p: Palette) = scope.run {
    val t = Table(Difficulty.EASY)
    t.x = 0.45f
    t.y = 0.95f
    t.serve()
    t.x = 0.42f
    t.y = 1.0f
    val h = size.height
    val w = h / Pinball.HEIGHT
    translate((size.width - w) / 2, 0f) {
        scale(w / size.width, w / size.width, Offset.Zero) {
            drawPinball(t, PinballState(Difficulty.EASY, stars = listOf(true, true, false, false)), p)
        }
    }
}
