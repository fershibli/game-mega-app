package io.github.fershibli.megagames.games.mancala

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** The side played on this phone (the bottom row); the computer plays the top row. */
private const val HUMAN = 0
private const val COMPUTER = 1

/** Timing (milliseconds) of the sowing and of the computer's turn. */
private const val SOW_STEP = 120L
private const val SOW_TOTAL = 1500L
private const val SOW_END_PAUSE = 250L
private const val THINK_PAUSE = 600L
private const val END_PAUSE = 900L

@Composable
fun MancalaGame(host: GameHost) {
    val rules = MancalaRules.of(host.variant?.key)
    LoadGame(host, MancalaState.serializer(), { Mancala.initial() }) { state -> MancalaScreen(state, host, rules) }
}

@Composable
private fun MancalaScreen(state: MutableState<MancalaState>, host: GameHost, rules: MancalaRules) {
    val s = state.value
    val context = LocalResources.current
    val scope = rememberCoroutineScope()
    val history = remember { mutableStateListOf<MancalaState>() }
    var hint by remember { mutableIntStateOf(-1) }
    // The move being sown on screen: the holes as they are seed by seed, and the hole just filled.
    var toAnimate by remember { mutableStateOf<MancalaState?>(null) }
    var frame by remember { mutableStateOf<List<Int>?>(null) }
    var dropped by remember { mutableIntStateOf(-1) }
    val pits = frame ?: s.pits
    val myTurn = s.turn == HUMAN && frame == null
    val moves = remember(s) { if (s.turn == HUMAN) Mancala.legalMoves(s.pits.toIntArray(), HUMAN, rules).toSet() else emptySet() }

    /** Shows [next], the result of a move, starting with its sowing. */
    fun show(next: MancalaState) {
        toAnimate = next
        frame = next.before?.toMutableList()?.also { it[next.last] = 0 }
        state.value = next
    }

    LaunchedEffect(s) {
        val before = s.before
        if (toAnimate === s && before != null && s.last >= 0) {
            val holes = before.toIntArray()
            val path = Mancala.sowPath(holes, s.last, rules)
            val step = minOf(SOW_STEP, SOW_TOTAL / maxOf(1, path.size))
            holes[s.last] = 0
            frame = holes.toList()
            for (h in path) {
                delay(step)
                holes[h]++
                dropped = h
                frame = holes.toList()
            }
            delay(SOW_END_PAUSE)
        }
        toAnimate = null
        frame = null
        dropped = -1
        when (s.turn) {
            -1 -> {
                val you = s.pits[Mancala.store(HUMAN)]
                val ai = s.pits[Mancala.store(COMPUTER)]
                val detail = if (you == ai) context.getString(R.string.mancala_draw, you) else context.getString(R.string.mancala_final, you, ai)
                delay(END_PAUSE)
                host.finish(GameOutcome(you > ai, detail))
            }
            COMPUTER -> {
                delay(THINK_PAUSE)
                val m = withContext(Dispatchers.Default) { Mancala.bestMove(s, rules, host.difficulty, Random.Default) } ?: return@LaunchedEffect
                show(Mancala.play(s, m, rules))
            }
        }
    }

    fun tap(hole: Int) {
        if (!myTurn || hole !in moves) return
        history += s
        hint = -1
        show(Mancala.play(s, hole, rules))
    }

    GameLayout(
        status = {
            val text = when {
                s.turn == -1 -> ""
                s.turn == COMPUTER -> stringResource(R.string.computer_turn)
                frame != null -> ""
                s.again -> stringResource(R.string.mancala_again)
                rules.oware && Mancala.seedsOnSide(s.pits.toIntArray(), COMPUTER) == 0 -> stringResource(R.string.mancala_feed)
                else -> stringResource(R.string.mancala_pick)
            }
            val score = stringResource(R.string.mancala_score, pits[Mancala.store(HUMAN)], pits[Mancala.store(COMPUTER)])
            StatusPill(if (text.isEmpty()) score else score + "\n" + text, emphasized = s.turn == HUMAN)
        },
        controls = {
            ActionBar {
                BigButton(stringResource(R.string.undo), {
                    history.removeLastOrNull()?.let { state.value = it; hint = -1 }
                }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = myTurn && history.isNotEmpty())
                BigButton(stringResource(R.string.hint), {
                    val now = s
                    scope.launch {
                        val best = withContext(Dispatchers.Default) { Mancala.bestMove(now, rules, Difficulty.MEDIUM, Random.Default) }
                        if (state.value == now) hint = best ?: -1
                    }
                }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = myTurn)
            }
        },
    ) {
        val p = LocalPalette.current
        val scale = LocalSettings.current.pieceScale
        val measurer = rememberTextMeasurer()
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            val w = minOf(maxWidth, maxHeight / MancalaGeometry.HEIGHT_RATIO)
            Canvas(
                Modifier
                    .size(w, w * MancalaGeometry.HEIGHT_RATIO)
                    .testTag("mancala-board")
                    .pointerInput(s, moves, frame == null) {
                        detectTapGestures { o -> tap(MancalaGeometry(size.width.toFloat()).hit(o)) }
                    },
            ) {
                val g = MancalaGeometry(size.width)
                drawMancala(g, pits, p, measurer, scale)
                fun ring(hole: Int, color: androidx.compose.ui.graphics.Color, width: Float) {
                    val r = g.rect(hole)
                    drawRoundRect(color, r.topLeft - Offset(width / 2, width / 2), Size(r.width + width, r.height + width), CornerRadius(r.width / 2 + width / 2), style = Stroke(width))
                }
                if (frame == null && s.last >= 0) ring(s.last, p.highlight.copy(alpha = 0.8f), g.column * 0.06f)
                if (myTurn) for (m in moves) ring(m, p.highlight, g.column * 0.09f)
                if (hint >= 0) ring(hint, p.hint, g.column * 0.13f)
                if (dropped >= 0) ring(dropped, p.hint.copy(alpha = 0.9f), g.column * 0.07f)
            }
        }
    }
}
