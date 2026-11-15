package io.github.fershibli.megagames.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.ui.theme.LocalPalette

/**
 * What a lesson and the game it is played on tell each other (see [LocalTutorial]).
 *
 * The game says where its places are ([resolver] for places drawn on its board, [tutorialSpot]
 * for its buttons) and how to play one itself ([perform]); it asks [allows] before answering a
 * touch and calls [completed] after it. The lesson says which place may be touched ([expected]).
 */
class TutorialGuide {
    /** The place the player may touch now; `null`: the game doesn't answer touches. */
    var expected by mutableStateOf<String?>(null)

    /** True once the player touched [expected]. */
    var done by mutableStateOf(false)
        private set

    /** Touches somewhere else while a place was [expected]: after a couple, the hand shows where. */
    var misses by mutableIntStateOf(0)
        private set

    /** Where a place drawn on the game's board is, in window coordinates. */
    var resolver: ((String) -> Rect?)? = null

    /** Plays the touch of a place, as if the player had touched it. */
    var perform: ((String) -> Unit)? = null

    internal val spots = mutableStateMapOf<String, Rect>()

    fun rect(place: String): Rect? = spots[place] ?: resolver?.invoke(place)

    /** Asked by the game before it answers a touch of [place] (`null`: nowhere in particular). */
    fun allows(place: String?): Boolean {
        if (place != null && place == expected) return true
        if (expected != null) misses++
        return false
    }

    /** Told by the game after it answered the touch that [allows] let through. */
    fun completed() {
        expected = null
        done = true
    }

    /** A new step starts: nothing may be touched until it says so. */
    fun reset() {
        expected = null
        done = false
        misses = 0
    }
}

/** The lesson being played on this game, or `null` in a normal game. */
val LocalTutorial = staticCompositionLocalOf<TutorialGuide?> { null }

/** Names a button (or any other composable) as a place of the lesson; does nothing in a normal game. */
fun Modifier.tutorialSpot(place: String): Modifier = composed {
    val guide = LocalTutorial.current
    if (guide == null) this else onGloballyPositioned { guide.spots[place] = it.boundsInWindow() }
}

/** A rectangle of a board, in its own coordinates, as the window sees it (zoom included). */
fun LayoutCoordinates.toWindow(rect: Rect): Rect {
    val a = localToWindow(rect.topLeft)
    val b = localToWindow(rect.bottomRight)
    return Rect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))
}

/**
 * Drawn over the game during a lesson: a ring around [place] and, when [hand] is above zero, a
 * hand on its way there (1 = arrived); [press] makes the finger touch. It lets every touch through.
 */
@Composable
fun TutorialOverlay(guide: TutorialGuide, place: String?, hand: Float, press: Boolean, pulse: Float, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    var origin by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var target by remember { mutableStateOf<Rect?>(null) }
    // Cards and pieces move while the step is on screen: follow the place frame by frame.
    LaunchedEffect(place) {
        target = null
        while (place != null) {
            withFrameNanos { }
            val o = origin?.takeIf { it.isAttached }
            target = if (o == null) null else guide.rect(place)?.let { Rect(o.windowToLocal(it.topLeft), o.windowToLocal(it.bottomRight)) }
        }
    }
    Canvas(modifier.onGloballyPositioned { origin = it }) {
        val r = target ?: return@Canvas
        val grow = 3.dp.toPx() + pulse * 5.dp.toPx()
        val radius = CornerRadius(10.dp.toPx())
        drawRoundRect(Color.Black.copy(alpha = 0.35f), r.topLeft - Offset(grow, grow), Size(r.width + grow * 2, r.height + grow * 2), radius, style = Stroke(7.dp.toPx()))
        drawRoundRect(p.hint, r.topLeft - Offset(grow, grow), Size(r.width + grow * 2, r.height + grow * 2), radius, style = Stroke(4.dp.toPx()))
        if (hand > 0f) {
            // The hand comes from the lower part of the board, the side the thumb would come from.
            val from = Offset(r.center.x + 70.dp.toPx(), r.center.y + 150.dp.toPx())
            val tip = from + (r.center - from) * hand
            if (press) drawCircle(p.hint.copy(alpha = 0.45f), 26.dp.toPx(), tip)
            drawHand(tip, if (press) 0.9f else 1f)
        }
    }
}

/** A pointing hand whose fingertip is at [tip]. */
private fun DrawScope.drawHand(tip: Offset, scale: Float) {
    val u = 9.dp.toPx() * scale
    val ink = Color(0xFF2B2622)
    val skin = Color(0xFFFFFBF2)
    rotate(-20f, tip) {
        fun part(topLeft: Offset, size: Size, corner: Float) {
            drawRoundRect(skin, topLeft, size, CornerRadius(corner))
            drawRoundRect(ink, topLeft, size, CornerRadius(corner), style = Stroke(u * 0.3f))
        }
        // Palm with three folded fingers, then the pointing finger over it.
        part(Offset(tip.x - u * 1.1f, tip.y + u * 3.6f), Size(u * 5.2f, u * 4.6f), u * 1.6f)
        part(Offset(tip.x - u * 1.1f, tip.y - u * 0.2f), Size(u * 2.2f, u * 6.2f), u * 1.1f)
        drawRoundRect(skin, Offset(tip.x - u * 0.8f, tip.y + u * 3.4f), Size(u * 1.6f, u * 2.4f), CornerRadius(u * 0.4f))
    }
}
