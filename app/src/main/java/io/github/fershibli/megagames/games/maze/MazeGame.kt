package io.github.fershibli.megagames.games.maze

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.LocalSettings
import io.github.fershibli.megagames.ui.theme.Palette
import io.github.fershibli.megagames.ui.theme.currentMotion
import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.spec
import io.github.fershibli.megagames.ui.theme.wait
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class MazeState(
    val size: Int,
    /** Open sides of every cell (bits [Maze.N], [Maze.E], [Maze.S], [Maze.W]). */
    val open: List<Int>,
    /** The path drawn by the player, from the start; it never crosses itself. */
    val path: List<Int> = listOf(0),
    /** Cells shown by the last Hint (cleared by the next move). */
    val hint: List<Int> = emptyList(),
    val hints: Int = 0,
) {
    val goal: Int get() = size * size - 1
    val done: Boolean get() = path.last() == goal
}

fun newMaze(size: Int, random: Random = Random.Default) = MazeState(size, Maze.generate(size, random))

/** Pause on the finished maze, with the whole way lit up, before the end of the game. */
private const val DONE_PAUSE_MS = 1800L

@Composable
fun MazeGame(host: GameHost) {
    LoadGame(host, MazeState.serializer(), { newMaze(Maze.sizeFor(host.difficulty)) }) { state -> MazeScreen(state, host) }
}

@Composable
private fun MazeScreen(state: MutableState<MazeState>, host: GameHost) {
    val s = state.value
    val p = LocalPalette.current
    val pace = currentPace
    val motion = currentMotion
    val scale = LocalSettings.current.pieceScale
    val doneText = stringResource(R.string.maze_done)
    val hintsText = stringResource(R.string.maze_hints_used, s.hints)
    // The way lights up from the start to the house once the house is reached.
    val glow = remember { Animatable(if (s.done) 1f else 0f) }

    LaunchedEffect(s.done) {
        if (!s.done) return@LaunchedEffect
        glow.animateTo(1f, motion.spec(1200))
        pace.wait(DONE_PAUSE_MS)
        host.finish(GameOutcome(true, if (s.hints > 0) "$doneText $hintsText" else doneText))
    }

    fun move(path: List<Int>) {
        val cur = state.value
        if (cur.done || path == cur.path) return
        state.value = cur.copy(path = path, hint = emptyList())
    }

    GameLayout(
        status = {
            val msg = when {
                s.done -> doneText
                s.hint.isNotEmpty() && s.hint.first() in s.path -> stringResource(R.string.maze_hint_back)
                s.hint.isNotEmpty() -> stringResource(R.string.maze_hint_next)
                s.path.size == 1 -> stringResource(R.string.maze_start)
                else -> stringResource(R.string.maze_going)
            }
            StatusPill(msg, emphasized = s.done)
        },
        controls = {
            ActionBar {
                BigButton(stringResource(R.string.undo), { move(state.value.path.dropLast(1)) }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = s.path.size > 1 && !s.done, compact = true)
                BigButton(stringResource(R.string.hint), {
                    val cur = state.value
                    if (!cur.done) state.value = cur.copy(hint = Maze.hint(cur.open, cur.size, cur.path, Maze.hintSteps(host.difficulty)), hints = cur.hints + 1)
                }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = !s.done, compact = true)
            }
        },
    ) {
        val n = s.size
        val description = stringResource(R.string.maze_board, n, n)
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            Canvas(
                Modifier
                    .size(side)
                    .semantics { contentDescription = description }
                    .testTag("maze-board")
                    .pointerInput(n) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            if (state.value.done) return@awaitEachGesture
                            val cellPx = size.width / n.toFloat()
                            fun cellAt(o: Offset): Int {
                                val r = (o.y / cellPx).toInt().coerceIn(0, n - 1)
                                val c = (o.x / cellPx).toInt().coerceIn(0, n - 1)
                                return r * n + c
                            }
                            val start = cellAt(down.position)
                            var dragged = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.isConsumed) return@awaitEachGesture // a zoom gesture took over
                                if (!change.pressed) break
                                if (!dragged && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    dragged = true
                                    val cur = state.value
                                    // A drag that starts away from the path first walks there along a corridor.
                                    move(Maze.tap(cur.open, n, cur.path, start))
                                }
                                if (dragged) {
                                    val cur = state.value
                                    move(Maze.drag(cur.open, n, cur.path, cellAt(change.position)))
                                    change.consume()
                                }
                            }
                            if (!dragged) {
                                val cur = state.value
                                move(Maze.tap(cur.open, n, cur.path, start))
                            }
                        }
                    },
            ) {
                drawMaze(p, s, glow.value, scale)
            }
        }
    }
}

/** Centre of [cell] on a board of [cell]-sized squares. */
private fun center(i: Int, n: Int, cell: Float) = Offset((i % n + 0.5f) * cell, (i / n + 0.5f) * cell)

/**
 * Draws the maze on paper: a soft grain, the path, the hint dots, thick walls, the house at the
 * finish and the round friend at the end of the path. [glow] lights the finished way.
 */
fun DrawScope.drawMaze(p: Palette, s: MazeState, glow: Float, scale: Float) {
    val n = s.size
    val cell = size.width / n
    drawRect(p.boardLight)
    // Paper grain: a fixed pattern of faint flecks (no random numbers, so it never flickers).
    val fleck = p.boardLine.copy(alpha = 0.06f)
    for (i in 0 until n * n) {
        val k = (i * 7919) % 97
        drawCircle(fleck, cell * 0.05f, Offset((i % n + k / 97f) * cell, (i / n + ((k * 31) % 97) / 97f) * cell))
    }
    // The start and finish squares, so both stand out at a glance.
    drawRoundRect(p.primary.copy(alpha = 0.22f), Offset.Zero, Size(cell, cell), CornerRadius(cell * 0.15f))
    drawRoundRect(p.highlight.copy(alpha = 0.55f), Offset((n - 1) * cell, (n - 1) * cell), Size(cell, cell), CornerRadius(cell * 0.15f))
    // The path drawn by the player.
    val lineWidth = cell * 0.36f * scale.coerceIn(0.8f, 1.3f)
    if (s.path.size > 1) {
        val line = Path()
        s.path.forEachIndexed { k, c ->
            val o = center(c, n, cell)
            if (k == 0) line.moveTo(o.x, o.y) else line.lineTo(o.x, o.y)
        }
        if (glow > 0f) drawPath(line, p.highlight, style = Stroke(lineWidth * (1f + 0.8f * glow), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(line, if (glow > 0f) p.success else p.primary, style = Stroke(lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    // Hint: dashed rings on the next cells, the first one bigger.
    s.hint.forEachIndexed { k, c ->
        val r = cell * if (k == 0) 0.36f else 0.24f
        drawCircle(p.hint, r, center(c, n, cell), style = Stroke(cell * 0.09f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 0.12f, cell * 0.08f))))
    }
    // Walls: thick, round-ended lines, and the outer border.
    val wall = cell * if (p.strongOutlines) 0.16f else 0.12f
    val ink = p.boardLine
    for (i in 0 until n * n) {
        val x = (i % n) * cell
        val y = (i / n) * cell
        if (s.open[i] and Maze.E == 0 && i % n < n - 1) drawLine(ink, Offset(x + cell, y), Offset(x + cell, y + cell), wall, StrokeCap.Round)
        if (s.open[i] and Maze.S == 0 && i / n < n - 1) drawLine(ink, Offset(x, y + cell), Offset(x + cell, y + cell), wall, StrokeCap.Round)
    }
    drawRect(ink, Offset(wall / 2, wall / 2), Size(size.width - wall, size.height - wall), style = Stroke(wall))
    drawHouse(p, center(s.goal, n, cell), cell)
    drawFriend(p, center(s.path.last(), n, cell), cell, happy = s.done)
}

/** The goal: a little house with a roof, a door and a window. */
private fun DrawScope.drawHouse(p: Palette, c: Offset, cell: Float) {
    val w = cell * 0.62f
    val body = Offset(c.x - w / 2, c.y - w * 0.1f)
    val outline = Stroke(cell * 0.05f)
    drawRect(p.cardFace, body, Size(w, w * 0.55f))
    drawRect(p.boardLine, body, Size(w, w * 0.55f), style = outline)
    val roof = Path().apply {
        moveTo(c.x - w * 0.62f, body.y)
        lineTo(c.x, c.y - w * 0.62f)
        lineTo(c.x + w * 0.62f, body.y)
        close()
    }
    drawPath(roof, p.pieces[0 % p.pieces.size])
    drawPath(roof, p.boardLine, style = outline)
    drawRect(p.boardLine, Offset(c.x - w * 0.1f, body.y + w * 0.25f), Size(w * 0.2f, w * 0.3f))
}

/** The start: a round friend with a smile, who moves along with the end of the path. */
private fun DrawScope.drawFriend(p: Palette, c: Offset, cell: Float, happy: Boolean) {
    val r = cell * if (happy) 0.26f else 0.3f
    val at = if (happy) c + Offset(-cell * 0.22f, cell * 0.2f) else c
    drawCircle(p.primary, r, at)
    drawCircle(p.onPrimary, r, at, style = Stroke(cell * 0.05f))
    drawCircle(p.onPrimary, r * 0.13f, at + Offset(-r * 0.35f, -r * 0.2f))
    drawCircle(p.onPrimary, r * 0.13f, at + Offset(r * 0.35f, -r * 0.2f))
    drawArc(p.onPrimary, 20f, 140f, false, at + Offset(-r * 0.45f, -r * 0.25f), Size(r * 0.9f, r * 0.8f), style = Stroke(r * 0.12f, cap = StrokeCap.Round))
}

/** The small maze of the home-screen picture, with the friend halfway to the house (made once). */
private val THUMBNAIL: MazeState by lazy {
    val maze = MazeState(5, Maze.generate(5, Random(7)))
    val way = Maze.solution(maze.open, 5)
    maze.copy(path = way.take(way.size / 2 + 1))
}

/** Home-screen picture: a small maze with the friend on the way to the house. */
fun mazeThumbnail(scope: DrawScope, p: Palette) = scope.drawMaze(p, THUMBNAIL, 0f, 1f)
