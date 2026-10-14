package io.github.fershibli.megagames.ui.art

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.ui.theme.Palette

// ---------------------------------------------------------------------------------------------
// Shared drawing helpers. Everything is vector art drawn at runtime: no bitmaps are shipped, the
// art stays sharp at any zoom level and recolors itself for each accessibility palette.
// ---------------------------------------------------------------------------------------------

fun Color.lighter(f: Float) = lerp(this, Color.White, f)
fun Color.darker(f: Float) = lerp(this, Color.Black, f)

/** Soft drop shadow for a rounded rectangle. */
fun DrawScope.softShadow(topLeft: Offset, size: Size, radius: Float, depth: Float, alpha: Float = 0.28f) {
    for (i in 3 downTo 1) {
        val spread = depth * i / 3f
        drawRoundRect(
            Color.Black.copy(alpha = alpha / (i + 1)),
            topLeft + Offset(spread * 0.4f, spread),
            Size(size.width + spread * 0.3f, size.height + spread * 0.2f),
            CornerRadius(radius + spread * 0.3f),
        )
    }
}

/**
 * A raised "block" (mahjong tile, dice, button): darker side faces to the bottom-right and a top
 * face with a gentle highlight, which gives a convincing 3D look with very little drawing.
 */
fun DrawScope.raisedBlock(
    topLeft: Offset,
    size: Size,
    face: Color,
    side: Color,
    sideDark: Color,
    depth: Float,
    radius: Float,
    outline: Color? = null,
) {
    softShadow(topLeft + Offset(depth, depth), size, radius, depth * 1.4f)
    // Side layers (bottom/right thickness).
    val steps = 4
    for (i in steps downTo 1) {
        val d = depth * i / steps
        val c = lerp(side, sideDark, i / steps.toFloat())
        drawRoundRect(c, topLeft + Offset(d, d), size, CornerRadius(radius))
    }
    // Face with a soft top-left highlight.
    drawRoundRect(
        Brush.linearGradient(listOf(face.lighter(0.35f), face, face.darker(0.06f)), topLeft, topLeft + Offset(size.width, size.height)),
        topLeft, size, CornerRadius(radius),
    )
    if (outline != null) drawRoundRect(outline, topLeft, size, CornerRadius(radius), style = Stroke(width = size.minDimension * 0.03f))
}

/** Shiny round piece (checker, reversi disc, dot). */
fun DrawScope.glossyDisc(center: Offset, radius: Float, color: Color, rim: Color? = null, shadow: Boolean = true) {
    if (shadow) {
        drawCircle(Color.Black.copy(alpha = 0.25f), radius * 1.02f, center + Offset(radius * 0.08f, radius * 0.14f))
    }
    drawCircle(color.darker(0.25f), radius, center)
    drawCircle(
        Brush.radialGradient(listOf(color.lighter(0.35f), color, color.darker(0.12f)), center - Offset(radius * 0.3f, radius * 0.35f), radius * 1.3f),
        radius * 0.88f, center,
    )
    // Concentric ridge, like a real checker.
    drawCircle(color.darker(0.18f), radius * 0.6f, center, style = Stroke(radius * 0.07f))
    drawCircle(Color.White.copy(alpha = 0.22f), radius * 0.28f, center - Offset(radius * 0.32f, radius * 0.38f))
    if (rim != null) drawCircle(rim, radius, center, style = Stroke(radius * 0.09f))
}

// ----- Card suits ----------------------------------------------------------------------------

private fun unitPath(build: Path.() -> Unit) = Path().apply(build)

private val heartPath = unitPath {
    moveTo(0.5f, 0.95f)
    cubicTo(0.2f, 0.72f, 0.0f, 0.5f, 0.05f, 0.3f)
    cubicTo(0.1f, 0.06f, 0.42f, 0.0f, 0.5f, 0.25f)
    cubicTo(0.58f, 0.0f, 0.9f, 0.06f, 0.95f, 0.3f)
    cubicTo(1.0f, 0.5f, 0.8f, 0.72f, 0.5f, 0.95f)
    close()
}
private val diamondPath = unitPath { moveTo(0.5f, 0f); lineTo(0.88f, 0.5f); lineTo(0.5f, 1f); lineTo(0.12f, 0.5f); close() }
private val spadePath = unitPath {
    moveTo(0.5f, 0.02f)
    cubicTo(0.8f, 0.28f, 1.0f, 0.42f, 0.96f, 0.62f)
    cubicTo(0.92f, 0.84f, 0.64f, 0.88f, 0.54f, 0.7f)
    lineTo(0.64f, 0.98f); lineTo(0.36f, 0.98f); lineTo(0.46f, 0.7f)
    cubicTo(0.36f, 0.88f, 0.08f, 0.84f, 0.04f, 0.62f)
    cubicTo(0.0f, 0.42f, 0.2f, 0.28f, 0.5f, 0.02f)
    close()
}
private val clubPath = unitPath {
    addOval(Rect(Offset(0.5f, 0.27f), 0.21f))
    addOval(Rect(Offset(0.27f, 0.57f), 0.21f))
    addOval(Rect(Offset(0.73f, 0.57f), 0.21f))
    moveTo(0.5f, 0.45f); lineTo(0.64f, 0.98f); lineTo(0.36f, 0.98f); close()
}

fun suitColor(suit: Suit, p: Palette) = when (suit) {
    Suit.HEARTS -> p.suitHearts
    Suit.DIAMONDS -> p.suitDiamonds
    Suit.SPADES -> p.suitSpades
    Suit.CLUBS -> p.suitClubs
}

fun DrawScope.drawSuit(suit: Suit, center: Offset, size: Float, color: Color) {
    val path = when (suit) {
        Suit.HEARTS -> heartPath
        Suit.DIAMONDS -> diamondPath
        Suit.SPADES -> spadePath
        Suit.CLUBS -> clubPath
    }
    translate(center.x - size / 2, center.y - size / 2) {
        scale(size) { drawPath(path, color) }
    }
}

private inline fun DrawScope.scale(s: Float, block: DrawScope.() -> Unit) {
    drawContext.transform.scale(s, s, Offset.Zero)
    block()
    drawContext.transform.scale(1 / s, 1 / s, Offset.Zero)
}

// ----- Cards ---------------------------------------------------------------------------------

/** Draws centered text with [measurer]. */
fun DrawScope.text(
    measurer: TextMeasurer,
    value: String,
    center: Offset,
    color: Color,
    size: TextUnit,
    bold: Boolean = true,
) {
    val layout = measurer.measure(value, TextStyle(color = color, fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}

/** Converts a pixel height into a font size that renders at roughly that height. */
fun DrawScope.pxToSp(px: Float): TextUnit = (px / density / fontScale).sp

/**
 * Card designed for legibility: big rank in the top-left corner, big suit in the top-right corner
 * (both visible when cards overlap), and a large symbol in the middle.
 */
fun DrawScope.drawCard(
    card: Card,
    topLeft: Offset,
    size: Size,
    p: Palette,
    measurer: TextMeasurer,
    faceUp: Boolean = true,
    selected: Boolean = false,
    dimmed: Boolean = false,
    french: Boolean = false,
    shadow: Boolean = true,
    wildMark: Boolean = false,
) {
    val r = size.width * 0.1f
    if (shadow) softShadow(topLeft, size, r, size.width * 0.05f, alpha = 0.22f)
    if (!faceUp) {
        drawCardBack(topLeft, size, p)
        return
    }
    val face = if (selected) lerp(p.cardFace, p.highlight, 0.45f) else p.cardFace
    drawRoundRect(Brush.verticalGradient(listOf(face, face.darker(0.05f)), topLeft.y, topLeft.y + size.height), topLeft, size, CornerRadius(r))
    drawRoundRect(
        if (selected) p.primary else p.cardEdge, topLeft, size, CornerRadius(r),
        style = Stroke(if (selected) size.width * 0.06f else if (p.strongOutlines) size.width * 0.03f else size.width * 0.015f),
    )
    val color = suitColor(card.suit, p)
    val pad = size.width * 0.08f
    val header = size.width * 0.34f
    text(measurer, Card.rankLabel(card.rank, french), Offset(topLeft.x + pad + header * 0.42f, topLeft.y + pad + header * 0.5f), color, pxToSp(header * 0.82f))
    drawSuit(card.suit, Offset(topLeft.x + size.width - pad - header * 0.42f, topLeft.y + pad + header * 0.5f), header * 0.78f, color)
    val bodyCenter = Offset(topLeft.x + size.width / 2, topLeft.y + size.height * 0.62f)
    if (card.rank in 11..13) {
        text(measurer, Card.rankLabel(card.rank, french), bodyCenter - Offset(0f, size.width * 0.06f), color, pxToSp(size.width * 0.5f))
        drawSuit(card.suit, bodyCenter + Offset(0f, size.width * 0.33f), size.width * 0.22f, color)
        // Small crown for face cards.
        val cw = size.width * 0.3f
        val cy = bodyCenter.y - size.width * 0.42f
        val crown = Path().apply {
            moveTo(bodyCenter.x - cw / 2, cy + cw * 0.3f); lineTo(bodyCenter.x - cw / 2, cy)
            lineTo(bodyCenter.x - cw / 4, cy + cw * 0.18f); lineTo(bodyCenter.x, cy - cw * 0.05f)
            lineTo(bodyCenter.x + cw / 4, cy + cw * 0.18f); lineTo(bodyCenter.x + cw / 2, cy)
            lineTo(bodyCenter.x + cw / 2, cy + cw * 0.3f); close()
        }
        drawPath(crown, Color(0xFFD4A21F))
    } else {
        drawSuit(card.suit, bodyCenter, size.width * 0.55f, color)
    }
    if (wildMark) {
        drawCircle(Color(0xFFD4A21F), size.width * 0.1f, Offset(topLeft.x + size.width * 0.16f, topLeft.y + size.height - size.width * 0.16f))
        text(measurer, "★", Offset(topLeft.x + size.width * 0.16f, topLeft.y + size.height - size.width * 0.16f), Color.White, pxToSp(size.width * 0.13f))
    }
    if (dimmed) drawRoundRect(Color.Black.copy(alpha = 0.3f), topLeft, size, CornerRadius(r))
}

fun DrawScope.drawCardBack(topLeft: Offset, size: Size, p: Palette) {
    val r = size.width * 0.1f
    drawRoundRect(p.cardFace, topLeft, size, CornerRadius(r))
    val inset = size.width * 0.07f
    val inner = Rect(topLeft + Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2))
    drawRoundRect(Brush.verticalGradient(listOf(p.cardBack.lighter(0.15f), p.cardBack.darker(0.1f)), inner.top, inner.bottom), inner.topLeft, inner.size, CornerRadius(r * 0.6f))
    val clip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(inner, CornerRadius(r * 0.6f))) }
    clipPath(clip) {
        val step = size.width * 0.18f
        var x = inner.left - inner.height
        while (x < inner.right) {
            drawLine(p.cardBackAccent.copy(alpha = 0.5f), Offset(x, inner.top), Offset(x + inner.height, inner.bottom), size.width * 0.02f)
            drawLine(p.cardBackAccent.copy(alpha = 0.5f), Offset(x + inner.height, inner.top), Offset(x, inner.bottom), size.width * 0.02f)
            x += step
        }
    }
    rotate(45f, inner.center) {
        drawRect(p.cardBackAccent, inner.center - Offset(size.width * 0.12f, size.width * 0.12f), Size(size.width * 0.24f, size.width * 0.24f))
    }
    drawRoundRect(p.cardEdge, topLeft, size, CornerRadius(r), style = Stroke(size.width * 0.015f))
}

/** Empty card slot (foundation, empty column...). */
fun DrawScope.drawCardSlot(topLeft: Offset, size: Size, color: Color, highlighted: Boolean = false) {
    val r = size.width * 0.1f
    drawRoundRect(color.copy(alpha = if (highlighted) 0.45f else 0.18f), topLeft, size, CornerRadius(r))
    drawRoundRect(color.copy(alpha = 0.6f), topLeft, size, CornerRadius(r), style = Stroke(size.width * 0.025f))
}

/** Small marker shapes, used together with colors in the accessible palettes. */
fun DrawScope.drawSymbol(index: Int, center: Offset, size: Float, color: Color) {
    val h = size / 2
    when (index % 9) {
        0 -> drawCircle(color, h * 0.8f, center)
        1 -> drawRect(color, center - Offset(h * 0.75f, h * 0.75f), Size(h * 1.5f, h * 1.5f))
        2 -> drawPath(Path().apply { moveTo(center.x, center.y - h); lineTo(center.x + h, center.y + h * 0.8f); lineTo(center.x - h, center.y + h * 0.8f); close() }, color)
        3 -> drawPath(Path().apply { moveTo(center.x, center.y - h); lineTo(center.x + h, center.y); lineTo(center.x, center.y + h); lineTo(center.x - h, center.y); close() }, color)
        4 -> {
            drawRect(color, center - Offset(h, h * 0.3f), Size(h * 2, h * 0.6f))
            drawRect(color, center - Offset(h * 0.3f, h), Size(h * 0.6f, h * 2))
        }
        5 -> drawCircle(color, h * 0.8f, center, style = Stroke(h * 0.35f))
        6 -> drawPath(Path().apply { moveTo(center.x, center.y + h); lineTo(center.x + h, center.y - h * 0.8f); lineTo(center.x - h, center.y - h * 0.8f); close() }, color)
        7 -> drawRect(color, center - Offset(h * 0.75f, h * 0.75f), Size(h * 1.5f, h * 1.5f), style = Stroke(h * 0.35f))
        else -> {
            val path = Path()
            for (i in 0 until 10) {
                val a = Math.PI * 2 * i / 10 - Math.PI / 2
                val rr = if (i % 2 == 0) h else h * 0.45f
                val x = center.x + (rr * kotlin.math.cos(a)).toFloat()
                val y = center.y + (rr * kotlin.math.sin(a)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, color)
        }
    }
}
