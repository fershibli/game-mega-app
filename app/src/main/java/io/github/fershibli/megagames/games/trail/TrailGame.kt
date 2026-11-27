package io.github.fershibli.megagames.games.trail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import io.github.fershibli.megagames.ui.components.IconView
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

/**
 * Pauses (milliseconds, scaled by the chosen pace) so a person can follow every pawn. Nothing
 * waits to be read: every card stays on the screen until someone taps it.
 */
private const val ROLL_PAUSE = 1200L
private const val TURN_PAUSE = 1500L
private const val END_PAUSE = 1200L

/**
 * A pawn's hop from one square to the next (slower with gentle movement), the rest on each square
 * before the next hop, and the die's tumbles before it settles.
 */
private const val HOP = 650L
private const val STEP_REST = 200L
private const val TUMBLE = 90L
private const val TUMBLES = 7

/** The turn arrow's slow up-and-down: one way in this many milliseconds, by this many dots. */
private const val BOB = 900
private const val BOB_DOTS = 2f

/** Pawn colors by seat (Ludo's red, blue, green and yellow), each with its own symbol. */
private val SEAT_COLORS = intArrayOf(0, 3, 1, 2)
/** The card: how far above the bottom of the game it sits, how wide it grows on a tablet, and the veil over the game behind it. */
private val CARD_LIFT = 28.dp
private val CARD_WIDTH = 560.dp
private const val SCRIM = 0.35f

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
            hop.animateTo(1f, tween((HOP * pace.factor * motion.duration).toInt(), easing = LinearEasing))
            for (seat in from.indices) {
                val target = s.positions[seat]
                if (from[seat] != target) shown[seat] = from[seat] + if (target > from[seat]) 1 else -1
            }
            hopFrom = null
            if (shown.toList() != s.positions) pace.wait(STEP_REST)
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
            // The computer answers a crossroads at once; its answer waits on the card until it is read.
            pending != null && pending.choiceStage >= 0 && pending.stays == null && !human(pending.seat) ->
                state.value = Trail.decide(s, Trail.computerStays(s, host.difficulty, Random.Default))
            pending != null || Trail.hasNews(s) -> Unit // the card waits for a tap
            s.die != 0 -> {
                pace.wait(TURN_PAUSE)
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
        if (settled(s)) TrailCard(s, names, ::human, state)
        HandoffOverlay(handoff) {}
    }
}

/**
 * The card of the square a pawn stopped on, over the game a little above the bottom: a story, a
 * crossroads, a heart or the arrival. Nothing goes on until it is answered: a crossroads by its
 * two choices, anything else by Go on. The computer's cards wait for Go on too, and at a
 * crossroads they show which way it chose.
 */
@Composable
private fun TrailCard(s: TrailState, names: List<String>, human: (Int) -> Boolean, state: MutableState<TrailState>) {
    val p = LocalPalette.current
    val pending = s.pending
    val seat = when {
        pending != null -> pending.seat
        Trail.hasNews(s) -> s.lastSeat
        else -> return
    }
    if (seat !in names.indices) return
    val choice = pending?.choiceStage?.takeIf { it >= 0 }
    val person = human(seat)
    // The computer's answer is on the table a moment after its pawn stops.
    if (choice != null && !person && pending.stays == null) return
    val who = names[seat]
    val story = pending?.story?.takeIf { it >= 0 } ?: if (pending == null) s.lastStory else -1
    val effect = when {
        choice != null -> null
        story >= 0 -> Story.entries[story].let { st -> effectText(st.hearts, st.move) }
        else -> effectText(s.lastHearts, s.lastMove)
    }
    val arrived = choice == null && story < 0 && pending == null && s.positions[seat] == Trail.FINISH
    val title = when {
        choice != null -> stringResource(Stage.entries[choice].label)
        story >= 0 -> stringResource(Story.entries[story].stage.label)
        arrived -> stringResource(R.string.trail_arrived, who)
        else -> null
    }
    val body = when {
        choice != null -> stringResource(Stage.entries[choice].choice)
        story >= 0 -> stringResource(Story.entries[story].text)
        else -> null
    }
    // The stage the card speaks about, for its picture.
    val stage = choice ?: story.takeIf { it >= 0 }?.let { Story.entries[it].stage.ordinal }
    val stay = stringResource(R.string.trail_stay, Trail.STAY_HEARTS)
    val go = stringResource(R.string.trail_go, Trail.GO_SQUARES)
    // "You reached the end!" already names who; otherwise the name comes first.
    val heading = if (arrived) title!! else listOfNotNull(who, title).joinToString(" · ")
    val paper = trailPaper(p)
    val shape = RoundedCornerShape(16.dp)
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(p.background.copy(alpha = SCRIM))
            // Swallows touches so nothing underneath is played while the card is open.
            .pointerInput(Unit) { detectTapGestures {} }
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = CARD_LIFT),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.widthIn(max = CARD_WIDTH).fillMaxWidth().heightIn(max = maxHeight)
                .shadow(10.dp, shape).clip(shape).background(paper)
                .border(BorderStroke(if (p.strongOutlines) 3.dp else 2.dp, if (p.strongOutlines) p.outline else Color(0xFFB9824F)), shape)
                .semantics { paneTitle = heading }
                .testTag("trail-card")
                .padding(14.dp),
        ) {
            // The words scroll when the text is very large; the buttons always stay in sight.
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(28.dp, 34.dp)) {
                        val v = TrailView(0f, 0f, kotlin.math.floor(size.height / 11f).coerceAtLeast(1f))
                        drawTrailPerson(v, Offset(size.width / 2, size.height - v.px), p, SEAT_COLORS[seat])
                    }
                    Text(heading, Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.titleLarge, color = p.text)
                }
                if (body != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (stage != null) Canvas(Modifier.size(72.dp, 58.dp)) { drawTrailVignette(stage, p) }
                        Text(body, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = p.text)
                    }
                }
                if (effect != null) Text(effect, style = MaterialTheme.typography.titleMedium, color = p.success)
                val picked = pending?.stays
                if (choice != null && !person && picked != null) {
                    val mark = RoundedCornerShape(12.dp)
                    Row(
                        Modifier.fillMaxWidth().clip(mark).background(p.highlight.copy(alpha = 0.55f))
                            .border(3.dp, p.primary, mark).padding(10.dp).testTag("trail-chose"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconView(AppIcon.CHECK, p.primary, size = 28.dp)
                        Text(
                            stringResource(R.string.trail_chose, who, if (picked) stay else go),
                            Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleMedium, color = p.text,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (choice != null && person) {
                // The two choices are the answer, one above the other so long words keep their size.
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton(stay, { state.value = Trail.choose(state.value, true) }, Modifier.fillMaxWidth().testTag("trail-stay"))
                    BigButton(go, { state.value = Trail.choose(state.value, false) }, Modifier.fillMaxWidth().testTag("trail-go"), style = ButtonStyle.SECONDARY)
                }
            } else {
                BigButton(
                    stringResource(R.string.trail_ok),
                    {
                        val now = state.value
                        val answer = now.pending?.stays
                        state.value = when {
                            now.pending?.story?.let { it >= 0 } == true -> Trail.readStory(now)
                            answer != null -> Trail.choose(now, answer)
                            else -> Trail.see(now)
                        }
                    },
                    Modifier.fillMaxWidth().testTag("trail-ok"), icon = AppIcon.CHECK,
                )
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

