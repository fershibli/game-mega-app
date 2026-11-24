package io.github.fershibli.megagames.games.trail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.games.ludo.drawLudoDie
import io.github.fershibli.megagames.games.ludo.ludoColor
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.Handoff
import io.github.fershibli.megagames.ui.components.HandoffOverlay
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.Seat
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.LocalSettings
import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.wait
import kotlin.random.Random

/** The seat played on this phone against the computer. */
private const val HUMAN_SEAT = 0
private const val DEFAULT_SEATS = 2

/** Pauses (milliseconds) so a person can follow every pawn. */
private const val ROLL_PAUSE = 900L
private const val TURN_PAUSE = 1300L
private const val READ_PAUSE = 2600L
private const val END_PAUSE = 1200L

/** Pawn colors by seat (Ludo's red, blue, green and yellow), each with its own symbol. */
private val SEAT_COLORS = intArrayOf(0, 3, 1, 2)
private val COLOR_NAMES = intArrayOf(R.string.ludo_red, R.string.ludo_blue, R.string.ludo_green, R.string.ludo_yellow)

@Composable
fun TrailGame(host: GameHost) {
    val seats = host.setup.seats.takeIf { it in 2..4 } ?: DEFAULT_SEATS
    LoadGame(host, TrailState.serializer(), { Trail.newGame(seats) }) { state -> TrailScreen(state, host) }
}

@Composable
private fun TrailScreen(state: MutableState<TrailState>, host: GameHost) {
    val s = state.value
    val p = LocalPalette.current
    val res = LocalResources.current
    val pace = currentPace
    val together = host.setup.together
    fun human(seat: Int) = together || seat == HUMAN_SEAT
    val names = (0 until s.seats).map { seat ->
        if (!together && seat == HUMAN_SEAT) stringResource(R.string.trail_you) else stringResource(COLOR_NAMES[seat])
    }
    val handoff = remember { Handoff() }
    fun seatOf(seat: Int) = Seat(names[seat], ludoColor(p, SEAT_COLORS[seat]))
    var announced by remember { mutableIntStateOf(s.turn) }
    LaunchedEffect(s.turn, s.over) {
        if (together && !s.over && s.turn != announced) handoff.passTo(seatOf(s.turn))
        announced = s.turn
    }

    LaunchedEffect(s) {
        if (s.over) {
            pace.wait(END_PAUSE)
            val leaders = Trail.leaders(s)
            val score = (0 until s.seats).joinToString(" · ") { res.getString(R.string.trail_score_entry, names[it], s.hearts[it]) }
            val who = leaders.joinToString(", ") { names[it] }
            val line = if (leaders.size > 1) res.getString(R.string.trail_tie, who) else res.getString(R.string.trail_winner, who)
            host.finish(GameOutcome(together || HUMAN_SEAT in leaders, "$line\n$score", shared = together))
            return@LaunchedEffect
        }
        val pending = s.pending
        when {
            pending != null && !human(pending.seat) -> {
                pace.wait(READ_PAUSE)
                state.value = if (pending.story >= 0) Trail.readStory(s) else Trail.choose(s, Trail.computerStays(s, host.difficulty, Random.Default))
            }
            pending != null -> Unit // waits for the person to read or choose
            s.die != 0 -> {
                pace.wait(if (s.lastStory >= 0 || s.lastHearts != 0 || s.lastMove != 0) READ_PAUSE else TURN_PAUSE)
                state.value = Trail.next(s)
            }
            !human(s.turn) -> {
                pace.wait(ROLL_PAUSE)
                state.value = Trail.roll(s, Random.Default.nextInt(1, 7), Random.Default)
            }
        }
    }

    val myTurn = !s.over && human(s.turn)
    fun roll() {
        if (myTurn && Trail.canRoll(s)) state.value = Trail.roll(s, Random.Default.nextInt(1, 7), Random.Default)
    }

    Box(Modifier.fillMaxSize()) {
        GameLayout(
            status = {
                val entry = stringResource(R.string.trail_score_entry)
                val hearts = (0 until s.seats).joinToString(" · ") { entry.format(names[it], s.hearts[it]) }
                val name = names[s.turn]
                val line = when {
                    s.over -> ""
                    s.pending != null -> ""
                    s.die != 0 -> stringResource(R.string.trail_rolled, names[s.lastSeat.coerceAtLeast(0)], s.die)
                    !human(s.turn) -> stringResource(R.string.trail_computer_turn, name)
                    together -> stringResource(R.string.trail_turn_of, name)
                    else -> stringResource(R.string.trail_your_turn)
                }
                StatusPill(if (line.isEmpty()) hearts else hearts + "\n" + line, Modifier.testTag("trail-status"), emphasized = myTurn && Trail.canRoll(s))
            },
            controls = {
                News(s, names, human = s.pending?.let { human(it.seat) } ?: false, state)
                ActionBar {
                    BigButton(stringResource(R.string.trail_roll), ::roll, Modifier.testTag("trail-roll"), icon = AppIcon.DICE, enabled = myTurn && Trail.canRoll(s))
                }
            },
        ) {
            val scale = LocalSettings.current.pieceScale
            val where = stringResource(R.string.trail_board, (0 until s.seats).joinToString(", ") { "${names[it]} ${s.positions[it] + 1}" })
            BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
                val cell = minOf(maxWidth / Trail.COLUMNS, maxHeight / (Trail.ROWS + 1.2f))
                Canvas(Modifier.size(cell * Trail.COLUMNS, cell * (Trail.ROWS + 1.2f)).testTag("trail-board").semantics { contentDescription = where }) {
                    val c = size.width / Trail.COLUMNS
                    drawTrailBoard(c, p)
                    // Pawns, grouped by square.
                    s.positions.withIndex().groupBy({ it.value }, { it.index }).forEach { (square, seats) ->
                        val center = squareCenter(square, c)
                        val n = seats.size
                        val r = c * (if (n == 1) 0.3f else 0.22f) * scale.coerceIn(0.85f, 1.25f)
                        seats.forEachIndexed { i, seat ->
                            val off = when (n) {
                                1 -> Offset.Zero
                                2 -> Offset((i - 0.5f) * c * 0.42f, 0f)
                                else -> Offset(((i % 2) - 0.5f) * c * 0.42f, ((i / 2) - 0.5f) * c * 0.42f)
                            }
                            val ring = if (seat == s.turn && !s.over) p.highlight else null
                            drawTrailPawn(center + off, r, p, SEAT_COLORS[seat], ring)
                        }
                    }
                    // The die, under the board, framed in the color of the seat to play.
                    val die = s.die.takeIf { it > 0 } ?: 0
                    drawLudoDie(Offset(size.width / 2, size.height - c * 0.6f), c * 0.95f, if (die > 0) die else 1, ludoColor(p, SEAT_COLORS[s.turn]), dimmed = die == 0)
                }
            }
        }
        HandoffOverlay(handoff) {}
    }
}

/**
 * The card under the board: the story being read, the crossroads to decide, or the last thing
 * that happened. A person reads and taps; the computer's cards go on by themselves.
 */
@Composable
private fun News(s: TrailState, names: List<String>, human: Boolean, state: MutableState<TrailState>) {
    val p = LocalPalette.current
    val pending = s.pending
    val seat = pending?.seat ?: s.lastSeat
    if (seat < 0) return
    val who = names[seat]
    val story = pending?.story?.takeIf { it >= 0 } ?: if (pending == null) s.lastStory else -1
    val effect = when {
        story >= 0 -> Story.entries[story].let { st -> effectText(st.hearts, st.move) }
        pending == null -> effectText(s.lastHearts, s.lastMove)
        else -> null
    }
    val title = when {
        pending?.choiceStage != null && pending.choiceStage >= 0 -> stringResource(Stage.entries[pending.choiceStage].label)
        story >= 0 -> stringResource(Story.entries[story].stage.label)
        s.lastSeat >= 0 && s.positions[s.lastSeat] == Trail.FINISH -> stringResource(R.string.trail_arrived, who)
        else -> null
    }
    val body = when {
        pending?.choiceStage != null && pending.choiceStage >= 0 -> stringResource(Stage.entries[pending.choiceStage].choice)
        story >= 0 -> stringResource(Story.entries[story].text)
        else -> null
    }
    if (title == null && body == null && effect == null) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surfaceAlt)
            .border(BorderStroke(if (p.strongOutlines) 2.dp else 1.dp, p.outline), RoundedCornerShape(16.dp))
            .padding(12.dp).heightIn(min = 40.dp).testTag("trail-news"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(22.dp)) { drawTrailPawn(center, size.width * 0.45f, p, SEAT_COLORS[seat], null) }
            Text(listOfNotNull(who, title).joinToString(" · "), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium, color = p.text)
        }
        if (body != null) Text(body, style = MaterialTheme.typography.bodyLarge, color = p.text)
        if (effect != null && pending?.choiceStage?.let { it < 0 } != false) Text(effect, style = MaterialTheme.typography.titleMedium, color = p.success)
        if (human && pending != null) {
            if (pending.story >= 0) {
                BigButton(stringResource(R.string.trail_ok), { state.value = Trail.readStory(state.value) }, Modifier.fillMaxWidth().testTag("trail-ok"), compact = true)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton(stringResource(R.string.trail_stay, Trail.STAY_HEARTS), { state.value = Trail.choose(state.value, true) }, Modifier.weight(1f).testTag("trail-stay"), compact = true)
                    BigButton(stringResource(R.string.trail_go, Trail.GO_SQUARES), { state.value = Trail.choose(state.value, false) }, Modifier.weight(1f).testTag("trail-go"), style = ButtonStyle.SECONDARY, compact = true)
                }
            }
        }
    }
}

@Composable
private fun effectText(hearts: Int, move: Int): String? = when {
    hearts > 0 -> stringResource(R.string.trail_effect_hearts, hearts)
    move > 0 -> stringResource(R.string.trail_effect_forward, move)
    move < 0 -> stringResource(R.string.trail_effect_back, -move)
    else -> null
}

