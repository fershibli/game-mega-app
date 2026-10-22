package io.github.fershibli.megagames.games.snake

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.ColorMode
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
import io.github.fershibli.megagames.ui.art.lighter
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
fun SnakeGame(host: GameHost) {
    val store = rememberWorldStore()
    LoadGame(host, SnakeState.serializer(), {
        Snake.newGame(host.difficulty, Random.nextLong(), Records.best(store, host.game, host.difficulty))
    }) { state -> SnakeScreen(state, host, store) }
}

@Composable
private fun SnakeScreen(state: MutableState<SnakeState>, host: GameHost, store: WorldStore) {
    val pace = currentPace
    val s = state.value
    val p = LocalPalette.current
    val left = LocalSettings.current.leftHanded
    var running by rememberRunning()

    GameTicker(running && !s.over, Snake.stepMillis(s.difficulty)) { state.value = Snake.step(state.value) }

    val best = maxOf(s.length, s.recordToBeat)
    val newBest = s.length > s.recordToBeat && s.length > Snake.START_LENGTH
    val detail = listOfNotNull(if (newBest) stringResource(R.string.snake_new_best) else null, stringResource(R.string.snake_final, s.length, best)).joinToString("\n")
    KeepBest(store, host.game, host.difficulty, s.length, s.recordToBeat)
    LaunchedEffect(s.over) {
        if (s.over) {
            running = false
            pace.wait(1500)
            host.finish(GameOutcome(newBest, detail))
        }
    }

    // A direction also starts (or continues) the game: one tap is enough to play.
    val go: (Int) -> Unit = { dir ->
        if (!state.value.over) {
            state.value = Snake.turn(state.value, dir)
            running = true
        }
    }

    GameLayout(
        status = {
            StatusPill(if (s.over) stringResource(R.string.snake_bump) else stringResource(R.string.snake_length, s.length, best), emphasized = s.over)
        },
        controls = {
            // The cross keeps its physical layout (up is up, left is left) and sits under the thumb.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val pause = @Composable {
                        BigButton(
                            stringResource(if (running) R.string.snake_pause else if (s.steps == 0) R.string.snake_start else R.string.snake_continue),
                            { running = !running }, Modifier.weight(1f),
                            style = if (running) ButtonStyle.SECONDARY else ButtonStyle.PRIMARY, enabled = !s.over,
                        )
                    }
                    if (!left) pause()
                    DirectionCross(enabled = !s.over, onDirection = go)
                    if (left) pause()
                }
            }
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            // The waiting message sits low on the board so the snake and the fruit stay in sight.
            Box(contentAlignment = Alignment.BottomCenter) {
                Canvas(
                    Modifier
                        .size(side)
                        .testTag("snake-board")
                        .pointerInput(Unit) {
                            // Swipe in a direction to turn that way.
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val limit = viewConfiguration.touchSlop * 2
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    val d = change.position - down.position
                                    if (d.getDistance() > limit) {
                                        go(if (abs(d.x) > abs(d.y)) (if (d.x > 0) Snake.RIGHT else Snake.LEFT) else (if (d.y > 0) Snake.DOWN else Snake.UP))
                                        change.consume()
                                        break
                                    }
                                }
                            }
                        },
                ) { drawSnakeBoard(s, p) }
                if (!running && !s.over) {
                    Column(
                        Modifier.padding(bottom = side * 0.06f).background(p.surface.copy(alpha = 0.92f), RoundedCornerShape(18.dp)).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(if (s.steps == 0) R.string.snake_ready else R.string.snake_paused),
                            style = MaterialTheme.typography.titleLarge, color = p.text, textAlign = TextAlign.Center,
                        )
                        BigButton(stringResource(if (s.steps == 0) R.string.snake_start else R.string.snake_continue), { running = true })
                    }
                }
            }
        }
    }
}

/** Four big arrows in a cross. */
@Composable
private fun DirectionCross(enabled: Boolean, onDirection: (Int) -> Unit) {
    val b = Modifier.size(64.dp)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PadButton(PadIcon.UP, stringResource(R.string.snake_up), { onDirection(Snake.UP) }, b, enabled)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PadButton(PadIcon.LEFT, stringResource(R.string.snake_left), { onDirection(Snake.LEFT) }, b, enabled)
            Spacer(b)
            PadButton(PadIcon.RIGHT, stringResource(R.string.snake_right), { onDirection(Snake.RIGHT) }, b, enabled)
        }
        PadButton(PadIcon.DOWN, stringResource(R.string.snake_down), { onDirection(Snake.DOWN) }, b, enabled)
    }
}

private val SNAKE_GREEN = Color(0xFF4CAF50)

private fun DrawScope.drawSnakeBoard(s: SnakeState, p: Palette) {
    val n = s.size
    val cell = size.width / n
    // A soft checkered lawn.
    drawRoundRect(p.boardLight, Offset.Zero, size, CornerRadius(cell * 0.4f))
    for (i in 0 until n * n) {
        if ((i / n + i % n) % 2 == 1) drawRect(p.boardDark, Offset((i % n) * cell, (i / n) * cell), Size(cell, cell))
    }
    if (s.walls) {
        drawRoundRect(p.woodDark, Offset.Zero, size, CornerRadius(cell * 0.4f), style = Stroke(cell * 0.3f))
    } else {
        drawRoundRect(p.boardLine, Offset.Zero, size, CornerRadius(cell * 0.4f), style = Stroke(cell * 0.06f), alpha = 0.4f)
    }
    if (s.fruit >= 0) drawFruit(s.fruitKind, Offset((s.fruit % n) * cell, (s.fruit / n) * cell), cell)

    val body = if (p.mode == ColorMode.NORMAL) SNAKE_GREEN else p.pieces[2]
    fun center(i: Int) = Offset((i % n + 0.5f) * cell, (i / n + 0.5f) * cell)
    // Joints between neighbouring segments (not across a wrap), then round segments on top.
    for (k in 0 until s.body.size - 1) {
        val a = s.body[k]
        val b = s.body[k + 1]
        if (abs(a % n - b % n) + abs(a / n - b / n) != 1) continue
        val ca = center(a)
        val cb = center(b)
        drawLine(body.darker(0.2f), ca, cb, cell * 0.7f)
    }
    for (k in s.body.indices.reversed()) {
        val c = center(s.body[k])
        val shade = if (k % 2 == 0) body else body.lighter(0.15f)
        drawCircle(body.darker(0.25f), cell * 0.43f, c)
        drawCircle(shade, cell * 0.36f, c)
    }
    // Head: a little bigger, with two friendly eyes looking where it goes.
    val head = center(s.body.first())
    drawCircle(body.darker(0.3f), cell * 0.5f, head)
    drawCircle(body, cell * 0.43f, head)
    val (fx, fy) = when (s.dir) {
        Snake.UP -> 0f to -1f
        Snake.DOWN -> 0f to 1f
        Snake.LEFT -> -1f to 0f
        else -> 1f to 0f
    }
    for (side in intArrayOf(-1, 1)) {
        val eye = head + Offset(fx * cell * 0.15f - fy * side * cell * 0.2f, fy * cell * 0.15f + fx * side * cell * 0.2f)
        drawCircle(Color.White, cell * 0.13f, eye)
        drawCircle(Color(0xFF222222), cell * 0.07f, eye + Offset(fx * cell * 0.04f, fy * cell * 0.04f))
    }
    if (p.strongOutlines) drawCircle(p.boardLine, cell * 0.5f, head, style = Stroke(cell * 0.06f))
}

// Unit shapes for the fruit, drawn scaled into each cell.
private val leaf = Path().apply {
    moveTo(0.5f, 0.22f); quadraticTo(0.62f, 0.02f, 0.86f, 0.06f); quadraticTo(0.76f, 0.28f, 0.5f, 0.22f); close()
}
private val berry = Path().apply {
    moveTo(0.2f, 0.32f); quadraticTo(0.5f, 0.22f, 0.8f, 0.32f); quadraticTo(0.9f, 0.6f, 0.5f, 0.94f); quadraticTo(0.1f, 0.6f, 0.2f, 0.32f); close()
}
private val berryTop = Path().apply {
    moveTo(0.3f, 0.3f); lineTo(0.4f, 0.16f); lineTo(0.5f, 0.28f); lineTo(0.6f, 0.16f); lineTo(0.7f, 0.3f); close()
}

private val RED = Color(0xFFE53935)
private val LEAF = Color(0xFF43A047)
private val STEM = Color(0xFF6D4C41)

/** A cute fruit in the cell at [topLeft]: apple, cherries, strawberry or pear. */
fun DrawScope.drawFruit(kind: Int, topLeft: Offset, cell: Float) {
    translate(topLeft.x, topLeft.y) {
        scale(cell, cell, Offset.Zero) {
            when (kind % 4) {
                0 -> { // apple
                    drawLine(STEM, Offset(0.5f, 0.3f), Offset(0.52f, 0.12f), 0.06f)
                    drawCircle(RED.darker(0.2f), 0.33f, Offset(0.5f, 0.58f))
                    drawCircle(RED, 0.29f, Offset(0.5f, 0.56f))
                    drawCircle(Color.White, 0.07f, Offset(0.39f, 0.47f), alpha = 0.6f)
                    drawPath(leaf, LEAF)
                }
                1 -> { // cherries
                    drawLine(LEAF, Offset(0.3f, 0.62f), Offset(0.55f, 0.12f), 0.05f)
                    drawLine(LEAF, Offset(0.72f, 0.66f), Offset(0.55f, 0.12f), 0.05f)
                    drawCircle(RED.darker(0.25f), 0.2f, Offset(0.3f, 0.7f))
                    drawCircle(RED.darker(0.25f), 0.2f, Offset(0.72f, 0.74f))
                    drawCircle(Color.White, 0.05f, Offset(0.24f, 0.64f), alpha = 0.6f)
                    drawCircle(Color.White, 0.05f, Offset(0.66f, 0.68f), alpha = 0.6f)
                    drawPath(leaf, LEAF)
                }
                2 -> { // strawberry
                    drawPath(berry, RED)
                    for ((x, y) in arrayOf(0.38f to 0.45f, 0.62f to 0.45f, 0.5f to 0.58f, 0.4f to 0.7f, 0.6f to 0.7f)) {
                        drawCircle(Color(0xFFFFEB3B), 0.025f, Offset(x, y))
                    }
                    drawPath(berryTop, LEAF)
                }
                else -> { // pear
                    val pear = Color(0xFFC0CA33)
                    drawLine(STEM, Offset(0.5f, 0.24f), Offset(0.54f, 0.08f), 0.06f)
                    drawCircle(pear.darker(0.2f), 0.19f, Offset(0.5f, 0.38f))
                    drawCircle(pear.darker(0.2f), 0.28f, Offset(0.5f, 0.64f))
                    drawCircle(pear, 0.16f, Offset(0.5f, 0.38f))
                    drawCircle(pear, 0.25f, Offset(0.5f, 0.63f))
                    drawCircle(Color.White, 0.06f, Offset(0.4f, 0.56f), alpha = 0.5f)
                }
            }
        }
    }
}

/** Home screen picture: a curled snake on a lawn about to reach an apple. */
fun DrawScope.snakeThumbnail(p: Palette) {
    val body = listOf(15, 14, 13, 19, 25, 26, 27, 28)
    drawSnakeBoard(SnakeState(Difficulty.EASY, 6, body, dir = Snake.RIGHT, fruit = 17, fruitKind = 0, seed = 0), p)
}
