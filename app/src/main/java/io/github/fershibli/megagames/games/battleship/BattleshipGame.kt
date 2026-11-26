package io.github.fershibli.megagames.games.battleship

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.wait
import kotlin.random.Random

/** Pauses (milliseconds) so a person sees where each shot fell. */
private const val COMPUTER_PAUSE = 1200L
private const val SEE_RESULT = 1400L
private const val END_PAUSE = 1500L

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
                    !s.playing -> stringResource(R.string.battleship_arrange)
                    Battleship.over(s) -> last
                    myTurn && together -> listOfNotNull(last, stringResource(R.string.battleship_turn_of, s.turn + 1)).joinToString("\n")
                    myTurn -> listOfNotNull(last, stringResource(R.string.battleship_your_turn)).joinToString("\n")
                    else -> listOfNotNull(last, stringResource(R.string.battleship_computer_aiming)).joinToString("\n")
                }
                StatusPill(line ?: "", Modifier.testTag("battleship-status"), emphasized = myTurn)
            },
            controls = if (!s.playing) ({
                ActionBar {
                    BigButton(stringResource(R.string.battleship_rearrange), { state.value = Battleship.rearrange(state.value, host.difficulty, Random.Default) }, Modifier.testTag("battleship-rearrange"), icon = AppIcon.SHUFFLE, style = ButtonStyle.SECONDARY, compact = true)
                    BigButton(stringResource(R.string.battleship_ready), { state.value = Battleship.ready(state.value) }, Modifier.testTag("battleship-ready"), icon = AppIcon.CHECK, compact = true)
                }
            }) else null,
        ) {
            val private = handoff.showsPrivate
            BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.TopCenter) {
                val n = s.size
                if (!s.playing) {
                    // Arranging: your own sea, big.
                    val side = minOf(maxWidth, maxHeight - 40.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.battleship_your_sea), style = MaterialTheme.typography.titleMedium, color = p.text)
                        Canvas(Modifier.size(side).testTag("battleship-own")) {
                            if (private) drawSea(n, s.fleets[viewer], emptySet(), p)
                        }
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
