package io.github.fershibli.megagames.games.battleship

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.Handoff
import io.github.fershibli.megagames.ui.components.HandoffOverlay
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.components.numberedSeat
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.currentHaptics
import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.wait
import kotlin.math.roundToInt
import kotlin.random.Random

/** Pauses (milliseconds) so a person sees where each shot fell. */
private const val COMPUTER_PAUSE = 1200L
private const val SEE_RESULT = 1400L
private const val END_PAUSE = 1500L
/** How long a ship that cannot go where it was asked stays outlined in red. */
private const val REFUSED_PAUSE = 900L

/**
 * The ship of [fleet] under [at] on the sea [g], or the one with a square nearest to it within
 * about a square (a finger is wider than a square): its index and the square touched, or null.
 */
private fun shipNear(fleet: List<Ship>, g: SeaGrid, at: Offset): Pair<Int, Int>? {
    var best: Pair<Int, Int>? = null
    var bestDistance = g.cell * 0.9f
    for ((i, ship) in fleet.withIndex()) for (cell in ship.cells) {
        val d = (g.center(cell) - at).getDistance()
        if (d < bestDistance) { bestDistance = d; best = i to cell }
    }
    return best
}

@Composable
fun BattleshipGame(host: GameHost) {
    val together = host.setup.together
    LoadGame(host, BattleshipState.serializer(), { Battleship.newGame(host.difficulty, Random.Default, together) }) { state ->
        BattleshipScreen(state, host, together)
    }
}

@Composable
private fun BattleshipScreen(state: MutableState<BattleshipState>, host: GameHost, together: Boolean) {
    val s = state.value
    val p = LocalPalette.current
    val res = LocalResources.current
    val pace = currentPace
    val handoff = remember { Handoff() }
    val haptics = currentHaptics
    fun human(seat: Int) = together || seat == 0
    val seats = listOf(numberedSeat(1), numberedSeat(2))
    // Whose sea is shown as "yours": the person holding the phone.
    val viewer = when {
        !together -> 0
        !s.playing -> s.placing.coerceAtMost(1)
        else -> s.turn
    }
    var announced by remember { mutableIntStateOf(if (s.playing) s.turn else s.placing) }
    LaunchedEffect(s.turn, s.placing) {
        if (!together || Battleship.over(s)) return@LaunchedEffect
        val next = if (s.playing) s.turn else s.placing
        if (next != announced && next <= 1) {
            // Let the shot be seen before covering the screen.
            if (s.playing) pace.wait(SEE_RESULT)
            handoff.passTo(seats[next])
        }
        announced = next
    }
    LaunchedEffect(s) {
        if (Battleship.over(s)) {
            pace.wait(END_PAUSE)
            val w = Battleship.winner(s)
            val detail = if (together) res.getString(R.string.battleship_player_won, w + 1)
            else if (w == 0) res.getString(R.string.battleship_you_won) else res.getString(R.string.battleship_computer_won)
            host.finish(GameOutcome(together || w == 0, detail, shared = together))
            return@LaunchedEffect
        }
        if (s.playing && !human(s.turn)) {
            pace.wait(COMPUTER_PAUSE)
            val cell = Battleship.choose(s, host.difficulty, Random.Default)
            if (cell >= 0) state.value = Battleship.shoot(s, cell)
        }
    }
    val myTurn = s.playing && !Battleship.over(s) && human(s.turn)

    // Arranging by hand: the ship being dragged and how far (in pixels), the ship that could not
    // go where it was asked (outlined in red for a moment), and the fleets before each change.
    val seat = Battleship.arranging(s, together)
    var lifted by remember { mutableIntStateOf(-1) }
    var lift by remember { mutableStateOf(Offset.Zero) }
    var refused by remember { mutableIntStateOf(-1) }
    var refusals by remember { mutableIntStateOf(0) }
    var noRoom by remember(seat) { mutableStateOf(false) }
    val history = remember(seat) { mutableStateListOf<List<Ship>>() }
    LaunchedEffect(refusals) {
        if (refused < 0) return@LaunchedEffect
        pace.wait(REFUSED_PAUSE)
        refused = -1
    }
    fun arranged(next: BattleshipState) {
        history += state.value.fleets[seat]
        state.value = next
        noRoom = false
        haptics.move()
    }
    fun refuse(index: Int) {
        refused = index
        refusals++
        noRoom = true
    }

    Box(Modifier.fillMaxSize()) {
        GameLayout(
            status = {
                val last = when {
                    s.lastSeat < 0 -> null
                    else -> {
                        val who = if (together) stringResource(R.string.player_number, s.lastSeat + 1) else if (s.lastSeat == 0) stringResource(R.string.battleship_you) else stringResource(R.string.battleship_computer)
                        stringResource(
                            when (s.lastResult) {
                                Shot.MISS -> R.string.battleship_miss
                                Shot.HIT -> R.string.battleship_hit
                                Shot.SUNK -> R.string.battleship_sunk
                            },
                            who,
                        )
                    }
                }
                val line = when {
                    !s.playing && noRoom -> stringResource(R.string.battleship_no_room)
                    !s.playing -> stringResource(R.string.battleship_arrange)
                    Battleship.over(s) -> last
                    myTurn && together -> listOfNotNull(last, stringResource(R.string.battleship_turn_of, s.turn + 1)).joinToString("\n")
                    myTurn -> listOfNotNull(last, stringResource(R.string.battleship_your_turn)).joinToString("\n")
                    else -> listOfNotNull(last, stringResource(R.string.battleship_computer_aiming)).joinToString("\n")
                }
                StatusPill(line ?: "", Modifier.testTag("battleship-status"), emphasized = myTurn || !s.playing && noRoom)
            },
            controls = if (!s.playing) ({
                ActionBar {
                    BigButton(stringResource(R.string.battleship_rearrange), { arranged(Battleship.rearrange(state.value, seat, host.difficulty, Random.Default)) }, Modifier.testTag("battleship-rearrange"), icon = AppIcon.SHUFFLE, style = ButtonStyle.SECONDARY, compact = true)
                    BigButton(stringResource(R.string.battleship_ready), { state.value = Battleship.ready(state.value) }, Modifier.testTag("battleship-ready"), icon = AppIcon.CHECK, compact = true)
                }
            }) else null,
        ) {
            val private = handoff.showsPrivate
            BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.TopCenter) {
                val n = s.size
                if (!s.playing) {
                    // Arranging: your own sea, big. Drag a ship to move it, tap it to turn it.
                    val side = minOf(maxWidth, maxHeight - 110.dp)
                    val description = stringResource(R.string.battleship_arrange)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.battleship_your_sea), style = MaterialTheme.typography.titleMedium, color = p.text)
                        Canvas(
                            Modifier.size(side).testTag("battleship-own").semantics { contentDescription = description }
                                .pointerInput(seat, n) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        val now = state.value
                                        if (now.playing || !handoff.showsPrivate) return@awaitEachGesture
                                        val g = SeaGrid(size.width.toFloat(), n)
                                        val (index, touched) = shipNear(now.fleets[seat], g, down.position) ?: return@awaitEachGesture
                                        var total = Offset.Zero
                                        var dragged = false
                                        while (true) {
                                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            total += change.positionChange()
                                            if (!dragged && total.getDistance() > viewConfiguration.touchSlop) {
                                                dragged = true
                                                lifted = index
                                            }
                                            if (dragged) {
                                                lift = total
                                                change.consume()
                                            }
                                        }
                                        lifted = -1
                                        lift = Offset.Zero
                                        val next = if (dragged) {
                                            Battleship.dropShip(now, seat, index, (total.y / g.cell).roundToInt(), (total.x / g.cell).roundToInt())
                                        } else Battleship.rotateShip(now, seat, index, touched)
                                        when {
                                            next == null -> refuse(index)
                                            // Let go where it was: nothing changes.
                                            next.fleets[seat] != now.fleets[seat] -> arranged(next)
                                        }
                                    }
                                },
                        ) {
                            if (!private) return@Canvas
                            // Where a dragged ship would land, if it can.
                            val landing = if (lifted >= 0) {
                                val cell = SeaGrid(size.width, n).cell
                                Battleship.dropShip(s, seat, lifted, (lift.y / cell).roundToInt(), (lift.x / cell).roundToInt())?.fleets?.get(seat)?.get(lifted)?.cells
                            } else null
                            drawSea(n, s.fleets[viewer], emptySet(), p, lifted = lifted, lift = lift, refused = refused, landing = landing.orEmpty())
                        }
                        // Undo the last move, turn or new arrangement (under the sea, where there is room).
                        BigButton(stringResource(R.string.undo), {
                            val before = history.removeLastOrNull() ?: return@BigButton
                            state.value = state.value.copy(fleets = state.value.fleets.mapIndexed { i, f -> if (i == seat) before else f })
                            noRoom = false
                        }, Modifier.testTag("battleship-undo"), icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = history.isNotEmpty())
                    }
                } else {
                    val big = minOf(maxWidth, (maxHeight - 64.dp) * 0.66f)
                    val small = minOf(maxWidth * 0.5f, maxHeight - big - 64.dp)
                    val target = 1 - viewer
                    val enemyShots = s.shots[viewer].toSet()
                    val sunkShips = s.fleets[target].filter { Battleship.sunk(s, target, it) }
                    val description = stringResource(R.string.battleship_target_board, Battleship.afloat(s, target))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val theirName = stringResource(if (together) R.string.battleship_their_sea_player else R.string.battleship_their_sea, target + 1)
                        Text(stringResource(R.string.battleship_sea_afloat, theirName, Battleship.afloat(s, target)), style = MaterialTheme.typography.titleMedium, color = p.text)
                        Canvas(
                            Modifier.size(big).testTag("battleship-target").semantics { contentDescription = description }
                                .pointerInput(s, myTurn) {
                                    detectTapGestures { pos ->
                                        if (!myTurn || !handoff.showsPrivate) return@detectTapGestures
                                        state.value = Battleship.shoot(state.value, SeaGrid(size.width.toFloat(), n).cellAt(pos))
                                    }
                                },
                        ) {
                            // Only the ships already sunk are shown on the other side's sea.
                            drawSea(n, sunkShips, enemyShots, p, last = if (s.lastSeat == viewer) s.lastCell else -1, sunk = sunkShips.flatMap { it.cells }.toSet() + enemyShots.filter { Battleship.result(s, viewer, it, s.shots[viewer]) != Shot.MISS })
                        }
                        Text(stringResource(R.string.battleship_sea_afloat, stringResource(R.string.battleship_your_sea), Battleship.afloat(s, viewer)), style = MaterialTheme.typography.titleSmall, color = p.textMuted)
                        Canvas(Modifier.size(small).testTag("battleship-own")) {
                            if (private) {
                                val theirShots = s.shots[target].toSet()
                                drawSea(n, s.fleets[viewer], theirShots, p, last = if (s.lastSeat == target) s.lastCell else -1, sunk = s.fleets[viewer].filter { Battleship.sunk(s, viewer, it) }.flatMap { it.cells }.toSet())
                            }
                        }
                    }
                }
            }
        }
        HandoffOverlay(handoff) {}
    }
}
