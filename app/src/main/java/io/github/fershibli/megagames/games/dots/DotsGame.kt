package io.github.fershibli.megagames.games.dots

import io.github.fershibli.megagames.ui.theme.wait
import io.github.fershibli.megagames.ui.theme.currentPace
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.ui.art.drawSymbol
import io.github.fershibli.megagames.ui.art.glossyDisc
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.Palette
import kotlin.random.Random

@Composable
fun DotsGame(host: GameHost) {
    LoadGame(host, DotsState.serializer(), { Dots.newGame(host.difficulty, Random.Default) }) { state -> DotsScreen(state, host) }
}

@Composable
private fun DotsScreen(state: MutableState<DotsState>, host: GameHost) {
    val s = state.value
    val history = remember { mutableStateListOf<DotsState>() }
    var path by remember { mutableStateOf<List<Int>>(emptyList()) }
    var loop by remember { mutableStateOf(false) }
    val p = LocalPalette.current

    val pace = currentPace
    LaunchedEffect(s.won, s.lost) {
        if (s.won) { pace.wait(500); host.finish(GameOutcome(true, null)) }
        else if (s.lost) { pace.wait(700); host.finish(GameOutcome(false, null)) }
    }

    GameLayout(
        status = { GoalsBar(s, p) },
        controls = {
            ActionBar {
                BigButton(stringResource(R.string.undo), { history.removeLastOrNull()?.let { state.value = it } }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = history.isNotEmpty() && !s.won && !s.lost)
            }
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            val cell = minOf(maxWidth / s.cols, maxHeight / s.rows)
            Canvas(
                Modifier
                    .size(cell * s.cols, cell * s.rows)
                    .pointerInput(s.cols) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val cur = state.value
                            if (cur.won || cur.lost) return@awaitEachGesture
                            val cellPx = size.width / cur.cols.toFloat()
                            fun cellAt(o: Offset): Int? {
                                val c = (o.x / cellPx).toInt()
                                val r = (o.y / cellPx).toInt()
                                if (c !in 0 until cur.cols || r !in 0 until cur.rows) return null
                                // Only count the touch when it is reasonably close to the dot center.
                                val center = Offset((c + 0.5f) * cellPx, (r + 0.5f) * cellPx)
                                return if ((o - center).getDistance() < cellPx * 0.48f) r * cur.cols + c else null
                            }
                            val first = cellAt(down.position) ?: return@awaitEachGesture
                            var line = listOf(first)
                            var closed = false
                            path = line
                            loop = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.isConsumed) { line = emptyList(); break }
                                if (!change.pressed) break
                                val c = cellAt(change.position)
                                if (c != null && c != line.last()) {
                                    when {
                                        line.size >= 2 && c == line[line.size - 2] -> { line = line.dropLast(1); closed = false }
                                        closed -> Unit
                                        cur.grid[c] != cur.grid[first] || !Dots.adjacent(line.last(), c, cur.cols) -> Unit
                                        c in line -> { closed = line.size >= 3; if (closed) line = line + c }
                                        else -> line = line + c
                                    }
                                    path = line
                                    loop = closed
                                }
                                change.consume()
                            }
                            val distinct = line.distinct()
                            if (distinct.size >= 2) {
                                Dots.play(cur, distinct, closed, Random.Default)?.let {
                                    history += cur
                                    state.value = it
                                }
                            }
                            path = emptyList()
                            loop = false
                        }
                    },
            ) {
                val cellPx = size.width / s.cols
                drawRoundRect(p.boardLight, Offset.Zero, size, CornerRadius(cellPx * 0.3f))
                val pathColor = path.firstOrNull()?.let { p.pieces[s.grid[it] % p.pieces.size] }
                if (loop && pathColor != null) drawRoundRect(pathColor.copy(alpha = 0.25f), Offset.Zero, size, CornerRadius(cellPx * 0.3f))
                fun center(i: Int) = Offset((i % s.cols + 0.5f) * cellPx, (i / s.cols + 0.5f) * cellPx)
                if (pathColor != null && path.size >= 2) {
                    path.zipWithNext().forEach { (a, b) -> drawLine(pathColor, center(a), center(b), cellPx * 0.16f) }
                }
                for (i in s.grid.indices) {
                    val color = p.pieces[s.grid[i] % p.pieces.size]
                    val inPath = i in path
                    val c = center(i)
                    glossyDisc(c, cellPx * (if (inPath) 0.3f else 0.26f), color, rim = if (inPath) p.text else null)
                    if (p.symbols) drawSymbol(s.grid[i], c, cellPx * 0.22f, Color.White.copy(alpha = 0.85f))
                }
                if (loop && pathColor != null) drawRoundRect(pathColor, Offset.Zero, size, CornerRadius(cellPx * 0.3f), style = Stroke(cellPx * 0.1f))
            }
        }
    }
}

@Composable
private fun GoalsBar(s: DotsState, p: Palette) {
    Row(
        Modifier.background(p.surfaceAlt, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        s.goals.forEachIndexed { k, goal ->
            if (goal == 0) return@forEachIndexed
            val color = p.pieces[k % p.pieces.size]
            Canvas(Modifier.size(22.dp)) {
                glossyDisc(center, size.minDimension / 2.2f, color, shadow = false)
                if (p.symbols) drawSymbol(k, center, size.minDimension * 0.4f, Color.White.copy(alpha = 0.85f))
            }
            val done = s.collected[k] >= goal
            Text(if (done) "✓" else "${s.collected[k]}/$goal", style = MaterialTheme.typography.titleMedium, color = if (done) p.success else p.text)
        }
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.dots_moves, s.movesLeft), style = MaterialTheme.typography.titleMedium, color = if (s.movesLeft <= 3) p.danger else p.text)
    }
}
