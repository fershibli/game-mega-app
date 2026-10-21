package io.github.fershibli.megagames.ui.components

import androidx.compose.ui.platform.testTag
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.currentMotion
import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.spec
import io.github.fershibli.megagames.ui.theme.wait
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Who plays next when people take turns: a name ("Light pieces", "Player 2") and an optional color. */
data class Seat(val name: String, val color: Color? = null)

/** "Player [number]" (counting from 1), for games whose seats have no color of their own. */
@Composable
fun numberedSeat(number: Int, color: Color? = null) = Seat(stringResource(R.string.player_number, number), color)

/**
 * Passing the device between people taking turns. While [phase] is not [Phase.PLAYING] the game
 * must hide everything private to a player (cards in hand, ships, the tiles of a rack...).
 */
@Stable
class Handoff {
    enum class Phase { PLAYING, COVERED, SWITCHING }

    var phase by mutableStateOf(Phase.PLAYING)
        internal set
    var next by mutableStateOf<Seat?>(null)
        private set

    /** True when the current player's private things may be shown. */
    val showsPrivate: Boolean get() = phase == Phase.PLAYING

    /** The turn is over: cover the screen and ask to pass the device to [seat]. */
    fun passTo(seat: Seat) {
        next = seat
        phase = Phase.COVERED
    }
}

/**
 * The cover shown between turns: a big button with the two-hands picture in the middle, "Pass to
 * the next player" and who that is below it, and a faint pair of turning arrows behind. A tap dims
 * the scene, calls [onSwitch] (the game shows the next player's side of the table), waits a moment
 * (following the chosen pace) and then lets the next player's private things appear.
 */
@Composable
fun HandoffOverlay(handoff: Handoff, onSwitch: () -> Unit) {
    val p = LocalPalette.current
    val pace = currentPace
    val motion = currentMotion
    val cover by animateFloatAsState(
        when (handoff.phase) {
            Handoff.Phase.PLAYING -> 0f
            Handoff.Phase.COVERED -> 0.94f
            Handoff.Phase.SWITCHING -> 0.55f
        },
        motion.spec(350), label = "handoff",
    )
    LaunchedEffect(handoff.phase) {
        if (handoff.phase == Handoff.Phase.SWITCHING) {
            pace.wait(900)
            handoff.phase = Handoff.Phase.PLAYING
        }
    }
    if (cover == 0f && handoff.phase == Handoff.Phase.PLAYING) return
    val ready = stringResource(R.string.pass_ready)
    Box(
        Modifier
            .fillMaxSize()
            .testTag("handoff")
            .background(p.background.copy(alpha = cover))
            // Swallows touches so nothing underneath is played by accident.
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        if (handoff.phase != Handoff.Phase.COVERED) return@Box
        Canvas(Modifier.fillMaxSize()) { turningArrows(p.text) }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(
                Modifier
                    .size(132.dp)
                    .shadow(8.dp, CircleShape)
                    .background(p.primary, CircleShape)
                    .clickable(role = Role.Button) {
                        handoff.phase = Handoff.Phase.SWITCHING
                        onSwitch()
                    }
                    .semantics { contentDescription = ready },
                contentAlignment = Alignment.Center,
            ) { IconView(takeTurnsIcon(), p.onPrimary, size = 84.dp) }
            Text(
                stringResource(R.string.pass_to_next), Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.titleLarge, color = p.text, textAlign = TextAlign.Center,
            )
            handoff.next?.let { seat ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    seat.color?.let { color ->
                        Box(Modifier.size(30.dp).shadow(2.dp, CircleShape).background(color, CircleShape))
                    }
                    Text(seat.name, style = MaterialTheme.typography.headlineSmall, color = p.text)
                }
            }
        }
    }
}

/**
 * Two arrows chasing each other around an oval, each stopping about 10° short of the other's
 * start, so the loop never quite closes. Drawn faintly, as outline and shadow only.
 */
private fun DrawScope.turningArrows(ink: Color) {
    val rx = size.width * 0.42f
    val ry = size.width * 0.3f
    val center = Offset(size.width / 2, size.height / 2)
    val width = size.width * 0.035f
    fun point(deg: Float): Offset {
        val a = Math.toRadians(deg.toDouble())
        return Offset(center.x + (rx * cos(a)).toFloat(), center.y + (ry * sin(a)).toFloat())
    }
    fun arrow(start: Float, sweep: Float, color: Color, shift: Offset) {
        drawArc(color, start, sweep, false, center - Offset(rx, ry) + shift, Size(rx * 2, ry * 2), style = Stroke(width, cap = StrokeCap.Round))
        val tip = point(start + sweep) + shift
        val back = point(start + sweep - 6f) + shift
        val angle = atan2(tip.y - back.y, tip.x - back.x)
        val head = size.width * 0.06f
        val path = Path().apply {
            moveTo(tip.x + head * 0.5f * cos(angle), tip.y + head * 0.5f * sin(angle))
            lineTo(tip.x + head * cos(angle + 2.5f), tip.y + head * sin(angle + 2.5f))
            lineTo(tip.x + head * cos(angle - 2.5f), tip.y + head * sin(angle - 2.5f))
            close()
        }
        drawPath(path, color)
    }
    val shadow = Offset(width * 0.6f, width * 0.6f)
    for ((start, sweep) in listOf(-80f to 170f, 100f to 170f)) {
        arrow(start, sweep, Color.Black.copy(alpha = 0.05f), shadow)
        arrow(start, sweep, ink.copy(alpha = 0.09f), Offset.Zero)
    }
}
