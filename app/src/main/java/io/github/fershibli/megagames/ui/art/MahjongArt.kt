package io.github.fershibli.megagames.ui.art

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import io.github.fershibli.megagames.ui.theme.Palette

private val CHINESE_NUMERALS = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九")
private val WINDS = listOf("東", "南", "西", "北")
private val WIND_LETTERS = listOf("E", "S", "W", "N")
private val FLOWERS = listOf("梅", "蘭", "菊", "竹")
private val SEASONS = listOf("春", "夏", "秋", "冬")

// Classic tile ink colors (kept constant: they sit on the ivory face in every palette).
private val INK_RED = Color(0xFFC0392B)
private val INK_GREEN = Color(0xFF1E7B45)
private val INK_BLUE = Color(0xFF1F4E9C)
private val INK_DARK = Color(0xFF1D2433)

/** Relative positions (0..1) of the pips for 1 to 9, used by dots and bamboo. */
private val PIPS: List<List<Offset>> = listOf(
    listOf(Offset(0.5f, 0.5f)),
    listOf(Offset(0.5f, 0.27f), Offset(0.5f, 0.73f)),
    listOf(Offset(0.22f, 0.2f), Offset(0.5f, 0.5f), Offset(0.78f, 0.8f)),
    listOf(Offset(0.28f, 0.27f), Offset(0.72f, 0.27f), Offset(0.28f, 0.73f), Offset(0.72f, 0.73f)),
    listOf(Offset(0.25f, 0.2f), Offset(0.75f, 0.2f), Offset(0.5f, 0.5f), Offset(0.25f, 0.8f), Offset(0.75f, 0.8f)),
    listOf(Offset(0.28f, 0.18f), Offset(0.72f, 0.18f), Offset(0.28f, 0.5f), Offset(0.72f, 0.5f), Offset(0.28f, 0.82f), Offset(0.72f, 0.82f)),
    listOf(Offset(0.2f, 0.14f), Offset(0.5f, 0.24f), Offset(0.8f, 0.34f), Offset(0.3f, 0.6f), Offset(0.7f, 0.6f), Offset(0.3f, 0.86f), Offset(0.7f, 0.86f)),
    listOf(Offset(0.28f, 0.14f), Offset(0.72f, 0.14f), Offset(0.28f, 0.38f), Offset(0.72f, 0.38f), Offset(0.28f, 0.62f), Offset(0.72f, 0.62f), Offset(0.28f, 0.86f), Offset(0.72f, 0.86f)),
    listOf(Offset(0.2f, 0.18f), Offset(0.5f, 0.18f), Offset(0.8f, 0.18f), Offset(0.2f, 0.5f), Offset(0.5f, 0.5f), Offset(0.8f, 0.5f), Offset(0.2f, 0.82f), Offset(0.5f, 0.82f), Offset(0.8f, 0.82f)),
)

/**
 * A mahjong tile with real depth: a thick green/ivory body, a bevelled face and the picture.
 * A small Western index (1-9, E/S/W/N) in the corner helps players who do not read Chinese.
 */
fun DrawScope.drawMahjongTile(
    face: Int,
    topLeft: Offset,
    size: Size,
    depth: Float,
    p: Palette,
    measurer: TextMeasurer,
    selected: Boolean = false,
    dimmed: Boolean = false,
    hinted: Boolean = false,
) {
    val r = size.width * 0.14f
    raisedBlock(topLeft, size, p.tileFace, p.tileSide, p.tileSideDark, depth, r, outline = if (p.strongOutlines) Color.Black else null)
    // Inner bevel line.
    val inset = size.width * 0.06f
    drawRoundRect(Color.Black.copy(alpha = 0.08f), topLeft + Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), CornerRadius(r * 0.7f), style = Stroke(size.width * 0.02f))
    val art = Rect(topLeft + Offset(size.width * 0.12f, size.height * 0.12f), Size(size.width * 0.76f, size.height * 0.76f))
    drawMahjongFace(face, art, p, measurer)
    if (selected) drawRoundRect(p.highlight.copy(alpha = 0.55f), topLeft, size, CornerRadius(r))
    if (hinted) drawRoundRect(p.hint, topLeft, size, CornerRadius(r), style = Stroke(size.width * 0.1f))
    if (selected) drawRoundRect(p.primary, topLeft, size, CornerRadius(r), style = Stroke(size.width * 0.08f))
    if (dimmed) drawRoundRect(Color.Black.copy(alpha = if (p.isDark) 0.38f else 0.24f), topLeft, size, CornerRadius(r))
}

fun DrawScope.drawMahjongFace(face: Int, art: Rect, p: Palette, measurer: TextMeasurer) {
    val w = art.width
    val h = art.height
    fun at(o: Offset) = Offset(art.left + o.x * w, art.top + o.y * h)
    val dotColors = if (p.mode == io.github.fershibli.megagames.core.ColorMode.COLORBLIND) listOf(p.pieces[4], p.pieces[2], p.pieces[5]) else listOf(INK_BLUE, INK_GREEN, INK_RED)
    when {
        face < 9 -> { // Dots
            val n = face + 1
            val pips = PIPS[face]
            val rad = if (n == 1) w * 0.36f else if (n <= 4) w * 0.17f else if (n <= 6) w * 0.15f else w * 0.12f
            pips.forEachIndexed { i, o ->
                val c = at(o)
                val col = dotColors[(i + n) % 3]
                drawCircle(col, rad, c)
                drawCircle(p.tileFace, rad * 0.62f, c)
                drawCircle(col, rad * 0.38f, c)
                if (n == 1) drawCircle(INK_RED, rad * 0.16f, c)
            }
            index(measurer, n.toString(), art, INK_BLUE)
        }
        face < 18 -> { // Bamboo
            val n = face - 8
            if (n == 1) {
                // The "one bamboo" is traditionally a bird; we draw a stylised bird on a stalk.
                val c = at(Offset(0.5f, 0.5f))
                drawOval(INK_GREEN, c - Offset(w * 0.22f, h * 0.18f), Size(w * 0.44f, h * 0.36f))
                drawCircle(INK_RED, w * 0.12f, c + Offset(w * 0.16f, -h * 0.2f))
                drawCircle(Color.White, w * 0.035f, c + Offset(w * 0.19f, -h * 0.22f))
                drawLine(INK_RED, c + Offset(w * 0.24f, -h * 0.2f), c + Offset(w * 0.36f, -h * 0.17f), w * 0.05f)
                drawLine(INK_GREEN, c + Offset(-w * 0.18f, h * 0.05f), c + Offset(-w * 0.38f, h * 0.28f), w * 0.07f)
                drawLine(INK_DARK, c + Offset(0f, h * 0.18f), c + Offset(0f, h * 0.36f), w * 0.04f)
            } else {
                val pips = PIPS[n - 1]
                val stickH = if (n <= 3) h * 0.36f else if (n <= 6) h * 0.27f else h * 0.2f
                val stickW = w * 0.11f
                pips.forEachIndexed { i, o ->
                    val c = at(o)
                    val red = (n == 5 && i == 2) || (n == 9 && i % 3 == 1) || (n == 7 && i == 0)
                    val col = if (red) INK_RED else INK_GREEN
                    drawRoundRect(col, c - Offset(stickW / 2, stickH / 2), Size(stickW, stickH), CornerRadius(stickW / 2))
                    drawLine(p.tileFace, c - Offset(stickW / 2, 0f), c + Offset(stickW / 2, 0f), stickW * 0.25f)
                    drawLine(col.darker(0.3f), c - Offset(0f, stickH * 0.4f), c + Offset(0f, stickH * 0.4f), stickW * 0.15f)
                }
            }
            index(measurer, n.toString(), art, INK_GREEN)
        }
        face < 27 -> { // Characters
            val n = face - 17
            text(measurer, CHINESE_NUMERALS[n - 1], at(Offset(0.5f, 0.3f)), INK_DARK, pxToSp(h * 0.36f))
            text(measurer, "萬", at(Offset(0.5f, 0.72f)), INK_RED, pxToSp(h * 0.38f))
            index(measurer, n.toString(), art, INK_RED)
        }
        face < 31 -> { // Winds
            val k = face - 27
            text(measurer, WINDS[k], at(Offset(0.5f, 0.55f)), INK_DARK, pxToSp(h * 0.55f))
            index(measurer, WIND_LETTERS[k], art, INK_DARK)
        }
        face == 31 -> text(measurer, "中", art.center, INK_RED, pxToSp(h * 0.62f))
        face == 32 -> text(measurer, "發", art.center, INK_GREEN, pxToSp(h * 0.62f))
        face == 33 -> {
            drawRoundRect(INK_BLUE, art.topLeft + Offset(w * 0.08f, h * 0.08f), Size(w * 0.84f, h * 0.84f), CornerRadius(w * 0.08f), style = Stroke(w * 0.08f))
            drawRoundRect(INK_BLUE, art.topLeft + Offset(w * 0.24f, h * 0.22f), Size(w * 0.52f, h * 0.56f), CornerRadius(w * 0.05f), style = Stroke(w * 0.04f))
        }
        else -> { // Flowers and seasons
            val flower = face < 38
            val k = if (flower) face - 34 else face - 38
            val petal = listOf(INK_RED, Color(0xFF8E44AD), Color(0xFFE67E22), INK_GREEN)[k]
            val c = at(Offset(0.5f, 0.32f))
            if (flower) {
                for (a in 0 until 5) {
                    val ang = Math.toRadians(a * 72.0 - 90)
                    drawCircle(petal, w * 0.11f, c + Offset((kotlin.math.cos(ang) * w * 0.13f).toFloat(), (kotlin.math.sin(ang) * w * 0.13f).toFloat()))
                }
                drawCircle(Color(0xFFF1C40F), w * 0.08f, c)
            } else {
                drawCircle(petal, w * 0.2f, c)
                drawCircle(p.tileFace, w * 0.12f, c)
                drawCircle(petal, w * 0.06f, c)
            }
            text(measurer, (if (flower) FLOWERS else SEASONS)[k], at(Offset(0.5f, 0.76f)), petal.darker(0.2f), pxToSp(h * 0.36f))
        }
    }
}

private fun DrawScope.index(measurer: TextMeasurer, label: String, art: Rect, color: Color) {
    text(measurer, label, Offset(art.left + art.width * 0.02f, art.top + art.height * 0.02f), color.copy(alpha = 0.85f), pxToSp(art.height * 0.17f))
}
