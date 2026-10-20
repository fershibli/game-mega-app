package io.github.fershibli.megagames.games.memory

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.drawCard
import io.github.fershibli.megagames.ui.art.drawCardBack
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.art.softShadow
import io.github.fershibli.megagames.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Pictures of the memory cards, all drawn in code. Each one is told apart by its shape first and
// its color second, so the game works for every kind of color vision. Shapes are given in a unit
// box (0..1 across and down) and outlined in a soft dark ink, like a children's picture book.
// ---------------------------------------------------------------------------------------------

/** Draws in a [size]-wide unit box around [c]. With a [tint], everything is one flat color. */
private class Pen(val d: DrawScope, val c: Offset, val s: Float, val tint: Color?) {
    val ink = Color(0xFF3B2F2A)
    val line = s * 0.028f

    fun pt(x: Float, y: Float) = Offset(c.x + (x - 0.5f) * s, c.y + (y - 0.5f) * s)
    fun col(color: Color) = tint ?: color
    val details: Boolean get() = tint == null

    fun Path.m(x: Float, y: Float) = pt(x, y).let { moveTo(it.x, it.y) }
    fun Path.l(x: Float, y: Float) = pt(x, y).let { lineTo(it.x, it.y) }
    fun Path.q(x1: Float, y1: Float, x2: Float, y2: Float) {
        val a = pt(x1, y1)
        val b = pt(x2, y2)
        quadraticTo(a.x, a.y, b.x, b.y)
    }
    fun Path.cu(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        val a = pt(x1, y1)
        val b = pt(x2, y2)
        val e = pt(x3, y3)
        cubicTo(a.x, a.y, b.x, b.y, e.x, e.y)
    }
    fun Path.oval(x: Float, y: Float, w: Float, h: Float) = addOval(Rect(pt(x - w / 2, y - h / 2), Size(w * s, h * s)))
    fun Path.disc(x: Float, y: Float, r: Float) = addOval(Rect(pt(x, y), r * s))

    /** Fills a shape over a slightly bigger ink copy, which leaves only an outer outline. */
    fun shape(color: Color, outline: Boolean = true, build: Path.() -> Unit) {
        val path = Path().apply(build)
        if (outline && details) d.drawPath(path, ink, style = Stroke(line * 2, join = StrokeJoin.Round))
        d.drawPath(path, col(color))
    }

    fun circle(x: Float, y: Float, r: Float, color: Color, outline: Boolean = true) = shape(color, outline) { disc(x, y, r) }
    fun ellipse(x: Float, y: Float, w: Float, h: Float, color: Color, outline: Boolean = true) = shape(color, outline) { oval(x, y, w, h) }
    fun poly(color: Color, vararg xy: Float, outline: Boolean = true) = shape(color, outline) {
        m(xy[0], xy[1])
        for (k in 2 until xy.size step 2) l(xy[k], xy[k + 1])
        close()
    }

    fun stroke(color: Color, width: Float, x1: Float, y1: Float, x2: Float, y2: Float) =
        d.drawLine(col(color), pt(x1, y1), pt(x2, y2), width * s, StrokeCap.Round)

    /** Inner details (eyes, seeds...) only appear in full color. */
    fun dot(x: Float, y: Float, r: Float, color: Color) { if (details) d.drawCircle(color, r * s, pt(x, y)) }
    fun detail(color: Color, width: Float, x1: Float, y1: Float, x2: Float, y2: Float) { if (details) stroke(color, width, x1, y1, x2, y2) }
    fun arc(color: Color, width: Float, build: Path.() -> Unit) {
        if (details) d.drawPath(Path().apply(build), color, style = Stroke(width * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    fun rotated(deg: Float, x: Float, y: Float, block: Pen.() -> Unit) = d.rotate(deg, pt(x, y)) { this@Pen.block() }

    /** Two friendly eyes with a glint. */
    fun eyes(dx: Float, y: Float, r: Float) {
        for (x in floatArrayOf(0.5f - dx, 0.5f + dx)) {
            dot(x, y, r, Color(0xFF2A211D))
            dot(x - r * 0.3f, y - r * 0.35f, r * 0.35f, Color.White)
        }
    }

    fun cheeks(dx: Float, y: Float) {
        dot(0.5f - dx, y, 0.055f, Color(0xFFF48FB1).copy(alpha = 0.7f))
        dot(0.5f + dx, y, 0.055f, Color(0xFFF48FB1).copy(alpha = 0.7f))
    }

    fun smile(y: Float, w: Float) = arc(ink, 0.025f) { m(0.5f - w, y); q(0.5f - w / 2, y + w * 0.8f, 0.5f, y); q(0.5f + w / 2, y + w * 0.8f, 0.5f + w, y) }
}

private fun starPath(pen: Pen, cx: Float, cy: Float, outer: Float, inner: Float, points: Int = 5) = Path().apply {
    for (i in 0 until points * 2) {
        val a = PI * i / points - PI / 2
        val r = if (i % 2 == 0) outer else inner
        val p = pen.pt(cx + (r * cos(a)).toFloat(), cy + (r * sin(a)).toFloat())
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

// ----- Symbols -------------------------------------------------------------------------------

private fun Pen.symbol(kind: Int) {
    when (kind) {
        0 -> { // star
            val path = starPath(this, 0.5f, 0.54f, 0.43f, 0.19f)
            if (details) d.drawPath(path, ink, style = Stroke(line * 2, join = StrokeJoin.Round))
            d.drawPath(path, col(Color(0xFFFFC83D)))
        }
        1 -> shape(Color(0xFFE5484D)) { // heart
            m(0.5f, 0.9f); cu(0.22f, 0.7f, 0.06f, 0.5f, 0.1f, 0.32f); cu(0.14f, 0.1f, 0.44f, 0.06f, 0.5f, 0.28f)
            cu(0.56f, 0.06f, 0.86f, 0.1f, 0.9f, 0.32f); cu(0.94f, 0.5f, 0.78f, 0.7f, 0.5f, 0.9f); close()
        }
        2 -> { // crescent moon
            val outer = Path().apply { disc(0.46f, 0.5f, 0.4f) }
            val bite = Path().apply { disc(0.64f, 0.38f, 0.33f) }
            val moon = Path().apply { op(outer, bite, PathOperation.Difference) }
            if (details) d.drawPath(moon, ink, style = Stroke(line * 2, join = StrokeJoin.Round))
            d.drawPath(moon, col(Color(0xFF6C7BD9)))
        }
        3 -> { // sun
            for (k in 0 until 8) {
                val a = PI * k / 4
                val x1 = 0.5f + (0.3f * cos(a)).toFloat()
                val y1 = 0.5f + (0.3f * sin(a)).toFloat()
                val x2 = 0.5f + (0.44f * cos(a)).toFloat()
                val y2 = 0.5f + (0.44f * sin(a)).toFloat()
                if (details) stroke(ink, 0.13f, x1, y1, x2, y2)
                stroke(Color(0xFFFF9F1C), 0.08f, x1, y1, x2, y2)
            }
            circle(0.5f, 0.5f, 0.24f, Color(0xFFFFB627))
        }
        4 -> shape(Color(0xFF3A8DDE)) { // drop
            m(0.5f, 0.08f); cu(0.56f, 0.24f, 0.82f, 0.48f, 0.8f, 0.64f); cu(0.79f, 0.82f, 0.66f, 0.92f, 0.5f, 0.92f)
            cu(0.34f, 0.92f, 0.21f, 0.82f, 0.2f, 0.64f); cu(0.18f, 0.48f, 0.44f, 0.24f, 0.5f, 0.08f); close()
        }
        5 -> { // leaf
            shape(Color(0xFF4CAF50)) { m(0.16f, 0.86f); cu(0.1f, 0.42f, 0.42f, 0.12f, 0.88f, 0.12f); cu(0.9f, 0.56f, 0.6f, 0.88f, 0.16f, 0.86f); close() }
            detail(Color(0xFF2E7D32), 0.03f, 0.16f, 0.86f, 0.7f, 0.32f)
        }
        6 -> { // flower
            for (k in 0 until 5) {
                val a = 2 * PI * k / 5 - PI / 2
                circle(0.5f + (0.22f * cos(a)).toFloat(), 0.5f + (0.22f * sin(a)).toFloat(), 0.17f, Color(0xFFF48FB1))
            }
            circle(0.5f, 0.5f, 0.13f, Color(0xFFFFD54F))
        }
        7 -> shape(Color(0xFFB3D4F5)) { // cloud
            disc(0.32f, 0.58f, 0.17f); disc(0.5f, 0.44f, 0.22f); disc(0.7f, 0.56f, 0.18f)
            addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(pt(0.18f, 0.56f), pt(0.84f, 0.75f)), CornerRadius(0.09f * s)))
        }
        8 -> poly(Color(0xFFFFD23F), 0.6f, 0.06f, 0.22f, 0.56f, 0.46f, 0.56f, 0.36f, 0.94f, 0.8f, 0.42f, 0.55f, 0.42f, 0.68f, 0.06f) // lightning
        9 -> { // gem
            poly(Color(0xFF4DD0E1), 0.3f, 0.2f, 0.7f, 0.2f, 0.9f, 0.4f, 0.5f, 0.9f, 0.1f, 0.4f)
            detail(Color(0xFF00838F), 0.02f, 0.1f, 0.4f, 0.9f, 0.4f)
            detail(Color(0xFF00838F), 0.02f, 0.38f, 0.4f, 0.5f, 0.9f)
            detail(Color(0xFF00838F), 0.02f, 0.62f, 0.4f, 0.5f, 0.9f)
        }
        10 -> poly(Color(0xFF9C6ADE), 0.5f, 0.1f, 0.92f, 0.86f, 0.08f, 0.86f) // triangle
        11 -> { // ring
            if (details) d.drawCircle(ink, 0.34f * s, pt(0.5f, 0.5f), style = Stroke(0.2f * s))
            d.drawCircle(col(Color(0xFFFF7F50)), 0.34f * s, pt(0.5f, 0.5f), style = Stroke(0.15f * s))
        }
        12 -> shape(Color(0xFF26A69A)) { // plus
            addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(pt(0.38f, 0.1f), pt(0.62f, 0.9f)), CornerRadius(0.06f * s)))
            addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(pt(0.1f, 0.38f), pt(0.9f, 0.62f)), CornerRadius(0.06f * s)))
        }
        13 -> { // house
            poly(Color(0xFFF2C14E), 0.2f, 0.5f, 0.8f, 0.5f, 0.8f, 0.9f, 0.2f, 0.9f)
            poly(Color(0xFFC0563E), 0.5f, 0.1f, 0.92f, 0.54f, 0.08f, 0.54f)
            if (details) poly(Color(0xFF8D5A3B), 0.42f, 0.66f, 0.58f, 0.66f, 0.58f, 0.9f, 0.42f, 0.9f)
        }
        else -> { // crown
            poly(Color(0xFFE0A526), 0.1f, 0.3f, 0.3f, 0.52f, 0.5f, 0.2f, 0.7f, 0.52f, 0.9f, 0.3f, 0.84f, 0.8f, 0.16f, 0.8f)
            dot(0.3f, 0.68f, 0.05f, Color(0xFFE5484D))
            dot(0.5f, 0.68f, 0.05f, Color(0xFF3A8DDE))
            dot(0.7f, 0.68f, 0.05f, Color(0xFF4CAF50))
        }
    }
}

// ----- Fruits --------------------------------------------------------------------------------

private fun Pen.fruit(kind: Int) {
    val leafGreen = Color(0xFF5DAE3B)
    val stem = Color(0xFF7A4E2D)
    when (kind) {
        0 -> { // apple
            stroke(stem, 0.05f, 0.5f, 0.3f, 0.54f, 0.1f)
            rotated(-30f, 0.66f, 0.17f) { ellipse(0.66f, 0.17f, 0.2f, 0.1f, leafGreen) }
            shape(Color(0xFFE53935)) {
                m(0.5f, 0.3f); cu(0.32f, 0.16f, 0.1f, 0.28f, 0.12f, 0.55f); cu(0.14f, 0.8f, 0.34f, 0.94f, 0.5f, 0.88f)
                cu(0.66f, 0.94f, 0.86f, 0.8f, 0.88f, 0.55f); cu(0.9f, 0.28f, 0.68f, 0.16f, 0.5f, 0.3f); close()
            }
            dot(0.3f, 0.48f, 0.06f, Color.White.copy(alpha = 0.6f))
        }
        1 -> { // pear
            stroke(stem, 0.05f, 0.5f, 0.2f, 0.53f, 0.06f)
            shape(Color(0xFFC0CA33)) {
                m(0.5f, 0.16f); cu(0.62f, 0.16f, 0.62f, 0.32f, 0.66f, 0.42f); cu(0.84f, 0.56f, 0.86f, 0.92f, 0.5f, 0.92f)
                cu(0.14f, 0.92f, 0.16f, 0.56f, 0.34f, 0.42f); cu(0.38f, 0.32f, 0.38f, 0.16f, 0.5f, 0.16f); close()
            }
            dot(0.38f, 0.62f, 0.05f, Color.White.copy(alpha = 0.5f))
        }
        2 -> { // cherries
            arc(stem, 0.035f) { m(0.3f, 0.62f); q(0.38f, 0.3f, 0.58f, 0.12f) }
            arc(stem, 0.035f) { m(0.7f, 0.58f); q(0.64f, 0.3f, 0.58f, 0.12f) }
            if (!details) { stroke(stem, 0.035f, 0.3f, 0.62f, 0.58f, 0.12f); stroke(stem, 0.035f, 0.7f, 0.58f, 0.58f, 0.12f) }
            rotated(20f, 0.68f, 0.14f) { ellipse(0.68f, 0.14f, 0.2f, 0.09f, leafGreen) }
            circle(0.3f, 0.7f, 0.18f, Color(0xFFC62828))
            circle(0.7f, 0.66f, 0.18f, Color(0xFFC62828))
            dot(0.25f, 0.65f, 0.045f, Color.White.copy(alpha = 0.6f))
            dot(0.65f, 0.61f, 0.045f, Color.White.copy(alpha = 0.6f))
        }
        3 -> { // banana
            shape(Color(0xFFFFE14D)) {
                m(0.14f, 0.3f); cu(0.18f, 0.78f, 0.62f, 0.96f, 0.92f, 0.62f); cu(0.64f, 0.76f, 0.32f, 0.64f, 0.24f, 0.26f); close()
            }
            dot(0.18f, 0.28f, 0.035f, stem)
            dot(0.9f, 0.63f, 0.03f, stem)
        }
        4 -> { // lemon
            shape(Color(0xFFF7D038)) {
                m(0.06f, 0.52f); q(0.12f, 0.44f, 0.18f, 0.42f); cu(0.3f, 0.2f, 0.7f, 0.2f, 0.82f, 0.42f)
                q(0.88f, 0.44f, 0.94f, 0.5f); q(0.88f, 0.56f, 0.82f, 0.6f); cu(0.7f, 0.82f, 0.3f, 0.82f, 0.18f, 0.6f); q(0.12f, 0.58f, 0.06f, 0.52f); close()
            }
            dot(0.36f, 0.42f, 0.045f, Color.White.copy(alpha = 0.6f))
        }
        5 -> { // strawberry
            shape(Color(0xFFE8384F)) {
                m(0.5f, 0.92f); cu(0.2f, 0.76f, 0.1f, 0.46f, 0.2f, 0.34f); cu(0.32f, 0.24f, 0.68f, 0.24f, 0.8f, 0.34f); cu(0.9f, 0.46f, 0.8f, 0.76f, 0.5f, 0.92f); close()
            }
            val seed = Color(0xFFFFE082)
            for ((x, y) in listOf(0.32f to 0.46f, 0.5f to 0.44f, 0.68f to 0.46f, 0.4f to 0.6f, 0.6f to 0.6f, 0.5f to 0.74f, 0.28f to 0.6f, 0.72f to 0.6f)) dot(x, y, 0.022f, seed)
            val leaves = starPath(this, 0.5f, 0.28f, 0.2f, 0.08f, 6)
            if (details) d.drawPath(leaves, ink, style = Stroke(line * 2, join = StrokeJoin.Round))
            d.drawPath(leaves, col(leafGreen))
        }
        6 -> { // grapes
            stroke(stem, 0.04f, 0.5f, 0.24f, 0.56f, 0.08f)
            rotated(-20f, 0.66f, 0.16f) { ellipse(0.66f, 0.16f, 0.22f, 0.11f, leafGreen) }
            val purple = Color(0xFF7E57C2)
            for ((x, y) in listOf(0.28f to 0.34f, 0.5f to 0.32f, 0.72f to 0.34f, 0.39f to 0.52f, 0.61f to 0.52f, 0.5f to 0.7f, 0.5f to 0.87f)) {
                circle(x, if (y > 0.86f) 0.86f else y, 0.11f, purple)
                dot(x - 0.035f, y - 0.035f, 0.025f, Color.White.copy(alpha = 0.55f))
            }
        }
        7 -> { // watermelon wedge
            shape(Color(0xFFEF5350)) { m(0.1f, 0.26f); q(0.5f, 0.06f, 0.9f, 0.26f); l(0.5f, 0.92f); close() }
            arc(Color(0xFF2E7D32), 0.08f) { m(0.12f, 0.26f); q(0.5f, 0.06f, 0.88f, 0.26f) }
            for ((x, y) in listOf(0.36f to 0.36f, 0.5f to 0.34f, 0.64f to 0.36f, 0.43f to 0.5f, 0.57f to 0.5f, 0.5f to 0.64f)) {
                if (details) d.drawOval(Color(0xFF2A211D), pt(x - 0.02f, y - 0.03f), Size(0.04f * s, 0.06f * s))
            }
        }
        8 -> { // pineapple
            val crown = Color(0xFF43A047)
            poly(crown, 0.5f, 0.04f, 0.58f, 0.36f, 0.42f, 0.36f)
            poly(crown, 0.3f, 0.12f, 0.5f, 0.38f, 0.36f, 0.38f)
            poly(crown, 0.7f, 0.12f, 0.64f, 0.38f, 0.5f, 0.38f)
            ellipse(0.5f, 0.64f, 0.5f, 0.58f, Color(0xFFF9A825))
            val clip = Path().apply { oval(0.5f, 0.64f, 0.5f, 0.58f) }
            if (details) d.clipPath(clip) {
                for (k in -3..3) {
                    val x = 0.5f + k * 0.12f
                    drawLine(Color(0xFFB26A00), pt(x - 0.3f, 0.35f), pt(x + 0.3f, 0.95f), 0.02f * s)
                    drawLine(Color(0xFFB26A00), pt(x + 0.3f, 0.35f), pt(x - 0.3f, 0.95f), 0.02f * s)
                }
            }
        }
        9 -> { // orange slice
            circle(0.5f, 0.5f, 0.42f, Color(0xFFF57C00))
            circle(0.5f, 0.5f, 0.35f, Color(0xFFFFB74D), outline = false)
            for (k in 0 until 8) {
                val a = PI * k / 4
                detail(Color(0xFFFFF3E0), 0.03f, 0.5f, 0.5f, 0.5f + (0.34f * cos(a)).toFloat(), 0.5f + (0.34f * sin(a)).toFloat())
            }
            dot(0.5f, 0.5f, 0.05f, Color(0xFFFFF3E0))
        }
        10 -> { // avocado half
            shape(Color(0xFF558B2F)) {
                m(0.5f, 0.08f); cu(0.66f, 0.08f, 0.7f, 0.3f, 0.76f, 0.44f); cu(0.9f, 0.7f, 0.76f, 0.94f, 0.5f, 0.94f)
                cu(0.24f, 0.94f, 0.1f, 0.7f, 0.24f, 0.44f); cu(0.3f, 0.3f, 0.34f, 0.08f, 0.5f, 0.08f); close()
            }
            if (details) shape(Color(0xFFD4E79E), outline = false) {
                m(0.5f, 0.16f); cu(0.6f, 0.16f, 0.64f, 0.34f, 0.69f, 0.46f); cu(0.81f, 0.68f, 0.7f, 0.87f, 0.5f, 0.87f)
                cu(0.3f, 0.87f, 0.19f, 0.68f, 0.31f, 0.46f); cu(0.36f, 0.34f, 0.4f, 0.16f, 0.5f, 0.16f); close()
            }
            circle(0.5f, 0.64f, 0.14f, Color(0xFF8D5A3B))
        }
        11 -> { // kiwi slice
            circle(0.5f, 0.5f, 0.42f, Color(0xFF8D6E63))
            circle(0.5f, 0.5f, 0.36f, Color(0xFF8BC34A), outline = false)
            dot(0.5f, 0.5f, 0.12f, Color(0xFFF1F8E9))
            for (k in 0 until 12) {
                val a = PI * k / 6
                dot(0.5f + (0.21f * cos(a)).toFloat(), 0.5f + (0.21f * sin(a)).toFloat(), 0.022f, Color(0xFF2A211D))
            }
        }
        12 -> { // pumpkin
            stroke(Color(0xFF6D8B3A), 0.08f, 0.5f, 0.3f, 0.54f, 0.12f)
            val orange = Color(0xFFEF6C00)
            ellipse(0.27f, 0.6f, 0.36f, 0.56f, orange)
            ellipse(0.73f, 0.6f, 0.36f, 0.56f, orange)
            ellipse(0.5f, 0.6f, 0.42f, 0.62f, orange.lighter(0.12f))
        }
        13 -> { // pomegranate
            poly(Color(0xFF8E1240), 0.36f, 0.28f, 0.36f, 0.1f, 0.44f, 0.18f, 0.5f, 0.08f, 0.56f, 0.18f, 0.64f, 0.1f, 0.64f, 0.28f)
            circle(0.5f, 0.58f, 0.36f, Color(0xFFAD1457))
            dot(0.38f, 0.46f, 0.06f, Color.White.copy(alpha = 0.4f))
        }
        else -> { // coconut half
            shape(Color(0xFF6D4C41)) { m(0.08f, 0.46f); l(0.92f, 0.46f); cu(0.92f, 0.76f, 0.72f, 0.9f, 0.5f, 0.9f); cu(0.28f, 0.9f, 0.08f, 0.76f, 0.08f, 0.46f); close() }
            ellipse(0.5f, 0.46f, 0.84f, 0.24f, Color(0xFFFAFAFA))
            if (details) d.drawOval(Color(0xFFEDE3D1), pt(0.24f, 0.4f), Size(0.52f * s, 0.12f * s))
            for ((x, y) in listOf(0.3f to 0.68f, 0.5f to 0.76f, 0.7f to 0.68f)) dot(x, y, 0.02f, Color(0xFF3E2723))
        }
    }
}

// ----- Animals -------------------------------------------------------------------------------

private fun Pen.animal(kind: Int) {
    val pink = Color(0xFFF48FB1)
    val nose = Color(0xFF3B2F2A)
    when (kind) {
        0 -> { // cat
            val fur = Color(0xFFF6A04D)
            poly(fur, 0.16f, 0.46f, 0.2f, 0.08f, 0.46f, 0.3f)
            poly(fur, 0.84f, 0.46f, 0.8f, 0.08f, 0.54f, 0.3f)
            if (details) { poly(pink, 0.23f, 0.36f, 0.25f, 0.18f, 0.38f, 0.3f, outline = false); poly(pink, 0.77f, 0.36f, 0.75f, 0.18f, 0.62f, 0.3f, outline = false) }
            ellipse(0.5f, 0.58f, 0.74f, 0.62f, fur)
            eyes(0.15f, 0.54f, 0.05f)
            poly(pink, 0.46f, 0.64f, 0.54f, 0.64f, 0.5f, 0.69f, outline = false)
            smile(0.7f, 0.07f)
            for (dy in floatArrayOf(-0.03f, 0.03f)) {
                detail(nose, 0.012f, 0.06f, 0.64f + dy * 2, 0.3f, 0.66f + dy)
                detail(nose, 0.012f, 0.94f, 0.64f + dy * 2, 0.7f, 0.66f + dy)
            }
        }
        1 -> { // dog
            val ear = Color(0xFF8D6E63)
            rotated(15f, 0.18f, 0.48f) { ellipse(0.18f, 0.5f, 0.22f, 0.46f, ear) }
            rotated(-15f, 0.82f, 0.48f) { ellipse(0.82f, 0.5f, 0.22f, 0.46f, ear) }
            ellipse(0.5f, 0.52f, 0.6f, 0.66f, Color(0xFFE0C097))
            ellipse(0.5f, 0.7f, 0.34f, 0.24f, Color(0xFFF5E6CC), outline = false)
            eyes(0.12f, 0.46f, 0.05f)
            ellipse(0.5f, 0.63f, 0.14f, 0.09f, nose)
            if (details) ellipse(0.5f, 0.82f, 0.08f, 0.08f, pink)
            smile(0.72f, 0.06f)
        }
        2 -> { // rabbit
            val white = Color(0xFFF5F5F5)
            ellipse(0.36f, 0.24f, 0.16f, 0.44f, white)
            ellipse(0.64f, 0.24f, 0.16f, 0.44f, white)
            if (details) { ellipse(0.36f, 0.25f, 0.07f, 0.32f, pink, outline = false); ellipse(0.64f, 0.25f, 0.07f, 0.32f, pink, outline = false) }
            circle(0.5f, 0.64f, 0.3f, white)
            eyes(0.12f, 0.6f, 0.045f)
            poly(pink, 0.46f, 0.68f, 0.54f, 0.68f, 0.5f, 0.73f, outline = false)
            smile(0.76f, 0.05f)
            cheeks(0.18f, 0.72f)
        }
        3 -> { // bear
            val fur = Color(0xFFA1785A)
            circle(0.24f, 0.28f, 0.13f, fur)
            circle(0.76f, 0.28f, 0.13f, fur)
            dot(0.24f, 0.28f, 0.06f, fur.lighter(0.35f))
            dot(0.76f, 0.28f, 0.06f, fur.lighter(0.35f))
            circle(0.5f, 0.56f, 0.36f, fur)
            ellipse(0.5f, 0.68f, 0.3f, 0.22f, Color(0xFFE8D2BA), outline = false)
            eyes(0.13f, 0.5f, 0.045f)
            ellipse(0.5f, 0.63f, 0.11f, 0.07f, nose)
            smile(0.7f, 0.05f)
        }
        4 -> { // pig
            val skin = Color(0xFFF8A5C2)
            poly(skin, 0.16f, 0.36f, 0.22f, 0.1f, 0.4f, 0.24f)
            poly(skin, 0.84f, 0.36f, 0.78f, 0.1f, 0.6f, 0.24f)
            circle(0.5f, 0.56f, 0.37f, skin)
            ellipse(0.5f, 0.66f, 0.3f, 0.2f, Color(0xFFE57FA4))
            dot(0.45f, 0.66f, 0.03f, Color(0xFF8E3B5B))
            dot(0.55f, 0.66f, 0.03f, Color(0xFF8E3B5B))
            eyes(0.14f, 0.46f, 0.045f)
        }
        5 -> { // mouse
            val fur = Color(0xFFB0BEC5)
            circle(0.22f, 0.3f, 0.19f, fur)
            circle(0.78f, 0.3f, 0.19f, fur)
            dot(0.22f, 0.3f, 0.11f, pink)
            dot(0.78f, 0.3f, 0.11f, pink)
            ellipse(0.5f, 0.62f, 0.5f, 0.5f, fur)
            eyes(0.1f, 0.58f, 0.04f)
            dot(0.5f, 0.7f, 0.04f, pink.darker(0.2f))
            for (dy in floatArrayOf(-0.03f, 0.03f)) {
                detail(nose, 0.01f, 0.12f, 0.7f + dy * 2, 0.36f, 0.71f + dy)
                detail(nose, 0.01f, 0.88f, 0.7f + dy * 2, 0.64f, 0.71f + dy)
            }
        }
        6 -> { // owl
            val feather = Color(0xFF8D6E63)
            poly(feather, 0.16f, 0.12f, 0.36f, 0.26f, 0.2f, 0.36f)
            poly(feather, 0.84f, 0.12f, 0.64f, 0.26f, 0.8f, 0.36f)
            ellipse(0.5f, 0.56f, 0.72f, 0.76f, feather)
            ellipse(0.5f, 0.66f, 0.42f, 0.4f, Color(0xFFD7B899), outline = false)
            circle(0.34f, 0.46f, 0.14f, Color.White)
            circle(0.66f, 0.46f, 0.14f, Color.White)
            eyes(0.16f, 0.46f, 0.065f)
            poly(Color(0xFFFFA726), 0.44f, 0.56f, 0.56f, 0.56f, 0.5f, 0.68f)
        }
        7 -> { // frog
            val green = Color(0xFF7CB342)
            circle(0.3f, 0.36f, 0.14f, green)
            circle(0.7f, 0.36f, 0.14f, green)
            ellipse(0.5f, 0.62f, 0.84f, 0.52f, green)
            if (details) d.drawRect(col(green), pt(0.2f, 0.4f), Size(0.6f * s, 0.12f * s))
            circle(0.3f, 0.35f, 0.08f, Color.White)
            circle(0.7f, 0.35f, 0.08f, Color.White)
            dot(0.31f, 0.36f, 0.045f, nose)
            dot(0.71f, 0.36f, 0.045f, nose)
            arc(nose, 0.028f) { m(0.28f, 0.64f); q(0.5f, 0.8f, 0.72f, 0.64f) }
            cheeks(0.28f, 0.62f)
        }
        8 -> { // chick
            val yellow = Color(0xFFFFD54F)
            arc(Color(0xFFE0A526), 0.03f) { m(0.5f, 0.24f); q(0.46f, 0.12f, 0.4f, 0.1f) }
            arc(Color(0xFFE0A526), 0.03f) { m(0.5f, 0.24f); q(0.54f, 0.1f, 0.6f, 0.08f) }
            circle(0.5f, 0.56f, 0.36f, yellow)
            eyes(0.13f, 0.5f, 0.05f)
            poly(Color(0xFFFF8F00), 0.42f, 0.6f, 0.58f, 0.6f, 0.5f, 0.7f)
            cheeks(0.24f, 0.62f)
        }
        9 -> { // fish
            val blue = Color(0xFF4FC3F7)
            poly(blue, 0.68f, 0.5f, 0.94f, 0.26f, 0.94f, 0.74f)
            poly(blue.darker(0.1f), 0.38f, 0.32f, 0.56f, 0.18f, 0.58f, 0.34f)
            ellipse(0.42f, 0.5f, 0.66f, 0.46f, blue)
            for (x in floatArrayOf(0.44f, 0.56f)) arc(Color(0xFF0288D1), 0.02f) { m(x, 0.4f); q(x + 0.06f, 0.5f, x, 0.6f) }
            dot(0.24f, 0.46f, 0.05f, Color.White)
            dot(0.24f, 0.46f, 0.03f, nose)
            arc(nose, 0.02f) { m(0.12f, 0.56f); q(0.16f, 0.6f, 0.2f, 0.57f) }
        }
        10 -> { // panda
            val black = Color(0xFF2E2A28)
            circle(0.24f, 0.28f, 0.13f, black)
            circle(0.76f, 0.28f, 0.13f, black)
            circle(0.5f, 0.56f, 0.37f, Color(0xFFFAFAFA))
            if (details) {
                rotated(-30f, 0.35f, 0.52f) { ellipse(0.35f, 0.52f, 0.14f, 0.2f, black, outline = false) }
                rotated(30f, 0.65f, 0.52f) { ellipse(0.65f, 0.52f, 0.14f, 0.2f, black, outline = false) }
            }
            dot(0.36f, 0.5f, 0.035f, Color.White)
            dot(0.64f, 0.5f, 0.035f, Color.White)
            ellipse(0.5f, 0.66f, 0.1f, 0.06f, black)
            smile(0.72f, 0.05f)
        }
        11 -> { // fox
            val orange = Color(0xFFEF7A2D)
            shape(orange) { m(0.12f, 0.12f); l(0.38f, 0.32f); l(0.62f, 0.32f); l(0.88f, 0.12f); l(0.86f, 0.5f); q(0.72f, 0.78f, 0.5f, 0.9f); q(0.28f, 0.78f, 0.14f, 0.5f); close() }
            if (details) {
                shape(Color(0xFFFFF3E0), outline = false) { m(0.16f, 0.52f); q(0.34f, 0.56f, 0.5f, 0.72f); q(0.66f, 0.56f, 0.84f, 0.52f); q(0.72f, 0.78f, 0.5f, 0.9f); q(0.28f, 0.78f, 0.16f, 0.52f); close() }
                poly(Color(0xFF5D4037), 0.2f, 0.2f, 0.34f, 0.32f, 0.24f, 0.38f, outline = false)
                poly(Color(0xFF5D4037), 0.8f, 0.2f, 0.66f, 0.32f, 0.76f, 0.38f, outline = false)
            }
            eyes(0.14f, 0.5f, 0.045f)
            ellipse(0.5f, 0.84f, 0.1f, 0.07f, nose)
        }
        12 -> { // lion
            val mane = Color(0xFFC1762B)
            for (k in 0 until 12) {
                val a = PI * k / 6
                circle(0.5f + (0.33f * cos(a)).toFloat(), 0.54f + (0.33f * sin(a)).toFloat(), 0.12f, mane)
            }
            circle(0.5f, 0.54f, 0.3f, mane, outline = false)
            circle(0.5f, 0.55f, 0.26f, Color(0xFFF2C14E))
            eyes(0.1f, 0.5f, 0.04f)
            poly(nose, 0.45f, 0.6f, 0.55f, 0.6f, 0.5f, 0.66f, outline = false)
            smile(0.67f, 0.05f)
        }
        13 -> { // cow
            val horn = Color(0xFFEFE3C8)
            poly(horn, 0.3f, 0.26f, 0.22f, 0.06f, 0.4f, 0.22f)
            poly(horn, 0.7f, 0.26f, 0.78f, 0.06f, 0.6f, 0.22f)
            rotated(-20f, 0.14f, 0.38f) { ellipse(0.14f, 0.38f, 0.24f, 0.12f, Color(0xFFFAFAFA)) }
            rotated(20f, 0.86f, 0.38f) { ellipse(0.86f, 0.38f, 0.24f, 0.12f, Color(0xFFFAFAFA)) }
            ellipse(0.5f, 0.5f, 0.56f, 0.64f, Color(0xFFFAFAFA))
            if (details) shape(Color(0xFF3E3A39), outline = false) { m(0.28f, 0.3f); q(0.4f, 0.26f, 0.42f, 0.38f); q(0.36f, 0.48f, 0.26f, 0.44f); close() }
            eyes(0.12f, 0.46f, 0.045f)
            ellipse(0.5f, 0.74f, 0.5f, 0.26f, pink)
            dot(0.42f, 0.74f, 0.03f, Color(0xFF8E3B5B))
            dot(0.58f, 0.74f, 0.03f, Color(0xFF8E3B5B))
        }
        else -> { // sheep
            val wool = Color(0xFFF2F2F2)
            for (k in 0 until 10) {
                val a = PI * k / 5
                circle(0.5f + (0.3f * cos(a)).toFloat(), 0.52f + (0.3f * sin(a)).toFloat(), 0.13f, wool)
            }
            circle(0.5f, 0.52f, 0.3f, wool, outline = false)
            val face = Color(0xFF5D4037)
            rotated(-25f, 0.24f, 0.48f) { ellipse(0.24f, 0.48f, 0.2f, 0.1f, face) }
            rotated(25f, 0.76f, 0.48f) { ellipse(0.76f, 0.48f, 0.2f, 0.1f, face) }
            ellipse(0.5f, 0.58f, 0.34f, 0.42f, face)
            dot(0.43f, 0.54f, 0.04f, Color.White)
            dot(0.57f, 0.54f, 0.04f, Color.White)
            dot(0.43f, 0.55f, 0.022f, nose)
            dot(0.57f, 0.55f, 0.022f, nose)
            smile(0.68f, 0.04f)
        }
    }
}

/** One of the shared symbols, for other games (the color pads use the first ones in one color). */
fun DrawScope.drawPictureSymbol(kind: Int, center: Offset, size: Float, tint: Color? = null) = Pen(this, center, size, tint).symbol(kind)

/** The picture of [face] for a picture variant, filling a box of [size] around [center]. */
fun DrawScope.drawMemoryPicture(variant: String, face: Int, center: Offset, size: Float) {
    val pen = Pen(this, center, size, null)
    val kind = Memory.pairOf(face) % Memory.PICTURES
    when (variant) {
        Memory.FRUITS -> pen.fruit(kind)
        Memory.ANIMALS -> pen.animal(kind)
        else -> pen.symbol(kind)
    }
}

/**
 * A whole memory card. [faceUp] chooses the side; [found] cards are faded so the cards still in
 * play stand out. Playing cards use one red and one black, so a pair is always the same color.
 */
fun DrawScope.drawMemoryCard(
    variant: String,
    face: Int,
    topLeft: Offset,
    size: Size,
    p: Palette,
    cardPalette: Palette,
    measurer: TextMeasurer,
    faceUp: Boolean,
    found: Boolean = false,
) {
    val r = size.width * 0.1f
    if (!faceUp) {
        softShadow(topLeft, size, r, size.width * 0.05f, alpha = 0.22f)
        drawCardBack(topLeft, size, p)
        return
    }
    if (variant == Memory.CARDS) {
        drawCard(Memory.card(face), topLeft, size, cardPalette, measurer, shadow = !found)
    } else {
        if (!found) softShadow(topLeft, size, r, size.width * 0.05f, alpha = 0.22f)
        drawRoundRect(Brush.verticalGradient(listOf(p.cardFace, p.cardFace.darker(0.05f)), topLeft.y, topLeft.y + size.height), topLeft, size, CornerRadius(r))
        drawRoundRect(p.cardEdge, topLeft, size, CornerRadius(r), style = Stroke(if (p.strongOutlines) size.width * 0.03f else size.width * 0.015f))
        val pic = minOf(size.width, size.height) * 0.84f
        drawMemoryPicture(variant, face, Offset(topLeft.x + size.width / 2, topLeft.y + size.height / 2), pic)
    }
    if (found) {
        drawRoundRect(p.background.copy(alpha = 0.5f), topLeft, size, CornerRadius(r))
        drawRoundRect(p.success, topLeft, size, CornerRadius(r), style = Stroke(size.width * 0.04f))
    }
}

/** The playing-card colors for memory: hearts and diamonds share one red, spades and clubs one black. */
fun memoryCardPalette(p: Palette): Palette = p.copy(suitDiamonds = p.suitHearts, suitClubs = p.suitSpades)

/** Home screen picture: a few cards, two of them turned up as a pair. */
fun DrawScope.memoryThumbnail(p: Palette) {
    drawRect(p.felt)
    val cols = 3
    val gap = size.width * 0.05f
    val w = (size.width - gap * (cols + 1)) / cols
    val h = (size.height - gap * 3) / 2
    for (i in 0 until 6) {
        val tl = Offset(gap + (i % cols) * (w + gap), gap + (i / cols) * (h + gap))
        val up = i == 1 || i == 5
        val r = w * 0.1f
        if (up) {
            drawRoundRect(p.cardFace, tl, Size(w, h), CornerRadius(r))
            drawRoundRect(p.cardEdge, tl, Size(w, h), CornerRadius(r), style = Stroke(w * 0.03f))
            drawMemoryPicture(Memory.FRUITS, 0, Offset(tl.x + w / 2, tl.y + h / 2), w * 0.85f)
        } else {
            drawCardBack(tl, Size(w, h), p)
        }
    }
}
