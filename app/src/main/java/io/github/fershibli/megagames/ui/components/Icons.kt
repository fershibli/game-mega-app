package io.github.fershibli.megagames.ui.components

import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Icons are drawn in code (no icon font, no image files) to keep the app tiny. */
enum class AppIcon { GEAR, BACK, UNDO, HINT, CLOSE, CHECK, HOME, NEW_GAME, ZOOM_RESET, PENCIL, ERASE, SHUFFLE, DICE, PLUS, MINUS, NEXT, PREV, ROTATE, INFO }

private val DIRECTIONAL = setOf(AppIcon.BACK, AppIcon.NEXT, AppIcon.PREV, AppIcon.ERASE)

@Composable
fun IconView(icon: AppIcon, tint: Color, modifier: Modifier = Modifier, size: Dp = 28.dp, description: String? = null) {
    val m = if (description != null) modifier.semantics { contentDescription = description } else modifier
    // Arrows point the way the text reads.
    val mirror = icon in DIRECTIONAL && LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(m.size(size)) { scale(if (mirror) -1f else 1f, 1f) { drawAppIcon(icon, tint) } }
}

fun DrawScope.drawAppIcon(icon: AppIcon, tint: Color) {
    val s = min(size.width, size.height)
    val c = center
    val stroke = Stroke(width = s * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (icon) {
        AppIcon.GEAR -> {
            val teeth = 8
            val outer = s * 0.48f
            val inner = s * 0.36f
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                val steps = teeth * 4
                for (i in 0..steps) {
                    val a = 2 * PI * i / steps - PI / 2
                    val r = if ((i % 4) == 1 || (i % 4) == 2) outer else inner
                    val p = Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat())
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
                addOval(Rect(c, s * 0.15f))
            }
            drawPath(path, tint)
        }
        AppIcon.BACK, AppIcon.PREV -> arrow(tint, stroke, left = true)
        AppIcon.NEXT -> arrow(tint, stroke, left = false)
        AppIcon.UNDO -> {
            drawArc(tint, 200f, 250f, false, Offset(s * 0.2f, s * 0.22f), Size(s * 0.62f, s * 0.62f), style = stroke)
            val head = Path().apply {
                moveTo(s * 0.08f, s * 0.38f); lineTo(s * 0.26f, s * 0.62f); lineTo(s * 0.42f, s * 0.36f); close()
            }
            drawPath(head, tint)
        }
        AppIcon.NEW_GAME -> {
            drawArc(tint, -60f, 290f, false, Offset(s * 0.18f, s * 0.18f), Size(s * 0.64f, s * 0.64f), style = stroke)
            val head = Path().apply {
                moveTo(s * 0.58f, s * 0.06f); lineTo(s * 0.86f, s * 0.22f); lineTo(s * 0.6f, s * 0.38f); close()
            }
            drawPath(head, tint)
        }
        AppIcon.HINT -> {
            drawCircle(tint, s * 0.27f, Offset(c.x, s * 0.38f))
            drawRoundRect(tint, Offset(s * 0.36f, s * 0.6f), Size(s * 0.28f, s * 0.14f), CornerRadius(s * 0.04f))
            drawRoundRect(tint, Offset(s * 0.39f, s * 0.78f), Size(s * 0.22f, s * 0.1f), CornerRadius(s * 0.05f))
        }
        AppIcon.INFO -> {
            drawCircle(tint, s * 0.42f, c, style = Stroke(s * 0.08f))
            drawCircle(tint, s * 0.07f, Offset(c.x, s * 0.3f))
            drawRoundRect(tint, Offset(c.x - s * 0.055f, s * 0.43f), Size(s * 0.11f, s * 0.32f), CornerRadius(s * 0.05f))
        }
        AppIcon.CLOSE -> {
            drawLine(tint, Offset(s * 0.2f, s * 0.2f), Offset(s * 0.8f, s * 0.8f), stroke.width, StrokeCap.Round)
            drawLine(tint, Offset(s * 0.8f, s * 0.2f), Offset(s * 0.2f, s * 0.8f), stroke.width, StrokeCap.Round)
        }
        AppIcon.CHECK -> {
            val p = Path().apply { moveTo(s * 0.15f, s * 0.52f); lineTo(s * 0.4f, s * 0.76f); lineTo(s * 0.86f, s * 0.24f) }
            drawPath(p, tint, style = stroke)
        }
        AppIcon.HOME -> {
            val p = Path().apply {
                moveTo(s * 0.5f, s * 0.1f); lineTo(s * 0.92f, s * 0.48f); lineTo(s * 0.8f, s * 0.48f)
                lineTo(s * 0.8f, s * 0.9f); lineTo(s * 0.58f, s * 0.9f); lineTo(s * 0.58f, s * 0.64f)
                lineTo(s * 0.42f, s * 0.64f); lineTo(s * 0.42f, s * 0.9f); lineTo(s * 0.2f, s * 0.9f)
                lineTo(s * 0.2f, s * 0.48f); lineTo(s * 0.08f, s * 0.48f); close()
            }
            drawPath(p, tint)
        }
        AppIcon.ZOOM_RESET -> {
            val w = stroke.width
            val l = s * 0.28f
            val a = s * 0.12f
            val b = s * 0.88f
            listOf(Offset(a, a) to Offset(1f, 1f), Offset(b, a) to Offset(-1f, 1f), Offset(a, b) to Offset(1f, -1f), Offset(b, b) to Offset(-1f, -1f))
                .forEach { (o, d) ->
                    drawLine(tint, o, Offset(o.x + d.x * l, o.y), w, StrokeCap.Round)
                    drawLine(tint, o, Offset(o.x, o.y + d.y * l), w, StrokeCap.Round)
                }
            drawCircle(tint, s * 0.1f, c)
        }
        AppIcon.PENCIL -> rotate(45f, c) {
            drawRoundRect(tint, Offset(s * 0.38f, s * 0.08f), Size(s * 0.24f, s * 0.6f), CornerRadius(s * 0.04f))
            val tip = Path().apply { moveTo(s * 0.38f, s * 0.72f); lineTo(s * 0.62f, s * 0.72f); lineTo(s * 0.5f, s * 0.94f); close() }
            drawPath(tip, tint)
        }
        AppIcon.ERASE -> {
            val p = Path().apply {
                moveTo(s * 0.06f, s * 0.5f); lineTo(s * 0.3f, s * 0.2f); lineTo(s * 0.92f, s * 0.2f)
                lineTo(s * 0.92f, s * 0.8f); lineTo(s * 0.3f, s * 0.8f); close()
            }
            drawPath(p, tint, style = Stroke(s * 0.08f, join = StrokeJoin.Round))
            drawLine(tint, Offset(s * 0.46f, s * 0.36f), Offset(s * 0.74f, s * 0.64f), s * 0.08f, StrokeCap.Round)
            drawLine(tint, Offset(s * 0.74f, s * 0.36f), Offset(s * 0.46f, s * 0.64f), s * 0.08f, StrokeCap.Round)
        }
        AppIcon.SHUFFLE -> {
            drawLine(tint, Offset(s * 0.1f, s * 0.28f), Offset(s * 0.78f, s * 0.72f), stroke.width, StrokeCap.Round)
            drawLine(tint, Offset(s * 0.1f, s * 0.72f), Offset(s * 0.78f, s * 0.28f), stroke.width, StrokeCap.Round)
            arrowHead(tint, Offset(s * 0.92f, s * 0.28f), right = true, s)
            arrowHead(tint, Offset(s * 0.92f, s * 0.72f), right = true, s)
        }
        AppIcon.DICE -> {
            drawRoundRect(tint, Offset(s * 0.1f, s * 0.1f), Size(s * 0.8f, s * 0.8f), CornerRadius(s * 0.16f), style = Stroke(s * 0.08f))
            listOf(Offset(0.3f, 0.3f), Offset(0.5f, 0.5f), Offset(0.7f, 0.7f)).forEach { drawCircle(tint, s * 0.08f, Offset(it.x * s, it.y * s)) }
        }
        AppIcon.ROTATE -> {
            // A board standing up, a quarter-turn arrow, and the same board lying down.
            drawRoundRect(tint, Offset(s * 0.08f, s * 0.08f), Size(s * 0.3f, s * 0.46f), CornerRadius(s * 0.05f), style = Stroke(s * 0.07f))
            drawRoundRect(tint, Offset(s * 0.46f, s * 0.62f), Size(s * 0.46f, s * 0.3f), CornerRadius(s * 0.05f), style = Stroke(s * 0.07f))
            val radius = s * 0.34f
            val pivot = Offset(s * 0.36f, s * 0.47f)
            drawArc(tint, 285f, 75f, false, pivot - Offset(radius, radius), Size(radius * 2, radius * 2), style = stroke)
            val tip = pivot + Offset(radius, 0f)
            val head = Path().apply {
                moveTo(tip.x, tip.y + s * 0.13f)
                lineTo(tip.x - s * 0.13f, tip.y - s * 0.02f)
                lineTo(tip.x + s * 0.13f, tip.y - s * 0.02f)
                close()
            }
            drawPath(head, tint)
        }
        AppIcon.PLUS -> {
            drawLine(tint, Offset(s * 0.5f, s * 0.15f), Offset(s * 0.5f, s * 0.85f), stroke.width, StrokeCap.Round)
            drawLine(tint, Offset(s * 0.15f, s * 0.5f), Offset(s * 0.85f, s * 0.5f), stroke.width, StrokeCap.Round)
        }
        AppIcon.MINUS -> drawLine(tint, Offset(s * 0.15f, s * 0.5f), Offset(s * 0.85f, s * 0.5f), stroke.width, StrokeCap.Round)
    }
}

private fun DrawScope.arrow(tint: Color, stroke: Stroke, left: Boolean) {
    val s = min(size.width, size.height)
    val (from, to) = if (left) s * 0.85f to s * 0.18f else s * 0.15f to s * 0.82f
    drawLine(tint, Offset(from, s * 0.5f), Offset(to, s * 0.5f), stroke.width, StrokeCap.Round)
    val dir = if (left) 1f else -1f
    val p = Path().apply {
        moveTo(to + dir * s * 0.28f, s * 0.22f); lineTo(to, s * 0.5f); lineTo(to + dir * s * 0.28f, s * 0.78f)
    }
    drawPath(p, tint, style = stroke)
}

private fun DrawScope.arrowHead(tint: Color, tip: Offset, right: Boolean, s: Float) {
    val d = if (right) -1f else 1f
    val p = Path().apply {
        moveTo(tip.x, tip.y); lineTo(tip.x + d * s * 0.2f, tip.y - s * 0.13f); lineTo(tip.x + d * s * 0.2f, tip.y + s * 0.13f); close()
    }
    drawPath(p, tint)
}
