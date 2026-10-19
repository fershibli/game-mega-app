package io.github.fershibli.megagames.games.sudoku

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.ui.art.pxToSp
import io.github.fershibli.megagames.ui.art.text
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.LocalSettings
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class SudokuState(
    val givens: List<Int>,
    val solution: List<Int>,
    val values: List<Int>,
    /** Pencil marks: bit n set means candidate n. */
    val notes: List<Int> = List(81) { 0 },
    val selected: Int = 40,
    val notesMode: Boolean = false,
    val hintsUsed: Int = 0,
) {
    val solved: Boolean get() = values == solution
}

fun newSudoku(difficulty: Difficulty, random: Random = Random.Default): SudokuState {
    val p = SudokuEngine.generate(difficulty, random)
    return SudokuState(p.givens.toList(), p.solution.toList(), p.givens.toList())
}

@Composable
fun SudokuGame(host: GameHost) {
    LoadGame(host, SudokuState.serializer(), { newSudoku(host.difficulty) }) { state ->
        SudokuScreen(state, host)
    }
}

@Composable
private fun SudokuScreen(state: MutableState<SudokuState>, host: GameHost) {
    val s = state.value
    val history = remember { mutableStateListOf<SudokuState>() }
    val showErrors = host.difficulty != Difficulty.HARD
    val scale = LocalSettings.current.pieceScale

    fun update(next: SudokuState) {
        if (next == s) return
        history += s.copy(selected = next.selected, notesMode = next.notesMode)
        if (history.size > 200) history.removeAt(0)
        state.value = next
        if (next.solved) host.finish(GameOutcome(true, null))
    }

    fun place(v: Int) {
        val i = s.selected
        if (s.givens[i] != 0) return
        if (s.notesMode && v != 0) {
            val notes = s.notes.toMutableList().also { it[i] = it[i] xor (1 shl v) }
            update(s.copy(notes = notes, values = s.values.toMutableList().also { it[i] = 0 }))
            return
        }
        val values = s.values.toMutableList().also { it[i] = if (it[i] == v) 0 else v }
        val notes = s.notes.toMutableList()
        notes[i] = 0
        if (v != 0) SudokuEngine.peers(i).forEach { notes[it] = notes[it] and (1 shl v).inv() }
        update(s.copy(values = values, notes = notes))
    }

    fun hint() {
        val wrongOrEmpty = { i: Int -> s.values[i] != s.solution[i] }
        val target = if (wrongOrEmpty(s.selected)) s.selected else (0 until 81).filter(wrongOrEmpty).randomOrNull() ?: return
        val values = s.values.toMutableList().also { it[target] = s.solution[target] }
        val notes = s.notes.toMutableList().also { it[target] = 0 }
        update(s.copy(values = values, notes = notes, selected = target, hintsUsed = s.hintsUsed + 1))
    }

    val counts = IntArray(10).also { c -> s.values.forEach { c[it]++ } }

    GameLayout(
        status = {
            StatusPill(if (s.notesMode) stringResource(R.string.sudoku_notes_on) else stringResource(R.string.sudoku_tap_cell))
        },
        controls = {
            val keyHeight = (60 * scale).dp
            for (row in listOf(1..5, 6..9)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (v in row) {
                        BigButton(
                            v.toString(), { place(v) }, Modifier.weight(1f).height(keyHeight),
                            style = if (s.notesMode) ButtonStyle.SECONDARY else ButtonStyle.PRIMARY,
                            enabled = counts[v] < 9, compact = true, bigText = true,
                        )
                    }
                    if (row.last == 9) {
                        BigButton("", { place(0) }, Modifier.weight(1f).height(keyHeight), icon = AppIcon.ERASE, style = ButtonStyle.SECONDARY, compact = true)
                    }
                }
            }
            ActionBar {
                BigButton(stringResource(R.string.undo), { history.removeLastOrNull()?.let { state.value = it } }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = history.isNotEmpty(), compact = true)
                BigButton(stringResource(R.string.hint), ::hint, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, compact = true)
                BigButton(stringResource(R.string.sudoku_notes), { state.value = s.copy(notesMode = !s.notesMode) }, icon = AppIcon.PENCIL, style = if (s.notesMode) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY, compact = true)
            }
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            SudokuBoard(s, showErrors, Modifier.size(side).testTag("sudoku-board")) { state.value = state.value.copy(selected = it) }
        }
    }
}

@Composable
private fun SudokuBoard(s: SudokuState, showErrors: Boolean, modifier: Modifier, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    val measurer = rememberTextMeasurer()
    val conflicts = remember(s.values) { SudokuEngine.conflicts(s.values) }
    // The tap detector below lives as long as the board, so it must always call the latest
    // callback: a stale one would bring back the state of the first tap and erase new numbers.
    val select by rememberUpdatedState(onSelect)
    Canvas(
        modifier.pointerInput(Unit) {
            detectTapGestures { pos ->
                val cell = size.width / 9f
                val c = (pos.x / cell).toInt().coerceIn(0, 8)
                val r = (pos.y / cell).toInt().coerceIn(0, 8)
                select(r * 9 + c)
            }
        },
    ) {
        val cell = size.width / 9f
        val sel = s.selected
        val selValue = s.values[sel]
        drawRect(p.boardLight)
        for (i in 0 until 81) {
            val r = i / 9
            val c = i % 9
            val tl = Offset(c * cell, r * cell)
            val related = SudokuEngine.row(i) == SudokuEngine.row(sel) || SudokuEngine.col(i) == SudokuEngine.col(sel) || SudokuEngine.box(i) == SudokuEngine.box(sel)
            val boxShade = ((r / 3) + (c / 3)) % 2 == 1
            when {
                i == sel -> drawRect(p.highlight, tl, Size(cell, cell))
                selValue != 0 && s.values[i] == selValue -> drawRect(p.highlight.copy(alpha = 0.55f), tl, Size(cell, cell))
                related -> drawRect(p.primary.copy(alpha = 0.10f), tl, Size(cell, cell))
                boxShade -> drawRect(p.boardDark.copy(alpha = 0.6f), tl, Size(cell, cell))
            }
            val v = s.values[i]
            val center = tl + Offset(cell / 2, cell / 2)
            if (v != 0) {
                val given = s.givens[i] != 0
                val wrong = !given && ((showErrors && v != s.solution[i]) || i in conflicts)
                val color = when {
                    wrong -> p.danger
                    given -> p.text
                    else -> p.primary
                }
                text(measurer, v.toString(), center, color, pxToSp(cell * 0.62f), bold = given || wrong)
                if (wrong) drawLine(p.danger, tl + Offset(cell * 0.2f, cell * 0.85f), tl + Offset(cell * 0.8f, cell * 0.85f), cell * 0.05f)
            } else if (s.notes[i] != 0) {
                for (n in 1..9) {
                    if (s.notes[i] and (1 shl n) == 0) continue
                    val nr = (n - 1) / 3
                    val nc = (n - 1) % 3
                    text(measurer, n.toString(), tl + Offset(cell * (nc + 0.5f) / 3f, cell * (nr + 0.5f) / 3f), p.textMuted, pxToSp(cell * 0.27f), bold = false)
                }
            }
        }
        val thin = if (p.strongOutlines) 1.5.dp.toPx() else 1.dp.toPx()
        val thick = if (p.strongOutlines) 4.dp.toPx() else 3.dp.toPx()
        for (k in 0..9) {
            val w = if (k % 3 == 0) thick else thin
            val a = if (k % 3 == 0) 1f else 0.4f
            drawLine(p.boardLine.copy(alpha = a), Offset(k * cell, 0f), Offset(k * cell, size.height), w)
            drawLine(p.boardLine.copy(alpha = a), Offset(0f, k * cell), Offset(size.width, k * cell), w)
        }
    }
}
