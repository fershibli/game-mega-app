package io.github.fershibli.megagames.games.ludo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.core.rememberWorldStore
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
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** The seat played on this phone; the computer plays every other seat. */
private const val HUMAN_SEAT = 0

/** Pauses (milliseconds) that let a person follow the computer and the automatic moves. */
private const val ROLL_PAUSE = 700L
private const val MOVE_PAUSE = 800L
private const val AUTO_MOVE_PAUSE = 900L
private const val NO_MOVE_PAUSE = 1500L
private const val END_PAUSE = 900L

/** The table chosen last time, remembered for the next game. */
@Serializable
data class LudoTable(val opponents: Int = 3)

private const val TABLE_KEY = "ludo"

/** Names of the colors, by color index. */
private val COLOR_NAMES = intArrayOf(R.string.ludo_red, R.string.ludo_green, R.string.ludo_yellow, R.string.ludo_blue)

@Composable
fun LudoGame(host: GameHost) {
    val rules = LudoRules.of(host.variant?.key)
    LoadGame(host, LudoState.serializer(), { LudoState() }) { state -> LudoScreen(state, host, rules) }
}

@Composable
private fun LudoScreen(state: MutableState<LudoState>, host: GameHost, rules: LudoRules) {
    val s = state.value
    val context = LocalResources.current
    val world = rememberWorldStore()
    val lastTable = remember { (world.load(TABLE_KEY, LudoTable.serializer())?.opponents ?: 3).coerceIn(1, 3) }
    val scope = rememberCoroutineScope()
    val history = remember { mutableStateListOf<LudoState>() }
    var hint by remember { mutableStateOf<LudoMove?>(null) }
    // The last number rolled, still shown (faded) after the piece has moved.
    var shownDie by remember { mutableIntStateOf(0) }
    if (s.die > 0) shownDie = s.die
    val started = s.colors.isNotEmpty()
    val moves = remember(s) { Ludo.distinct(Ludo.moves(s, rules)) }
    val myTurn = started && s.winner < 0 && s.turn == HUMAN_SEAT

    LaunchedEffect(s) {
        if (!started) return@LaunchedEffect
        if (s.winner >= 0) {
            val detail = if (s.winner == HUMAN_SEAT) context.getString(R.string.ludo_you_won)
            else context.getString(R.string.ludo_other_won, context.getString(COLOR_NAMES[s.colors[s.winner]]))
            delay(END_PAUSE)
            host.finish(GameOutcome(s.winner == HUMAN_SEAT, detail))
            return@LaunchedEffect
        }
        val computer = s.turn != HUMAN_SEAT
        when {
            s.die == 0 -> if (computer) {
                delay(ROLL_PAUSE)
                state.value = Ludo.roll(s, Random.Default.nextInt(1, 7))
            }
            moves.isEmpty() -> {
                delay(NO_MOVE_PAUSE)
                state.value = Ludo.pass(s)
            }
            computer -> {
                delay(MOVE_PAUSE)
                val m = withContext(Dispatchers.Default) { Ludo.choose(s, rules, host.difficulty, Random.Default) }
                if (m != null) state.value = Ludo.play(s, m, rules)
            }
            moves.size == 1 -> {
                // Only one thing to do: do it for the player after a short pause.
                delay(AUTO_MOVE_PAUSE)
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
        val color = s.colors[HUMAN_SEAT]
        val cell = row * 15 + col
        // A tap on a piece first, then on the square where a piece would land.
        val m = moves.firstOrNull { (it.from == Ludo.YARD && LudoBoard.inYard(color, row, col)) || (it.from >= 0 && LudoBoard.cell(color, it.from) == cell) }
            ?: moves.firstOrNull { LudoBoard.cell(color, it.to) == cell }
        if (m != null) play(m)
    }

    GameLayout(
        status = {
            val name = if (started) stringResource(COLOR_NAMES[s.colors[s.turn]]) else ""
            val text = when {
                !started -> ""
                s.winner >= 0 -> ""
                !myTurn -> if (s.die == 0) stringResource(R.string.ludo_computer_turn, name) else stringResource(R.string.ludo_computer_rolled, name, s.die)
                s.die == 0 -> stringResource(R.string.ludo_roll_prompt)
                Ludo.forfeited(s, rules) -> stringResource(R.string.ludo_three_sixes)
                moves.isEmpty() -> stringResource(R.string.ludo_no_move, s.die)
                else -> stringResource(R.string.ludo_pick_piece, s.die)
            }
            if (!started) {
                StatusPill(stringResource(R.string.ludo_you_are))
            } else {
                val head = stringResource(R.string.ludo_home_count, s.pieces[HUMAN_SEAT].count { it == Ludo.HOME })
                StatusPill(if (text.isEmpty()) head else head + "\n" + text, emphasized = myTurn)
            }
        },
        controls = {
            ActionBar {
                BigButton(stringResource(R.string.ludo_roll), ::roll, icon = AppIcon.DICE, enabled = myTurn && s.die == 0, compact = true)
                BigButton(stringResource(R.string.undo), {
                    history.removeLastOrNull()?.let { state.value = it; hint = null }
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
        val p = LocalPalette.current
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
                val playing = if (started) s.colors.toSet() else Ludo.colorsFor(lastTable + 1).toSet()
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
                                seat == HUMAN_SEAT && hint?.from == pos && hint?.piece == k -> p.hint
                                seat == HUMAN_SEAT && pos in movable -> p.highlight
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
                                seat == HUMAN_SEAT && hint?.from == pos -> p.hint
                                seat == HUMAN_SEAT && pos in movable -> p.highlight
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
            if (!started) TableChooser(lastTable) { n ->
                world.save(TABLE_KEY, LudoTable.serializer(), LudoTable(n))
                state.value = Ludo.newGame(n + 1)
            }
        }
    }
}

/** "How many computer opponents?" with three big buttons; the last choice is the main button. */
@Composable
private fun TableChooser(last: Int, onChoose: (Int) -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(20.dp))
                .background(p.surface)
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.ludo_choose_opponents), style = MaterialTheme.typography.titleLarge, color = p.text, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (n in 1..3) {
                    BigButton(n.toString(), { onChoose(n) }, Modifier.weight(1f), style = if (n == last) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY, bigText = true)
                }
            }
        }
    }
}
