package io.github.fershibli.megagames.games.killer

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import io.github.fershibli.megagames.games.sudoku.SudokuEngine
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
data class KillerState(
    val solution: List<Int>,
    /** Cage number of every square. */
    val cage: List<Int>,
    /** Total of every cage. */
    val sums: List<Int>,
    val values: List<Int> = List(81) { 0 },
    /** Pencil marks: bit n set means candidate n. */
    val notes: List<Int> = List(81) { 0 },
    val selected: Int = 40,
    val notesMode: Boolean = false,
    val hintsUsed: Int = 0,
    /** Square whose cage combinations the Hint is showing (-1 = none). */
    val hintCell: Int = -1,
) {
    val solved: Boolean get() = values == solution
}

fun newKiller(difficulty: Difficulty, random: Random = Random.Default): KillerState {
    val p = Killer.generate(difficulty, random)
    return KillerState(p.solution.toList(), p.cage.toList(), p.sums.toList())
}

@Composable
fun KillerGame(host: GameHost) {
    LoadGame(host, KillerState.serializer(), { newKiller(host.difficulty) }) { state ->
        KillerScreen(state, host)
    }
}

@Composable
private fun KillerScreen(state: MutableState<KillerState>, host: GameHost) {
    val s = state.value
    val history = remember { mutableStateListOf<KillerState>() }
    val showErrors = host.difficulty != Difficulty.HARD
    val cages = remember(s.cage) { Killer.cagesOf(s.cage.toIntArray(), s.sums.size) }

    fun update(next: KillerState) {
        if (next == s) return
        history += s.copy(selected = next.selected, notesMode = next.notesMode, hintCell = -1)
        if (history.size > 200) history.removeAt(0)
        state.value = next
        if (next.solved) host.finish(GameOutcome(true, null))
    }

    fun place(v: Int) {
        val i = s.selected
        if (s.notesMode && v != 0) {
            val notes = s.notes.toMutableList().also { it[i] = it[i] xor (1 shl v) }
            update(s.copy(notes = notes, values = s.values.toMutableList().also { it[i] = 0 }))
            return
        }
        val values = s.values.toMutableList().also { it[i] = if (it[i] == v) 0 else v }
        val notes = s.notes.toMutableList()
        notes[i] = 0
        if (v != 0) {
            val clear = (1 shl v).inv()
            SudokuEngine.peers(i).forEach { notes[it] = notes[it] and clear }
            cages[s.cage[i]].forEach { notes[it] = notes[it] and clear }
        }
        update(s.copy(values = values, notes = notes))
    }

    // First tap: the combinations that fit the selected square's cage. Second tap: its digit.
    fun hint() {
        if (s.hintCell != s.selected) {
            state.value = s.copy(hintCell = s.selected)
            return
        }
        val wrongOrEmpty = { i: Int -> s.values[i] != s.solution[i] }
        val target = if (wrongOrEmpty(s.selected)) s.selected else (0 until 81).filter(wrongOrEmpty).randomOrNull() ?: return
        val values = s.values.toMutableList().also { it[target] = s.solution[target] }
        val notes = s.notes.toMutableList().also { it[target] = 0 }
        update(s.copy(values = values, notes = notes, selected = target, hintsUsed = s.hintsUsed + 1))
    }

    val counts = IntArray(10).also { c -> s.values.forEach { c[it]++ } }

    GameLayout(
        status = {
            val status = when {
                s.hintCell == s.selected -> {
                    val cells = cages[s.cage[s.selected]]
                    var placed = 0
                    for (c in cells) if (s.values[c] != 0 && s.values[c] == s.solution[c]) placed = placed or (1 shl s.values[c])
                    val combos = combinationsFor(s.sums[s.cage[s.selected]], cells.size, placed)
                    stringResource(R.string.killer_hint_combos, s.sums[s.cage[s.selected]], combinationsText(combos)) +
                        "\n" + stringResource(R.string.killer_hint_again)
                }
                s.notesMode -> stringResource(R.string.sudoku_notes_on)
                else -> stringResource(R.string.killer_tap_cell)
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
                digitEnabled = { counts[it] < 9 },
            )
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            KillerBoard(s, cages, showErrors, Modifier.size(side).testTag("killer-board")) {
                state.value = state.value.copy(selected = it, hintCell = -1)
            }
        }
    }
}

@Composable
private fun KillerBoard(s: KillerState, cages: List<IntArray>, showErrors: Boolean, modifier: Modifier, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    val measurer = rememberTextMeasurer()
    val conflicts = remember(s.values) { Killer.conflicts(s.values, cages, s.sums) }
    val density = LocalDensity.current.density
    val dash = remember(density) { PathEffect.dashPathEffect(floatArrayOf(4f * density, 3f * density)) }
    // The tap detector lives as long as the board: it must always call the latest callback.
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
        drawRect(p.boardLight)
        for (i in 0 until 81) {
            val r = i / 9
            val c = i % 9
            val tl = Offset(c * cell, r * cell)
            drawRect(cellColor(p, s, i), tl, Size(cell, cell))
            val v = s.values[i]
            if (v != 0) {
                val wrong = (showErrors && v != s.solution[i]) || i in conflicts
                text(measurer, v.toString(), tl + Offset(cell / 2, cell * 0.56f), if (wrong) p.danger else p.primary, pxToSp(cell * 0.56f), bold = wrong)
                if (wrong) drawLine(p.danger, tl + Offset(cell * 0.25f, cell * 0.88f), tl + Offset(cell * 0.75f, cell * 0.88f), cell * 0.05f)
            } else if (s.notes[i] != 0) {
                // Notes keep clear of the cage total in the corner.
                for (n in 1..9) {
                    if (s.notes[i] and (1 shl n) == 0) continue
                    val nr = (n - 1) / 3
                    val nc = (n - 1) % 3
                    text(measurer, n.toString(), tl + Offset(cell * (0.34f + nc * 0.24f), cell * (0.42f + nr * 0.22f)), p.textMuted, pxToSp(cell * 0.21f), bold = false)
                }
            }
        }
        drawCages(p, s, cell, dash, measurer)
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

/** Background of a square: selected, same digit as the selection, same row/column/box, or plain. */
private fun cellColor(p: Palette, s: KillerState, i: Int): Color {
    val sel = s.selected
    val selValue = s.values[sel]
    val related = SudokuEngine.row(i) == SudokuEngine.row(sel) || SudokuEngine.col(i) == SudokuEngine.col(sel) || SudokuEngine.box(i) == SudokuEngine.box(sel)
    return when {
        i == sel -> p.highlight
        selValue != 0 && s.values[i] == selValue -> lerpOver(p.boardLight, p.highlight, 0.55f)
        s.cage[i] == s.cage[sel] -> lerpOver(p.boardLight, p.primary, 0.18f)
        related -> lerpOver(p.boardLight, p.primary, 0.10f)
        else -> p.boardLight
    }
}

private fun lerpOver(base: Color, top: Color, alpha: Float) = androidx.compose.ui.graphics.lerp(base, top, alpha)

/**
 * Dashed outline just inside every cage, and the cage total in the corner of its first square.
 * Each side of a square on the edge of its cage gets a dashed segment; segments reach into the
 * neighbouring square of the same cage so the outline runs on without gaps.
 */
private fun DrawScope.drawCages(p: Palette, s: KillerState, cell: Float, dash: PathEffect, measurer: TextMeasurer) {
    val d = cell * 0.09f
    val stroke = if (p.strongOutlines) 2.5.dp.toPx() else 1.5.dp.toPx()
    val color = p.boardLine.copy(alpha = 0.85f)
    fun same(i: Int, r: Int, c: Int) = r in 0..8 && c in 0..8 && s.cage[r * 9 + c] == s.cage[i]
    for (i in 0 until 81) {
        val r = i / 9
        val c = i % 9
        val left = c * cell
        val top = r * cell
        val right = left + cell
        val bottom = top + cell
        // How far a segment runs at each end: inset at an outer corner, past the edge at an inner one.
        fun ext(side: Boolean, diagonal: Boolean) = if (!side) d else if (diagonal) -d else 0f
        if (!same(i, r - 1, c)) {
            val x0 = left + ext(same(i, r, c - 1), same(i, r - 1, c - 1))
            val x1 = right - ext(same(i, r, c + 1), same(i, r - 1, c + 1))
            drawLine(color, Offset(x0, top + d), Offset(x1, top + d), stroke, pathEffect = dash)
        }
        if (!same(i, r + 1, c)) {
            val x0 = left + ext(same(i, r, c - 1), same(i, r + 1, c - 1))
            val x1 = right - ext(same(i, r, c + 1), same(i, r + 1, c + 1))
            drawLine(color, Offset(x0, bottom - d), Offset(x1, bottom - d), stroke, pathEffect = dash)
        }
        if (!same(i, r, c - 1)) {
            val y0 = top + ext(same(i, r - 1, c), same(i, r - 1, c - 1))
            val y1 = bottom - ext(same(i, r + 1, c), same(i, r + 1, c - 1))
            drawLine(color, Offset(left + d, y0), Offset(left + d, y1), stroke, pathEffect = dash)
        }
        if (!same(i, r, c + 1)) {
            val y0 = top + ext(same(i, r - 1, c), same(i, r - 1, c + 1))
            val y1 = bottom - ext(same(i, r + 1, c), same(i, r + 1, c + 1))
            drawLine(color, Offset(right - d, y0), Offset(right - d, y1), stroke, pathEffect = dash)
        }
    }
    // Totals, on a patch of the square's own color so the dashes never cross them.
    val seen = HashSet<Int>()
    for (i in 0 until 81) {
        if (!seen.add(s.cage[i])) continue
        val label = s.sums[s.cage[i]].toString()
        val tl = Offset(i % 9 * cell, i / 9 * cell)
        val w = cell * (if (label.length > 1) 0.46f else 0.28f)
        drawRect(cellColor(p, s, i), tl + Offset(d * 0.4f, d * 0.4f), Size(w, cell * 0.34f))
        text(measurer, label, tl + Offset(d * 0.4f + w / 2, d * 0.4f + cell * 0.17f), p.text, pxToSp(cell * 0.28f))
    }
}

/** Home screen picture: a corner of a killer sudoku with dashed cages and their totals. */
fun killerThumbnail(scope: DrawScope, p: Palette, m: TextMeasurer) = scope.run {
    drawRect(p.boardLight)
    val pad = size.width * 0.06f
    val cell = (size.width - pad * 2) / 4
    val cage = intArrayOf(0, 0, 1, 2, 3, 0, 1, 2, 3, 4, 4, 2, 5, 5, 6, 6)
    val sums = intArrayOf(15, 9, 17, 4, 12, 8, 7)
    val digits = mapOf(1 to 7, 5 to 2, 6 to 5, 11 to 8, 13 to 3)
    val d = cell * 0.1f
    val stroke = cell * 0.05f
    val dash = PathEffect.dashPathEffect(floatArrayOf(cell * 0.12f, cell * 0.08f))
    fun same(i: Int, r: Int, c: Int) = r in 0..3 && c in 0..3 && cage[r * 4 + c] == cage[i]
    for (i in 0 until 16) {
        val r = i / 4
        val c = i % 4
        val left = pad + c * cell
        val top = pad + r * cell
        if (!same(i, r - 1, c)) drawLine(p.boardLine, Offset(left + d, top + d), Offset(left + cell - d, top + d), stroke, pathEffect = dash)
        if (!same(i, r + 1, c)) drawLine(p.boardLine, Offset(left + d, top + cell - d), Offset(left + cell - d, top + cell - d), stroke, pathEffect = dash)
        if (!same(i, r, c - 1)) drawLine(p.boardLine, Offset(left + d, top + d), Offset(left + d, top + cell - d), stroke, pathEffect = dash)
        if (!same(i, r, c + 1)) drawLine(p.boardLine, Offset(left + cell - d, top + d), Offset(left + cell - d, top + cell - d), stroke, pathEffect = dash)
        digits[i]?.let { text(m, it.toString(), Offset(left + cell / 2, top + cell * 0.58f), p.primary, pxToSp(cell * 0.5f)) }
    }
    val firsts = HashSet<Int>()
    for (i in 0 until 16) if (firsts.add(cage[i])) {
        text(m, sums[cage[i]].toString(), Offset(pad + i % 4 * cell + cell * 0.32f, pad + i / 4 * cell + cell * 0.26f), p.text, pxToSp(cell * 0.24f))
    }
    for (k in 0..4) {
        val w = if (k == 0 || k == 4) cell * 0.06f else cell * 0.02f
        drawLine(p.boardLine, Offset(pad + k * cell, pad), Offset(pad + k * cell, pad + 4 * cell), w)
        drawLine(p.boardLine, Offset(pad, pad + k * cell), Offset(pad + 4 * cell, pad + k * cell), w)
    }
}
