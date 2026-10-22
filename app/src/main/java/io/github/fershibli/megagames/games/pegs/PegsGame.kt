package io.github.fershibli.megagames.games.pegs

import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.wait
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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

/** Pause before the end-of-game message, so the last jump can be seen. */
private const val END_PAUSE_MS = 700L

@Composable
fun PegsGame(host: GameHost) {
    val french = host.variant?.key == "french"
    LoadGame(host, PegsState.serializer(), { Pegs.newGame(french, host.difficulty) }) { state -> PegsScreen(state, host) }
}

@Composable
private fun PegsScreen(state: MutableState<PegsState>, host: GameHost) {
    val pace = currentPace
    val s = state.value
    val resources = LocalResources.current
    val legal = Pegs.legalMoves(s.french, s.pegs)

    LaunchedEffect(s.pegs) {
        if (!Pegs.isOver(s)) return@LaunchedEffect
        pace.wait(END_PAUSE_MS)
        val n = s.count
        when {
            Pegs.isPerfect(s) -> host.finish(GameOutcome(true, resources.getString(R.string.pegs_win_perfect)))
            n == 1 -> host.finish(GameOutcome(true, resources.getString(R.string.pegs_win_one)))
            else -> host.finish(GameOutcome(false, resources.getQuantityString(R.plurals.pegs_end_left, n, n)))
        }
    }

    // Reads the latest state: the tap handler below is installed once.
    fun tap(cell: Int) {
        val cur = state.value
        if (cell < 0 || !Pegs.isHole(cur.french, cell)) {
            state.value = cur.copy(selected = -1)
            return
        }
        when {
            cur.pegs[cell] -> state.value = cur.copy(selected = if (cur.selected == cell) -1 else cell)
            cur.selected >= 0 -> Pegs.play(cur, cur.selected, cell)?.let { state.value = it }
        }
    }

    GameLayout(
        status = {
            val action = when {
                legal.isEmpty() -> stringResource(R.string.pegs_no_jumps)
                s.selected >= 0 -> stringResource(R.string.pegs_pick_hole)
                else -> stringResource(R.string.pegs_pick_peg)
            }
            StatusPill(stringResource(R.string.pegs_left, s.count) + "\n" + action, emphasized = s.selected < 0 && legal.isNotEmpty())
        },
        controls = {
            ActionBar {
                BigButton(stringResource(R.string.undo), { state.value = Pegs.undo(state.value) }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = s.history.isNotEmpty() && !host.finished)
                BigButton(stringResource(R.string.pegs_restart), { state.value = Pegs.restart(state.value) }, icon = AppIcon.NEW_GAME, style = ButtonStyle.SECONDARY, enabled = s.history.isNotEmpty() && !host.finished)
            }
        },
    ) {
        val p = LocalPalette.current
        val targets = if (s.showJumps && s.selected >= 0) legal.filter { it.from == s.selected }.map { it.to }.toSet() else emptySet()
        val movable = if (s.showJumps && s.selected < 0) legal.map { it.from }.toSet() else emptySet()
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            Canvas(
                Modifier
                    .size(side)
                    .testTag("pegs-board")
                    .pointerInput(Unit) {
                        detectTapGestures { pos -> tap(PegsGeometry(size.width.toFloat()).cellAt(pos)) }
                    },
            ) {
                drawPegBoard(s.french, s.pegs, p, s.selected, targets, movable, s.goal)
            }
        }
    }
}
