package io.github.fershibli.megagames.games.morris

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.games.checkers.drawCheckersPiece
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.LocalSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** The side played on this phone (it places first); the computer plays the other one. */
private const val HUMAN = Morris.WHITE
private const val COMPUTER = Morris.BLACK

/** Pauses (milliseconds) so a person can follow the computer's turn. */
private const val THINK_PAUSE = 600L
private const val REMOVE_PAUSE = 900L
private const val END_PAUSE = 900L

@Composable
fun MorrisGame(host: GameHost) {
    val rules = MorrisRules.of(host.variant?.key)
    LoadGame(host, MorrisState.serializer(), { Morris.initial(rules) }) { state -> MorrisScreen(state, host, rules) }
}

@Composable
private fun MorrisScreen(state: MutableState<MorrisState>, host: GameHost, rules: MorrisRules) {
    val s = state.value
    val context = LocalResources.current
    val scope = rememberCoroutineScope()
    val history = remember { mutableStateListOf<MorrisState>() }
    var selected by remember { mutableIntStateOf(-1) }
    var hint by remember { mutableStateOf<MorrisMove?>(null) }
    // The computer's whole turn, kept while its mill's removal is shown in a second step.
    var aiPlan by remember { mutableStateOf<MorrisMove?>(null) }
    val board = s.board.toIntArray()
    val myHand = s.hand[Morris.handIndex(HUMAN)]
    val myTurn = s.turn == HUMAN
    val steps = remember(s) { if (myTurn && !s.removing) Morris.steps(board, myHand, HUMAN, rules) else emptyList() }
    val removable = remember(s) { if (s.removing) Morris.removable(board, s.turn, rules).toSet() else emptySet() }

    LaunchedEffect(s) {
        when {
            s.turn == 0 -> {
                val detail = context.getString(
                    when (s.reason) {
                        3 -> R.string.morris_draw
                        4 -> R.string.morris_draw_full
                        1 -> if (s.winner == HUMAN) R.string.morris_ai_two_left else R.string.morris_you_two_left
                        else -> if (s.winner == HUMAN) R.string.morris_ai_blocked else R.string.morris_you_blocked
                    },
                )
                delay(END_PAUSE)
                host.finish(GameOutcome(s.winner == HUMAN, detail))
            }
            s.turn == COMPUTER && s.removing -> {
                delay(REMOVE_PAUSE)
                val planned = aiPlan?.remove?.takeIf { it in removable }
                val r = planned ?: withContext(Dispatchers.Default) { Morris.bestRemoval(s, rules, Random.Default) }
                aiPlan = null
                state.value = Morris.remove(s, r, rules)
            }
            s.turn == COMPUTER -> {
                delay(THINK_PAUSE)
                val m = withContext(Dispatchers.Default) { Morris.bestMove(s, rules, host.difficulty, Random.Default) } ?: return@LaunchedEffect
                aiPlan = m
                state.value = Morris.step(s, m.from, m.to, rules)
            }
        }
    }

    fun tap(point: Int) {
        if (!myTurn) return
        hint = null
        if (s.removing) {
            if (point in removable) state.value = Morris.remove(s, point, rules)
            return
        }
        val target = steps.firstOrNull { it.to == point && (it.from == -1 || it.from == selected) }
        if (target != null) {
            history += s
            selected = -1
            state.value = Morris.step(s, target.from, target.to, rules)
            return
        }
        selected = if (steps.any { it.from == point && point >= 0 }) point else -1
    }

    GameLayout(
        status = {
            val text = when {
                s.turn == 0 -> ""
                !myTurn -> stringResource(R.string.computer_turn)
                s.removing -> stringResource(R.string.morris_remove)
                myHand > 0 -> stringResource(R.string.morris_place, myHand)
                selected >= 0 -> stringResource(R.string.morris_pick_target)
                Morris.canFly(board, myHand, HUMAN, rules) -> stringResource(R.string.morris_fly)
                else -> stringResource(R.string.morris_pick_piece)
            }
            val you = Morris.count(board, HUMAN) + myHand
            val ai = Morris.count(board, COMPUTER) + s.hand[Morris.handIndex(COMPUTER)]
            val score = stringResource(R.string.morris_score, you, ai)
            StatusPill(if (text.isEmpty()) score else score + "\n" + text, emphasized = myTurn)
        },
        controls = {
            ActionBar {
                BigButton(stringResource(R.string.undo), {
                    history.removeLastOrNull()?.let { state.value = it; selected = -1; hint = null }
                }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = myTurn && history.isNotEmpty())
                BigButton(stringResource(R.string.hint), {
                    val now = s
                    scope.launch {
                        val best = withContext(Dispatchers.Default) {
                            if (now.removing) now.last?.copy(remove = Morris.bestRemoval(now, rules, Random.Default))
                            else Morris.bestMove(now, rules, Difficulty.MEDIUM, Random.Default)
                        }
                        if (state.value == now) {
                            hint = best
                            selected = -1
                        }
                    }
                }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = myTurn)
            }
        },
    ) {
        val p = LocalPalette.current
        val scale = LocalSettings.current.pieceScale
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            val w = minOf(maxWidth, maxHeight / MorrisGeometry.HEIGHT_RATIO)
            Canvas(
                Modifier
                    .size(w, w * MorrisGeometry.HEIGHT_RATIO)
                    .testTag("morris-board")
                    .pointerInput(s, selected, steps) {
                        detectTapGestures { o ->
                            val g = MorrisGeometry(size.width.toFloat(), rules)
                            val nearest = (0 until rules.points).minBy { i -> (g.point(rules.x(i), rules.y(i)) - o).getDistance() }
                            if ((g.point(rules.x(nearest), rules.y(nearest)) - o).getDistance() < g.spacing * 0.6f) tap(nearest)
                        }
                    },
            ) {
                val g = MorrisGeometry(size.width, rules)
                fun at(i: Int) = g.point(rules.x(i), rules.y(i))
                drawMorrisBoard(Offset(0f, g.strip), size.width, g.margin, g.spacing, rules, p)
                val radius = (g.spacing * 0.36f * scale).coerceAtMost(minOf(g.spacing * 0.46f, size.width * 0.068f))
                // The last move: a line from where the piece came from, and a cross where a piece was removed.
                s.last?.let { m ->
                    if (m.from >= 0) drawLine(p.highlight, at(m.from), at(m.to), g.spacing * 0.12f, StrokeCap.Round)
                    drawCircle(p.highlight, radius * 1.3f, at(m.to))
                    if (m.remove >= 0) cross(at(m.remove), radius * 0.6f, p.danger)
                }
                hint?.let { m ->
                    if (m.from >= 0) drawCircle(p.hint, radius * 1.25f, at(m.from), style = Stroke(radius * 0.25f))
                    drawCircle(p.hint, radius * 1.25f, at(m.to), style = Stroke(radius * 0.25f))
                    if (m.remove >= 0) drawCircle(p.hint, radius * 1.25f, at(m.remove), style = Stroke(radius * 0.25f))
                }
                val movable = if (selected < 0 && myTurn && myHand == 0) steps.map { it.from }.toSet() else emptySet()
                if (selected >= 0) drawCircle(p.highlight, radius * 1.3f, at(selected))
                for (i in 0 until rules.points) {
                    val v = board[i]
                    if (v == 0) continue
                    if (i in movable) drawCircle(p.highlight, radius * 1.22f, at(i), style = Stroke(radius * 0.22f))
                    if (myTurn && i in removable) drawCircle(p.danger, radius * 1.25f, at(i), style = Stroke(radius * 0.25f))
                    drawCheckersPiece(at(i), radius, morrisColor(p, v), false, p.strongOutlines)
                }
                if (selected >= 0) for (st in steps) if (st.from == selected) drawCircle(p.hint, radius * 0.45f, at(st.to))
                // Pieces still to place: the computer's above the board, yours below.
                hand(s.hand[Morris.handIndex(COMPUTER)], g.strip / 2, morrisColor(p, COMPUTER), p.strongOutlines)
                hand(myHand, size.height - g.strip / 2, morrisColor(p, HUMAN), p.strongOutlines)
            }
        }
    }
}

private fun DrawScope.cross(c: Offset, r: Float, color: Color) {
    drawLine(color, c + Offset(-r, -r), c + Offset(r, r), r * 0.35f, StrokeCap.Round)
    drawLine(color, c + Offset(-r, r), c + Offset(r, -r), r * 0.35f, StrokeCap.Round)
}

/** A row of [n] small pieces centred on the line [y]. */
private fun DrawScope.hand(n: Int, y: Float, color: Color, strong: Boolean) {
    if (n <= 0) return
    val step = minOf(size.width * 0.08f, size.width * 0.92f / n)
    val r = minOf(step * 0.42f, size.width * 0.035f)
    val left = size.width / 2 - step * (n - 1) / 2
    for (k in 0 until n) drawCheckersPiece(Offset(left + k * step, y), r, color, false, strong)
}
