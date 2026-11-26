package io.github.fershibli.megagames.games.parking

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.currentMotion
import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.spec
import io.github.fershibli.megagames.ui.theme.wait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun ParkingGame(host: GameHost) {
    LoadGame(host, ParkingState.serializer(), { Parking.newGame(host.difficulty, Random.Default) }) { state -> ParkingScreen(state, host) }
}

@Composable
private fun ParkingScreen(state: MutableState<ParkingState>, host: GameHost) {
    val s = state.value
    val p = LocalPalette.current
    val pace = currentPace
    val motion = currentMotion
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    // The vehicle being dragged and how far (in squares) it has been pulled from its place.
    var dragging by remember { mutableIntStateOf(-1) }
    var pull by remember { mutableFloatStateOf(0f) }
    // The red car driving out at the end, in squares past its last place.
    val away = remember { Animatable(0f) }
    // The vehicle just let go, gliding the last bit into its squares.
    var sliding by remember { mutableIntStateOf(-1) }
    val slide = remember { Animatable(0f) }

    LaunchedEffect(s.solved) {
        if (!s.solved) return@LaunchedEffect
        away.animateTo(3f, motion.spec(900))
        pace.wait(600)
        val detail = buildString {
            append(res.getString(R.string.parking_done_moves, s.moves, s.par))
            if (s.hints > 0) append('\n').append(res.getString(R.string.parking_hints_used, s.hints))
        }
        host.finish(GameOutcome(true, detail))
    }

    GameLayout(
        status = {
            val line = when {
                s.solved -> stringResource(R.string.parking_done)
                s.hintVehicle >= 0 -> stringResource(R.string.parking_hint_move)
                else -> stringResource(R.string.parking_goal)
            }
            StatusPill(stringResource(R.string.parking_moves, s.moves, s.par) + "\n" + line, Modifier.testTag("parking-status"), emphasized = s.solved)
        },
        controls = {
            val playing = !s.solved && !host.finished
            ActionBar {
                BigButton(stringResource(R.string.undo), { state.value = Parking.undo(state.value) }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = playing && s.history.isNotEmpty(), compact = true)
                BigButton(stringResource(R.string.hint), {
                    val now = state.value
                    scope.launch {
                        val h = withContext(Dispatchers.Default) { Parking.hint(now) }
                        if (state.value == now) state.value = h
                    }
                }, Modifier.testTag("parking-hint"), icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = playing, compact = true)
                BigButton(stringResource(R.string.parking_restart), { state.value = Parking.restart(state.value) }, icon = AppIcon.NEW_GAME, style = ButtonStyle.SECONDARY, enabled = playing && s.moves > 0, compact = true)
            }
        },
    ) {
        val description = stringResource(R.string.parking_board, s.vehicles.size)
        BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            Canvas(
                Modifier
                    .size(side)
                    .semantics { contentDescription = description }
                    .testTag("parking-board")
                    .pointerInput(s) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val view = ParkingView.of(size.width.toFloat(), size.height.toFloat())
                            val cell = view.cell
                            val (c, r) = view.cellAt(down.position)
                            if (s.solved || r !in 0 until Parking.SIZE || c !in 0 until Parking.SIZE) return@awaitEachGesture
                            val i = s.vehicles.indices.firstOrNull { r * Parking.SIZE + c in Parking.cells(s.vehicles[it], s.positions[it]) } ?: return@awaitEachGesture
                            val v = s.vehicles[i]
                            val reach = Parking.reach(s.vehicles, s.positions, i)
                            dragging = i
                            pull = 0f
                            var total = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val d = change.positionChange()
                                total += (if (v.horizontal) d.x else d.y) / cell
                                pull = total.coerceIn((reach.first - s.positions[i]).toFloat(), (reach.last - s.positions[i]).toFloat())
                                change.consume()
                            }
                            val to = s.positions[i] + pull.roundToInt()
                            // The vehicle glides from where the finger left it into its squares.
                            val left = s.positions[i] + pull - to
                            dragging = -1
                            pull = 0f
                            state.value = Parking.move(state.value, i, to)
                            if (left != 0f) {
                                sliding = i
                                scope.launch {
                                    slide.snapTo(left)
                                    slide.animateTo(0f, motion.spec(220))
                                    if (sliding == i) sliding = -1
                                }
                            }
                        }
                    },
            ) {
                drawParking(s, p, dragging, pull, away.value, sliding, slide.value)
            }
        }
    }
}
