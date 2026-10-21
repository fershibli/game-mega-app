package io.github.fershibli.megagames.games.eastern

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.art.pxToSp
import io.github.fershibli.megagames.ui.art.text
import io.github.fershibli.megagames.ui.theme.Palette
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** How a movement mark is drawn on a piece. */
enum class MarkKind {
    /** A dot per step: the piece goes exactly there. */
    STEP,
    /** An arrow: the piece goes any distance that way. */
    SLIDE,
    /** An arrow with a bar across: the piece goes that way by jumping over one piece. */
    HOP,
    /** A hollow dot: a step allowed only later (a soldier once across the river). */
    LATER,
}

/**
 * One direction a piece moves in, seen by its owner ([dy] = -1 is forward), so a beginner can read
 * the movement on the piece itself without knowing the character. [dots] shows longer jumps as two
 * dots (the xiangqi elephant goes two points diagonally).
 */
data class MoveMark(val dx: Int, val dy: Int, val kind: MarkKind = MarkKind.STEP, val dots: Int = 1)

internal val ORTHOGONAL = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)
internal val DIAGONAL = listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1)
internal val HORSE_JUMPS = listOf(1 to -2, 2 to -1, 2 to 1, 1 to 2, -1 to 2, -2 to 1, -2 to -1, -1 to -2)

internal fun marks(dirs: List<Pair<Int, Int>>, kind: MarkKind = MarkKind.STEP, dots: Int = 1) = dirs.map { MoveMark(it.first, it.second, kind, dots) }

/**
 * Draws [marks] around [center] at [radius] (inside the piece, around its character). The
 * marks are flipped when [flip] is set, for pieces whose forward is down the screen.
 */
fun DrawScope.drawMoveMarks(marks: List<MoveMark>, center: Offset, radius: Float, color: Color, flip: Boolean = false, squash: Float = 1f) {
    val dot = radius * 0.13f
    for (m in marks) {
        val dy = if (flip) -m.dy else m.dy
        val a = atan2(dy.toFloat(), m.dx.toFloat())
        val ux = cos(a)
        val uy = sin(a) * squash
        fun at(f: Float) = Offset(center.x + ux * radius * f, center.y + uy * radius * f)
        when (m.kind) {
            MarkKind.STEP -> if (m.dots >= 2) {
                drawCircle(color, dot * 0.85f, at(0.72f))
                drawCircle(color, dot, at(1f))
            } else drawCircle(color, dot, at(1f))
            MarkKind.LATER -> drawCircle(color, dot * 0.8f, at(1f), style = Stroke(dot * 0.45f))
            MarkKind.SLIDE, MarkKind.HOP -> {
                val tip = at(1.12f)
                val base = at(0.66f)
                drawLine(color, base, tip, dot * 0.75f, StrokeCap.Round)
                val px = -uy / hypot(ux, uy)
                val py = ux / hypot(ux, uy)
                val back = at(0.86f)
                val head = Path().apply {
                    moveTo(tip.x + ux * dot * 0.6f, tip.y + uy * dot * 0.6f)
                    lineTo(back.x + px * dot * 1.5f, back.y + py * dot * 1.5f)
                    lineTo(back.x - px * dot * 1.5f, back.y - py * dot * 1.5f)
                    close()
                }
                drawPath(head, color)
                if (m.kind == MarkKind.HOP) {
                    // The piece it jumps over: a bar across the arrow.
                    val mid = at(0.74f)
                    drawLine(color, Offset(mid.x + px * dot * 2.2f, mid.y + py * dot * 2.2f), Offset(mid.x - px * dot * 2.2f, mid.y - py * dot * 2.2f), dot * 0.8f, StrokeCap.Round)
                }
            }
        }
    }
}

/**
 * Round wooden disc with a character, as used in xiangqi and janggi. [ink] colors the character,
 * the inner ring and the movement marks; the computer's discs get a double ring so the two sides
 * differ by more than color.
 */
fun DrawScope.drawDisc(
    center: Offset,
    radius: Float,
    character: String,
    ink: Color,
    marks: List<MoveMark>,
    flipMarks: Boolean,
    doubleRing: Boolean,
    p: Palette,
    m: TextMeasurer,
    showMarks: Boolean = true,
) {
    val face = if (p.strongOutlines) Color(0xFFFFFBF0) else Color(0xFFF4E2BC)
    drawCircle(Color.Black.copy(alpha = 0.3f), radius, center + Offset(radius * 0.06f, radius * 0.12f))
    drawCircle(face.darker(0.35f), radius, center)
    drawCircle(Brush.radialGradient(listOf(face.lighter(0.4f), face, face.darker(0.1f)), center - Offset(radius * 0.3f, radius * 0.35f), radius * 1.4f), radius * 0.9f, center)
    val ring = if (p.strongOutlines) radius * 0.08f else radius * 0.05f
    drawCircle(ink, radius * 0.86f, center, style = Stroke(ring))
    if (doubleRing) drawCircle(ink, radius * 0.76f, center, style = Stroke(ring * 0.6f))
    text(m, character, center + Offset(0f, -radius * 0.04f), ink, pxToSp(radius * (if (showMarks) 0.74f else 1.0f)))
    if (showMarks) drawMoveMarks(marks, center, radius * 0.66f, ink.copy(alpha = 0.9f), flipMarks)
}

/** The outline of a shogi piece (a pentagon pointing forward) in a [w] × [h] box. */
private fun wedge(topLeft: Offset, w: Float, h: Float) = Path().apply {
    moveTo(topLeft.x + w * 0.5f, topLeft.y)
    lineTo(topLeft.x + w * 0.86f, topLeft.y + h * 0.2f)
    lineTo(topLeft.x + w * 0.96f, topLeft.y + h)
    lineTo(topLeft.x + w * 0.04f, topLeft.y + h)
    lineTo(topLeft.x + w * 0.14f, topLeft.y + h * 0.2f)
    close()
}

/**
 * Shogi piece: a wooden wedge pointing at the opponent, the character in the middle (red when
 * promoted) and the movement marks around it. The computer's pieces point down and are a shade
 * darker.
 */
fun DrawScope.drawWedge(
    center: Offset,
    size: Float,
    character: String,
    promoted: Boolean,
    marks: List<MoveMark>,
    upsideDown: Boolean,
    p: Palette,
    m: TextMeasurer,
) {
    val w = size * 0.84f
    val h = size * 0.94f
    val topLeft = Offset(center.x - w / 2, center.y - h / 2)
    val base = if (upsideDown) Color(0xFFDDB574) else Color(0xFFF0D39A)
    rotate(if (upsideDown) 180f else 0f, center) {
        val outline = wedge(topLeft, w, h)
        val shadow = wedge(topLeft + Offset(w * 0.05f, h * 0.05f), w, h)
        drawPath(shadow, Color.Black.copy(alpha = 0.3f))
        drawPath(outline, Brush.verticalGradient(listOf(base.lighter(0.25f), base, base.darker(0.12f)), topLeft.y, topLeft.y + h))
        drawPath(outline, if (p.strongOutlines) Color.Black else base.darker(0.45f), style = Stroke(size * (if (p.strongOutlines) 0.05f else 0.03f)))
        val ink = if (promoted) Color(0xFFC0261C) else Color(0xFF1D1A16)
        val textCenter = Offset(center.x, topLeft.y + h * 0.6f)
        text(m, character, textCenter, ink, pxToSp(h * 0.37f))
        drawMoveMarks(marks, textCenter, w * 0.37f, ink.copy(alpha = 0.85f), squash = 1.12f)
    }
}

/** Where things are on a game canvas: the board's points or squares plus a strip above and below. */
class BoardGeometry(
    val width: Float,
    val height: Float,
    val cols: Int,
    val rows: Int,
    /** True when pieces stand on line crossings (xiangqi, janggi), false for squares (shogi). */
    val points: Boolean,
    /** Height of each strip (captured pieces or hand) in cells. */
    val strip: Float,
) {
    val margin = if (points) 0.55f else 0.3f
    val cell = minOf(width / (cols + 2 * margin), height / (rows + 2 * margin + 2 * strip))
    val left = (width - cols * cell) / 2
    val top = (height - (rows + 2 * margin + 2 * strip) * cell) / 2 + (strip + margin) * cell

    fun center(sq: Int) = Offset(left + (sq % cols + 0.5f) * cell, top + (sq / cols + 0.5f) * cell)

    fun square(o: Offset): Int? {
        val c = kotlin.math.floor((o.x - left) / cell).toInt()
        val r = kotlin.math.floor((o.y - top) / cell).toInt()
        return if (c in 0 until cols && r in 0 until rows) r * cols + c else null
    }

    /** Vertical middle of the strip of [side] (0 = the player's, below the board). */
    fun stripY(side: Int) = if (side == 0) top + (rows + margin + strip / 2) * cell else top - (margin + strip / 2) * cell

    /** Which slot of [slots] in [side]'s strip [o] falls in, or null. */
    fun stripSlot(o: Offset, side: Int, slots: Int): Int? {
        if (slots == 0 || kotlin.math.abs(o.y - stripY(side)) > strip * cell * 0.6f) return null
        val i = kotlin.math.floor((o.x - left) / (cols * cell / slots)).toInt()
        return i.takeIf { it in 0 until slots }
    }

    fun slotCenter(side: Int, index: Int, slots: Int) = Offset(left + (index + 0.5f) * cols * cell / slots, stripY(side))

    companion object {
        /** Width / height of the canvas for these proportions. */
        fun aspect(cols: Int, rows: Int, points: Boolean, strip: Float): Float {
            val margin = if (points) 0.55f else 0.3f
            return (cols + 2 * margin) / (rows + 2 * margin + 2 * strip)
        }
    }
}

/** Wooden board background with a frame, shared by the three boards. */
fun DrawScope.drawWoodBoard(g: BoardGeometry, p: Palette) {
    val pad = g.margin * g.cell
    val tl = Offset(g.left - pad, g.top - pad)
    val sz = Size(g.cols * g.cell + 2 * pad, g.rows * g.cell + 2 * pad)
    val wood = if (p.isDark) p.wood else Color(0xFFE2B972)
    drawRect(Color.Black.copy(alpha = 0.25f), tl + Offset(g.cell * 0.06f, g.cell * 0.1f), sz)
    drawRect(Brush.linearGradient(listOf(wood.lighter(0.15f), wood, wood.darker(0.1f)), tl, tl + Offset(sz.width, sz.height)), tl, sz)
    drawRect(p.woodDark, tl, sz, style = Stroke(g.cell * 0.05f))
}

/** Color of the board lines: dark ink on light wood, light lines on the dark-mode wood. */
fun boardInk(p: Palette): Color = if (p.isDark) p.boardLine else Color(0xFF3A2614)

/**
 * The 9 × 10 board of xiangqi and janggi: lines through the points, the two palaces with their
 * diagonals and, for xiangqi, the river splitting the files in the middle.
 */
fun DrawScope.drawPalaceBoard(g: BoardGeometry, p: Palette, river: Boolean, m: TextMeasurer?) {
    drawWoodBoard(g, p)
    val ink = boardInk(p)
    val w = if (p.strongOutlines) g.cell * 0.05f else g.cell * 0.03f
    fun pt(r: Int, c: Int) = g.center(r * 9 + c)
    for (r in 0..9) drawLine(ink, pt(r, 0), pt(r, 8), w)
    for (c in 0..8) {
        if (river && c in 1..7) {
            drawLine(ink, pt(0, c), pt(4, c), w)
            drawLine(ink, pt(5, c), pt(9, c), w)
        } else drawLine(ink, pt(0, c), pt(9, c), w)
    }
    for (r0 in listOf(0, 7)) {
        drawLine(ink, pt(r0, 3), pt(r0 + 2, 5), w)
        drawLine(ink, pt(r0, 5), pt(r0 + 2, 3), w)
    }
    if (river) {
        val y = (pt(4, 0).y + pt(5, 0).y) / 2
        drawRect(Color(0xFF6FA8C8).copy(alpha = if (p.isDark) 0.25f else 0.3f), Offset(pt(4, 0).x + w, pt(4, 0).y + w), Size(pt(4, 8).x - pt(4, 0).x - 2 * w, g.cell - 2 * w))
        if (m != null) {
            text(m, "楚 河", Offset(pt(4, 2).x, y), ink.copy(alpha = 0.6f), pxToSp(g.cell * 0.45f), bold = false)
            text(m, "漢 界", Offset(pt(4, 6).x, y), ink.copy(alpha = 0.6f), pxToSp(g.cell * 0.45f), bold = false)
        }
    }
}

/** Highlights shared by the three boards: selection, legal targets, last move, check and hint. */
fun DrawScope.drawTargets(g: BoardGeometry, targets: Collection<Int>, occupied: (Int) -> Boolean, p: Palette) {
    for (t in targets) {
        val c = g.center(t)
        if (occupied(t)) {
            drawCircle(p.hint, g.cell * 0.47f, c, style = Stroke(g.cell * 0.09f))
        } else {
            drawCircle(Color.Black.copy(alpha = 0.45f), g.cell * 0.2f, c)
            drawCircle(p.hint, g.cell * 0.17f, c)
        }
    }
}

fun DrawScope.drawHintArrow(g: BoardGeometry, from: Offset, to: Offset, p: Palette) {
    val color = p.primary
    drawLine(color.copy(alpha = 0.8f), from, to, g.cell * 0.12f, StrokeCap.Round)
    drawCircle(color, g.cell * 0.5f, from, style = Stroke(g.cell * 0.08f))
    drawCircle(color, g.cell * 0.5f, to, style = Stroke(g.cell * 0.1f))
}
