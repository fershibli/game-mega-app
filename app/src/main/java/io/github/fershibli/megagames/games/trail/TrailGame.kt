package io.github.fershibli.megagames.games.trail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
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
import io.github.fershibli.megagames.core.Motion
import io.github.fershibli.megagames.ui.theme.currentMotion
import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.swing
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

/** A pawn's hop from one square to the next, and the die's tumbles before it settles. */
private const val HOP = 300L
private const val TUMBLE = 90L
private const val TUMBLES = 7

/** The turn arrow's slow up-and-down: one way in this many milliseconds, by this many dots. */
private const val BOB = 900
private const val BOB_DOTS = 2f

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
    val motion = currentMotion

    // What the board shows can lag behind the rules for a moment: the die tumbles, then the
    // pawns walk square by square. The rules wait for the board before going on.
    val shown = remember { mutableStateListOf<Int>().apply { addAll(s.positions) } }
    var seenDie by remember { mutableIntStateOf(s.die) }
    var tumble by remember { mutableIntStateOf(0) }
    var hopFrom by remember { mutableStateOf<List<Int>?>(null) }
    val hop = remember { Animatable(0f) }
    // The arrow over the pawn to play bobs gently, slower with gentle movement and still when off.
    val bob = rememberInfiniteTransition(label = "trail-arrow").animateFloat(
        0f, motion.swing(BOB_DOTS),
        infiniteRepeatable(tween((BOB * motion.duration).toInt().coerceAtLeast(1), easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    LaunchedEffect(s.die, s.positions) {
        hopFrom = null
        if (s.die != 0 && seenDie == 0 && motion != Motion.NONE) {
            try {
                repeat(TUMBLES) { k ->
                    tumble = Random.Default.nextInt(1, 7) * (if (k % 2 == 0) 1 else -1)
                    pace.wait(TUMBLE)
                }
            } finally {
                tumble = 0
            }
        }
        seenDie = s.die
        while (shown.toList() != s.positions) {
            if (motion == Motion.NONE || shown.size != s.positions.size) {
                shown.clear()
                shown.addAll(s.positions)
                break
            }
            val from = shown.toList()
            hopFrom = from
            hop.snapTo(0f)
            hop.animateTo(1f, tween((HOP * pace.factor).toInt(), easing = LinearEasing))
            for (seat in from.indices) {
                val target = s.positions[seat]
                if (from[seat] != target) shown[seat] = from[seat] + if (target > from[seat]) 1 else -1
            }
            hopFrom = null
        }
    }
    fun settled(t: TrailState) = shown.toList() == t.positions && seenDie == t.die && tumble == 0

    var announced by remember { mutableIntStateOf(s.turn) }
    LaunchedEffect(s.turn, s.over) {
        if (together && !s.over && s.turn != announced) handoff.passTo(seatOf(s.turn))
        announced = s.turn
    }

    LaunchedEffect(s) {
        snapshotFlow { settled(s) }.first { it }
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
                if (settled(s)) News(s, names, human = s.pending?.let { human(it.seat) } ?: false, state)
                ActionBar {
                    BigButton(stringResource(R.string.trail_roll), ::roll, Modifier.testTag("trail-roll"), icon = AppIcon.DICE, enabled = myTurn && Trail.canRoll(s))
                }
            },
        ) {
            val scale = LocalSettings.current.pieceScale
            val where = stringResource(R.string.trail_board, (0 until s.seats).joinToString(", ") { "${names[it]} ${s.positions[it] + 1}" })
            Canvas(Modifier.fillMaxSize().padding(6.dp).testTag("trail-board").semantics { contentDescription = where }) {
                val v = TrailView.fitting(size)
                drawTrailBoard(v, p)
                // Pawns, grouped by square; the ones on their way hop between two squares.
                val from = hopFrom
                val walking = (0 until s.seats).filter { from != null && from[it] != s.positions[it] }.toSet()
                val pawnScale = scale.coerceIn(0.85f, 1.25f)
                val feet = ArrayList<Pair<Int, Offset>>()
                (0 until s.seats).filter { it !in walking }.groupBy { shown.getOrElse(it) { 0 } }.forEach { (square, seats) ->
                    seats.forEachIndexed { k, seat -> feet += seat to pawnFoot(v, v.square(square), k, seats.size) }
                }
                for (seat in walking) {
                    val a = from!![seat]
                    val b = a + if (s.positions[seat] > a) 1 else -1
                    feet += seat to hopFoot(v, a, b.coerceIn(0, Trail.FINISH), hop.value)
                }
                for ((seat, foot) in feet.sortedBy { it.second.y }) {
                    drawTrailPerson(v, foot, p, SEAT_COLORS[seat], pawnScale, walking = seat in walking && hop.value in 0.25f..0.75f, turn = seat == s.turn && !s.over, bob = bob.value)
                }
                // The die, under the board, on a mat of the color of the seat to play.
                val face = if (tumble != 0) kotlin.math.abs(tumble) else s.die.takeIf { it > 0 } ?: 1
                val shake = if (tumble == 0) 0 else if (tumble > 0) 1 else -1
                drawTrailDie(v, face, ludoColor(p, SEAT_COLORS[s.turn]), dimmed = s.die == 0 && tumble == 0, shake, p)
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
    // The stage the card speaks about, for its picture.
    val stage = when {
        pending?.choiceStage != null && pending.choiceStage >= 0 -> pending.choiceStage
        story >= 0 -> Story.entries[story].stage.ordinal
        else -> null
    }
    val paper = trailPaper(p)
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(paper)
            .border(BorderStroke(if (p.strongOutlines) 2.dp else 1.5.dp, if (p.strongOutlines) p.outline else Color(0xFFB9824F)), shape)
            .padding(12.dp).heightIn(min = 40.dp).testTag("trail-news"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(24.dp, 30.dp)) {
                val v = TrailView(0f, 0f, kotlin.math.floor(size.height / 11f).coerceAtLeast(1f))
                drawTrailPerson(v, Offset(size.width / 2, size.height - v.px), p, SEAT_COLORS[seat])
            }
            Text(listOfNotNull(who, title).joinToString(" · "), Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.titleMedium, color = p.text)
        }
        if (body != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (stage != null) Canvas(Modifier.size(64.dp, 52.dp)) { drawTrailVignette(stage, p) }
                Text(body, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = p.text)
            }
        }
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

