package io.github.fershibli.megagames.games.ludo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Size
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
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.Handoff
import io.github.fershibli.megagames.ui.components.HandoffOverlay
import io.github.fershibli.megagames.ui.components.Seat
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.LocalSettings
import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.wait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** The seat played on this phone against the computer, which plays every other seat. */
private const val HUMAN_SEAT = 0

/** Seats when the table size is not known (a game opened without the "How many at the table?" step). */
private const val DEFAULT_SEATS = 4

/** Pauses (milliseconds) that let a person follow the computer and the automatic moves. */
private const val ROLL_PAUSE = 700L
private const val MOVE_PAUSE = 800L
private const val AUTO_MOVE_PAUSE = 900L
private const val NO_MOVE_PAUSE = 1500L
private const val END_PAUSE = 900L

/** Names of the colors, by color index. */
private val COLOR_NAMES = intArrayOf(R.string.ludo_red, R.string.ludo_green, R.string.ludo_yellow, R.string.ludo_blue)

@Composable
fun LudoGame(host: GameHost) {
    val rules = LudoRules.of(host.variant?.key)
    // The table size comes from "How many at the table?": 2, 3 or 4 seats.
    val seats = host.setup.seats.takeIf { it in 2..4 } ?: DEFAULT_SEATS
    LoadGame(host, LudoState.serializer(), { Ludo.newGame(seats) }) { state -> LudoScreen(state, host, rules, seats) }
}

@Composable
private fun LudoScreen(state: MutableState<LudoState>, host: GameHost, rules: LudoRules, seats: Int) {
    val s = state.value
    val context = LocalResources.current
    val scope = rememberCoroutineScope()
    val history = remember { mutableStateListOf<LudoState>() }
    var hint by remember { mutableStateOf<LudoMove?>(null) }
    // The last number rolled, still shown (faded) after the piece has moved.
    var shownDie by remember { mutableIntStateOf(0) }
    if (s.die > 0) shownDie = s.die
    // Saves from before the table was chosen in the app have no seats yet: start that table now.
    val started = s.colors.isNotEmpty()
    LaunchedEffect(started) { if (!started) state.value = Ludo.newGame(seats) }
    val moves = remember(s) { Ludo.distinct(Ludo.moves(s, rules)) }
    // Taking turns on this phone: every seat is a person, and the phone goes round the table.
    val together = host.setup.together
    val handoff = remember { Handoff() }
    fun human(seat: Int) = seat == HUMAN_SEAT || together
    val myTurn = started && s.winner < 0 && human(s.turn)
    val names = COLOR_NAMES.map { stringResource(it) }
    val p = LocalPalette.current
    fun seatOf(seat: Int) = Seat(names[s.colors[seat]], ludoColor(p, s.colors[seat]))
    // The seat whose turn was last announced; a new seat to play gets the handoff.
    var announced by remember { mutableIntStateOf(s.turn) }
    LaunchedEffect(s.turn, s.winner) {
        if (together && started && s.winner < 0 && s.turn != announced) handoff.passTo(seatOf(s.turn))
        announced = s.turn
    }

    val pace = currentPace
    LaunchedEffect(s) {
        if (!started) return@LaunchedEffect
        if (s.winner >= 0) {
            val winner = context.getString(COLOR_NAMES[s.colors[s.winner]])
            pace.wait(END_PAUSE)
            if (together) {
                host.finish(GameOutcome(true, context.getString(R.string.ludo_other_won, winner), shared = true))
            } else {
                val detail = if (s.winner == HUMAN_SEAT) context.getString(R.string.ludo_you_won) else context.getString(R.string.ludo_other_won, winner)
                host.finish(GameOutcome(s.winner == HUMAN_SEAT, detail))
            }
            return@LaunchedEffect
        }
        val computer = !human(s.turn)
        when {
            s.die == 0 -> if (computer) {
                pace.wait(ROLL_PAUSE)
                state.value = Ludo.roll(s, Random.Default.nextInt(1, 7))
            }
            moves.isEmpty() -> {
                pace.wait(NO_MOVE_PAUSE)
                state.value = Ludo.pass(s)
            }
            computer -> {
                pace.wait(MOVE_PAUSE)
                val m = withContext(Dispatchers.Default) { Ludo.choose(s, rules, host.difficulty, Random.Default) }
                if (m != null) state.value = Ludo.play(s, m, rules)
            }
            moves.size == 1 -> {
                // Only one thing to do: do it for the player after a short pause.
                pace.wait(AUTO_MOVE_PAUSE)
                state.value = Ludo.play(s, moves.first(), rules)
            }
        }
    }

    fun roll() {
        if (!myTurn || s.die != 0) return
        hint = null
        state.value = Ludo.roll(s, Random.Default.nextInt(1, 7))
    }

    fun play(m: LudoMove) {
        // Undo goes back to real choices only: a forced move would just be played again.
        if (moves.size > 1) history += s
        hint = null
        state.value = Ludo.play(s, m, rules)
    }

    fun tap(row: Int, col: Int) {
        if (!myTurn) return
        if (s.die == 0) {
            if (LudoBoard.inCenter(row, col)) roll()
            return
        }
        val color = s.colors[s.turn]
        val cell = row * 15 + col
        // A tap on a piece first, then on the square where a piece would land.
        val m = moves.firstOrNull { (it.from == Ludo.YARD && LudoBoard.inYard(color, row, col)) || (it.from >= 0 && LudoBoard.cell(color, it.from) == cell) }
            ?: moves.firstOrNull { LudoBoard.cell(color, it.to) == cell }
        if (m != null) play(m)
    }

    Box(Modifier.fillMaxSize()) {
        GameLayout(
            status = {
                if (started) {
                    val name = names[s.colors[s.turn]]
                    val text = when {
                        s.winner >= 0 -> ""
                        !myTurn -> if (s.die == 0) stringResource(R.string.ludo_computer_turn, name) else stringResource(R.string.ludo_computer_rolled, name, s.die)
                        together && s.die == 0 -> stringResource(R.string.ludo_turn_roll, name)
                        s.die == 0 -> stringResource(R.string.ludo_roll_prompt)
                        Ludo.forfeited(s, rules) -> stringResource(R.string.ludo_three_sixes)
                        together && moves.isEmpty() -> stringResource(R.string.ludo_turn_no_move, name, s.die)
                        moves.isEmpty() -> stringResource(R.string.ludo_no_move, s.die)
                        together -> stringResource(R.string.ludo_turn_pick, name, s.die)
                        else -> stringResource(R.string.ludo_pick_piece, s.die)
                    }
                    val head = if (together) {
                        stringResource(R.string.ludo_center_all, s.colors.indices.joinToString(" · ") { seat -> names[s.colors[seat]] + " " + s.pieces[seat].count { it == Ludo.HOME } })
                    } else stringResource(R.string.ludo_home_count, s.pieces[HUMAN_SEAT].count { it == Ludo.HOME })
                    StatusPill(if (text.isEmpty()) head else head + "\n" + text, emphasized = myTurn)
                }
            },
            controls = {
                ActionBar {
                    BigButton(stringResource(R.string.ludo_roll), ::roll, icon = AppIcon.DICE, enabled = myTurn && s.die == 0, compact = true)
                    BigButton(stringResource(R.string.undo), {
                        history.removeLastOrNull()?.let {
                            // Going back is not a new turn: no handoff for it.
                            announced = it.turn
                            state.value = it
                            hint = null
                        }
                    }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = myTurn && history.isNotEmpty(), compact = true)
                    BigButton(stringResource(R.string.hint), {
                        val now = s
                        scope.launch {
                            val best = withContext(Dispatchers.Default) { Ludo.choose(now, rules, Difficulty.HARD, Random.Default) }
                            if (state.value == now) hint = best
                        }
                    }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = myTurn && moves.size > 1, compact = true)
                }
            },
        ) {
            val scale = LocalSettings.current.pieceScale
            BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight)
                Canvas(
                    Modifier
                        .size(side)
                        .testTag("ludo-board")
                        .pointerInput(s, moves) {
                            detectTapGestures { pos ->
                                val cell = size.width / 15f
                                val col = (pos.x / cell).toInt()
                                val row = (pos.y / cell).toInt()
                                if (row in 0 until 15 && col in 0 until 15) tap(row, col)
                            }
                        },
                ) {
                    val cell = size.width / 15f
                    val playing = if (started) s.colors.toSet() else Ludo.colorsFor(seats).toSet()
                    drawLudoBoard(cell, p, playing, rules.safeSquares)
                    // The last move's square.
                    s.last?.let { m ->
                        val c = LudoBoard.cell(s.colors[m.seat], m.to)
                        if (c >= 0) drawRect(p.highlight.copy(alpha = 0.55f), Offset(c % 15 * cell, c / 15 * cell), Size(cell, cell))
                    }
                    hint?.let { m ->
                        val c = LudoBoard.cell(s.colors[m.seat], m.to)
                        if (c >= 0) drawCircle(p.hint, cell * 0.5f, LudoBoard.cellCenter(c) * cell, style = Stroke(cell * 0.14f))
                    }
                    if (started) {
                        val movable = if (myTurn) moves.map { it.from }.toSet() else emptySet()
                        val trackRadius = (cell * 0.4f * scale).coerceAtMost(cell * 0.5f)
                        val yardRadius = (cell * 0.62f * scale).coerceAtMost(cell * 0.85f)
                        // Pieces on the track and in the home columns, grouped by square.
                        val bySquare = HashMap<Int, MutableList<Pair<Int, Int>>>()
                        for (seat in s.colors.indices) {
                            val color = s.colors[seat]
                            for (k in 0 until Ludo.PIECES) {
                                val pos = s.pieces[seat][k]
                                val ring = when {
                                    seat == s.turn && hint?.from == pos && hint?.piece == k -> p.hint
                                    seat == s.turn && pos in movable -> p.highlight
                                    else -> null
                                }
                                when (pos) {
                                    Ludo.YARD -> drawLudoPiece(LudoBoard.yardSpot(color, k) * cell, yardRadius, p, color, ring)
                                    Ludo.HOME -> drawLudoPiece(LudoBoard.homeSpot(color, k) * cell, cell * 0.22f, p, color)
                                    else -> bySquare.getOrPut(LudoBoard.cell(color, pos)) { ArrayList() } += seat to k
                                }
                            }
                        }
                        for ((c, list) in bySquare) {
                            val center = LudoBoard.cellCenter(c) * cell
                            val n = list.size
                            val r = if (n == 1) trackRadius else trackRadius * (if (n == 2) 0.72f else 0.56f)
                            list.forEachIndexed { i, (seat, k) ->
                                val offset = when (n) {
                                    1 -> Offset.Zero
                                    2 -> Offset((i - 0.5f) * cell * 0.46f, 0f)
                                    else -> Offset(((i % 2) - 0.5f) * cell * 0.46f, ((i / 2) - 0.5f) * cell * 0.46f)
                                }
                                val pos = s.pieces[seat][k]
                                val ring = when {
                                    seat == s.turn && hint?.from == pos -> p.hint
                                    seat == s.turn && pos in movable -> p.highlight
                                    else -> null
                                }
                                drawLudoPiece(center + offset, r, p, s.colors[seat], ring)
                            }
                        }
                        // The die in the middle, framed in the color of the seat to play.
                        val dieCenter = Offset(7.5f, 7.5f) * cell
                        if (myTurn && s.die == 0) drawCircle(p.highlight, cell * 1.55f, dieCenter)
                        drawLudoDie(dieCenter, cell * 1.9f, if (s.die > 0) s.die else shownDie, ludoColor(p, s.colors[s.turn]), dimmed = s.die == 0)
                    }
                }
            }
        }
        HandoffOverlay(handoff) {}
    }
}
