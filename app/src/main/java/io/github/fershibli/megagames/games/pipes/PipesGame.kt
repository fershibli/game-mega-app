package io.github.fershibli.megagames.games.pipes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class PipesState(val size: Int, val source: Int, val masks: List<Int>, val solution: List<Int>, val moves: Int = 0)

@Composable
fun PipesGame(host: GameHost) {
    LoadGame(host, PipesState.serializer(), {
        val p = Pipes.generate(Pipes.sizeFor(host.difficulty), Random.Default)
        PipesState(p.size, p.source, p.scrambled.toList(), p.solution.toList())
    }) { state -> PipesScreen(state, host) }
}

@Composable
private fun PipesScreen(state: MutableState<PipesState>, host: GameHost) {
    val s = state.value
    val masks = s.masks.toIntArray()
    val connected = remember(s.masks) { Pipes.connected(masks, s.size, s.source) }
    val solved = remember(s.masks) { Pipes.isSolved(masks, s.size, s.source) }
    val history = remember { mutableStateListOf<PipesState>() }
    val spin = remember { Animatable(0f) }
    var spinning by remember { mutableIntStateOf(-1) }
    var hinted by remember { mutableIntStateOf(-1) }
    var finishing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(solved) {
        if (solved && !finishing) {
            finishing = true
            delay(900)
            host.finish(GameOutcome(true, null))
        }
    }
    LaunchedEffect(hinted) { if (hinted >= 0) { delay(1500); hinted = -1 } }

    fun turn(i: Int, times: Int = 1) {
        if (solved) return
        history += s
        if (history.size > 300) history.removeAt(0)
        state.value = s.copy(masks = s.masks.toMutableList().also { it[i] = Pipes.rotate(it[i], times) }, moves = s.moves + 1)
        spinning = i
        scope.launch {
            spin.snapTo(-90f * times)
            spin.animateTo(0f, tween(180))
        }
    }

    GameLayout(
        status = { StatusPill(stringResource(R.string.pipes_status, connected.size, masks.size), emphasized = solved) },
        controls = {
            ActionBar {
                BigButton(stringResource(R.string.undo), { history.removeLastOrNull()?.let { state.value = it } }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = history.isNotEmpty() && !solved)
                BigButton(stringResource(R.string.hint), {
                    val wrong = masks.indices.filter { masks[it] != s.solution[it] }
                    val pick = wrong.firstOrNull { it in connected } ?: wrong.firstOrNull()
                    if (pick != null) {
                        val times = (1..3).first { Pipes.rotate(masks[pick], it) == s.solution[pick] }
                        turn(pick, times)
                        hinted = pick
                    }
                }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = !solved)
            }
        },
    ) {
        val p = LocalPalette.current
        BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            Canvas(
                Modifier
                    .size(side)
                    .pointerInput(s) {
                        detectTapGestures { pos ->
                            val cell = size.width / s.size.toFloat()
                            val c = (pos.x / cell).toInt().coerceIn(0, s.size - 1)
                            val r = (pos.y / cell).toInt().coerceIn(0, s.size - 1)
                            turn(r * s.size + c)
                        }
                    },
            ) {
                val cell = size.width / s.size
                drawRoundRect(p.boardDark.darker(0.08f), Offset.Zero, size, CornerRadius(cell * 0.2f))
                for (i in masks.indices) {
                    val tl = Offset((i % s.size) * cell, (i / s.size) * cell)
                    val inset = cell * 0.04f
                    // Raised square tile under each piece.
                    drawRoundRect(Color.Black.copy(alpha = 0.18f), tl + Offset(inset * 1.6f, inset * 2f), Size(cell - inset * 2, cell - inset * 2), CornerRadius(cell * 0.14f))
                    drawRoundRect(
                        Brush.linearGradient(listOf(p.boardLight.lighter(0.1f), p.boardLight.darker(0.06f)), tl, tl + Offset(cell, cell)),
                        tl + Offset(inset, inset), Size(cell - inset * 2, cell - inset * 2), CornerRadius(cell * 0.14f),
                    )
                    if (i == hinted) drawRoundRect(p.hint, tl + Offset(inset, inset), Size(cell - inset * 2, cell - inset * 2), CornerRadius(cell * 0.14f), style = Stroke(cell * 0.07f))
                    val center = tl + Offset(cell / 2, cell / 2)
                    val angle = if (i == spinning) spin.value else 0f
                    rotate(angle, center) {
                        drawPipe(masks[i], center, cell, if (i in connected) p.water else p.pipe, p, isSource = i == s.source, wet = i in connected)
                    }
                }
            }
        }
    }
}

internal fun DrawScope.drawPipe(mask: Int, c: Offset, cell: Float, color: Color, p: Palette, isSource: Boolean, wet: Boolean) {
    val half = cell / 2
    val width = cell * 0.24f
    val ends = listOf(Pipes.N to Offset(0f, -half), Pipes.E to Offset(half, 0f), Pipes.S to Offset(0f, half), Pipes.W to Offset(-half, 0f))
        .filter { mask and it.first != 0 }.map { c + it.second }
    // Three passes give a rounded "tube": dark outline, body, then a soft highlight.
    for (e in ends) drawLine(color.darker(0.35f), c, e, width * 1.25f, StrokeCap.Butt)
    drawCircle(color.darker(0.35f), width * 0.62f, c)
    for (e in ends) drawLine(color, c, e, width, StrokeCap.Butt)
    drawCircle(color, width * 0.5f, c)
    for (e in ends) {
        val shift = if (e.x == c.x) Offset(-width * 0.18f, 0f) else Offset(0f, -width * 0.18f)
        drawLine(Color.White.copy(alpha = 0.35f), c + shift, e + shift, width * 0.22f, StrokeCap.Butt)
    }
    // Pipe joints at the edges.
    for (e in ends) {
        val ring = if (e.x == c.x) Size(width * 1.5f, width * 0.45f) else Size(width * 0.45f, width * 1.5f)
        val tl = Offset(e.x - ring.width / 2 + (c.x - e.x).coerceIn(-1f, 1f) * ring.width / 2, e.y - ring.height / 2 + (c.y - e.y).coerceIn(-1f, 1f) * ring.height / 2)
        drawRect(color.darker(0.2f), tl, ring)
    }
    when {
        isSource -> {
            drawCircle(p.water.darker(0.4f), cell * 0.32f, c)
            drawCircle(Brush.radialGradient(listOf(p.water.lighter(0.4f), p.water), c - Offset(cell * 0.08f, cell * 0.08f), cell * 0.3f), cell * 0.28f, c)
            drawCircle(Color.White.copy(alpha = 0.6f), cell * 0.07f, c - Offset(cell * 0.09f, cell * 0.09f))
        }
        Integer.bitCount(mask) == 1 -> {
            // End point: a small tank that fills when water arrives.
            drawCircle(color.darker(0.35f), cell * 0.22f, c)
            drawCircle(if (wet) p.water.lighter(0.15f) else p.boardLight, cell * 0.17f, c)
            if (wet) drawCircle(Color.White.copy(alpha = 0.5f), cell * 0.05f, c - Offset(cell * 0.06f, cell * 0.06f))
        }
    }
}
