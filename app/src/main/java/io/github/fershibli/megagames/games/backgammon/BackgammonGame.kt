package io.github.fershibli.megagames.games.backgammon

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.glossyDisc
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.art.pxToSp
import io.github.fershibli.megagames.ui.art.raisedBlock
import io.github.fershibli.megagames.ui.art.text
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.LocalSettings
import io.github.fershibli.megagames.ui.theme.LocalUpdateSettings
import io.github.fershibli.megagames.ui.theme.Palette
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class BackgammonState(
    val position: BgPosition,
    val turn: Int = HUMAN,
    /** The dice rolled this turn (empty = the human still has to roll). */
    val roll: List<Int> = emptyList(),
    /** Position at the start of the human's turn, for "undo". */
    val turnStart: BgPosition? = null,
    /** Moves the human has already made this turn. */
    val played: List<BgMove> = emptyList(),
    /** The computer's planned moves for [roll] (shown one by one, then applied). */
    val aiMoves: List<BgMove> = emptyList(),
)

@Composable
fun BackgammonGame(host: GameHost) {
    LoadGame(host, BackgammonState.serializer(), {
        // Opening roll decides who starts; on a tie, roll again.
        val r = Random.Default
        var a: Int
        var b: Int
        do { a = r.nextInt(1, 7); b = r.nextInt(1, 7) } while (a == b)
        if (a > b) BackgammonState(BgPosition.initial(), HUMAN, listOf(a, b), BgPosition.initial())
        else BackgammonState(BgPosition.initial(), AI)
    }) { state -> BackgammonScreen(state, host) }
}

/**
 * Board geometry shared by drawing and touch handling, always described "standing up": the 24
 * points run down two columns, your home (points 1-6) at the bottom left, the tray for borne-off
 * checkers along the bottom and the bar across the middle.
 *
 * When [sideways] is true (the default) that board is turned a quarter turn counter-clockwise on
 * screen, which gives the traditional horizontal board: points 1-12 along the bottom with your
 * home at the bottom right, 13-24 along the top, the bar down the middle and the tray on the
 * right. The checkers are smaller, so it is meant to be used with pinch zoom. Standing up, the
 * board fills the screen and the checkers are bigger.
 *
 * [width] and [height] are the board's own (standing) size; [canvasHeight] is the height of the
 * area it is drawn in.
 */
private class BoardGeometry(canvasWidth: Float, private val canvasHeight: Float, val sideways: Boolean) {
    val width = if (sideways) canvasHeight else canvasWidth
    val height = if (sideways) canvasWidth else canvasHeight
    val frame = width * 0.025f
    val tray = height * 0.075f
    val bar = height * 0.09f
    val inner = Rect(frame, frame, width - frame, height - frame - tray)
    val ph = (inner.height - bar) / 12f
    val halfW = inner.width / 2f
    val triangle = inner.width * 0.42f
    val trayRect = Rect(frame, height - frame - tray + frame * 0.4f, width - frame, height - frame * 0.4f)
    val barRect = Rect(inner.left, inner.top + ph * 6, inner.right, inner.top + ph * 6 + bar)
    val radius = minOf(ph * 0.46f, halfW / 12.5f)

    /** Absolute point number -> its row in the left (1-12) or right (13-24) column. */
    fun pointRect(p: Int): Rect {
        val left = p <= 12
        val idx = if (left) 12 - p else p - 13 // 0 = top row
        val y = inner.top + idx * ph + (if (idx >= 6) bar else 0f)
        return if (left) Rect(inner.left, y, inner.left + halfW, y + ph) else Rect(inner.left + halfW, y, inner.right, y + ph)
    }

    /** Screen position -> position on the standing board (the inverse of [orient]). */
    fun toBoard(o: Offset) = if (sideways) Offset(canvasHeight - o.y, o.x) else o

    fun hit(screen: Offset): Int? {
        val o = toBoard(screen)
        if (trayRect.contains(o)) return 0
        if (barRect.contains(o)) return 25
        for (p in 1..24) if (pointRect(p).contains(o)) return p
        return null
    }
}

@Composable
private fun BackgammonScreen(state: MutableState<BackgammonState>, host: GameHost) {
    val s = state.value
    val context = LocalResources.current
    val settings = LocalSettings.current
    val updateSettings = LocalUpdateSettings.current
    val sideways = !settings.backgammonUpright
    var selected by remember { mutableIntStateOf(-1) }
    var noMoves by remember { mutableIntStateOf(0) }

    // All legal sequences for the human's roll, computed from the start of the turn.
    val sequences = remember(s.turnStart, s.roll, s.turn) {
        if (s.turn == HUMAN && s.roll.isNotEmpty() && s.turnStart != null) {
            Backgammon.sequences(Backgammon.view(s.turnStart, HUMAN), Backgammon.diceList(s.roll[0], s.roll[1]))
        } else emptyList()
    }
    val nextMoves = remember(sequences, s.played) {
        sequences.filter { it.size > s.played.size && it.subList(0, s.played.size) == s.played }.map { it[s.played.size] }.distinct()
    }
    val turnComplete = s.turn == HUMAN && s.roll.isNotEmpty() && nextMoves.isEmpty()
    val usedDice = s.played.map { it.die }
    val win = Backgammon.winner(s.position)

    LaunchedEffect(win) {
        if (win != 0) {
            val mult = Backgammon.winMultiplier(s.position, win)
            val detail = when (mult) {
                3 -> context.getString(R.string.backgammon_backgammon)
                2 -> context.getString(R.string.backgammon_gammon)
                else -> null
            }
            delay(600)
            host.finish(GameOutcome(win == HUMAN, detail))
        }
    }

    // Computer's turn: roll and plan (saved, so a restart replays the same plan), then show the
    // moves one by one and finally commit the new position.
    var aiShown by remember { mutableIntStateOf(0) }
    LaunchedEffect(s.turn, s.roll, s.aiMoves, win) {
        if (s.turn != AI || win != 0) return@LaunchedEffect
        if (s.roll.isEmpty()) {
            delay(700)
            val r = Random.Default
            val roll = listOf(r.nextInt(1, 7), r.nextInt(1, 7))
            val view = Backgammon.view(s.position, AI)
            val moves = withContext(Dispatchers.Default) { Backgammon.choose(view, Backgammon.diceList(roll[0], roll[1]), host.difficulty, r) }
            aiShown = 0
            state.value = s.copy(roll = roll, aiMoves = moves)
            return@LaunchedEffect
        }
        delay(700)
        for (k in 1..s.aiMoves.size) {
            aiShown = k
            delay(650)
        }
        if (s.aiMoves.isEmpty()) delay(900)
        val end = Backgammon.play(Backgammon.view(s.position, AI), s.aiMoves)
        aiShown = 0
        state.value = BackgammonState(Backgammon.toPosition(end, AI), HUMAN)
    }
    val shownPosition = if (s.turn == AI && aiShown > 0) {
        Backgammon.toPosition(Backgammon.play(Backgammon.view(s.position, AI), s.aiMoves.take(aiShown)), AI)
    } else s.position

    fun roll() {
        val r = Random.Default
        val roll = listOf(r.nextInt(1, 7), r.nextInt(1, 7))
        state.value = s.copy(roll = roll, turnStart = s.position, played = emptyList())
        selected = -1
        noMoves = 0
    }

    fun move(m: BgMove) {
        val v = Backgammon.play(Backgammon.view(s.position, HUMAN), listOf(m))
        state.value = s.copy(position = Backgammon.toPosition(v, HUMAN), played = s.played + m)
        selected = -1
    }

    fun endTurn() {
        selected = -1
        state.value = BackgammonState(s.position, AI)
    }

    fun tap(target: Int?) {
        if (s.turn != HUMAN || s.roll.isEmpty() || target == null) return
        // Tapping a destination of the selected checker moves it.
        if (selected >= 0) {
            val m = nextMoves.firstOrNull { it.from == selected && it.to == target }
            if (m != null) { move(m); return }
        }
        val from = if (target == 0) -1 else target
        selected = if (nextMoves.any { it.from == from }) from else -1
        // A single possible destination: move right away (fewer taps).
        val dests = nextMoves.filter { it.from == selected }.distinctBy { it.to }
        if (dests.size == 1 && selected >= 0) move(dests.first())
    }

    GameLayout(
        status = {
            val text = when {
                s.turn == AI -> stringResource(R.string.computer_turn)
                s.roll.isEmpty() -> stringResource(R.string.backgammon_roll_prompt)
                turnComplete && sequences.isEmpty() -> stringResource(R.string.backgammon_no_moves)
                turnComplete -> stringResource(R.string.backgammon_done_prompt)
                selected >= 0 -> stringResource(R.string.backgammon_pick_target)
                else -> stringResource(R.string.backgammon_pick_checker)
            }
            StatusPill(stringResource(R.string.backgammon_off, shownPosition.offHuman, shownPosition.offAi) + "\n" + text, emphasized = s.turn == HUMAN)
        },
        controls = {
            ActionBar {
                when {
                    s.turn == HUMAN && s.roll.isEmpty() -> BigButton(stringResource(R.string.backgammon_roll), ::roll, icon = AppIcon.DICE, compact = true)
                    else -> BigButton(stringResource(R.string.backgammon_done), ::endTurn, icon = AppIcon.CHECK, enabled = turnComplete, compact = true)
                }
                BigButton(stringResource(R.string.undo), {
                    s.turnStart?.let { state.value = s.copy(position = it, played = emptyList()) }
                    selected = -1
                }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = s.turn == HUMAN && s.played.isNotEmpty(), compact = true)
                BigButton(
                    stringResource(R.string.backgammon_rotate),
                    { updateSettings(settings.copy(backgammonUpright = !settings.backgammonUpright)) },
                    icon = AppIcon.ROTATE, style = ButtonStyle.SECONDARY, compact = true,
                )
            }
        },
    ) {
        val p = LocalPalette.current
        val measurer = rememberTextMeasurer()
        val targets = nextMoves.filter { it.from == selected }.map { it.to }.toSet()
        val sources = nextMoves.map { it.from }.toSet()
        BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
            // Same proportions either way: 4 wide by 5 tall standing, 5 wide by 4 tall sideways.
            val (w, h) = if (sideways) {
                minOf(maxWidth, maxHeight * 1.25f).let { it to it * 0.8f }
            } else {
                minOf(maxWidth, maxHeight * 0.8f) to maxHeight
            }
            Canvas(
                Modifier
                    .size(w, h)
                    .testTag("backgammon-board")
                    .pointerInput(s, selected, nextMoves, sideways) {
                        detectTapGestures { o -> tap(BoardGeometry(size.width.toFloat(), size.height.toFloat(), sideways).hit(o)) }
                    },
            ) {
                val g = BoardGeometry(size.width, size.height, sideways)
                orient(g) {
                    drawBoard(g, p, measurer)
                    // Highlights
                    for (t in targets) {
                        val r = if (t == 0) g.trayRect else g.pointRect(t)
                        drawRoundRect(p.hint.copy(alpha = 0.45f), r.topLeft, r.size, CornerRadius(g.ph * 0.2f))
                    }
                    if (s.turn == HUMAN && selected < 0) for (src in sources) {
                        if (src == 25) continue
                        val r = g.pointRect(src)
                        drawRoundRect(p.highlight.copy(alpha = 0.35f), r.topLeft, r.size, CornerRadius(g.ph * 0.2f))
                    }
                    drawCheckers(g, shownPosition, p, measurer, selected)
                    val lastAi = if (aiShown > 0) s.aiMoves[aiShown - 1] else null
                    if (lastAi != null && lastAi.to != 0) {
                        val abs = 25 - lastAi.to
                        val r = g.pointRect(abs)
                        drawRoundRect(p.danger, r.topLeft, r.size, CornerRadius(g.ph * 0.2f), style = Stroke(g.ph * 0.08f))
                    }
                    if (s.roll.isNotEmpty()) {
                        val dice = Backgammon.diceList(s.roll[0], s.roll[1])
                        val used = if (s.turn == HUMAN) usedDice else s.aiMoves.take(aiShown).map { it.die }
                        val remaining = dice.toMutableList().also { list -> used.forEach { list.remove(it) } }
                        drawDice(g, s.roll, dice, remaining, s.turn == AI, p)
                    }
                }
            }
        }
    }
}

/** Turns the standing board into the sideways one on screen (see [BoardGeometry]). */
private inline fun DrawScope.orient(g: BoardGeometry, block: DrawScope.() -> Unit) {
    if (!g.sideways) return block()
    withTransform({
        translate(0f, size.height)
        rotate(-90f, Offset.Zero)
    }, block)
}

/** Draws [block] upright around [pivot] even on the sideways board: text, dice and the light on the checkers. */
private inline fun DrawScope.upright(g: BoardGeometry, pivot: Offset, block: DrawScope.() -> Unit) {
    if (g.sideways) rotate(90f, pivot, block) else block()
}

private fun DrawScope.drawBoard(g: BoardGeometry, p: Palette, measurer: TextMeasurer) {
    val board = Size(g.width, g.height)
    drawRoundRect(Brush.linearGradient(listOf(p.wood.lighter(0.15f), p.woodDark), Offset.Zero, Offset(g.width, g.height)), Offset.Zero, board, CornerRadius(g.frame * 1.5f))
    drawRect(Color.Black.copy(alpha = 0.3f), g.inner.topLeft + Offset(g.frame * 0.3f, g.frame * 0.3f), g.inner.size)
    val felt = if (p.isDark) p.felt.darker(0.1f) else p.felt.lighter(0.05f)
    drawRect(Brush.horizontalGradient(listOf(felt.lighter(0.06f), felt.darker(0.08f), felt.lighter(0.06f)), g.inner.left, g.inner.right), g.inner.topLeft, g.inner.size)
    for (pt in 1..24) {
        val r = g.pointRect(pt)
        val left = pt <= 12
        val color = if (pt % 2 == 0) p.pointA else p.pointB
        val tri = Path().apply {
            if (left) { moveTo(r.left, r.top); lineTo(r.left, r.bottom); lineTo(r.left + g.triangle, r.center.y) }
            else { moveTo(r.right, r.top); lineTo(r.right, r.bottom); lineTo(r.right - g.triangle, r.center.y) }
            close()
        }
        drawPath(tri, Brush.linearGradient(listOf(color.lighter(0.12f), color.darker(0.12f)), Offset(0f, r.top), Offset(0f, r.bottom)))
        drawPath(tri, color.darker(0.35f), style = Stroke(1.5f))
        val labelX = if (left) r.left + g.triangle + g.ph * 0.35f else r.right - g.triangle - g.ph * 0.35f
        val label = Offset(labelX, r.center.y)
        upright(g, label) { text(measurer, pt.toString(), label, Color.White.copy(alpha = 0.6f), pxToSp(g.ph * 0.3f), bold = false) }
    }
    drawLine(Color.Black.copy(alpha = 0.25f), Offset(g.inner.center.x, g.inner.top), Offset(g.inner.center.x, g.inner.bottom), 2f)
    // Bar and tray
    drawRect(Brush.verticalGradient(listOf(p.woodDark, p.wood, p.woodDark), g.barRect.top, g.barRect.bottom), g.barRect.topLeft, g.barRect.size)
    drawRoundRect(p.woodDark.darker(0.25f), g.trayRect.topLeft, g.trayRect.size, CornerRadius(g.frame))
    drawLine(p.wood, Offset(g.trayRect.center.x, g.trayRect.top), Offset(g.trayRect.center.x, g.trayRect.bottom), 3f)
}

private fun DrawScope.drawCheckers(g: BoardGeometry, pos: BgPosition, p: Palette, measurer: TextMeasurer, selected: Int) {
    val r = g.radius
    fun stack(baseX: Float, y: Float, dir: Float, count: Int, color: Color, rim: Color, highlightTop: Boolean, room: Float) {
        if (count == 0) return
        val maxVisible = 5
        val step = if (count <= maxVisible) r * 2 else (room - r * 2) / (count - 1)
        for (k in 0 until count) {
            val c = Offset(baseX + dir * (r + k * minOf(step, r * 2)), y)
            upright(g, c) { glossyDisc(c, r, color, rim = if (highlightTop && k == count - 1) p.highlight else rim) }
        }
        if (count > maxVisible) {
            val c = Offset(baseX + dir * (r + (count - 1) * minOf(step, r * 2)), y)
            upright(g, c) { text(measurer, count.toString(), c, if (color.luminance() > 0.5f) Color.Black else Color.White, pxToSp(r * 0.9f)) }
        }
    }
    val human = p.checkerPlayer
    val ai = p.checkerAi
    val humanRim = Color.Black.copy(alpha = 0.35f)
    val aiRim = if (p.strongOutlines) Color.White else Color.Black.copy(alpha = 0.35f)
    for (pt in 1..24) {
        val v = pos.points[pt]
        if (v == 0) continue
        val rect = g.pointRect(pt)
        val left = pt <= 12
        stack(if (left) rect.left else rect.right, rect.center.y, if (left) 1f else -1f, kotlin.math.abs(v), if (v > 0) human else ai, if (v > 0) humanRim else aiRim, v > 0 && pt == selected, g.halfW)
    }
    // Bar: player's checkers on the left end, computer's on the right end (the dice sit between).
    stack(g.barRect.left, g.barRect.center.y, 1f, pos.barHuman, human, humanRim, selected == 25, g.halfW * 0.45f)
    stack(g.barRect.right, g.barRect.center.y, -1f, pos.barAi, ai, aiRim, false, g.halfW * 0.45f)
    // Borne-off checkers as thin slabs in the tray: yours on the left, the computer's on the right.
    val slabW = (g.trayRect.width / 2 - g.frame) / 15f
    for (k in 0 until pos.offHuman) {
        val x = g.trayRect.left + g.frame * 0.5f + k * slabW
        drawRoundRect(human, Offset(x, g.trayRect.top + g.frame * 0.4f), Size(slabW * 0.85f, g.trayRect.height - g.frame * 0.8f), CornerRadius(slabW * 0.3f))
    }
    for (k in 0 until pos.offAi) {
        val x = g.trayRect.right - g.frame * 0.5f - (k + 1) * slabW
        drawRoundRect(ai, Offset(x, g.trayRect.top + g.frame * 0.4f), Size(slabW * 0.85f, g.trayRect.height - g.frame * 0.8f), CornerRadius(slabW * 0.3f))
    }
}

private fun Color.luminance() = 0.299f * red + 0.587f * green + 0.114f * blue

private fun DrawScope.drawDice(g: BoardGeometry, roll: List<Int>, all: List<Int>, remaining: List<Int>, ai: Boolean, p: Palette) {
    val side = g.bar * 0.78f
    val cx = g.barRect.center.x
    val cy = g.barRect.center.y
    val count = all.size
    val left = cx - (count * side + (count - 1) * side * 0.25f) / 2
    val rem = remaining.toMutableList()
    all.forEachIndexed { i, v ->
        val used = !rem.remove(v)
        val tl = Offset(left + i * side * 1.25f, cy - side / 2)
        val face = if (ai) Color(0xFF3A2A22) else Color(0xFFFBF7EE)
        val pip = if (ai) Color.White else Color(0xFF222222)
        val o = side * 0.25f
        val c = tl + Offset(side / 2, side / 2)
        val spots = when (v) {
            1 -> listOf(Offset.Zero)
            2 -> listOf(Offset(-o, -o), Offset(o, o))
            3 -> listOf(Offset(-o, -o), Offset.Zero, Offset(o, o))
            4 -> listOf(Offset(-o, -o), Offset(o, -o), Offset(-o, o), Offset(o, o))
            5 -> listOf(Offset(-o, -o), Offset(o, -o), Offset.Zero, Offset(-o, o), Offset(o, o))
            else -> listOf(Offset(-o, -o), Offset(o, -o), Offset(-o, 0f), Offset(o, 0f), Offset(-o, o), Offset(o, o))
        }
        upright(g, c) {
            raisedBlock(tl, Size(side, side), if (used) face.darker(0.35f) else face, face.darker(0.25f), face.darker(0.45f), side * 0.08f, side * 0.18f)
            spots.forEach { drawCircle(if (used) pip.copy(alpha = 0.4f) else pip, side * 0.09f, c + it) }
        }
    }
}
