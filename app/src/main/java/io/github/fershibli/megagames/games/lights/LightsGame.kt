package io.github.fershibli.megagames.games.lights

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.LocalSettings
import io.github.fershibli.megagames.ui.theme.Palette
import io.github.fershibli.megagames.ui.theme.currentMotion
import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.spec
import io.github.fershibli.megagames.ui.theme.wait
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** Pause on the dark grid before the end of the game. */
private const val DONE_PAUSE_MS = 1500L

/** A lit lamp: warm yellow in every palette (an unlit lamp is dark grey, so they never look alike). */
private val LAMP_ON = Color(0xFFFFD54F)
private val LAMP_OFF = Color(0xFF55524D)

@Composable
fun LightsGame(host: GameHost) {
    LoadGame(host, LightsState.serializer(), { Lights.newGame(host.difficulty) }) { state -> LightsScreen(state, host) }
}

@Composable
private fun LightsScreen(state: MutableState<LightsState>, host: GameHost) {
    val s = state.value
    val n = s.size
    val p = LocalPalette.current
    val pace = currentPace
    val motion = currentMotion
    val scale = LocalSettings.current.pieceScale
    val resources = LocalResources.current
    // How lit each lamp looks (0..1), so lamps fade on and off instead of blinking.
    val glow = remember(n) { List(n * n) { Animatable(if (s.on[it]) 1f else 0f) } }
    LaunchedEffect(s.on) {
        s.on.forEachIndexed { i, lit -> launch { glow[i].animateTo(if (lit) 1f else 0f, motion.spec(260)) } }
    }

    LaunchedEffect(s.done) {
        if (!s.done) return@LaunchedEffect
        pace.wait(DONE_PAUSE_MS)
        val detail = buildString {
            append(resources.getQuantityString(R.plurals.lights_done_moves, s.moves, s.moves))
            if (s.hints > 0) append(' ').append(resources.getString(R.string.lights_hints_used, s.hints))
        }
        host.finish(GameOutcome(true, detail))
    }

    GameLayout(
        status = {
            val msg = when {
                s.done -> stringResource(R.string.lights_done)
                s.hint >= 0 -> stringResource(R.string.lights_hint_tap)
                else -> stringResource(R.string.lights_goal)
            }
            StatusPill(stringResource(R.string.lights_moves, s.moves) + "\n" + msg, emphasized = s.done)
        },
        controls = {
            val playing = !s.done && !host.finished
            ActionBar {
                BigButton(stringResource(R.string.undo), { state.value = Lights.undo(state.value) }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = playing && s.presses.isNotEmpty(), compact = true)
                BigButton(stringResource(R.string.hint), { state.value = Lights.hint(state.value) }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = playing, compact = true)
                BigButton(stringResource(R.string.lights_restart), { state.value = Lights.restart(state.value) }, icon = AppIcon.NEW_GAME, style = ButtonStyle.SECONDARY, enabled = playing && s.presses.isNotEmpty(), compact = true)
            }
        },
    ) {
        val description = stringResource(R.string.lights_board, n, n)
        BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            Canvas(
                Modifier
                    .size(side)
                    .semantics { contentDescription = description }
                    .testTag("lights-board")
                    .pointerInput(n) {
                        detectTapGestures { pos ->
                            val cell = size.width / n.toFloat()
                            val c = (pos.x / cell).toInt().coerceIn(0, n - 1)
                            val r = (pos.y / cell).toInt().coerceIn(0, n - 1)
                            state.value = Lights.press(state.value, r * n + c)
                        }
                    },
            ) {
                drawLights(p, n, s.hint, scale) { glow[it].value }
            }
        }
    }
}

/**
 * Draws the panel of round lamps. [lit] says how lit each lamp is (0 = off, 1 = on); lit lamps
 * have a halo and short rays, so they differ by shape too, not only by color.
 */
fun DrawScope.drawLights(p: Palette, n: Int, hint: Int, scale: Float, lit: (Int) -> Float) {
    val cell = size.width / n
    drawRoundRect(p.surfaceAlt, Offset.Zero, size, CornerRadius(cell * 0.3f))
    drawRoundRect(p.outline, Offset.Zero, size, CornerRadius(cell * 0.3f), style = Stroke(if (p.strongOutlines) cell * 0.05f else cell * 0.025f))
    val radius = cell * 0.34f * scale.coerceIn(0.85f, 1.2f)
    for (i in 0 until n * n) {
        val c = Offset((i % n + 0.5f) * cell, (i / n + 0.5f) * cell)
        val g = lit(i)
        if (g > 0f) {
            drawCircle(LAMP_ON.copy(alpha = 0.35f * g), radius * 1.38f, c)
            // Rays: eight short strokes around the lamp.
            for (k in 0 until 8) {
                val a = k * Math.PI / 4
                val dx = cos(a).toFloat()
                val dy = sin(a).toFloat()
                drawLine(LAMP_ON.darker(0.25f).copy(alpha = g), c + Offset(dx, dy) * radius * 1.12f, c + Offset(dx, dy) * radius * 1.4f, cell * 0.035f, StrokeCap.Round)
            }
        }
        drawCircle(Color.Black.copy(alpha = 0.22f), radius, c + Offset(cell * 0.02f, cell * 0.04f))
        drawCircle(lerp(LAMP_OFF, LAMP_ON, g), radius, c)
        // A soft shine on the glass.
        drawCircle(Color.White.copy(alpha = 0.18f + 0.3f * g), radius * 0.3f, c - Offset(radius * 0.35f, radius * 0.38f))
        drawCircle(if (p.strongOutlines) p.boardLine else LAMP_OFF.darker(0.35f), radius, c, style = Stroke(cell * if (p.strongOutlines) 0.04f else 0.025f))
    }
    if (hint >= 0) {
        val c = Offset((hint % n + 0.5f) * cell, (hint / n + 0.5f) * cell)
        drawCircle(p.hint, radius * 1.32f, c, style = Stroke(cell * 0.07f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 0.1f, cell * 0.07f))))
    }
}

/** Home-screen picture: a 3×3 panel with a plus of lit lamps. */
fun lightsThumbnail(scope: DrawScope, p: Palette) = scope.drawLights(p, 3, -1, 1f) { if (THUMBNAIL_LIT[it]) 1f else 0f }

private val THUMBNAIL_LIT = booleanArrayOf(false, true, false, true, true, true, false, true, false)
