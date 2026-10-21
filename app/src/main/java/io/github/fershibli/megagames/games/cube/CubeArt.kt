package io.github.fershibli.megagames.games.cube

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import io.github.fershibli.megagames.core.ColorMode
import io.github.fershibli.megagames.ui.theme.Palette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Sticker colors of the six faces (U, D, F, B, L, R): a common scheme of our own shades, a
// saturated one for high contrast and one built on the Okabe & Ito colors for color blindness.
private val NormalColors = arrayOf(Color(0xFFF7F7F2), Color(0xFFF5D33B), Color(0xFF2FA35A), Color(0xFF2F6FD0), Color(0xFFF08A24), Color(0xFFD23B3B))
private val ContrastColors = arrayOf(Color(0xFFFFFFFF), Color(0xFFFFE600), Color(0xFF00A651), Color(0xFF0040FF), Color(0xFFFF7A00), Color(0xFFD00000))
private val ColorblindColors = arrayOf(Color(0xFFF5F5F5), Color(0xFFF0E442), Color(0xFF009E73), Color(0xFF0072B2), Color(0xFFE69F00), Color(0xFFCC79A7))
// A light, warm body: the pieces show their colors without the black frame of commercial cubes.
private val Body = Color(0xFF9C9488)

fun cubeColors(p: Palette): Array<Color> = when (p.mode) {
    ColorMode.NORMAL -> NormalColors
    ColorMode.HIGH_CONTRAST -> ContrastColors
    ColorMode.COLORBLIND -> ColorblindColors
}

/** Rotation of [deg] degrees around [axis] (right-hand rule), as a row-major 3×3 matrix. */
internal fun rotation(axis: Int, deg: Float, out: FloatArray) {
    val r = Math.toRadians(deg.toDouble())
    val c = cos(r).toFloat()
    val s = sin(r).toFloat()
    out.fill(0f)
    when (axis) {
        0 -> { out[0] = 1f; out[4] = c; out[5] = -s; out[7] = s; out[8] = c }
        1 -> { out[0] = c; out[2] = s; out[4] = 1f; out[6] = -s; out[8] = c }
        else -> { out[0] = c; out[1] = -s; out[3] = s; out[4] = c; out[8] = 1f }
    }
}

/** out = a × b (row-major 3×3). [out] may not be [a] or [b]. */
internal fun multiply(a: FloatArray, b: FloatArray, out: FloatArray) {
    for (r in 0 until 3) for (c in 0 until 3) out[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
}

/**
 * Draws the cube with a tiny orthographic projection (no 3D library). The cube is cut into at
 * most three blocks along the axis of the layer that is turning; the blocks are drawn far to
 * near (painter's order), and for each block only the faces that look at the viewer. Every
 * sticker is the unit square under an affine transform, so the same rounded rectangle and the
 * same symbol paths serve every sticker: nothing is allocated while drawing a frame.
 *
 * It also remembers where the last frame was drawn, to find the sticker under a finger.
 */
class CubeRenderer {
    /** View rotation (model to screen space, y up), set by the caller before [draw]. */
    val view = FloatArray(9).also { rotation(1, 0f, it) }
    private val turn = FloatArray(9)
    private val m = FloatArray(9)
    private val affine = Matrix()
    private var scale = 1f
    private var cx = 0f
    private var cy = 0f
    private var drawnN = 0

    // Symbols for the accessible palettes, in a -0.5..0.5 box: dot, star, heart, square, triangle, ring.
    private val star = Path().apply {
        for (i in 0 until 10) {
            val a = Math.PI * 2 * i / 10 - Math.PI / 2
            val r = if (i % 2 == 0) 0.52 else 0.22
            val x = (r * cos(a)).toFloat()
            val y = (r * sin(a)).toFloat() + 0.03f
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    private val heart = Path().apply {
        moveTo(0f, 0.42f)
        cubicTo(-0.55f, 0.05f, -0.5f, -0.45f, 0f, -0.18f)
        cubicTo(0.5f, -0.45f, 0.55f, 0.05f, 0f, 0.42f)
        close()
    }
    private val triangle = Path().apply { moveTo(0f, -0.45f); lineTo(0.46f, 0.38f); lineTo(-0.46f, 0.38f); close() }

    private fun x(v0: Float, v1: Float, v2: Float) = (m[0] * v0 + m[1] * v1 + m[2] * v2) * scale
    private fun y(v0: Float, v1: Float, v2: Float) = -(m[3] * v0 + m[4] * v1 + m[5] * v2) * scale
    private fun z(v0: Float, v1: Float, v2: Float) = m[6] * v0 + m[7] * v1 + m[8] * v2

    /** Sets [affine] so the unit square -1..1 lands on center + u·[ux, uy, uz] + w·[wx, wy, wz]. */
    private fun place(c0: Float, c1: Float, c2: Float, u0: Float, u1: Float, u2: Float, w0: Float, w1: Float, w2: Float) {
        val v = affine.values
        v.fill(0f)
        v[Matrix.ScaleX] = x(u0, u1, u2)
        v[Matrix.SkewY] = y(u0, u1, u2)
        v[Matrix.SkewX] = x(w0, w1, w2)
        v[Matrix.ScaleY] = y(w0, w1, w2)
        v[Matrix.ScaleZ] = 1f
        v[Matrix.TranslateX] = cx + x(c0, c1, c2)
        v[Matrix.TranslateY] = cy + y(c0, c1, c2)
        v[Matrix.Perspective2] = 1f
    }

    /**
     * Draws [stickers] (a cube of size [n]). While a layer turns, [turnAxis]/[turnLayer] name it
     * and [turnDeg] is how far it still is from its place. [selected] outlines a face (-1: none).
     */
    fun draw(
        scope: DrawScope,
        stickers: List<Int>,
        n: Int,
        colors: Array<Color>,
        symbols: Boolean,
        outline: Boolean,
        turnAxis: Int = -1,
        turnLayer: Int = 0,
        turnDeg: Float = 0f,
        selected: Int = -1,
        selectedColor: Color = Color.Transparent,
        fill: Float = 0.38f,
    ) = with(scope) {
        frame(size.width, size.height, n, fill)
        val g = CubeGeometry.of(n)
        if (turnAxis < 0 || abs(turnDeg) < 0.01f) {
            view.copyInto(m)
            block(this, g, stickers, n, colors, symbols, outline, 0, 0, n - 1)
            if (selected >= 0) outlineFace(this, selected, n, selectedColor)
            return@with
        }
        // Blocks along the turning axis, far side first.
        val nearPositive = view[6 + turnAxis] > 0
        rotation(turnAxis, turnDeg, turn)
        for (k in 0 until 3) {
            val part = if (nearPositive) k else 2 - k
            val lo = when (part) { 0 -> 0; 1 -> turnLayer; else -> turnLayer + 1 }
            val hi = when (part) { 0 -> turnLayer - 1; 1 -> turnLayer; else -> n - 1 }
            if (lo > hi) continue
            if (part == 1) multiply(view, turn, m) else view.copyInto(m)
            block(this, g, stickers, n, colors, symbols, outline, turnAxis, lo, hi)
        }
    }

    /** One block: layers [lo]..[hi] along [axis], drawn with the current matrix [m]. */
    private fun block(
        scope: DrawScope, g: CubeGeometry, stickers: List<Int>, n: Int, colors: Array<Color>,
        symbols: Boolean, outline: Boolean, axis: Int, lo: Int, hi: Int,
    ) = with(scope) {
        val nf = n.toFloat()
        val center = -nf + lo + hi + 1
        for (f in 0 until 6) {
            val normal = Cube.NORMAL[f]
            val j = Cube.faceAxis(f)
            val s = normal[j]
            val nz = z(normal[0].toFloat(), normal[1].toFloat(), normal[2].toFloat())
            if (nz <= 0.001f) continue
            val half = if (j == axis) (hi - lo + 1).toFloat() else nf
            // Face center of the block.
            var c0 = 0f; var c1 = 0f; var c2 = 0f
            when (axis) { 0 -> c0 = center; 1 -> c1 = center; else -> c2 = center }
            when (j) { 0 -> c0 += s * half; 1 -> c1 += s * half; else -> c2 += s * half }
            val r = Cube.RIGHT[f]
            val d = Cube.DOWN[f]
            val rHalf = if (r[axis] != 0) (hi - lo + 1).toFloat() else nf
            val dHalf = if (d[axis] != 0) (hi - lo + 1).toFloat() else nf
            place(c0, c1, c2, r[0] * rHalf, r[1] * rHalf, r[2] * rHalf, d[0] * dHalf, d[1] * dHalf, d[2] * dHalf)
            withTransform({ transform(affine) }) { drawRect(Body, Offset(-1f, -1f), Size(2f, 2f)) }
            val outer = j != axis || (s < 0 && lo == 0) || (s > 0 && hi == n - 1)
            if (!outer) continue
            val light = 0.72f + 0.28f * nz
            val per = n * n
            for (i in f * per until (f + 1) * per) {
                if (j != axis) {
                    val l = g.layerOf(i, axis)
                    if (l < lo || l > hi) continue
                }
                val p0 = g.pos[i * 3].toFloat()
                val p1 = g.pos[i * 3 + 1].toFloat()
                val p2 = g.pos[i * 3 + 2].toFloat()
                val k = 0.88f
                place(p0, p1, p2, r[0] * k, r[1] * k, r[2] * k, d[0] * k, d[1] * k, d[2] * k)
                val color = stickers[i]
                val base = colors[color]
                val shaded = Color(base.red * light, base.green * light, base.blue * light)
                withTransform({ transform(affine) }) {
                    drawRoundRect(shaded, Offset(-1f, -1f), Size(2f, 2f), CornerRadius(0.3f))
                    if (outline) drawRoundRect(Color.Black, Offset(-1f, -1f), Size(2f, 2f), CornerRadius(0.3f), style = Stroke(0.1f))
                    if (symbols) symbol(this, color, if (base.red * 0.3f + base.green * 0.59f + base.blue * 0.11f > 0.5f) Color.Black.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.92f))
                }
            }
        }
    }

    /** The symbol of color [c], drawn in the unit square of a sticker. */
    fun symbol(scope: DrawScope, c: Int, color: Color) = with(scope) {
        withTransform({ scale(1.3f, 1.3f, Offset.Zero) }) {
            when (c) {
                0 -> drawCircle(color, 0.24f, Offset.Zero)
                1 -> drawPath(star, color)
                2 -> drawPath(heart, color)
                3 -> drawRect(color, Offset(-0.34f, -0.34f), Size(0.68f, 0.68f))
                4 -> drawPath(triangle, color)
                else -> drawCircle(color, 0.32f, Offset.Zero, style = Stroke(0.13f))
            }
        }
    }

    private fun outlineFace(scope: DrawScope, f: Int, n: Int, color: Color) = with(scope) {
        val normal = Cube.NORMAL[f]
        if (z(normal[0].toFloat(), normal[1].toFloat(), normal[2].toFloat()) <= 0.05f) return@with
        val nf = n.toFloat()
        val r = Cube.RIGHT[f]
        val d = Cube.DOWN[f]
        place(normal[0] * nf, normal[1] * nf, normal[2] * nf, r[0] * nf, r[1] * nf, r[2] * nf, d[0] * nf, d[1] * nf, d[2] * nf)
        withTransform({ transform(affine) }) {
            drawRect(color, Offset(-1.02f, -1.02f), Size(2.04f, 2.04f), style = Stroke(0.07f))
        }
    }

    /** Where the cube is drawn in a [width]×[height] area: needed by [draw], [hit] and [swipe]. */
    fun frame(width: Float, height: Float, n: Int, fill: Float = 0.38f) {
        drawnN = n
        cx = width / 2
        cy = height / 2
        scale = min(width, height) * fill / (n * sqrt(3f) * 0.92f)
    }

    /** Index of the sticker under [point] (after [frame] and with the current [view]), or -1 for empty space. */
    fun hit(point: Offset): Int {
        val n = drawnN
        if (n == 0) return -1
        view.copyInto(m)
        val nf = n.toFloat()
        for (f in 0 until 6) {
            val no = Cube.NORMAL[f]
            if (z(no[0].toFloat(), no[1].toFloat(), no[2].toFloat()) <= 0.001f) continue
            val r = Cube.RIGHT[f]
            val d = Cube.DOWN[f]
            val ox = point.x - cx - x(no[0] * nf, no[1] * nf, no[2] * nf)
            val oy = point.y - cy - y(no[0] * nf, no[1] * nf, no[2] * nf)
            val ax = x(r[0] * nf, r[1] * nf, r[2] * nf)
            val ay = y(r[0] * nf, r[1] * nf, r[2] * nf)
            val bx = x(d[0] * nf, d[1] * nf, d[2] * nf)
            val by = y(d[0] * nf, d[1] * nf, d[2] * nf)
            val det = ax * by - ay * bx
            if (abs(det) < 1e-3f) continue
            val u = (ox * by - oy * bx) / det
            val w = (ax * oy - ay * ox) / det
            if (u < -1f || u > 1f || w < -1f || w > 1f) continue
            val col = floor((u + 1f) / 2f * n).toInt().coerceIn(0, n - 1)
            val row = floor((w + 1f) / 2f * n).toInt().coerceIn(0, n - 1)
            return CubeGeometry.of(n).index(f, row, col)
        }
        return -1
    }

    /**
     * The turn asked for by a swipe of [delta] (screen pixels) that started on [sticker]: the
     * sticker's own row or column moves the way the finger went.
     */
    fun swipe(sticker: Int, delta: Offset): CubeMove? {
        val n = drawnN
        if (n == 0 || delta.getDistance() < 1f) return null
        view.copyInto(m)
        val g = CubeGeometry.of(n)
        val f = g.face[sticker]
        var best = -2f
        var dir: IntArray? = null
        for (v in arrayOf(Cube.RIGHT[f], Cube.DOWN[f])) for (sign in intArrayOf(1, -1)) {
            val sx = x(v[0] * sign.toFloat(), v[1] * sign.toFloat(), v[2] * sign.toFloat())
            val sy = y(v[0] * sign.toFloat(), v[1] * sign.toFloat(), v[2] * sign.toFloat())
            val len = sqrt(sx * sx + sy * sy)
            if (len < 1e-3f) continue
            val dot = (sx * delta.x + sy * delta.y) / (len * delta.getDistance())
            if (dot > best) { best = dot; dir = intArrayOf(v[0] * sign, v[1] * sign, v[2] * sign) }
        }
        val a = dir ?: return null
        val no = Cube.NORMAL[f]
        // Moving a sticker of the face with normal N along A is a turn around N × A.
        val w = intArrayOf(no[1] * a[2] - no[2] * a[1], no[2] * a[0] - no[0] * a[2], no[0] * a[1] - no[1] * a[0])
        val axis = w.indexOfFirst { it != 0 }
        if (axis < 0) return null
        return CubeMove(axis, g.layerOf(sticker, axis), w[axis])
    }
}

/** Home screen picture: a 3×3 cube with a few turned layers, seen from above. */
fun DrawScope.cubeThumbnail(p: Palette) {
    drawRect(p.boardDark)
    val r = CubeRenderer()
    val a = FloatArray(9)
    val b = FloatArray(9)
    rotation(0, 26f, a)
    rotation(1, -38f, b)
    multiply(a, b, r.view)
    val moves = listOf(CubeMove(1, 2, -1), CubeMove(0, 2, 1), CubeMove(2, 2, -1))
    val stickers = Cube.applyAll(Cube.solved(3), moves, 3).toList()
    r.draw(this, stickers, 3, cubeColors(p), p.symbols, p.strongOutlines, fill = 0.45f)
}
