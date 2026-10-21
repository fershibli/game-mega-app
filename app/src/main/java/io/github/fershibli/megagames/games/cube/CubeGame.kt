package io.github.fershibli.megagames.games.cube

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.RoundIconButton
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Length of a layer or whole-cube turn animation. The app has no motion setting yet; when it
 * gets one, this is the single place to read it from (0 turns the animation off).
 */
private const val TURN_MS = 220

/** Order of the face buttons: top, left, front, right, back, bottom. */
private val FaceOrder = intArrayOf(Cube.U, Cube.L, Cube.F, Cube.R, Cube.B, Cube.D)

@Composable
fun CubeGame(host: GameHost) {
    val n = host.variant?.key?.toIntOrNull()?.coerceIn(2, 5) ?: 3
    LoadGame(host, CubeState.serializer(), { newCube(n, host.difficulty, Random.Default) }) { state -> CubeScreen(state, host) }
}

/**
 * How the cube is held: free yaw/pitch from dragging, times a whole-cube [orientation] changed in
 * quarter turns by the arrow buttons (so any face can be brought into view), times the part of a
 * quarter turn still being animated.
 */
@Stable
private class CubeCamera {
    var yaw by mutableFloatStateOf(-38f)
    var pitch by mutableFloatStateOf(26f)
    var orientation by mutableStateOf(intArrayOf(1, 0, 0, 0, 1, 0, 0, 0, 1))
    var spinAxis by mutableIntStateOf(1)
    val spin = Animatable(0f)
    private val a = FloatArray(9)
    private val b = FloatArray(9)
    private val c = FloatArray(9)

    fun drag(delta: Offset, density: Float) {
        yaw += delta.x / density * 0.5f
        pitch = (pitch + delta.y / density * 0.5f).coerceIn(-80f, 80f)
    }

    /** A quarter turn of the whole cube: around the vertical axis, or the one closest to horizontal on screen. */
    fun quarter(vertical: Boolean, sign: Int): Pair<Int, Int> {
        var axis = 1
        var s = sign
        if (!vertical) {
            val r = Math.toRadians(yaw.toDouble())
            if (abs(cos(r)) >= abs(sin(r))) { axis = 0; if (cos(r) < 0) s = -s } else { axis = 2; if (sin(r) < 0) s = -s }
        }
        val q = IntArray(9)
        rotation(axis, 90f * s, a)
        for (i in 0 until 9) q[i] = Math.round(a[i])
        val o = orientation
        orientation = IntArray(9) { k -> (0 until 3).sumOf { t -> q[(k / 3) * 3 + t] * o[t * 3 + k % 3] } }
        spinAxis = axis
        return axis to s
    }

    /** View matrix into [out]: pitch × yaw × (rest of the whole-cube turn) × orientation. */
    fun matrix(out: FloatArray) {
        rotation(0, pitch, a)
        rotation(1, yaw, b)
        multiply(a, b, c)
        rotation(spinAxis, spin.value, a)
        multiply(c, a, b)
        val o = orientation
        for (i in 0 until 9) a[i] = o[i].toFloat()
        multiply(b, a, out)
    }
}

@Composable
private fun CubeScreen(state: MutableState<CubeState>, host: GameHost) {
    val s = state.value
    val n = s.size
    val p = LocalPalette.current
    val colors = cubeColors(p)
    val solved = remember(s.stickers) { s.solved }
    val renderer = remember { CubeRenderer() }
    val camera = remember { CubeCamera() }
    val turnAnim = remember { Animatable(0f) }
    var turning by remember { mutableStateOf<CubeMove?>(null) }
    var selected by remember { mutableIntStateOf(Cube.F) }
    var depth by remember { mutableIntStateOf(0) }
    var hint by remember { mutableStateOf<CubeHint?>(null) }
    var thinking by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val movesText = stringResource(R.string.cube_moves, s.history.size)

    LaunchedEffect(solved) {
        if (solved && !finishing) {
            finishing = true
            delay(1200)
            host.finish(GameOutcome(true, movesText))
        }
    }

    fun animate(move: CubeMove) {
        turning = move
        scope.launch {
            turnAnim.snapTo(1f)
            turnAnim.animateTo(0f, tween(TURN_MS))
        }
    }

    fun play(move: CubeMove) {
        if (solved || thinking) return
        state.value = state.value.turn(move)
        hint = null
        animate(move)
    }

    fun spinCube(vertical: Boolean, sign: Int) {
        val (_, s2) = camera.quarter(vertical, sign)
        scope.launch {
            camera.spin.snapTo(-90f * s2)
            camera.spin.animateTo(0f, tween(TURN_MS))
        }
    }

    val onSwipe by rememberUpdatedState { sticker: Int, delta: Offset -> renderer.swipe(sticker, delta)?.let { play(it) } }
    val stateSize by rememberUpdatedState { state.value.size }
    val onTap by rememberUpdatedState { sticker: Int -> selected = CubeGeometry.of(n).face[sticker] }

    val status = when {
        solved -> stringResource(R.string.cube_solved)
        hint != null && hint!!.stage >= 0 -> {
            val stages = CubeSolver.stages(n)
            stringResource(R.string.cube_hint_step, stages.indexOf(hint!!.stage) + 1, stages.size, stringResource(stageName(hint!!.stage)))
        }
        hint != null -> stringResource(R.string.cube_hint_back, hint!!.left - 1)
        else -> movesText
    }

    GameLayout(
        status = { StatusPill(status, emphasized = solved) },
        controls = {
            // Face buttons: a small picture of each face as it is now.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FaceOrder.forEachIndexed { k, f ->
                    FaceButton(
                        s.stickers, n, f, colors, p.symbols, selected == f, stringResource(R.string.cube_face, k + 1),
                        { selected = f }, Modifier.weight(1f),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PadButton(stringResource(R.string.cube_turn_ccw), { play(Cube.faceTurn(selected, depth, false, n)) }, Modifier.weight(1f).testTag("cube-ccw")) { turnArrow(it, clockwise = false) }
                PadButton(stringResource(R.string.cube_turn_cw), { play(Cube.faceTurn(selected, depth, true, n)) }, Modifier.weight(1f).testTag("cube-cw")) { turnArrow(it, clockwise = true) }
                if (n >= 4) {
                    BigButton(
                        stringResource(R.string.cube_layer, depth + 1), { depth = (depth + 1) % ((n + 1) / 2) },
                        Modifier.weight(1f), style = ButtonStyle.SECONDARY, compact = true,
                    )
                }
            }
            ActionBar {
                BigButton(stringResource(R.string.undo), {
                    val last = state.value.history.lastOrNull()
                    state.value.undo()?.let {
                        state.value = it
                        hint = null
                        animate(last!!.inverse())
                    }
                }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = s.history.isNotEmpty() && !solved && !thinking)
                BigButton(stringResource(R.string.hint), {
                    val before = state.value
                    thinking = true
                    scope.launch {
                        val next = withContext(Dispatchers.Default) { nextHint(before) }
                        thinking = false
                        if (next != null && state.value == before) {
                            val (planned, h) = next
                            state.value = planned.turn(h.move, byHint = true)
                            hint = h
                            // Show which face the hint turned.
                            if (h.move.layer == 0 || h.move.layer == n - 1) {
                                selected = Cube.NORMAL.indexOfFirst { it[h.move.axis] == (if (h.move.layer == 0) -1 else 1) }
                            }
                            animate(h.move)
                        }
                    }
                }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = !solved && !thinking)
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .testTag("cube-board")
                    .pointerInput(Unit) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            camera.matrix(renderer.view)
                            renderer.frame(size.width.toFloat(), size.height.toFloat(), stateSize())
                            val hit = renderer.hit(down.position)
                            var last = down.position
                            var total = Offset.Zero
                            var fired = false
                            var rotating = false
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } > 1) break // two fingers: zoom
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (!fired && !rotating && hit >= 0) onTap(hit)
                                    break
                                }
                                val delta = change.position - last
                                last = change.position
                                total += delta
                                if (hit >= 0) {
                                    if (!fired && total.getDistance() > slop * 1.5f) {
                                        onSwipe(hit, total)
                                        fired = true
                                    }
                                } else if (rotating || total.getDistance() > slop) {
                                    rotating = true
                                    camera.drag(delta, density)
                                }
                                if (fired || rotating) change.consume()
                            }
                        }
                    },
            ) {
                camera.matrix(renderer.view)
                val move = turning
                val angle = if (move == null) 0f else -move.dir * 90f * turnAnim.value
                renderer.draw(
                    this, s.stickers, n, colors, p.symbols, p.strongOutlines,
                    turnAxis = move?.axis ?: -1, turnLayer = move?.layer ?: 0, turnDeg = angle,
                    selected = if (solved) -1 else selected, selectedColor = p.highlight,
                )
            }
            // Big arrows turn the whole cube a quarter turn, to see the hidden faces.
            val arrow = 52.dp
            RoundIconButton(AppIcon.PREV, stringResource(R.string.cube_view_left), { spinCube(true, -1) }, Modifier.align(AbsoluteAlignment.CenterLeft).padding(6.dp), size = arrow)
            RoundIconButton(AppIcon.NEXT, stringResource(R.string.cube_view_right), { spinCube(true, 1) }, Modifier.align(AbsoluteAlignment.CenterRight).padding(6.dp), size = arrow)
            RoundIconButton(AppIcon.NEXT, stringResource(R.string.cube_view_up), { spinCube(false, -1) }, Modifier.align(Alignment.TopCenter).padding(6.dp).rotate(-90f), size = arrow)
            RoundIconButton(AppIcon.NEXT, stringResource(R.string.cube_view_down), { spinCube(false, 1) }, Modifier.align(Alignment.BottomCenter).padding(6.dp).rotate(90f), size = arrow)
        }
    }
}

private fun stageName(stage: Int) = when (stage) {
    CubeSolver.CROSS -> R.string.cube_stage_cross
    CubeSolver.CORNERS -> R.string.cube_stage_corners
    CubeSolver.MIDDLE -> R.string.cube_stage_middle
    CubeSolver.TOP_CROSS -> R.string.cube_stage_top_cross
    CubeSolver.TOP_EDGES -> R.string.cube_stage_top_edges
    CubeSolver.TOP_PLACE -> R.string.cube_stage_top_place
    CubeSolver.TOP_TURN -> R.string.cube_stage_top_turn
    else -> R.string.cube_stage_top_face
}

/** A face button: the face drawn flat as it is now, outlined when chosen. */
@Composable
private fun FaceButton(
    stickers: List<Int>, n: Int, face: Int, colors: Array<Color>, symbols: Boolean, chosen: Boolean,
    description: String, onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(if (chosen) p.highlight else p.surfaceAlt)
            .border(BorderStroke(if (chosen) 3.dp else 1.dp, if (chosen) p.primary else p.outline), shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description; selected = chosen }
            .padding(6.dp),
    ) {
        val renderer = remember { CubeRenderer() }
        Canvas(Modifier.fillMaxSize()) {
            val cell = size.width / n
            drawRect(Color(0xFF232327))
            for (r in 0 until n) for (c in 0 until n) {
                val color = stickers[face * n * n + r * n + c]
                val tl = Offset(c * cell + cell * 0.07f, r * cell + cell * 0.07f)
                drawRoundRect(colors[color], tl, Size(cell * 0.86f, cell * 0.86f), CornerRadius(cell * 0.15f))
                if (symbols && n <= 3) {
                    val center = tl + Offset(cell * 0.43f, cell * 0.43f)
                    val l = colors[color].let { it.red * 0.3f + it.green * 0.59f + it.blue * 0.11f }
                    withTransform({ translate(center.x, center.y); scale(cell * 0.43f, cell * 0.43f, Offset.Zero) }) {
                        renderer.symbol(this, color, if (l > 0.5f) Color.Black.copy(alpha = 0.72f) else Color.White)
                    }
                }
            }
        }
    }
}

/** Square-ish button with a drawing instead of an icon (the turn arrows). */
@Composable
private fun PadButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier, art: DrawScope.(Color) -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .height(60.dp)
            .clip(shape)
            .background(p.primary)
            .then(if (p.strongOutlines) Modifier.border(2.dp, p.outline, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) { art(p.onPrimary) }
    }
}

/** A round arrow, clockwise or not, like on a dial. */
private fun DrawScope.turnArrow(color: Color, clockwise: Boolean) {
    val r = size.minDimension * 0.36f
    val c = center
    val w = size.minDimension * 0.11f
    val sweep = if (clockwise) 270f else -270f
    val start = -90f + if (clockwise) 20f else -20f
    drawArc(color, start, sweep, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(w, cap = StrokeCap.Round))
    // Arrow head at the end of the arc.
    val end = Math.toRadians((start + sweep).toDouble())
    val tip = Offset(c.x + r * cos(end).toFloat(), c.y + r * sin(end).toFloat())
    val tangent = if (clockwise) Offset(-sin(end).toFloat(), cos(end).toFloat()) else Offset(sin(end).toFloat(), -cos(end).toFloat())
    val normal = Offset(cos(end).toFloat(), sin(end).toFloat())
    val h = w * 2.2f
    val head = Path().apply {
        moveTo(tip.x + tangent.x * h, tip.y + tangent.y * h)
        lineTo(tip.x + normal.x * h * 0.8f, tip.y + normal.y * h * 0.8f)
        lineTo(tip.x - normal.x * h * 0.8f, tip.y - normal.y * h * 0.8f)
        close()
    }
    drawPath(head, color)
}
