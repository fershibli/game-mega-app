package io.github.fershibli.megagames.games.checkers

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
import androidx.compose.ui.graphics.StrokeCap
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
import io.github.fershibli.megagames.games.chess.boardSquares
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.lighter
import androidx.compose.foundation.layout.Box
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.BoardSide
import io.github.fershibli.megagames.ui.components.Handoff
import io.github.fershibli.megagames.ui.components.HandoffOverlay
import io.github.fershibli.megagames.ui.components.sideSeat
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.wait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class CheckersState(
    val board: List<Int>,
    /** Side to move ([Checkers.PLAYER] or [Checkers.AI]), or 0 when the game is over. */
    val turn: Int = Checkers.PLAYER,
    val last: CheckersMove? = null,
    /** Kings-only quiet moves in a row, for the draw rule. */
    val quiet: Int = 0,
    /** At the end: the winning side, or 0 for a draw. */
    val winner: Int = 0,
    /** Why the game ended: 1 = no pieces left, 2 = no moves left, 3 = draw. */
    val reason: Int = 0,
)

@Composable
fun CheckersGame(host: GameHost) {
    val rules = CheckersRules.of(host.variant?.key)
    LoadGame(host, CheckersState.serializer(), { CheckersState(Checkers.initial(rules).toList()) }) { state ->
        CheckersScreen(state, host, rules)
    }
}

/** Plays [move] and works out what comes next: the other side's turn, a win or a draw. */
fun advanceCheckers(s: CheckersState, move: CheckersMove, rules: CheckersRules): CheckersState {
    val board = s.board.toIntArray()
    val side = Checkers.owner(board[move.from])
    val next = Checkers.apply(board, move, rules)
    val quiet = if (Checkers.isQuietKingMove(board, move)) s.quiet + 1 else 0
    val base = CheckersState(next.toList(), -side, move, quiet)
    return when {
        Checkers.count(next, -side) == 0 -> base.copy(turn = 0, winner = side, reason = 1)
        Checkers.legalMoves(next, -side, rules).isEmpty() -> base.copy(turn = 0, winner = side, reason = 2)
        quiet >= Checkers.DRAW_PLIES -> base.copy(turn = 0, winner = 0, reason = 3)
        else -> base
    }
}

/** Distinct results of a set of moves (same landing square and same pieces taken). */
private fun results(moves: List<CheckersMove>) = moves.map { it.to to it.captured.toSet() }.distinct().size

@Composable
private fun CheckersScreen(state: MutableState<CheckersState>, host: GameHost, rules: CheckersRules) {
    val s = state.value
    val history = remember { mutableStateListOf<CheckersState>() }
    // The squares tapped so far for the move being entered: the piece, then each landing square.
    var partial by remember { mutableStateOf(emptyList<Int>()) }
    var hint by remember { mutableStateOf<CheckersMove?>(null) }
    val context = LocalResources.current
    val board = s.board.toIntArray()
    val n = rules.size
    // Taking turns on this phone: both colors are people, and the phone is passed after each move.
    val together = host.setup.together
    val handoff = remember { Handoff() }
    fun human(side: Int) = side == Checkers.PLAYER || (together && side == Checkers.AI)
    // The side that starts plays the dark pieces under English rules, the light ones elsewhere.
    fun sideOf(side: Int) = if ((side == Checkers.PLAYER) == rules.darkMovesFirst) BoardSide.DARK else BoardSide.LIGHT
    val (firstColor, secondColor) = checkersColors(LocalPalette.current, rules.darkMovesFirst)
    val firstSeat = sideSeat(sideOf(Checkers.PLAYER), firstColor)
    val secondSeat = sideSeat(sideOf(Checkers.AI), secondColor)
    val legal = remember(s) { if (s.turn != 0 && human(s.turn)) Checkers.legalMoves(board, s.turn, rules) else emptyList() }
    val candidates = if (partial.isEmpty()) emptyList() else legal.filter { it.path.size >= partial.size && it.path.subList(0, partial.size) == partial }
    val mustCapture = legal.firstOrNull()?.captured?.isNotEmpty() == true

    val pace = currentPace
    LaunchedEffect(s) {
        when {
            s.turn == 0 && together -> {
                val score = context.getString(
                    R.string.sides_score,
                    context.getString(sideOf(Checkers.PLAYER).label), Checkers.count(board, Checkers.PLAYER),
                    context.getString(sideOf(Checkers.AI).label), Checkers.count(board, Checkers.AI),
                )
                val result = if (s.reason == 3) context.getString(R.string.checkers_draw) else context.getString(sideOf(s.winner).wins)
                pace.wait(700)
                host.finish(GameOutcome(true, result + "\n" + score, shared = true))
            }
            s.turn == 0 -> {
                val detail = when (s.reason) {
                    3 -> context.getString(R.string.checkers_draw)
                    1 -> context.getString(if (s.winner == Checkers.PLAYER) R.string.checkers_ai_no_pieces else R.string.checkers_you_no_pieces)
                    else -> context.getString(if (s.winner == Checkers.PLAYER) R.string.checkers_ai_no_moves else R.string.checkers_you_no_moves)
                }
                pace.wait(700)
                host.finish(GameOutcome(s.winner == Checkers.PLAYER, detail))
            }
            s.turn == Checkers.AI && !together -> {
                pace.wait(600)
                val move = withContext(Dispatchers.Default) { Checkers.bestMove(board, Checkers.AI, rules, host.difficulty, Random.Default) }
                if (move != null) state.value = advanceCheckers(s, move, rules)
            }
        }
    }

    fun play(move: CheckersMove) {
        history += s
        partial = emptyList()
        hint = null
        val next = advanceCheckers(s, move, rules)
        state.value = next
        if (together && next.turn != 0) handoff.passTo(if (next.turn == Checkers.PLAYER) firstSeat else secondSeat)
    }

    fun tap(sq: Int) {
        if (s.turn == 0 || !human(s.turn)) return
        if (partial.isNotEmpty()) {
            val ahead = candidates.filter { it.path.size > partial.size }
            // The final square of a capture that can only be done one way: play it at once.
            val finals = ahead.filter { it.to == sq }
            if (finals.isNotEmpty() && results(finals) == 1) return play(finals.first())
            // Otherwise one more landing square of a multiple capture.
            val steps = ahead.filter { it.path[partial.size] == sq }
            if (steps.isNotEmpty()) {
                if (results(steps) == 1) return play(steps.first())
                partial = partial + sq
                return
            }
        }
        partial = if (legal.any { it.from == sq }) listOf(sq) else emptyList()
    }

    Box(Modifier.fillMaxSize()) {
        GameLayout(
            status = {
                val text = when {
                    s.turn == 0 -> ""
                    !human(s.turn) -> stringResource(R.string.computer_turn)
                    partial.size > 1 -> stringResource(R.string.checkers_continue)
                    partial.isNotEmpty() -> stringResource(R.string.checkers_pick_target)
                    mustCapture -> stringResource(R.string.checkers_must_capture)
                    together -> ""
                    else -> stringResource(R.string.checkers_pick_piece)
                }
                val score = if (together) {
                    stringResource(
                        R.string.sides_score, stringResource(sideOf(Checkers.PLAYER).label), Checkers.count(board, Checkers.PLAYER),
                        stringResource(sideOf(Checkers.AI).label), Checkers.count(board, Checkers.AI),
                    )
                } else stringResource(R.string.checkers_score, Checkers.count(board, Checkers.PLAYER), Checkers.count(board, Checkers.AI))
                val turn = if (together && s.turn != 0) stringResource(sideOf(s.turn).turn) else ""
                StatusPill(listOf(score, turn, text).filter { it.isNotEmpty() }.joinToString("\n"), emphasized = s.turn != 0 && human(s.turn))
            },
            controls = {
                ActionBar {
                    BigButton(stringResource(R.string.undo), {
                        history.removeLastOrNull()?.let { state.value = it; partial = emptyList(); hint = null }
                    }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = history.isNotEmpty() && s.turn != 0 && human(s.turn))
                    BigButton(stringResource(R.string.hint), {
                        hint = Checkers.bestMove(board, s.turn, rules, Difficulty.MEDIUM, Random.Default)
                        partial = emptyList()
                    }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = s.turn != 0 && human(s.turn))
                }
            },
        ) {
            val p = LocalPalette.current
            val (lightSquare, darkSquare) = boardSquares(p)
            val mine = firstColor
            val theirs = secondColor
            // What to mark on the board.
            val movable = if (partial.isEmpty() && mustCapture) legal.map { it.from }.toSet() else emptySet()
            val nextSteps = candidates.filter { it.path.size > partial.size }.map { it.path[partial.size] }.toSet()
            val finals = candidates.filter { it.path.size > partial.size + 1 }.map { it.to }.toSet()
            val takenSoFar = candidates.firstOrNull()?.captured?.take(partial.size - 1).orEmpty().toSet()
            BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight)
                Canvas(
                    Modifier
                        .size(side)
                        .testTag("checkers-board")
                        .pointerInput(s, partial, legal) {
                            detectTapGestures { pos ->
                                val frame = size.width * 0.03f
                                val cell = (size.width - frame * 2) / n
                                val c = ((pos.x - frame) / cell).toInt()
                                val r = ((pos.y - frame) / cell).toInt()
                                if (pos.x >= frame && pos.y >= frame && c in 0 until n && r in 0 until n) tap(r * n + c)
                            }
                        },
                ) {
                    val frame = size.width * 0.03f
                    val cell = (size.width - frame * 2) / n
                    fun topLeft(i: Int) = Offset(frame + (i % n) * cell, frame + (i / n) * cell)
                    fun center(i: Int) = topLeft(i) + Offset(cell / 2, cell / 2)
                    drawRoundRect(Brush.linearGradient(listOf(p.wood.lighter(0.2f), p.woodDark)), Offset.Zero, size, CornerRadius(frame))
                    drawRect(lightSquare, Offset(frame, frame), Size(cell * n, cell * n))
                    for (i in 0 until n * n) {
                        if (Checkers.playable(i / n, i % n, rules)) drawRect(darkSquare, topLeft(i), Size(cell, cell))
                    }
                    // The last move: its squares tinted and its path drawn as a line.
                    s.last?.let { m ->
                        for (sq in listOf(m.from, m.to)) drawRect(p.highlight.copy(alpha = 0.45f), topLeft(sq), Size(cell, cell))
                        for (k in 1 until m.path.size) {
                            drawLine(p.highlight.copy(alpha = 0.8f), center(m.path[k - 1]), center(m.path[k]), cell * 0.08f, StrokeCap.Round)
                        }
                    }
                    hint?.let { m ->
                        for (sq in listOf(m.from, m.to)) {
                            drawRoundRect(p.hint, topLeft(sq) + Offset(cell * 0.05f, cell * 0.05f), Size(cell * 0.9f, cell * 0.9f), CornerRadius(cell * 0.1f), style = Stroke(cell * 0.09f))
                        }
                    }
                    val strong = p.strongOutlines
                    val moving = partial.firstOrNull() ?: -1
                    for (i in 0 until n * n) {
                        val v = board[i]
                        if (v == 0 || i == moving) continue
                        val color = if (Checkers.owner(v) == Checkers.PLAYER) mine else theirs
                        if (i in movable) drawCircle(p.highlight, cell * 0.49f, center(i), style = Stroke(cell * 0.09f))
                        drawCheckersPiece(center(i), cell * 0.4f, color, Checkers.isKing(v), strong, alpha = if (i in takenSoFar) 0.35f else 1f)
                    }
                    // The piece being moved, drawn where it is now in the middle of its move.
                    if (moving >= 0) {
                        val at = partial.last()
                        drawRect(p.highlight.copy(alpha = 0.6f), topLeft(moving), Size(cell, cell))
                        drawCircle(p.highlight, cell * 0.49f, center(at), style = Stroke(cell * 0.1f))
                        val color = if (Checkers.owner(board[moving]) == Checkers.PLAYER) mine else theirs
                        drawCheckersPiece(center(at), cell * 0.4f, color, Checkers.isKing(board[moving]), strong)
                    }
                    for (t in nextSteps) drawCircle(p.hint.darker(0.1f), cell * 0.2f, center(t))
                    for (t in finals - nextSteps) drawCircle(p.hint, cell * 0.3f, center(t), style = Stroke(cell * 0.07f))
                    if (strong) drawRect(Color.Black, Offset(frame, frame), Size(cell * n, cell * n), style = Stroke(2f))
                }
            }
        }
        HandoffOverlay(handoff) {}
    }
}

