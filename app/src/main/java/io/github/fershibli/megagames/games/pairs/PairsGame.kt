package io.github.fershibli.megagames.games.pairs

import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.wait
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.core.WorldStore
import io.github.fershibli.megagames.core.rememberWorldStore
import io.github.fershibli.megagames.games.arcade.GameTicker
import io.github.fershibli.megagames.games.arcade.KeepBest
import io.github.fershibli.megagames.games.arcade.PadButton
import io.github.fershibli.megagames.games.arcade.PadIcon
import io.github.fershibli.megagames.games.arcade.Records
import io.github.fershibli.megagames.games.arcade.rememberRunning
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.LocalSettings
import io.github.fershibli.megagames.ui.theme.Palette
import kotlin.math.abs
import kotlin.random.Random

@Composable
fun PairsGame(host: GameHost) {
    val store = rememberWorldStore()
    LoadGame(host, PairsState.serializer(), {
        Pairs.newGame(host.difficulty, Random.nextLong(), Records.best(store, host.game, host.difficulty))
    }) { state -> PairsScreen(state, host, store) }
}

@Composable
private fun PairsScreen(state: MutableState<PairsState>, host: GameHost, store: WorldStore) {
    val pace = currentPace
    val s = state.value
    val p = LocalPalette.current
    val pieceScale = LocalSettings.current.pieceScale
    var running by rememberRunning()
    // Easy: the pair waits while a finger rests on the controls.
    var holding by remember { mutableStateOf(false) }
    val waitForFinger = s.difficulty == Difficulty.EASY && holding

    GameTicker(running && !waitForFinger && !s.over, Pairs.intervalMillis(s)) { state.value = Pairs.tick(state.value) }

    val best = maxOf(s.score, s.recordToBeat)
    val newBest = s.score > s.recordToBeat && s.score > 0
    val detail = listOfNotNull(if (newBest) stringResource(R.string.pairs_new_best) else null, stringResource(R.string.pairs_final, s.score, best)).joinToString("\n")
    KeepBest(store, host.game, host.difficulty, s.score, s.recordToBeat)
    LaunchedEffect(s.over) {
        if (s.over) {
            running = false
            pace.wait(1500)
            host.finish(GameOutcome(newBest, detail))
        }
    }

    val act: ((PairsState) -> PairsState) -> Unit = { f -> if (running) state.value = f(state.value) }

    GameLayout(
        status = {
            val text = when {
                s.over -> stringResource(R.string.pairs_full)
                s.chain > 1 -> stringResource(R.string.pairs_chain, s.chain)
                else -> stringResource(R.string.pairs_score, s.score, best)
            }
            StatusPill(text, emphasized = s.chain > 1 || s.over)
        },
        controls = {
            // The arrows keep their physical places (left on the left) in every language.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    Modifier.fillMaxWidth().pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                holding = event.changes.any { it.pressed }
                            }
                        }
                    },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val enabled = running && !s.over
                    PadButton(PadIcon.LEFT, stringResource(R.string.pairs_left), { act { Pairs.move(it, -1) } }, Modifier.weight(1f), enabled)
                    PadButton(PadIcon.ROTATE, stringResource(R.string.pairs_turn), { act { Pairs.rotate(it) } }, Modifier.weight(1f), enabled)
                    PadButton(PadIcon.DROP, stringResource(R.string.pairs_drop), { act { Pairs.drop(it) } }, Modifier.weight(1f), enabled)
                    PadButton(PadIcon.RIGHT, stringResource(R.string.pairs_right), { act { Pairs.move(it, 1) } }, Modifier.weight(1f), enabled)
                }
            }
            BigButton(
                stringResource(if (running) R.string.pairs_pause else if (s.steps == 0) R.string.pairs_start else R.string.pairs_continue),
                { running = !running }, Modifier.fillMaxWidth(),
                style = if (running) ButtonStyle.SECONDARY else ButtonStyle.PRIMARY, enabled = !s.over,
            )
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            val showNext = Pairs.showsNext(s.difficulty)
            val cell = minOf(maxWidth / (Pairs.WIDTH + if (showNext) 2.2f else 0f), maxHeight / Pairs.HEIGHT)
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(cell * 0.2f)) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(
                        Modifier
                            .size(cell * Pairs.WIDTH, cell * Pairs.HEIGHT)
                            .testTag("pairs-well")
                            .pointerInput(Unit) {
                                // Swipe sideways to move, swipe down to drop, tap to turn.
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    if (!running) return@awaitEachGesture
                                    val cellPx = size.width / Pairs.WIDTH.toFloat()
                                    var stepsX = 0
                                    var dropped = false
                                    var moved = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        val d = change.position - down.position
                                        if (d.getDistance() > viewConfiguration.touchSlop) moved = true
                                        val wanted = (d.x / cellPx).toInt()
                                        while (stepsX < wanted) { act { Pairs.move(it, 1) }; stepsX++ }
                                        while (stepsX > wanted) { act { Pairs.move(it, -1) }; stepsX-- }
                                        if (!dropped && d.y > cellPx * 2.5f && d.y > abs(d.x) * 2) { act { Pairs.drop(it) }; dropped = true }
                                        if (moved) change.consume()
                                    }
                                    if (!moved) act { Pairs.rotate(it) }
                                }
                            },
                    ) {
                        drawWell(s, p, pieceScale)
                    }
                    if (!running && !s.over) PauseCover(s.steps == 0) { running = true }
                }
                if (showNext) {
                    Column(Modifier.width(cell * 2f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.pairs_next), style = MaterialTheme.typography.titleMedium, color = p.text, textAlign = TextAlign.Center, maxLines = 2)
                        Canvas(Modifier.size(cell * 1.6f, cell * 2.6f)) {
                            val c = size.width / 1.6f
                            drawRoundRect(p.surfaceAlt, Offset.Zero, size, CornerRadius(c * 0.3f))
                            val r = (c * 0.42f * pieceScale).coerceAtMost(c * 0.5f)
                            drawDrop(Offset(size.width / 2, size.height / 2 - c * 0.5f), r, s.next[1] - 1, p)
                            drawDrop(Offset(size.width / 2, size.height / 2 + c * 0.5f), r, s.next[0] - 1, p)
                        }
                    }
                }
            }
        }
    }
}

/** Shown over the well while the game waits: a calm word and the big button to go on. */
@Composable
private fun PauseCover(fresh: Boolean, onGo: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.background(p.surface.copy(alpha = 0.92f), RoundedCornerShape(18.dp)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(if (fresh) R.string.pairs_ready else R.string.pairs_paused),
            style = MaterialTheme.typography.titleLarge, color = p.text, textAlign = TextAlign.Center,
        )
        BigButton(stringResource(if (fresh) R.string.pairs_start else R.string.pairs_continue), onGo)
    }
}

private fun DrawScope.drawWell(s: PairsState, p: Palette, pieceScale: Float) {
    val cell = size.width / Pairs.WIDTH
    val radius = (cell * 0.42f * pieceScale).coerceAtMost(cell * 0.5f)
    drawRoundRect(p.boardDark, Offset.Zero, size, CornerRadius(cell * 0.3f))
    // Faint column guides help to see where the pair will land.
    for (c in 1 until Pairs.WIDTH) drawLine(p.boardLine, Offset(c * cell, 0f), Offset(c * cell, size.height), cell * 0.02f, alpha = 0.15f)
    // The entry: when it fills up, the game is over.
    drawRect(p.highlight, Offset(Pairs.SPAWN_COL * cell, 0f), Size(cell, cell * 2), alpha = 0.25f)
    fun center(r: Int, c: Int) = Offset((c + 0.5f) * cell, (r + 0.5f) * cell)
    for (i in s.grid.indices) {
        val v = s.grid[i]
        if (v == 0) continue
        val c = center(i / Pairs.WIDTH, i % Pairs.WIDTH)
        if (i in s.popping) {
            // About to pop: a bright ring around a smaller drop.
            drawCircle(p.highlight, radius * 1.05f, c, style = Stroke(cell * 0.08f))
            drawDrop(c, radius * 0.8f, v - 1, p)
        } else {
            drawDrop(c, radius, v - 1, p)
        }
    }
    if (s.falling && !s.over) {
        drawDrop(center(s.row, s.col), radius, s.pair[0] - 1, p)
        drawDrop(center(s.partnerRow, s.partnerCol), radius, s.pair[1] - 1, p)
    }
    drawRoundRect(p.boardLine, Offset.Zero, size, CornerRadius(cell * 0.3f), style = Stroke(if (p.strongOutlines) cell * 0.08f else cell * 0.04f), alpha = 0.7f)
}

// Unit shapes for the symbols inside the drops (drawn scaled, so nothing is allocated per frame).
private val triangle = Path().apply { moveTo(0.5f, 0.05f); lineTo(0.95f, 0.9f); lineTo(0.05f, 0.9f); close() }
private val diamond = Path().apply { moveTo(0.5f, 0f); lineTo(1f, 0.5f); lineTo(0.5f, 1f); lineTo(0f, 0.5f); close() }

/**
 * A round drop of color [index] with its own symbol (dot, square, triangle, diamond, cross), so
 * colors never have to be told apart by hue alone.
 */
fun DrawScope.drawDrop(center: Offset, radius: Float, index: Int, p: Palette) {
    val color = p.pieces[index % p.pieces.size]
    drawCircle(Color.Black, radius, center + Offset(radius * 0.06f, radius * 0.12f), alpha = 0.25f)
    drawCircle(color.darker(0.3f), radius, center)
    drawCircle(color, radius * 0.86f, center - Offset(0f, radius * 0.05f))
    drawCircle(Color.White, radius * 0.22f, center - Offset(radius * 0.35f, radius * 0.4f), alpha = 0.45f)
    val light = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue > 0.6f
    val mark = if (light) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.9f)
    val h = radius * 0.42f
    when (index % 5) {
        0 -> drawCircle(mark, h * 0.75f, center)
        1 -> drawRect(mark, center - Offset(h * 0.7f, h * 0.7f), Size(h * 1.4f, h * 1.4f))
        2 -> translate(center.x - h, center.y - h) { scale(h * 2, h * 2, Offset.Zero) { drawPath(triangle, mark) } }
        3 -> translate(center.x - h, center.y - h) { scale(h * 2, h * 2, Offset.Zero) { drawPath(diamond, mark) } }
        else -> {
            drawRect(mark, center - Offset(h, h * 0.3f), Size(h * 2, h * 0.6f))
            drawRect(mark, center - Offset(h * 0.3f, h), Size(h * 0.6f, h * 2))
        }
    }
}

/** Home screen picture: a narrow well with drops, a group of three about to pop and a pair falling. */
fun DrawScope.pairsThumbnail(p: Palette) {
    drawRect(p.boardDark)
    val cell = size.width / 5
    fun c(r: Int, col: Int) = Offset((col + 0.5f) * cell, (r + 0.5f) * cell)
    val r = cell * 0.42f
    val bottom = listOf(4 to 0, 4 to 1, 4 to 2, 4 to 3, 4 to 4, 3 to 0, 3 to 3, 3 to 4, 2 to 4)
    val colors = listOf(0, 0, 1, 2, 2, 0, 2, 1, 1)
    bottom.forEachIndexed { i, (row, col) ->
        if (colors[i] == 0) drawCircle(p.highlight, r * 1.1f, c(row, col), style = Stroke(cell * 0.07f))
        drawDrop(c(row, col), r, colors[i], p)
    }
    drawDrop(c(1, 1), r, 0, p)
    drawDrop(c(0, 1), r, 3, p)
}
