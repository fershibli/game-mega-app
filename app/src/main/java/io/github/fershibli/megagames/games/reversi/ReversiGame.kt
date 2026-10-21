package io.github.fershibli.megagames.games.reversi

import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Box
import io.github.fershibli.megagames.ui.components.Seat
import io.github.fershibli.megagames.ui.components.HandoffOverlay
import io.github.fershibli.megagames.ui.components.Handoff
import io.github.fershibli.megagames.ui.theme.wait
import io.github.fershibli.megagames.ui.theme.currentPace
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.glossyDisc
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class ReversiState(
    val board: List<Int>,
    val turn: Int = Reversi.PLAYER,
    val last: Int = -1,
    /** Side that had to pass on the previous turn (0 = nobody), to tell the player. */
    val passed: Int = 0,
)

private val DarkDisc = Color(0xFF232323)
private val LightDisc = Color(0xFFF4F1EA)

@Composable
fun ReversiGame(host: GameHost) {
    LoadGame(host, ReversiState.serializer(), { ReversiState(Reversi.initial().toList()) }) { state ->
        ReversiScreen(state, host)
    }
}

/** Plays [pos] for [side] and works out whose turn is next (handling passes). */
private fun advance(s: ReversiState, pos: Int, side: Int): ReversiState? {
    val board = Reversi.play(s.board.toIntArray(), pos, side) ?: return null
    val other = Reversi.other(side)
    return when {
        Reversi.legalMoves(board, other).isNotEmpty() -> ReversiState(board.toList(), other, pos, 0)
        Reversi.legalMoves(board, side).isNotEmpty() -> ReversiState(board.toList(), side, pos, other)
        else -> ReversiState(board.toList(), 0, pos, 0) // game over
    }
}

@Composable
private fun ReversiScreen(state: MutableState<ReversiState>, host: GameHost) {
    val s = state.value
    val history = remember { mutableStateListOf<ReversiState>() }
    var hint by remember { mutableIntStateOf(-1) }
    val context = LocalResources.current
    val board = s.board.toIntArray()
    val mine = Reversi.count(board, Reversi.PLAYER)
    val theirs = Reversi.count(board, Reversi.AI)
    // Taking turns on this phone: both colors are people, and the phone is passed after each turn.
    val together = host.setup.together
    val handoff = remember { Handoff() }
    val darkSeat = Seat(stringResource(R.string.reversi_dark), DarkDisc)
    val lightSeat = Seat(stringResource(R.string.reversi_light), LightDisc)
    fun human(side: Int) = side == Reversi.PLAYER || (together && side == Reversi.AI)

    val pace = currentPace
    LaunchedEffect(s) {
        if (s.turn == 0 && together) {
            val winner = when {
                mine > theirs -> context.getString(R.string.reversi_dark_wins)
                theirs > mine -> context.getString(R.string.reversi_light_wins)
                else -> context.getString(R.string.reversi_tie)
            }
            host.finish(GameOutcome(true, winner + "\n" + context.getString(R.string.reversi_score_together, mine, theirs), shared = true))
        } else if (s.turn == 0) {
            val detail = context.getString(R.string.score_line, mine, theirs)
            host.finish(GameOutcome(mine > theirs, if (mine == theirs) context.getString(R.string.reversi_tie) + "\n" + detail else detail))
        } else if (s.turn == Reversi.AI && !together) {
            pace.wait(650)
            val move = withContext(Dispatchers.Default) { Reversi.bestMove(board, Reversi.AI, host.difficulty, Random.Default) }
            if (move != null) advance(s, move, Reversi.AI)?.let { state.value = it }
        }
    }

    fun play(pos: Int) {
        val side = s.turn
        advance(s, pos, side)?.let {
            history += s
            hint = -1
            state.value = it
            if (together && it.turn != 0 && it.turn != side) handoff.passTo(if (it.turn == Reversi.PLAYER) darkSeat else lightSeat)
        }
    }

    Box(Modifier.fillMaxSize()) {
        GameLayout(
            status = {
                val text = when {
                    together && s.passed == Reversi.AI -> stringResource(R.string.reversi_light_passed)
                    together && s.passed == Reversi.PLAYER -> stringResource(R.string.reversi_dark_passed)
                    together -> stringResource(if (s.turn == Reversi.PLAYER) R.string.reversi_dark_turn else R.string.reversi_light_turn)
                    s.turn == Reversi.AI -> stringResource(R.string.computer_turn)
                    s.passed == Reversi.AI -> stringResource(R.string.reversi_ai_passed)
                    s.passed == Reversi.PLAYER -> stringResource(R.string.reversi_you_passed)
                    else -> stringResource(R.string.your_turn)
                }
                val score = if (together) stringResource(R.string.reversi_score_together, mine, theirs) else stringResource(R.string.reversi_score, mine, theirs)
                StatusPill(score + "\n" + text, emphasized = human(s.turn))
            },
            controls = {
                ActionBar {
                    BigButton(stringResource(R.string.undo), {
                        history.removeLastOrNull()?.let { state.value = it; hint = -1 }
                    }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = history.isNotEmpty() && human(s.turn))
                    BigButton(stringResource(R.string.hint), {
                        hint = Reversi.bestMove(board, s.turn, Difficulty.MEDIUM, Random.Default) ?: -1
                    }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = human(s.turn))
                }
            },
        ) {
            val p = LocalPalette.current
            val legal = if (s.turn != 0 && human(s.turn)) Reversi.legalMoves(board, s.turn).toSet() else emptySet()
            BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight)
                Canvas(
                    Modifier
                        .size(side)
                        .testTag("reversi-board")
                        .pointerInput(s) {
                            detectTapGestures { pos ->
                                val frame = size.width * 0.04f
                                val cell = (size.width - frame * 2) / 8f
                                val c = ((pos.x - frame) / cell).toInt()
                                val r = ((pos.y - frame) / cell).toInt()
                                if (c !in 0..7 || r !in 0..7 || s.turn == 0 || !human(s.turn)) return@detectTapGestures
                                play(r * 8 + c)
                            }
                        },
                ) {
                    val frame = size.width * 0.04f
                    val cell = (size.width - frame * 2) / 8f
                    // Wooden frame with a bevel, then the felt.
                    drawRoundRect(Brush.linearGradient(listOf(p.wood.lighter(0.2f), p.woodDark)), Offset.Zero, size, CornerRadius(frame))
                    drawRect(Color.Black.copy(alpha = 0.25f), Offset(frame * 0.8f, frame * 0.8f), Size(size.width - frame * 1.6f, size.height - frame * 1.6f))
                    val boardColor = if (p.mode == io.github.fershibli.megagames.core.ColorMode.COLORBLIND) p.felt else Color(0xFF2E7D4F).let { if (p.isDark) it.darker(0.3f) else it }
                    drawRect(Brush.radialGradient(listOf(boardColor.lighter(0.08f), boardColor.darker(0.12f)), center, size.width * 0.7f), Offset(frame, frame), Size(cell * 8, cell * 8))
                    val line = Color.Black.copy(alpha = if (p.strongOutlines) 0.8f else 0.45f)
                    for (k in 0..8) {
                        drawLine(line, Offset(frame + k * cell, frame), Offset(frame + k * cell, frame + cell * 8), if (p.strongOutlines) 3f else 2f)
                        drawLine(line, Offset(frame, frame + k * cell), Offset(frame + cell * 8, frame + k * cell), if (p.strongOutlines) 3f else 2f)
                    }
                    for (k in listOf(2, 6)) for (m in listOf(2, 6)) drawCircle(line, cell * 0.07f, Offset(frame + k * cell, frame + m * cell))
                    for (i in 0 until 64) {
                        val center = Offset(frame + (i % 8 + 0.5f) * cell, frame + (i / 8 + 0.5f) * cell)
                        when (board[i]) {
                            Reversi.PLAYER -> glossyDisc(center, cell * 0.4f, DarkDisc, rim = if (p.strongOutlines) Color.White else null)
                            Reversi.AI -> glossyDisc(center, cell * 0.4f, LightDisc, rim = if (p.strongOutlines) Color.Black else Color.Black.copy(alpha = 0.3f))
                            else -> if (i in legal) {
                                drawCircle(if (i == hint) p.highlight else Color.White.copy(alpha = 0.35f), cell * (if (i == hint) 0.22f else 0.13f), center)
                            }
                        }
                        if (i == s.last) drawCircle(p.danger, cell * 0.08f, center)
                        if (i == hint) drawRoundRect(p.highlight, Offset(center.x - cell / 2, center.y - cell / 2), Size(cell, cell), CornerRadius(cell * 0.1f), style = Stroke(cell * 0.08f))
                    }
                }
            }
        }
        HandoffOverlay(handoff) {}
    }
}
