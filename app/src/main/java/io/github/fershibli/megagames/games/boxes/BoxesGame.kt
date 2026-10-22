package io.github.fershibli.megagames.games.boxes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.games.arcade.Arrow
import io.github.fershibli.megagames.games.arcade.ArrowPad
import io.github.fershibli.megagames.games.arcade.swipes
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.lighter
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Pause on the finished room before the end of the game. */
private const val DONE_PAUSE_MS = 1500L

/** How the last action looks while it plays: the friend's way and the box it pushed. */
private class Walk(val cells: List<Int>, val boxIndex: Int, val boxFrom: Int)

@Composable
fun BoxesGame(host: GameHost) {
    LoadGame(host, BoxesState.serializer(), { Boxes.newGame(host.difficulty) }) { state -> BoxesScreen(state, host) }
}

@Composable
private fun BoxesScreen(state: MutableState<BoxesState>, host: GameHost) {
    val s = state.value
    val p = LocalPalette.current
    val pace = currentPace
    val motion = currentMotion
    val left = LocalSettings.current.leftHanded
    val scale = LocalSettings.current.pieceScale
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val dead = remember(s.map) { Boxes.deadSquares(s.map, s.width, s.height) }
    val stuck = remember(s.boxes) { Boxes.stuckBox(s, dead) }
    var walk by remember { mutableStateOf<Walk?>(null) }
    val progress = remember { Animatable(1f) }
    var thinking by remember { mutableStateOf(false) }
    var noWay by remember { mutableStateOf(false) }

    LaunchedEffect(s.done) {
        if (!s.done) return@LaunchedEffect
        pace.wait(DONE_PAUSE_MS)
        val detail = buildString {
            append(resources.getString(R.string.boxes_done_detail, s.moves, s.pushes))
            if (s.hints > 0) append(' ').append(resources.getString(R.string.boxes_hints_used, s.hints))
        }
        host.finish(GameOutcome(true, detail))
    }

    // Plays an action and animates the friend along its way (installed once: reads the latest state).
    fun act(next: BoxesState?) {
        val cur = state.value
        if (next == null || cur.done || host.finished) return
        val cells = ArrayList<Int>()
        cells += cur.player
        var at = cur.player
        for (c in next.history.last()) {
            at = Boxes.next(at, "urdlURDL".indexOf(c) % 4, cur.width, cur.height)
            cells += at
        }
        val pushed = next.boxes.indices.firstOrNull { next.boxes[it] != cur.boxes[it] } ?: -1
        walk = Walk(cells, pushed, if (pushed >= 0) cur.boxes[pushed] else -1)
        noWay = false
        state.value = next
        scope.launch {
            progress.snapTo(0f)
            progress.animateTo(1f, motion.spec((110 * (cells.size - 1)).coerceAtMost(700), LinearEasing))
        }
    }

    val onArrow: (Arrow) -> Unit = { arrow ->
        val dir = when (arrow) {
            Arrow.UP -> 0
            Arrow.RIGHT -> 1
            Arrow.DOWN -> 2
            Arrow.LEFT -> 3
        }
        act(Boxes.step(state.value, dir))
    }

    GameLayout(
        status = {
            val msg = when {
                s.done -> stringResource(R.string.boxes_done)
                noWay -> stringResource(R.string.boxes_no_way)
                stuck >= 0 -> stringResource(R.string.boxes_stuck)
                thinking -> stringResource(R.string.boxes_thinking)
                s.hint >= 0 -> stringResource(R.string.boxes_hint, stringResource(arrowName(s.hint)))
                s.moves == 0 -> stringResource(R.string.boxes_goal)
                else -> stringResource(R.string.boxes_left, s.boxes.count { s.map[it] != Boxes.SPOT })
            }
            StatusPill(stringResource(R.string.boxes_counts, s.moves, s.pushes) + "\n" + msg, emphasized = s.done || stuck >= 0)
        },
        controls = {
            val playing = !s.done && !host.finished
            // The arrows keep their physical layout and sit under the thumb.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val buttons = @Composable {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BigButton(stringResource(R.string.undo), {
                                walk = null
                                noWay = false
                                state.value = Boxes.undo(state.value)
                            }, Modifier.fillMaxWidth(), icon = AppIcon.UNDO, style = if (stuck >= 0) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY, enabled = playing && s.history.isNotEmpty(), compact = true)
                            BigButton(stringResource(R.string.hint), {
                                if (thinking) return@BigButton
                                thinking = true
                                scope.launch {
                                    val cur = state.value
                                    val dir = withContext(Dispatchers.Default) { Boxes.hintStep(cur) }
                                    thinking = false
                                    if (state.value != cur) return@launch
                                    if (dir < 0) noWay = true else state.value = cur.copy(hint = dir, hints = cur.hints + 1)
                                }
                            }, Modifier.fillMaxWidth(), icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = playing, compact = true)
                            BigButton(stringResource(R.string.boxes_restart), {
                                walk = null
                                noWay = false
                                state.value = Boxes.restart(state.value)
                            }, Modifier.fillMaxWidth(), icon = AppIcon.NEW_GAME, style = ButtonStyle.SECONDARY, enabled = playing && s.history.isNotEmpty(), compact = true)
                        }
                    }
                    if (!left) buttons()
                    ArrowPad(
                        listOf(stringResource(R.string.boxes_up), stringResource(R.string.boxes_right), stringResource(R.string.boxes_down), stringResource(R.string.boxes_left_arrow)),
                        onArrow, enabled = playing, tagPrefix = "boxes",
                    )
                    if (left) buttons()
                }
            }
        },
    ) {
        val description = stringResource(R.string.boxes_board, s.boxes.size)
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            val cellDp = minOf(maxWidth / s.width, maxHeight / s.height)
            Canvas(
                Modifier
                    .size(cellDp * s.width, cellDp * s.height)
                    .semantics { contentDescription = description }
                    .testTag("boxes-board")
                    .swipes(onTap = { pos, size ->
                        val cur = state.value
                        val cell = size.width / cur.width.toFloat()
                        val x = (pos.x / cell).toInt().coerceIn(0, cur.width - 1)
                        val y = (pos.y / cell).toInt().coerceIn(0, cur.height - 1)
                        act(Boxes.tap(cur, y * cur.width + x))
                    }, onArrow = onArrow),
            ) {
                drawRoom(p, s, walk, progress.value, stuck, scale)
            }
        }
    }
}

private fun arrowName(dir: Int) = when (dir) {
    0 -> R.string.boxes_up
    1 -> R.string.boxes_right
    2 -> R.string.boxes_down
    else -> R.string.boxes_left_arrow
}

/** Centre of square [i] in a room [w] squares wide. */
private fun center(i: Int, w: Int, cell: Float) = Offset((i % w + 0.5f) * cell, (i / w + 0.5f) * cell)

/**
 * Draws the room: brick walls, a tiled floor, round spots, wooden crates (green with a tick once
 * on a spot) and the round friend. While [progress] < 1 the friend walks along [walk].
 */
private fun DrawScope.drawRoom(p: Palette, s: BoxesState, walk: Walk?, progress: Float, stuck: Int, scale: Float) {
    val w = s.width
    val cell = size.width / w
    for (i in s.map.indices) {
        val tl = Offset((i % w) * cell, (i / w) * cell)
        when (s.map[i]) {
            Boxes.WALL -> {
                drawRect(p.woodDark, tl, Size(cell, cell))
                // Two rows of bricks.
                val mortar = p.woodDark.darker(0.35f)
                drawLine(mortar, tl + Offset(0f, cell / 2), tl + Offset(cell, cell / 2), cell * 0.04f)
                drawLine(mortar, tl + Offset(cell * 0.5f, 0f), tl + Offset(cell * 0.5f, cell / 2), cell * 0.04f)
                drawLine(mortar, tl + Offset(cell * 0.25f, cell / 2), tl + Offset(cell * 0.25f, cell), cell * 0.04f)
                drawLine(mortar, tl + Offset(cell * 0.75f, cell / 2), tl + Offset(cell * 0.75f, cell), cell * 0.04f)
            }
            Boxes.FLOOR, Boxes.SPOT -> {
                drawRect(p.boardLight, tl, Size(cell, cell))
                drawRect(p.boardLine.copy(alpha = 0.12f), tl, Size(cell, cell), style = Stroke(cell * 0.02f))
            }
        }
        if (s.map[i] == Boxes.SPOT) {
            val c = center(i, w, cell)
            drawCircle(p.hint.copy(alpha = 0.45f), cell * 0.3f, c)
            drawCircle(p.hint.darker(0.35f), cell * 0.3f, c, style = Stroke(cell * 0.05f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 0.09f, cell * 0.06f))))
        }
    }
    // Where the friend and the pushed box are now, part way along the last action.
    val cells = walk?.cells
    val steps = (cells?.size ?: 1) - 1
    val friend: Offset = if (cells != null && progress < 1f && steps > 0) {
        val t = progress * steps
        val k = t.toInt().coerceAtMost(steps - 1)
        val a = center(cells[k], w, cell)
        a + (center(cells[k + 1], w, cell) - a) * (t - k)
    } else center(s.player, w, cell)
    s.boxes.forEachIndexed { bi, b ->
        var at = center(b, w, cell)
        if (walk != null && bi == walk.boxIndex && progress < 1f) {
            val from = center(walk.boxFrom, w, cell)
            at = from + (at - from) * progress
        }
        drawCrate(p, at, cell, onSpot = s.map[b] == Boxes.SPOT && (walk == null || bi != walk.boxIndex || progress >= 1f), stuck = b == stuck)
    }
    drawFriend(p, friend, cell * scale.coerceIn(0.85f, 1.15f), happy = s.done)
    if (s.hint >= 0) {
        // A bold arrow from the friend in the hinted direction.
        val c = center(s.player, w, cell)
        val dir = Offset(Boxes.dx(s.hint).toFloat(), Boxes.dy(s.hint).toFloat())
        val side = Offset(-dir.y, dir.x)
        val tip = c + dir * cell * 0.95f
        drawLine(p.hint.darker(0.3f), c + dir * cell * 0.4f, tip - dir * cell * 0.15f, cell * 0.12f, StrokeCap.Round)
        val head = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo((tip - dir * cell * 0.25f + side * cell * 0.2f).x, (tip - dir * cell * 0.25f + side * cell * 0.2f).y)
            lineTo((tip - dir * cell * 0.25f - side * cell * 0.2f).x, (tip - dir * cell * 0.25f - side * cell * 0.2f).y)
            close()
        }
        drawPath(head, p.hint.darker(0.3f))
    }
}

/** A wooden crate with cross planks; green with a tick on a spot, ringed in red when stuck. */
private fun DrawScope.drawCrate(p: Palette, c: Offset, cell: Float, onSpot: Boolean, stuck: Boolean) {
    val half = cell * 0.4f
    val tl = c - Offset(half, half)
    val body = if (onSpot) p.success else p.wood
    drawRoundRect(Color.Black.copy(alpha = 0.2f), tl + Offset(cell * 0.03f, cell * 0.05f), Size(half * 2, half * 2), CornerRadius(cell * 0.08f))
    drawRoundRect(body, tl, Size(half * 2, half * 2), CornerRadius(cell * 0.08f))
    val plank = body.darker(0.3f)
    drawRoundRect(plank, tl, Size(half * 2, half * 2), CornerRadius(cell * 0.08f), style = Stroke(cell * 0.07f))
    if (onSpot) {
        val tick = Path().apply {
            moveTo(c.x - half * 0.5f, c.y)
            lineTo(c.x - half * 0.1f, c.y + half * 0.42f)
            lineTo(c.x + half * 0.55f, c.y - half * 0.4f)
        }
        drawPath(tick, body.lighter(0.85f), style = Stroke(cell * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    } else {
        drawLine(plank, tl + Offset(cell * 0.1f, cell * 0.1f), tl + Offset(half * 2 - cell * 0.1f, half * 2 - cell * 0.1f), cell * 0.06f)
        drawLine(plank, tl + Offset(half * 2 - cell * 0.1f, cell * 0.1f), tl + Offset(cell * 0.1f, half * 2 - cell * 0.1f), cell * 0.06f)
    }
    if (stuck) drawCircle(p.danger, half * 1.25f, c, style = Stroke(cell * 0.07f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 0.12f, cell * 0.08f))))
}

/** The round friend who pushes the boxes. */
private fun DrawScope.drawFriend(p: Palette, at: Offset, cell: Float, happy: Boolean) {
    val r = cell * 0.32f
    drawCircle(Color.Black.copy(alpha = 0.2f), r, at + Offset(cell * 0.03f, cell * 0.05f))
    drawCircle(p.primary, r, at)
    drawCircle(p.onPrimary, r, at, style = Stroke(cell * 0.05f))
    drawCircle(p.onPrimary, r * 0.13f, at + Offset(-r * 0.35f, -r * 0.2f))
    drawCircle(p.onPrimary, r * 0.13f, at + Offset(r * 0.35f, -r * 0.2f))
    val smile = if (happy) 160f else 140f
    drawArc(p.onPrimary, 90f - smile / 2, smile, false, at + Offset(-r * 0.45f, -r * 0.25f), Size(r * 0.9f, r * 0.8f), style = Stroke(r * 0.12f, cap = StrokeCap.Round))
}

/** The small room of the home-screen picture (made once). */
private val THUMBNAIL: BoxesState by lazy { Boxes.makeRoom(Boxes.recipe(io.github.fershibli.megagames.core.Difficulty.EASY), Random(3)) ?: Boxes.newGame(io.github.fershibli.megagames.core.Difficulty.EASY, Random(3)) }

/** Home-screen picture: a small room with two crates and the friend. */
fun boxesThumbnail(scope: DrawScope, p: Palette) = scope.drawRoom(p, THUMBNAIL, null, 1f, -1, 1f)
