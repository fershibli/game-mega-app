package io.github.fershibli.megagames.games.merge

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.core.WorldStore
import io.github.fershibli.megagames.core.rememberWorldStore
import io.github.fershibli.megagames.games.arcade.Arrow
import io.github.fershibli.megagames.games.arcade.ArrowPad
import io.github.fershibli.megagames.games.arcade.KeepBest
import io.github.fershibli.megagames.games.arcade.Records
import io.github.fershibli.megagames.games.arcade.swipes
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.pxToSp
import io.github.fershibli.megagames.ui.art.text
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.BigDialog
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

/** Pause on the full board before the end of the game. */
private const val END_PAUSE_MS = 1500L

/**
 * Calm pastel tiles, one per number (2, 4, 8, ...), cool and soft rather than warm. The numbers
 * on them are always near-black, so they read well on every tile and in dark mode too.
 */
private val TILE_COLORS = listOf(
    Color(0xFFD6E8F7), Color(0xFFD5F0E1), Color(0xFFFBE6D2), Color(0xFFE6DAF4), Color(0xFFFFF1BF),
    Color(0xFFCDEDEC), Color(0xFFF6D7DE), Color(0xFFDCE8C6), Color(0xFFC9DAF5), Color(0xFFF2D9C4),
    Color(0xFFDDD0EE), Color(0xFFCFE6CB),
)
private val INK = Color(0xFF1E2228)

/** Position of a tile number in the list of tiles: 2 is 1, 4 is 2, 8 is 3... */
private fun exponent(value: Int) = Integer.numberOfTrailingZeros(value)

/** How the last slide looks while it moves: the tiles before it and where each one went. */
private class SlideAnimation(val before: List<Int>, val target: IntArray, val merged: Set<Int>, val spawned: Int)

@Composable
fun MergeGame(host: GameHost) {
    val store = rememberWorldStore()
    LoadGame(host, MergeState.serializer(), {
        MergeRules.newGame(host.difficulty, recordToBeat = Records.best(store, host.game, host.difficulty))
    }) { state -> MergeScreen(state, host, store) }
}

@Composable
private fun MergeScreen(state: MutableState<MergeState>, host: GameHost, store: WorldStore) {
    val s = state.value
    val p = LocalPalette.current
    val pace = currentPace
    val motion = currentMotion
    val left = LocalSettings.current.leftHanded
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    var animation by remember { mutableStateOf<SlideAnimation?>(null) }
    // 0 → 1 while tiles slide, then 0 → 1 again while the new tile grows.
    val slide = remember { Animatable(1f) }
    val pop = remember { Animatable(1f) }
    var goalAsked by remember { mutableStateOf(false) }
    val canMove = remember(s.tiles) { MergeRules.canMove(s) }
    val best = maxOf(s.score, s.recordToBeat)
    KeepBest(store, host.game, host.difficulty, s.score, s.recordToBeat)

    fun finishDetail(): String = listOfNotNull(
        if (s.score > s.recordToBeat) resources.getString(R.string.merge_new_best) else null,
        resources.getString(R.string.merge_final, s.score, s.biggest),
    ).joinToString("\n")

    LaunchedEffect(canMove) {
        if (canMove) return@LaunchedEffect
        pace.wait(END_PAUSE_MS)
        host.finish(GameOutcome(s.goalReached, finishDetail()))
    }

    // Installed once by the swipe detector: always reads the latest state.
    val play: (Arrow) -> Unit = play@{ arrow ->
        val cur = state.value
        if (host.finished || (cur.goalReached && !cur.keepGoing) || slide.isRunning) return@play
        val dir = when (arrow) {
            Arrow.UP -> MergeRules.UP
            Arrow.RIGHT -> MergeRules.RIGHT
            Arrow.DOWN -> MergeRules.DOWN
            Arrow.LEFT -> MergeRules.LEFT
        }
        val (next, moved) = MergeRules.move(cur, dir) ?: return@play
        animation = SlideAnimation(cur.tiles, moved.target, moved.merged, MergeRules.spawned(moved, next))
        state.value = next
        scope.launch {
            slide.snapTo(0f)
            pop.snapTo(0f)
            slide.animateTo(1f, motion.spec(140))
            pop.animateTo(1f, motion.spec(180))
        }
    }

    if (s.goalReached && !s.keepGoing && !host.finished) {
        if (!goalAsked) {
            LaunchedEffect(Unit) {
                pace.wait(600)
                goalAsked = true
            }
        } else {
            BigDialog(
                title = stringResource(R.string.merge_goal_title, s.goal),
                message = stringResource(R.string.merge_goal_message),
                onDismiss = { state.value = state.value.copy(keepGoing = true) },
                buttons = listOf(
                    stringResource(R.string.merge_keep_playing) to { state.value = state.value.copy(keepGoing = true) },
                    stringResource(R.string.merge_finish) to { host.finish(GameOutcome(true, finishDetail())) },
                ),
            )
        }
    }

    GameLayout(
        status = {
            val msg = when {
                !canMove -> stringResource(R.string.merge_no_moves)
                s.keepGoing -> stringResource(R.string.merge_keep_going)
                else -> stringResource(R.string.merge_goal, s.goal)
            }
            StatusPill(stringResource(R.string.merge_score, s.score, best) + "\n" + msg, emphasized = !canMove)
        },
        controls = {
            // The arrows keep their physical layout and sit under the thumb.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val undo = @Composable {
                        BigButton(
                            stringResource(R.string.undo), {
                                animation = null
                                state.value = MergeRules.undo(state.value)
                            },
                            Modifier.weight(1f), icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY,
                            enabled = s.undo.isNotEmpty() && !host.finished,
                        )
                    }
                    if (!left) undo()
                    ArrowPad(
                        listOf(stringResource(R.string.merge_up), stringResource(R.string.merge_right), stringResource(R.string.merge_down), stringResource(R.string.merge_left)),
                        play, enabled = canMove && !host.finished, tagPrefix = "merge",
                    )
                    if (left) undo()
                }
            }
        },
    ) {
        val description = stringResource(R.string.merge_board, s.size, s.size)
        BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            Canvas(
                Modifier
                    .size(side)
                    .semantics { contentDescription = description }
                    .testTag("merge-board")
                    .swipes(onArrow = play),
            ) {
                val a = animation
                if (a != null && slide.value < 1f) {
                    drawMergeBoard(p, measurer, s.size, a.before, slide.value, a.target, emptySet(), -1, 1f)
                } else {
                    drawMergeBoard(p, measurer, s.size, s.tiles, 1f, null, a?.merged ?: emptySet(), a?.spawned ?: -1, pop.value)
                }
            }
        }
    }
}

/**
 * Draws the board. While a slide runs ([progress] < 1), every tile of [tiles] glides towards
 * its [target] square; afterwards [merged] tiles swell a little and the [spawned] tile grows
 * in, both following [pop].
 */
private fun DrawScope.drawMergeBoard(
    p: Palette,
    measurer: TextMeasurer?,
    n: Int,
    tiles: List<Int>,
    progress: Float,
    target: IntArray?,
    merged: Set<Int>,
    spawned: Int,
    pop: Float,
) {
    val gap = size.width * 0.025f
    val cell = (size.width - gap * (n + 1)) / n
    drawRoundRect(p.surfaceAlt, Offset.Zero, size, CornerRadius(gap * 3))
    if (p.strongOutlines) drawRoundRect(p.outline, Offset.Zero, size, CornerRadius(gap * 3), style = Stroke(gap * 0.6f))
    fun corner(i: Int) = Offset(gap + (i % n) * (cell + gap), gap + (i / n) * (cell + gap))
    for (i in 0 until n * n) drawRoundRect(p.background.copy(alpha = 0.7f), corner(i), Size(cell, cell), CornerRadius(cell * 0.16f))
    for (i in 0 until n * n) {
        val v = tiles[i]
        if (v == 0) continue
        var at = corner(i)
        if (target != null && target[i] >= 0) at += (corner(target[i]) - at) * progress
        val grow = when (i) {
            spawned -> pop
            in merged -> 1f + 0.12f * (1f - kotlin.math.abs(pop * 2f - 1f))
            else -> 1f
        }
        drawTile(p, measurer, v, at, cell, grow)
    }
}

/** One tile: a pastel square with its number and, in the accessible palettes, a row of dots. */
private fun DrawScope.drawTile(p: Palette, measurer: TextMeasurer?, value: Int, topLeft: Offset, cell: Float, grow: Float) {
    if (grow <= 0.01f) return
    val e = exponent(value)
    val color = TILE_COLORS[(e - 1).coerceAtLeast(0) % TILE_COLORS.size]
    val side = cell * grow
    val tl = topLeft + Offset((cell - side) / 2, (cell - side) / 2)
    drawRoundRect(Color.Black.copy(alpha = 0.16f), tl + Offset(0f, side * 0.04f), Size(side, side), CornerRadius(side * 0.16f))
    drawRoundRect(color, tl, Size(side, side), CornerRadius(side * 0.16f))
    drawRoundRect(color.darker(if (p.strongOutlines) 0.7f else 0.18f), tl, Size(side, side), CornerRadius(side * 0.16f), style = Stroke(side * if (p.strongOutlines) 0.05f else 0.025f))
    val digits = value.toString().length
    val textSize = side * when {
        digits <= 2 -> 0.46f
        digits == 3 -> 0.38f
        else -> 0.3f
    }
    val center = tl + Offset(side / 2, side * if (p.symbols) 0.44f else 0.5f)
    if (measurer != null) text(measurer, value.toString(), center, INK, pxToSp(textSize))
    if (p.symbols) {
        // As many dots as the step of the number (2 = 1 dot, 4 = 2 dots...), two rows at most.
        val perRow = 6
        val r = side * 0.035f
        for (k in 0 until e.coerceAtMost(12)) {
            val row = k / perRow
            val inRow = minOf(perRow, e - row * perRow)
            val x = tl.x + side / 2 + (k % perRow - (inRow - 1) / 2f) * r * 3f
            val y = tl.y + side * (0.78f + row * 0.1f)
            drawCircle(INK, r, Offset(x, y))
        }
    }
}

/** Home-screen picture: a 4×4 board with a few pastel tiles (no numbers at this size). */
fun mergeThumbnail(scope: DrawScope, p: Palette) = scope.drawMergeBoard(p, null, 4, THUMBNAIL_TILES, 1f, null, emptySet(), -1, 1f)

private val THUMBNAIL_TILES = listOf(2, 0, 0, 4, 0, 8, 0, 0, 16, 32, 4, 0, 64, 128, 8, 2)
