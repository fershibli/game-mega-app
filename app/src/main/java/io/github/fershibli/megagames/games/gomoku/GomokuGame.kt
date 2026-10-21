package io.github.fershibli.megagames.games.gomoku

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.games.go.GridBoard
import io.github.fershibli.megagames.games.go.drawGridBoard
import io.github.fershibli.megagames.games.go.drawStone
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Pause before the computer answers, so the player sees their own stone land first. */
private const val AI_DELAY_MS = 400L

/** Time to look at the winning line before the result screen. */
private const val END_DELAY_MS = 1_200L

@Serializable
data class GomokuState(
    /** Every move so far (`row * 15 + column`); Black plays first. */
    val moves: List<Int> = emptyList(),
    /** The color the computer plays; the player has the other one. */
    val computer: Int = Gomoku.WHITE,
)

/** How a game stands after its moves: the board, whose turn it is, and the end if it came. */
class GomokuPosition(moves: List<Int>) {
    val board = Gomoku.board(moves)
    val toMove = Gomoku.colorOf(moves.size)
    val last = moves.lastOrNull() ?: -1
    val line = if (last >= 0) Gomoku.winningLine(board, last) else emptyList()
    val winner = if (line.isNotEmpty()) board[last] else Gomoku.EMPTY
    val full = moves.size >= Gomoku.CELLS
    val over get() = winner != Gomoku.EMPTY || full
}

@Composable
fun GomokuGame(host: GameHost) {
    LoadGame(host, GomokuState.serializer(), { GomokuState() }) { state -> GomokuScreen(state, host) }
}

@Composable
private fun GomokuScreen(state: MutableState<GomokuState>, host: GameHost) {
    val s = state.value
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    val pos = remember(s.moves) { GomokuPosition(s.moves) }
    val human = Gomoku.opponent(s.computer)
    val myTurn = !pos.over && pos.toMove == human
    var ghost by remember { mutableIntStateOf(-1) }
    var hint by remember { mutableIntStateOf(-1) }

    LaunchedEffect(s.moves) {
        when {
            pos.over -> {
                delay(END_DELAY_MS)
                val detail = when (pos.winner) {
                    human -> R.string.gomoku_win
                    Gomoku.EMPTY -> R.string.gomoku_draw
                    else -> R.string.gomoku_lose
                }
                host.finish(GameOutcome(pos.winner == human, res.getString(detail)))
            }
            pos.toMove == s.computer -> {
                delay(AI_DELAY_MS)
                val move = withContext(Dispatchers.Default) { GomokuAi.bestMove(pos.board, s.computer, host.difficulty, Random.Default) }
                state.value = s.copy(moves = s.moves + move)
            }
        }
    }

    fun tap(i: Int) {
        if (!myTurn || pos.board[i] != Gomoku.EMPTY) return
        if (ghost != i) {
            ghost = i
            return
        }
        ghost = -1
        hint = -1
        state.value = s.copy(moves = s.moves + i)
    }

    GameLayout(
        status = {
            val text = when {
                pos.over -> ""
                pos.toMove == s.computer -> stringResource(R.string.computer_turn)
                ghost >= 0 -> stringResource(R.string.gomoku_confirm)
                else -> stringResource(R.string.gomoku_your_turn)
            }
            if (text.isNotEmpty()) StatusPill(text, emphasized = myTurn)
        },
        controls = {
            ActionBar {
                BigButton(stringResource(R.string.undo), {
                    state.value = s.copy(moves = s.moves.dropLast(2))
                    ghost = -1
                    hint = -1
                }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = myTurn && s.moves.size >= 2)
                BigButton(stringResource(R.string.hint), {
                    ghost = -1
                    scope.launch {
                        val best = withContext(Dispatchers.Default) { GomokuAi.bestMove(pos.board, human, Difficulty.MEDIUM, Random.Default) }
                        if (state.value.moves == s.moves) hint = best
                    }
                }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = myTurn)
            }
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            GridBoard(
                Gomoku.N, Modifier.size(side).testTag("gomoku-board"),
                stoneAt = { pos.board[it] },
                last = pos.last,
                ghost = ghost,
                ghostColor = human,
                hint = hint,
                line = pos.line,
                onTap = { tap(it) },
            )
        }
    }
}

/** Home screen picture: part of the board with a black line of five crossing white stones. */
fun gomokuThumbnail(scope: DrawScope, p: Palette) = scope.run {
    val n = 7
    drawGridBoard(n, p)
    val cell = size.width / n
    fun at(r: Int, c: Int) = Offset((c + 0.5f) * cell, (r + 0.5f) * cell)
    val r = cell * 0.46f
    for (k in 1..5) drawStone(at(k, k), r, Gomoku.BLACK, p.strongOutlines)
    for ((y, x) in listOf(1 to 3, 2 to 4, 3 to 1, 4 to 2, 5 to 3)) drawStone(at(y, x), r, Gomoku.WHITE, p.strongOutlines)
    drawLine(p.highlight, at(1, 1), at(5, 5), cell * 0.12f, StrokeCap.Round)
}
