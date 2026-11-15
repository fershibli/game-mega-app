package io.github.fershibli.megagames.games.arcade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlin.math.abs

/** The four directions of an [ArrowPad], as screen steps (dx, dy). */
enum class Arrow(val dx: Int, val dy: Int) { UP(0, -1), RIGHT(1, 0), DOWN(0, 1), LEFT(-1, 0) }

/**
 * Four big arrows in a cross (up is up and left is left in every language). [descriptions] are
 * the spoken names of the arrows in [Arrow] order; [tagPrefix] gives each one a test tag.
 */
@Composable
fun ArrowPad(descriptions: List<String>, onArrow: (Arrow) -> Unit, enabled: Boolean = true, tagPrefix: String? = null) {
    val b = Modifier.size(62.dp)
    @Composable
    fun pad(arrow: Arrow, icon: PadIcon) {
        val tagged = if (tagPrefix == null) b else b.testTag("$tagPrefix-${arrow.name.lowercase()}")
        PadButton(icon, descriptions[arrow.ordinal], { onArrow(arrow) }, tagged, enabled)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        pad(Arrow.UP, PadIcon.UP)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            pad(Arrow.LEFT, PadIcon.LEFT)
            Spacer(b)
            pad(Arrow.RIGHT, PadIcon.RIGHT)
        }
        pad(Arrow.DOWN, PadIcon.DOWN)
    }
}

/**
 * Calls [onArrow] once for every swipe on this element, in the direction the finger went
 * furthest, and [onTap] (with the element's size) for a touch that did not travel. A swipe counts once the finger has
 * travelled a little more than a tap would. Both are installed once, so they must read the
 * latest game state themselves.
 */
fun Modifier.swipes(onTap: ((Offset, IntSize) -> Unit)? = null, onArrow: (Arrow) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val limit = viewConfiguration.touchSlop * 2
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.isConsumed) break // a zoom gesture took over
            if (!change.pressed) {
                onTap?.invoke(change.position, size)
                break
            }
            val d = change.position - down.position
            if (d.getDistance() > limit) {
                onArrow(if (abs(d.x) > abs(d.y)) (if (d.x > 0) Arrow.RIGHT else Arrow.LEFT) else (if (d.y > 0) Arrow.DOWN else Arrow.UP))
                change.consume()
                break
            }
        }
    }
}
