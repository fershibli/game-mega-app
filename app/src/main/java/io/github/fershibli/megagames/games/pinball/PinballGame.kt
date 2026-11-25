package io.github.fershibli.megagames.games.pinball

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.core.WorldStore
import io.github.fershibli.megagames.core.rememberWorldStore
import io.github.fershibli.megagames.games.arcade.GameTicker
import io.github.fershibli.megagames.games.arcade.KeepBest
import io.github.fershibli.megagames.games.arcade.Records
import io.github.fershibli.megagames.games.arcade.rememberRunning
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.currentHaptics
import io.github.fershibli.megagames.ui.theme.currentMotion
import io.github.fershibli.megagames.core.Motion
import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.wait

/** Milliseconds between two frames of the table. */
private const val FRAME_MS = 16L

@Composable
fun PinballGame(host: GameHost) {
    val store = rememberWorldStore()
    LoadGame(host, PinballState.serializer(), { Pinball.newGame(host.difficulty, Records.best(store, host.game, host.difficulty)) }) { state ->
        PinballScreen(state, host, store)
    }
}

/** A short message over the table: a lost ball, a saved one, all the stars lit. */
private enum class Note { NONE, SAVED, LOST, STARS, LANES, TARGETS }

@Composable
private fun PinballScreen(state: MutableState<PinballState>, host: GameHost, store: WorldStore) {
    val s = state.value
    val p = LocalPalette.current
    val pace = currentPace
    val haptics = currentHaptics
    var running by rememberRunning()
    val table = remember { Table(s.difficulty) }
    val backdrop = remember { PinballBackdrop() }
    table.effects = currentMotion != Motion.NONE
    var frame by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf(Note.NONE) }

    GameTicker(running && !s.over && table.inPlay, FRAME_MS) {
        val (next, hits) = table.step(FRAME_MS / 1000f, state.value)
        if (next != state.value) state.value = next
        if (Hit.BUMPER in hits) haptics.button()
        when {
            Hit.SAVED in hits -> note = Note.SAVED
            Hit.DRAINED in hits -> note = Note.LOST
            Hit.ALL_STARS in hits -> note = Note.STARS
            Hit.ALL_LANES in hits -> note = Note.LANES
            Hit.ALL_TARGETS in hits -> note = Note.TARGETS
        }
        frame++
    }
    // A saved ball comes back by itself after a moment.
    LaunchedEffect(note) {
        when (note) {
            Note.SAVED -> {
                pace.wait(1200)
                table.serve()
                note = Note.NONE
            }
            Note.STARS, Note.LANES, Note.TARGETS -> {
                val shown = note
                pace.wait(1500)
                if (note == shown) note = Note.NONE
            }
            else -> Unit
        }
    }

    val best = maxOf(s.score, s.recordToBeat)
    val newBest = s.score > s.recordToBeat && s.score > 0
    KeepBest(store, host.game, host.difficulty, s.score, s.recordToBeat)
    val detail = listOfNotNull(
        if (newBest) stringResource(R.string.pinball_new_best) else null,
        stringResource(R.string.pinball_final, s.score, best),
    ).joinToString("\n")
    LaunchedEffect(s.over) {
        if (s.over) {
            running = false
            pace.wait(1500)
            host.finish(GameOutcome(newBest, detail))
        }
    }

    fun serve() {
        if (s.over || table.inPlay) return
        table.serve()
        note = Note.NONE
        state.value = state.value.copy(served = state.value.served + 1)
        running = true
        frame++
    }

    fun launch() {
        if (table.launch()) {
            running = true
            haptics.button()
        }
        frame++
    }

    GameLayout(
        status = {
            val lines = listOfNotNull(
                stringResource(R.string.pinball_score, s.score, best),
                stringResource(R.string.pinball_balls, s.balls),
                if (s.multiplier > 1) stringResource(R.string.pinball_double) else null,
            )
            ScorePanel(lines.joinToString("\n"), Modifier.testTag("pinball-status"))
        },
        controls = {
            // The flipper buttons keep their physical sides in every language.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FlipperButton(stringResource(R.string.pinball_left), Modifier.weight(1f).testTag("pinball-left"), !s.over) { table.pressed[0] = it; frame++ }
                FlipperButton(stringResource(R.string.pinball_right), Modifier.weight(1f).testTag("pinball-right"), !s.over) { table.pressed[1] = it; frame++ }
            }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                @Suppress("UNUSED_EXPRESSION") frame
                if (table.onPlunger) {
                    LaunchButton(stringResource(R.string.pinball_launch), table.charge, Modifier.weight(1f).testTag("pinball-launch"), { table.pulling = it; frame++ }, ::launch)
                } else {
                    BigButton(
                        stringResource(if (running) R.string.pinball_pause else R.string.pinball_continue), { running = !running },
                        Modifier.weight(1f), style = ButtonStyle.SECONDARY, enabled = !s.over && table.inPlay, compact = true,
                    )
                }
            }
        },
    ) {
        val description = stringResource(R.string.pinball_board)
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            val w = minOf(maxWidth, maxHeight / Pinball.HEIGHT)
            Box(contentAlignment = Alignment.Center) {
                Canvas(
                    Modifier
                        .size(w, w * Pinball.HEIGHT)
                        .semantics { contentDescription = description }
                        .testTag("pinball-table")
                        .pointerInput(Unit) {
                            // Holding the left or right half of the table works the flipper on that side.
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val side = if (down.position.x < size.width / 2) 0 else 1
                                table.pressed[side] = true
                                frame++
                                waitForUpOrCancellation()
                                table.pressed[side] = false
                                frame++
                            }
                        },
                ) {
                    @Suppress("UNUSED_EXPRESSION") frame
                    drawPinball(table, s, p, backdrop)
                }
                val message = when {
                    s.over -> null
                    note == Note.SAVED -> stringResource(R.string.pinball_saved)
                    note == Note.STARS -> stringResource(R.string.pinball_all_stars)
                    note == Note.LANES -> stringResource(R.string.pinball_all_lanes)
                    note == Note.TARGETS -> stringResource(R.string.pinball_all_targets)
                    !running && table.inPlay -> stringResource(R.string.pinball_paused)
                    !table.inPlay && note == Note.LOST -> stringResource(R.string.pinball_lost, s.balls)
                    !table.inPlay -> if (s.served == 0) stringResource(R.string.pinball_ready) else stringResource(R.string.pinball_next)
                    else -> null
                }
                if (message != null) {
                    Column(
                        Modifier.padding(24.dp).background(p.surface.copy(alpha = 0.94f), RoundedCornerShape(18.dp)).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(message, style = MaterialTheme.typography.titleLarge, color = p.text, textAlign = TextAlign.Center)
                        when {
                            !table.inPlay && note != Note.SAVED -> BigButton(stringResource(R.string.pinball_serve), ::serve, Modifier.testTag("pinball-serve"))
                            !running && table.inPlay -> BigButton(stringResource(R.string.pinball_continue), { running = true })
                        }
                    }
                }
            }
        }
    }
}

/** The score as a lit panel, like the board over a real table. */
@Composable
private fun ScorePanel(text: String, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.fillMaxWidth().clip(shape).background(Color(0xFF1A1030))
            .border(3.dp, Color(0xFFFFB300), shape).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
            color = Color(0xFFFFD54F), textAlign = TextAlign.Center,
        )
    }
}

/**
 * The plunger's button: hold it to pull the spring (the bar fills), let go to launch. A tap is
 * enough on Easy, where the strength is chosen for you.
 */
@Composable
private fun LaunchButton(label: String, charge: Float, modifier: Modifier, onHold: (Boolean) -> Unit, onLaunch: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .height(64.dp)
            .clip(shape)
            .background(p.success)
            .border(if (p.strongOutlines) 2.dp else 0.dp, p.outline, shape)
            .semantics {
                role = Role.Button
                onClick { onLaunch(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    onHold(true)
                    waitForUpOrCancellation()
                    onHold(false)
                    onLaunch()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(charge.coerceIn(0f, 1f)).background(Color.White.copy(alpha = 0.3f)))
        // Black or white, whichever stands out more on the button.
        val l = p.success.luminance()
        val ink = if ((l + 0.05f) / 0.05f > 1.05f / (l + 0.05f)) Color.Black else Color.White
        Text("▲ $label", style = MaterialTheme.typography.titleLarge, color = ink, textAlign = TextAlign.Center)
    }
}

/** A big button that works a flipper while it is held down. */
@Composable
private fun FlipperButton(label: String, modifier: Modifier, enabled: Boolean, onPress: (Boolean) -> Unit) {
    val p = LocalPalette.current
    var down by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .height(72.dp)
            .clip(shape)
            .background(if (down) p.primary.copy(alpha = 0.8f) else p.primary)
            .border(if (p.strongOutlines) 2.dp else 0.dp, p.outline, shape)
            .semantics { role = Role.Button }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    down = true
                    onPress(true)
                    waitForUpOrCancellation()
                    down = false
                    onPress(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, color = p.onPrimary.copy(alpha = if (enabled) 1f else 0.4f), textAlign = TextAlign.Center)
    }
}
