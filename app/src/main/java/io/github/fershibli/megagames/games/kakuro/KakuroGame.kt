package io.github.fershibli.megagames.games.kakuro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.games.numbers.NumberControls
import io.github.fershibli.megagames.games.numbers.combinationsFor
import io.github.fershibli.megagames.games.numbers.combinationsText
import io.github.fershibli.megagames.ui.art.pxToSp
import io.github.fershibli.megagames.ui.art.text
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.Palette
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class KakuroState(
    val width: Int,
    val height: Int,
    /** Digit of every white square, 0 for the black squares (the sums are read from it). */
    val solution: List<Int>,
    val values: List<Int>,
    /** Pencil marks: bit n set means candidate n. */
    val notes: List<Int>,
    val selected: Int,
    val notesMode: Boolean = false,
    val hintsUsed: Int = 0,
    /** Square whose run combinations the Hint is showing (-1 = none). */
    val hintCell: Int = -1,
) {
    val solved: Boolean get() = values == solution
}

fun newKakuro(difficulty: Difficulty, random: Random = Random.Default): KakuroState {
    val p = Kakuro.generate(difficulty, random)
    val n = p.width * p.height
    return KakuroState(p.width, p.height, p.solution.toList(), List(n) { 0 }, List(n) { 0 }, p.solution.indexOfFirst { it != 0 })
}

@Composable
fun KakuroGame(host: GameHost) {
    LoadGame(host, KakuroState.serializer(), { newKakuro(host.difficulty) }) { state ->
        KakuroScreen(state, host)
    }
}

@Composable
private fun KakuroScreen(state: MutableState<KakuroState>, host: GameHost) {
    val s = state.value
    val history = remember { mutableStateListOf<KakuroState>() }
    val showErrors = host.difficulty != Difficulty.HARD
    val runs = remember(s.solution) { Kakuro.runsOf(s.width, s.height, s.solution.toIntArray()) }

    fun update(next: KakuroState) {
        if (next == s) return
        history += s.copy(selected = next.selected, notesMode = next.notesMode, hintCell = -1)
        if (history.size > 200) history.removeAt(0)
        state.value = next
        if (next.solved) host.finish(GameOutcome(true, null))
    }

    fun place(v: Int) {
        val i = s.selected
        if (s.solution[i] == 0) return
        if (s.notesMode && v != 0) {
            val notes = s.notes.toMutableList().also { it[i] = it[i] xor (1 shl v) }
            update(s.copy(notes = notes, values = s.values.toMutableList().also { it[i] = 0 }))
            return
        }
        val values = s.values.toMutableList().also { it[i] = if (it[i] == v) 0 else v }
        val notes = s.notes.toMutableList()
        notes[i] = 0
        if (v != 0) {
            for (run in runs) if (i in run.cells) run.cells.forEach { notes[it] = notes[it] and (1 shl v).inv() }
        }
        update(s.copy(values = values, notes = notes))
    }

    // First tap: the combinations that fit the selected square's runs. Second tap: its digit.
    fun hint() {
        if (s.hintCell != s.selected) {
            state.value = s.copy(hintCell = s.selected)
            return
        }
        val wrongOrEmpty = { i: Int -> s.solution[i] != 0 && s.values[i] != s.solution[i] }
        val target = if (wrongOrEmpty(s.selected)) s.selected else s.solution.indices.filter(wrongOrEmpty).randomOrNull() ?: return
        val values = s.values.toMutableList().also { it[target] = s.solution[target] }
        val notes = s.notes.toMutableList().also { it[target] = 0 }
        update(s.copy(values = values, notes = notes, selected = target, hintsUsed = s.hintsUsed + 1))
    }

    GameLayout(
        status = {
            val status = when {
                s.hintCell == s.selected -> {
                    // "→ 16: 7+9   ↓ 6: 1+2+3" for the runs across and down through this square.
                    val lines = runs.filter { s.selected in it.cells }.sortedBy { !it.across }.joinToString("\n") { run ->
                        var placed = 0
                        for (c in run.cells) if (s.values[c] != 0 && s.values[c] == s.solution[c]) placed = placed or (1 shl s.values[c])
                        (if (run.across) "→ " else "↓ ") + run.sum + ": " + combinationsText(combinationsFor(run.sum, run.cells.size, placed), 3)
                    }
                    lines + "\n" + stringResource(R.string.kakuro_hint_again)
                }
                s.notesMode -> stringResource(R.string.sudoku_notes_on)
                else -> stringResource(R.string.kakuro_tap_cell)
            }
            StatusPill(status)
        },
        controls = {
            NumberControls(
                notesMode = s.notesMode,
                canUndo = history.isNotEmpty(),
                onDigit = ::place,
                onUndo = { history.removeLastOrNull()?.let { state.value = it } },
                onHint = ::hint,
                onNotes = { state.value = s.copy(notesMode = !s.notesMode) },
            )
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            val cell = minOf(maxWidth / s.width, maxHeight / s.height)
            KakuroBoard(s, runs, showErrors, Modifier.size(cell * s.width, cell * s.height).testTag("kakuro-board")) {
                if (state.value.solution[it] != 0) state.value = state.value.copy(selected = it, hintCell = -1)
            }
        }
    }
}

@Composable
private fun KakuroBoard(s: KakuroState, runs: List<Kakuro.Run>, showErrors: Boolean, modifier: Modifier, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    val measurer = rememberTextMeasurer()
    val conflicts = remember(s.values) { Kakuro.conflicts(s.values, runs) }
    val active = runs.filter { s.selected in it.cells }
    // The tap detector lives as long as the board: it must always call the latest callback.
    val select by rememberUpdatedState(onSelect)
    Canvas(
        modifier.pointerInput(s.width, s.height) {
            detectTapGestures { pos ->
                val cell = size.width / s.width.toFloat()
                val c = (pos.x / cell).toInt().coerceIn(0, s.width - 1)
                val r = (pos.y / cell).toInt().coerceIn(0, s.height - 1)
                select(r * s.width + c)
            }
        },
    ) {
        val cell = size.width / s.width
        val selValue = s.values[s.selected]
        for (i in s.solution.indices) {
            val tl = Offset(i % s.width * cell, i / s.width * cell)
            if (s.solution[i] == 0) continue
            val bg = when {
                i == s.selected -> p.highlight
                selValue != 0 && s.values[i] == selValue -> p.highlight.copy(alpha = 0.55f)
                active.any { i in it.cells } -> p.primary.copy(alpha = 0.12f)
                else -> null
            }
            drawRect(p.boardLight, tl, Size(cell, cell))
            if (bg != null) drawRect(bg, tl, Size(cell, cell))
            val v = s.values[i]
            if (v != 0) {
                val wrong = (showErrors && v != s.solution[i]) || i in conflicts
                text(measurer, v.toString(), tl + Offset(cell / 2, cell / 2), if (wrong) p.danger else p.primary, pxToSp(cell * 0.62f), bold = wrong)
                if (wrong) drawLine(p.danger, tl + Offset(cell * 0.2f, cell * 0.86f), tl + Offset(cell * 0.8f, cell * 0.86f), cell * 0.05f)
            } else if (s.notes[i] != 0) {
                for (n in 1..9) {
                    if (s.notes[i] and (1 shl n) == 0) continue
                    val nr = (n - 1) / 3
                    val nc = (n - 1) % 3
                    text(measurer, n.toString(), tl + Offset(cell * (nc + 0.5f) / 3f, cell * (nr + 0.5f) / 3f), p.textMuted, pxToSp(cell * 0.27f), bold = false)
                }
            }
        }
        drawClues(p, s, runs, active, cell, measurer)
        val line = if (p.strongOutlines) 2.dp.toPx() else 1.dp.toPx()
        for (k in 0..s.width) drawLine(p.boardLine.copy(alpha = 0.6f), Offset(k * cell, 0f), Offset(k * cell, size.height), line)
        for (k in 0..s.height) drawLine(p.boardLine.copy(alpha = 0.6f), Offset(0f, k * cell), Offset(size.width, k * cell), line)
        drawRect(p.boardLine, Offset.Zero, size, style = androidx.compose.ui.graphics.drawscope.Stroke(line * 2.5f))
    }
}

/**
 * Black squares: a diagonal, the sum of the run to the right in the upper right half and the
 * sum of the run below in the lower left half. The sums of the selected square's runs are
 * highlighted.
 */
private fun DrawScope.drawClues(p: Palette, s: KakuroState, runs: List<Kakuro.Run>, active: List<Kakuro.Run>, cell: Float, measurer: TextMeasurer) {
    val across = IntArray(s.solution.size) { -1 }
    val down = IntArray(s.solution.size) { -1 }
    for (run in runs) if (run.across) across[run.clue] = run.sum else down[run.clue] = run.sum
    for (i in s.solution.indices) {
        if (s.solution[i] != 0) continue
        val tl = Offset(i % s.width * cell, i / s.width * cell)
        drawRect(p.boardLine, tl, Size(cell, cell))
        if (across[i] < 0 && down[i] < 0) continue
        drawLine(p.boardLight.copy(alpha = 0.7f), tl, tl + Offset(cell, cell), cell * 0.03f)
        val size = pxToSp(cell * 0.34f)
        if (across[i] >= 0) {
            val on = active.any { it.across && it.clue == i }
            text(measurer, across[i].toString(), tl + Offset(cell * 0.7f, cell * 0.3f), if (on) p.highlight else p.boardLight, size)
        }
        if (down[i] >= 0) {
            val on = active.any { !it.across && it.clue == i }
            text(measurer, down[i].toString(), tl + Offset(cell * 0.3f, cell * 0.72f), if (on) p.highlight else p.boardLight, size)
        }
    }
}

/** Home screen picture: a small kakuro corner with its sums. */
fun kakuroThumbnail(scope: DrawScope, p: Palette, m: TextMeasurer) = scope.run {
    val n = 4
    val cell = size.width / n
    // 0 = white square; otherwise "across/down" sums (−1 = none).
    val clues = mapOf(0 to (-1 to -1), 1 to (-1 to 16), 2 to (-1 to 9), 3 to (-1 to -1), 4 to (10 to -1), 7 to (-1 to 9), 8 to (15 to -1), 12 to (-1 to -1), 13 to (8 to -1))
    val digits = mapOf(5 to 9, 6 to 1, 9 to 7, 10 to 5, 14 to 3)
    for (i in 0 until n * n) {
        val tl = Offset(i % n * cell, i / n * cell)
        val clue = clues[i]
        if (clue == null) {
            drawRect(p.boardLight, tl, Size(cell, cell))
            digits[i]?.let { text(m, it.toString(), tl + Offset(cell / 2, cell / 2), p.primary, pxToSp(cell * 0.6f)) }
            continue
        }
        drawRect(p.boardLine, tl, Size(cell, cell))
        if (clue.first < 0 && clue.second < 0) continue
        drawLine(p.boardLight, tl, tl + Offset(cell, cell), cell * 0.04f)
        if (clue.first >= 0) text(m, clue.first.toString(), tl + Offset(cell * 0.7f, cell * 0.3f), p.boardLight, pxToSp(cell * 0.32f))
        if (clue.second >= 0) text(m, clue.second.toString(), tl + Offset(cell * 0.3f, cell * 0.72f), p.boardLight, pxToSp(cell * 0.32f))
    }
    for (k in 0..n) {
        drawLine(p.boardLine, Offset(k * cell, 0f), Offset(k * cell, size.height), cell * 0.03f)
        drawLine(p.boardLine, Offset(0f, k * cell), Offset(size.width, k * cell), cell * 0.03f)
    }
}
