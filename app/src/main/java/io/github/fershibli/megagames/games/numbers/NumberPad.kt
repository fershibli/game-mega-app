package io.github.fershibli.megagames.games.numbers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.theme.LocalSettings

/**
 * The controls of the number puzzles, the same as Sudoku's: digits 1 to 9 and an eraser on two
 * rows of big keys (digits turn into small notes in notes mode), then Undo, Hint and Notes.
 */
@Composable
fun NumberControls(
    notesMode: Boolean,
    canUndo: Boolean,
    onDigit: (Int) -> Unit,
    onUndo: () -> Unit,
    onHint: () -> Unit,
    onNotes: () -> Unit,
    digitEnabled: (Int) -> Boolean = { true },
) {
    val keyHeight = (60 * LocalSettings.current.pieceScale).dp
    for (row in listOf(1..5, 6..9)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (v in row) {
                BigButton(
                    v.toString(), { onDigit(v) }, Modifier.weight(1f).height(keyHeight),
                    style = if (notesMode) ButtonStyle.SECONDARY else ButtonStyle.PRIMARY,
                    enabled = digitEnabled(v), compact = true, bigText = true,
                )
            }
            if (row.last == 9) {
                BigButton("", { onDigit(0) }, Modifier.weight(1f).height(keyHeight), icon = AppIcon.ERASE, style = ButtonStyle.SECONDARY, compact = true)
            }
        }
    }
    ActionBar {
        BigButton(stringResource(R.string.undo), onUndo, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = canUndo, compact = true)
        BigButton(stringResource(R.string.hint), onHint, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, compact = true)
        BigButton(stringResource(R.string.sudoku_notes), onNotes, icon = AppIcon.PENCIL, style = if (notesMode) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY, compact = true)
    }
}

/** "7+9 · 6+..." : the digits of each combination, at most [max] of them. */
fun combinationsText(combos: List<Int>, max: Int = 4): String {
    val shown = combos.take(max).joinToString("  ·  ") { SumLogic.digits(it).joinToString("+") }
    return if (combos.size > max) "$shown  ·  …" else shown
}

/**
 * The combinations of a group of squares adding up to [sum] that contain every digit already
 * placed correctly in it ([placed], a bit mask), so the Hint never gives away a wrong idea.
 */
fun combinationsFor(sum: Int, size: Int, placed: Int): List<Int> =
    SumLogic.combos(sum, size).filter { it and placed == placed }
