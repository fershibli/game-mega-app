package io.github.fershibli.megagames.games.farm

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.theme.Palette

// Colors of the farm: the same in every palette, so a tomato is always red and a carrot orange.
private val SOIL = Color(0xFF8D6E4F)
private val SOIL_WET = Color(0xFF5D4632)
private val LEAF = Color(0xFF5FA845)
private val LEAF_DARK = Color(0xFF3E7D2C)
private val GRASS = Color(0xFF9CCC65)
private val BARN_RED = Color(0xFFC0392B)
private val WATER = Color(0xFF4FA3E0)

private val ITEM_COLOR = mapOf(
    Item.LETTUCE to Color(0xFF8BC34A),
    Item.CARROT to Color(0xFFF57C00),
    Item.CORN to Color(0xFFFBC02D),
    Item.TOMATO to Color(0xFFE53935),
    Item.STRAWBERRY to Color(0xFFD81B60),
    Item.PUMPKIN to Color(0xFFEF6C00),
    Item.EGG to Color(0xFFFFF3E0),
    Item.MILK to Color(0xFFFFFFFF),
    Item.WOOL to Color(0xFFF5F5F5),
)

private fun outline(p: Palette) = if (p.strongOutlines) p.boardLine else Color.Black.copy(alpha = 0.45f)

/** One farm product, drawn in a circle of radius [r] around [c]. Each has its own shape. */
fun DrawScope.drawItem(item: Item, c: Offset, r: Float, p: Palette) {
    val color = ITEM_COLOR.getValue(item)
    val line = Stroke(r * if (p.strongOutlines) 0.12f else 0.07f)
    val edge = outline(p)
    when (item) {
        Item.LETTUCE -> {
            for (k in 0 until 5) {
                val a = Math.toRadians(-90.0 + k * 72.0)
                val o = c + Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat()) * r * 0.38f
                drawCircle(color.darker(0.1f * (k % 2)), r * 0.5f, o)
            }
            drawCircle(color.lighter(0.35f), r * 0.38f, c)
            drawCircle(edge, r * 0.9f, c, style = line)
        }
        Item.CARROT -> {
            val body = Path().apply {
                moveTo(c.x - r * 0.42f, c.y - r * 0.45f); lineTo(c.x + r * 0.42f, c.y - r * 0.45f); lineTo(c.x, c.y + r); close()
            }
            drawPath(body, color); drawPath(body, edge, style = line)
            for (k in -1..1) drawLine(LEAF, Offset(c.x, c.y - r * 0.45f), Offset(c.x + k * r * 0.35f, c.y - r), r * 0.16f, StrokeCap.Round)
        }
        Item.CORN -> {
            drawOval(color, Offset(c.x - r * 0.32f, c.y - r * 0.9f), Size(r * 0.64f, r * 1.7f))
            for (row in 0 until 4) drawLine(color.darker(0.25f), Offset(c.x - r * 0.28f, c.y - r * 0.5f + row * r * 0.35f), Offset(c.x + r * 0.28f, c.y - r * 0.5f + row * r * 0.35f), r * 0.05f)
            drawOval(edge, Offset(c.x - r * 0.32f, c.y - r * 0.9f), Size(r * 0.64f, r * 1.7f), style = line)
            drawLine(LEAF, Offset(c.x, c.y + r * 0.8f), Offset(c.x - r * 0.6f, c.y - r * 0.2f), r * 0.18f, StrokeCap.Round)
            drawLine(LEAF, Offset(c.x, c.y + r * 0.8f), Offset(c.x + r * 0.6f, c.y - r * 0.1f), r * 0.18f, StrokeCap.Round)
        }
        Item.TOMATO -> {
            drawCircle(color, r * 0.8f, c + Offset(0f, r * 0.1f))
            drawCircle(Color.White.copy(alpha = 0.35f), r * 0.2f, c + Offset(-r * 0.3f, -r * 0.15f))
            drawCircle(edge, r * 0.8f, c + Offset(0f, r * 0.1f), style = line)
            for (k in -1..1) drawLine(LEAF_DARK, Offset(c.x, c.y - r * 0.6f), Offset(c.x + k * r * 0.4f, c.y - r * 0.75f), r * 0.14f, StrokeCap.Round)
        }
        Item.STRAWBERRY -> {
            val body = Path().apply {
                moveTo(c.x - r * 0.75f, c.y - r * 0.35f)
                quadraticTo(c.x, c.y - r * 0.75f, c.x + r * 0.75f, c.y - r * 0.35f)
                quadraticTo(c.x + r * 0.6f, c.y + r * 0.5f, c.x, c.y + r * 0.95f)
                quadraticTo(c.x - r * 0.6f, c.y + r * 0.5f, c.x - r * 0.75f, c.y - r * 0.35f)
                close()
            }
            drawPath(body, color); drawPath(body, edge, style = line)
            for ((dx, dy) in listOf(-0.3f to 0f, 0.3f to 0f, 0f to 0.3f, -0.15f to 0.55f, 0.15f to 0.55f, 0f to -0.25f)) {
                drawCircle(Color(0xFFFFF59D), r * 0.07f, c + Offset(dx * r, dy * r))
            }
            drawLine(LEAF_DARK, Offset(c.x - r * 0.4f, c.y - r * 0.55f), Offset(c.x + r * 0.4f, c.y - r * 0.55f), r * 0.2f, StrokeCap.Round)
        }
        Item.PUMPKIN -> {
            for (dx in listOf(-0.4f, 0.4f, 0f)) drawOval(if (dx == 0f) color.lighter(0.1f) else color, Offset(c.x + dx * r - r * 0.45f, c.y - r * 0.6f), Size(r * 0.9f, r * 1.35f))
            drawOval(edge, Offset(c.x - r * 0.88f, c.y - r * 0.6f), Size(r * 1.76f, r * 1.35f), style = line)
            drawLine(LEAF_DARK, Offset(c.x, c.y - r * 0.55f), Offset(c.x + r * 0.15f, c.y - r * 0.95f), r * 0.18f, StrokeCap.Round)
        }
        Item.EGG -> {
            drawOval(color, Offset(c.x - r * 0.6f, c.y - r * 0.85f), Size(r * 1.2f, r * 1.65f))
            drawOval(edge, Offset(c.x - r * 0.6f, c.y - r * 0.85f), Size(r * 1.2f, r * 1.65f), style = line)
            drawCircle(Color.White.copy(alpha = 0.7f), r * 0.15f, c + Offset(-r * 0.22f, -r * 0.35f))
        }
        Item.MILK -> {
            val top = c.y - r * 0.9f
            drawRoundRect(color, Offset(c.x - r * 0.5f, c.y - r * 0.4f), Size(r, r * 1.3f), CornerRadius(r * 0.15f))
            drawRect(WATER, Offset(c.x - r * 0.5f, c.y + r * 0.05f), Size(r, r * 0.35f))
            val roof = Path().apply { moveTo(c.x - r * 0.5f, c.y - r * 0.4f); lineTo(c.x, top); lineTo(c.x + r * 0.5f, c.y - r * 0.4f); close() }
            drawPath(roof, Color(0xFFE0E0E0))
            drawPath(roof, edge, style = line)
            drawRoundRect(edge, Offset(c.x - r * 0.5f, c.y - r * 0.4f), Size(r, r * 1.3f), CornerRadius(r * 0.15f), style = line)
        }
        Item.WOOL -> {
            for ((dx, dy) in listOf(-0.4f to 0.1f, 0.4f to 0.1f, 0f to -0.3f, 0f to 0.35f, -0.25f to -0.1f, 0.25f to -0.1f)) {
                drawCircle(color, r * 0.42f, c + Offset(dx * r, dy * r))
                drawCircle(edge.copy(alpha = 0.25f), r * 0.42f, c + Offset(dx * r, dy * r), style = Stroke(r * 0.05f))
            }
        }
    }
}

/**
 * One bed of the field in a [size] square: brown soil (darker once watered), then a sprout, a
 * young plant, or the ripe crop. A bed that can be watered shows a small drop.
 */
fun DrawScope.drawPlot(plot: Plot, stage: Stage, waterable: Boolean, p: Palette) {
    val w = size.width
    val corner = CornerRadius(w * 0.12f)
    drawRoundRect(GRASS.darker(if (p.isDark) 0.35f else 0f), Offset.Zero, size, corner)
    val soilTop = Offset(w * 0.08f, w * 0.08f)
    val soilSize = Size(w * 0.84f, w * 0.84f)
    drawRoundRect(if (plot.wateredAt != null) SOIL_WET else SOIL, soilTop, soilSize, CornerRadius(w * 0.08f))
    // Furrows.
    for (k in 1..3) drawLine(Color.Black.copy(alpha = 0.18f), Offset(w * 0.14f, w * (0.08f + 0.21f * k)), Offset(w * 0.86f, w * (0.08f + 0.21f * k)), w * 0.02f)
    val crop = plot.sown
    val c = Offset(w / 2, w / 2)
    when {
        crop == null -> {
            // An empty bed: a big plus, so it reads as "sow here".
            drawLine(Color.White.copy(alpha = 0.75f), Offset(w * 0.35f, c.y), Offset(w * 0.65f, c.y), w * 0.06f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.75f), Offset(c.x, w * 0.35f), Offset(c.x, w * 0.65f), w * 0.06f, StrokeCap.Round)
        }
        stage == Stage.SPROUT -> {
            drawLine(LEAF, Offset(c.x, w * 0.68f), Offset(c.x, w * 0.5f), w * 0.04f, StrokeCap.Round)
            drawOval(LEAF, Offset(c.x - w * 0.14f, w * 0.42f), Size(w * 0.14f, w * 0.08f))
            drawOval(LEAF, Offset(c.x, w * 0.42f), Size(w * 0.14f, w * 0.08f))
        }
        stage == Stage.GROWING -> {
            drawLine(LEAF_DARK, Offset(c.x, w * 0.75f), Offset(c.x, w * 0.35f), w * 0.05f, StrokeCap.Round)
            for (k in 0 until 3) {
                val y = w * (0.4f + k * 0.11f)
                drawOval(LEAF, Offset(c.x - w * 0.24f, y), Size(w * 0.22f, w * 0.1f))
                drawOval(LEAF, Offset(c.x + w * 0.02f, y), Size(w * 0.22f, w * 0.1f))
            }
            drawItem(crop.item, Offset(c.x, w * 0.3f), w * 0.1f, p)
        }
        else -> {
            drawCircle(Color(0xFFFFF59D).copy(alpha = 0.55f), w * 0.36f, c)
            drawItem(crop.item, c, w * 0.27f, p)
        }
    }
    if (waterable) {
        val d = Offset(w * 0.82f, w * 0.2f)
        val drop = Path().apply {
            moveTo(d.x, d.y - w * 0.1f)
            quadraticTo(d.x + w * 0.09f, d.y + w * 0.02f, d.x, d.y + w * 0.07f)
            quadraticTo(d.x - w * 0.09f, d.y + w * 0.02f, d.x, d.y - w * 0.1f)
            close()
        }
        drawPath(drop, WATER)
        drawPath(drop, Color.White, style = Stroke(w * 0.015f))
    }
    drawRoundRect(outline(p), Offset.Zero, size, corner, style = Stroke(w * if (p.strongOutlines) 0.04f else 0.02f))
}

/** A farm animal standing on grass, filling a [size] box. */
fun DrawScope.drawAnimal(animal: Animal, p: Palette) {
    val w = size.width
    val h = size.height
    val s = minOf(w, h)
    val c = Offset(w / 2, h * 0.55f)
    val edge = outline(p)
    val line = Stroke(s * 0.02f)
    drawOval(GRASS.darker(if (p.isDark) 0.35f else 0f), Offset(c.x - s * 0.42f, h * 0.78f), Size(s * 0.84f, s * 0.14f))
    when (animal) {
        Animal.CHICKEN -> {
            val body = Color(0xFFFFFFFF)
            drawLine(Color(0xFFFFA000), c + Offset(-s * 0.06f, s * 0.12f), c + Offset(-s * 0.06f, s * 0.26f), s * 0.03f)
            drawLine(Color(0xFFFFA000), c + Offset(s * 0.06f, s * 0.12f), c + Offset(s * 0.06f, s * 0.26f), s * 0.03f)
            drawOval(body, Offset(c.x - s * 0.24f, c.y - s * 0.12f), Size(s * 0.44f, s * 0.3f))
            drawOval(edge, Offset(c.x - s * 0.24f, c.y - s * 0.12f), Size(s * 0.44f, s * 0.3f), style = line)
            val head = c + Offset(s * 0.16f, -s * 0.2f)
            drawCircle(body, s * 0.12f, head)
            drawCircle(edge, s * 0.12f, head, style = line)
            drawCircle(BARN_RED, s * 0.05f, head + Offset(0f, -s * 0.12f))
            drawCircle(Color.Black, s * 0.02f, head + Offset(s * 0.03f, -s * 0.02f))
            val beak = Path().apply { moveTo(head.x + s * 0.1f, head.y - s * 0.02f); lineTo(head.x + s * 0.2f, head.y + s * 0.02f); lineTo(head.x + s * 0.1f, head.y + s * 0.05f); close() }
            drawPath(beak, Color(0xFFFFA000))
        }
        Animal.COW -> {
            val body = Color(0xFFFAFAFA)
            for (dx in listOf(-0.2f, -0.08f, 0.1f, 0.22f)) drawLine(body.darker(0.2f), c + Offset(s * dx, s * 0.05f), c + Offset(s * dx, s * 0.26f), s * 0.05f, StrokeCap.Round)
            drawRoundRect(body, Offset(c.x - s * 0.3f, c.y - s * 0.16f), Size(s * 0.6f, s * 0.3f), CornerRadius(s * 0.12f))
            drawCircle(Color(0xFF3E2723), s * 0.07f, c + Offset(-s * 0.1f, -s * 0.04f))
            drawCircle(Color(0xFF3E2723), s * 0.05f, c + Offset(s * 0.12f, s * 0.04f))
            drawRoundRect(edge, Offset(c.x - s * 0.3f, c.y - s * 0.16f), Size(s * 0.6f, s * 0.3f), CornerRadius(s * 0.12f), style = line)
            val head = c + Offset(s * 0.32f, -s * 0.18f)
            drawOval(body, Offset(head.x - s * 0.12f, head.y - s * 0.12f), Size(s * 0.24f, s * 0.26f))
            drawOval(Color(0xFFF8BBD0), Offset(head.x - s * 0.1f, head.y + s * 0.03f), Size(s * 0.2f, s * 0.1f))
            drawOval(edge, Offset(head.x - s * 0.12f, head.y - s * 0.12f), Size(s * 0.24f, s * 0.26f), style = line)
            drawCircle(Color.Black, s * 0.02f, head + Offset(-s * 0.04f, -s * 0.03f))
            drawCircle(Color.Black, s * 0.02f, head + Offset(s * 0.05f, -s * 0.03f))
            drawLine(Color(0xFFBCAAA4), head + Offset(-s * 0.08f, -s * 0.1f), head + Offset(-s * 0.13f, -s * 0.17f), s * 0.03f, StrokeCap.Round)
            drawLine(Color(0xFFBCAAA4), head + Offset(s * 0.08f, -s * 0.1f), head + Offset(s * 0.13f, -s * 0.17f), s * 0.03f, StrokeCap.Round)
        }
        Animal.SHEEP -> {
            val face = Color(0xFF424242)
            for (dx in listOf(-0.15f, -0.05f, 0.08f, 0.18f)) drawLine(face, c + Offset(s * dx, s * 0.05f), c + Offset(s * dx, s * 0.25f), s * 0.035f, StrokeCap.Round)
            for ((dx, dy) in listOf(-0.18f to 0f, 0f to -0.08f, 0.16f to 0f, -0.06f to 0.06f, 0.08f to 0.06f, -0.1f to -0.08f, 0.1f to -0.1f)) {
                drawCircle(Color(0xFFF5F5F5), s * 0.11f, c + Offset(s * dx, s * dy))
                drawCircle(edge.copy(alpha = 0.3f), s * 0.11f, c + Offset(s * dx, s * dy), style = Stroke(s * 0.01f))
            }
            val head = c + Offset(s * 0.28f, -s * 0.12f)
            drawOval(face, Offset(head.x - s * 0.08f, head.y - s * 0.1f), Size(s * 0.16f, s * 0.2f))
            drawCircle(Color.White, s * 0.02f, head + Offset(-s * 0.03f, -s * 0.03f))
            drawCircle(Color.White, s * 0.02f, head + Offset(s * 0.03f, -s * 0.03f))
        }
    }
}

/** Home-screen picture: a red barn next to a field with ripe crops. */
fun farmThumbnail(scope: DrawScope, p: Palette) = with(scope) {
    val w = size.width
    val h = size.height
    drawRect(Color(0xFFB3E5FC).darker(if (p.isDark) 0.45f else 0f), Offset.Zero, Size(w, h * 0.5f))
    drawRect(GRASS.darker(if (p.isDark) 0.35f else 0f), Offset(0f, h * 0.5f), Size(w, h * 0.5f))
    drawCircle(Color(0xFFFFD54F), w * 0.08f, Offset(w * 0.85f, h * 0.16f))
    // The barn.
    val bx = w * 0.06f
    val by = h * 0.3f
    val bw = w * 0.4f
    val bh = h * 0.4f
    val roof = Path().apply { moveTo(bx - w * 0.02f, by); lineTo(bx + bw / 2, by - h * 0.18f); lineTo(bx + bw + w * 0.02f, by); close() }
    drawPath(roof, BARN_RED.darker(0.3f))
    drawRect(BARN_RED, Offset(bx, by), Size(bw, bh))
    drawRect(Color.White, Offset(bx + bw * 0.3f, by + bh * 0.4f), Size(bw * 0.4f, bh * 0.6f), style = Stroke(w * 0.015f))
    drawLine(Color.White, Offset(bx + bw * 0.3f, by + bh * 0.4f), Offset(bx + bw * 0.7f, by + bh), w * 0.015f)
    drawLine(Color.White, Offset(bx + bw * 0.7f, by + bh * 0.4f), Offset(bx + bw * 0.3f, by + bh), w * 0.015f)
    drawRect(outline(p), Offset(bx, by), Size(bw, bh), style = Stroke(w * 0.012f))
    // Two rows of crops.
    val items = listOf(Item.TOMATO, Item.CARROT, Item.CORN, Item.PUMPKIN)
    for (row in 0 until 2) {
        drawRoundRect(SOIL, Offset(w * 0.52f, h * (0.56f + row * 0.2f)), Size(w * 0.44f, h * 0.15f), CornerRadius(w * 0.03f))
        for (k in 0 until 2) drawItem(items[row * 2 + k], Offset(w * (0.63f + k * 0.22f), h * (0.63f + row * 0.2f)), w * 0.075f, p)
    }
    drawRoundRect(outline(p), Offset.Zero, size, CornerRadius(w * 0.06f), style = Stroke(w * 0.01f))
}
