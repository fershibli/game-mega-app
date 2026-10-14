package io.github.fershibli.megagames.games.connect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.drawSymbol
import io.github.fershibli.megagames.ui.art.glossyDisc
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.random.Random

@Serializable
data class ConnectState(
    val size: Int,
    /** Dots: color k has its two dots at ends[2k] and ends[2k+1]. */
    val ends: List<Int>,
    val solution: List<List<Int>>,
    /** Line drawn by the player for each color. */
    val paths: List<List<Int>>,
    val mustFill: Boolean,
) {
    val colors get() = ends.size / 2
    fun colorOfEnd(cell: Int) = ends.indexOf(cell).let { if (it < 0) -1 else it / 2 }
    fun isComplete(k: Int) = Connect.complete(paths[k], ends[2 * k], ends[2 * k + 1])
    val filled get() = paths.sumOf { it.size }
    val won get() = (0 until colors).all { isComplete(it) } && (!mustFill || filled == size * size)
}

@Composable
fun ConnectGame(host: GameHost) {
    LoadGame(host, ConnectState.serializer(), {
        val p = Connect.generate(Connect.sizeFor(host.difficulty), Connect.colorsFor(host.difficulty), Random.Default)
        ConnectState(p.size, p.ends, p.segments, List(p.segments.size) { emptyList() }, mustFill = host.difficulty != Difficulty.EASY)
    }) { state -> ConnectScreen(state, host) }
}

/** Applies the player's finger entering [cell] while drawing color [k]. */
private fun extend(s: ConnectState, k: Int, cell: Int): ConnectState {
    val path = s.paths[k]
    if (path.isEmpty() || cell == path.last()) return s
    val inOwn = path.indexOf(cell)
    if (inOwn >= 0) return s.copy(paths = s.paths.toMutableList().also { it[k] = path.take(inOwn + 1) })
    if (!Connect.adjacent(path.last(), cell, s.size)) return s
    if (s.isComplete(k)) return s
    val endColor = s.colorOfEnd(cell)
    if (endColor >= 0 && endColor != k) return s
    val paths = s.paths.toMutableList()
    // Drawing over another line cuts that line.
    for (o in paths.indices) if (o != k) {
        val idx = paths[o].indexOf(cell)
        if (idx >= 0) paths[o] = paths[o].take(idx)
    }
    paths[k] = path + cell
    return s.copy(paths = paths)
}

private fun startAt(s: ConnectState, cell: Int): Pair<ConnectState, Int>? {
    val end = s.colorOfEnd(cell)
    if (end >= 0) return s.copy(paths = s.paths.toMutableList().also { it[end] = listOf(cell) }) to end
    val k = s.paths.indexOfFirst { cell in it }
    if (k < 0) return null
    return s.copy(paths = s.paths.toMutableList().also { it[k] = it[k].take(it[k].indexOf(cell) + 1) }) to k
}

@Composable
private fun ConnectScreen(state: MutableState<ConnectState>, host: GameHost) {
    val s = state.value
    val history = remember { mutableStateListOf<ConnectState>() }
    var active by remember { mutableIntStateOf(-1) }
    val won = s.won
    val connectedCount = (0 until s.colors).count { s.isComplete(it) }

    LaunchedEffect(won) { if (won) { delay(700); host.finish(GameOutcome(true, null)) } }

    GameLayout(
        status = {
            val msg = when {
                connectedCount == s.colors && !won -> stringResource(R.string.connect_fill)
                else -> stringResource(R.string.connect_status, connectedCount, s.colors, s.filled * 100 / (s.size * s.size))
            }
            StatusPill(msg, emphasized = won)
        },
        controls = {
            ActionBar {
                BigButton(stringResource(R.string.undo), { history.removeLastOrNull()?.let { state.value = it } }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = history.isNotEmpty() && !won)
                BigButton(stringResource(R.string.hint), {
                    // Reveal the correct line of one color that is not solved yet.
                    val k = (0 until s.colors).firstOrNull { s.paths[it] != s.solution[it] && s.paths[it] != s.solution[it].reversed() } ?: return@BigButton
                    history += s
                    var next = s.copy(paths = s.paths.toMutableList().also { it[k] = listOf(s.solution[k].first()) })
                    s.solution[k].drop(1).forEach { next = extend(next, k, it) }
                    state.value = next
                }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = !won)
                BigButton(stringResource(R.string.connect_clear), {
                    history += s
                    state.value = s.copy(paths = List(s.colors) { emptyList() })
                }, icon = AppIcon.NEW_GAME, style = ButtonStyle.SECONDARY, enabled = !won && s.filled > 0)
            }
        },
    ) {
        val p = LocalPalette.current
        BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            Canvas(
                Modifier
                    .size(side)
                    .pointerInput(s.size) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val cellPx = size.width / state.value.size.toFloat()
                            fun cellAt(o: Offset): Int? {
                                val c = (o.x / cellPx).toInt()
                                val r = (o.y / cellPx).toInt()
                                val n = state.value.size
                                return if (c in 0 until n && r in 0 until n) r * n + c else null
                            }
                            val before = state.value
                            val start = cellAt(down.position)?.let { startAt(before, it) } ?: return@awaitEachGesture
                            state.value = start.first
                            active = start.second
                            var last = cellAt(down.position)!!
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed || change.isConsumed) break
                                val target = cellAt(change.position)
                                if (target != null && target != last) {
                                    // Walk one grid step at a time so fast swipes do not skip cells.
                                    val n = state.value.size
                                    while (last != target) {
                                        val dr = target / n - last / n
                                        val dc = target % n - last % n
                                        val step = if (abs(dc) >= abs(dr)) last + Integer.signum(dc) else last + Integer.signum(dr) * n
                                        state.value = extend(state.value, active, step)
                                        last = step
                                    }
                                }
                                change.consume()
                            }
                            active = -1
                            if (state.value != before) history += before
                        }
                    },
            ) {
                val n = s.size
                val cell = size.width / n
                drawRoundRect(p.boardDark, Offset.Zero, size, CornerRadius(cell * 0.15f))
                val line = p.boardLine.copy(alpha = if (p.strongOutlines) 0.6f else 0.2f)
                for (k in 1 until n) {
                    drawLine(line, Offset(k * cell, 0f), Offset(k * cell, size.height), 2f)
                    drawLine(line, Offset(0f, k * cell), Offset(size.width, k * cell), 2f)
                }
                fun center(i: Int) = Offset((i % n + 0.5f) * cell, (i / n + 0.5f) * cell)
                // Lightly tinted cells under each line.
                s.paths.forEachIndexed { k, path ->
                    val color = p.pieces[k % p.pieces.size]
                    path.forEach { i -> drawRect(color.copy(alpha = if (s.isComplete(k)) 0.28f else 0.16f), Offset((i % n) * cell, (i / n) * cell), Size(cell, cell)) }
                }
                s.paths.forEachIndexed { k, path ->
                    if (path.size < 2) return@forEachIndexed
                    val color = p.pieces[k % p.pieces.size]
                    val pth = Path().apply {
                        moveTo(center(path[0]).x, center(path[0]).y)
                        path.drop(1).forEach { lineTo(center(it).x, center(it).y) }
                    }
                    drawPath(pth, color.darker(0.3f), style = Stroke(cell * 0.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(pth, color, style = Stroke(cell * 0.32f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(pth, Color.White.copy(alpha = 0.25f), style = Stroke(cell * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                for (k in 0 until s.colors) {
                    val color = p.pieces[k % p.pieces.size]
                    for (e in listOf(s.ends[2 * k], s.ends[2 * k + 1])) {
                        val c = center(e)
                        glossyDisc(c, cell * 0.36f, color, rim = if (k == active) p.text else null)
                        if (p.symbols) drawSymbol(k, c, cell * 0.32f, if (color.luminanceSimple() > 0.55f) Color.Black.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.9f))
                    }
                }
            }
        }
    }
}

private fun Color.luminanceSimple() = 0.299f * red + 0.587f * green + 0.114f * blue
