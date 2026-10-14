package io.github.fershibli.megagames.games.crossword

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.AppLanguage
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
import io.github.fershibli.megagames.ui.components.KeyButton
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.RoundIconButton
import io.github.fershibli.megagames.ui.theme.LocalGameLanguage
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.LocalSettings
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class CrosswordState(
    val size: Int,
    val language: AppLanguage,
    val words: List<PlacedWord>,
    /** Letters typed by the player, one per cell ("" = empty). */
    val entries: List<String>,
    val selected: Int,
    val across: Boolean = true,
    /** Letters offered in the letter bank come from here (distractors). */
    val alphabet: List<String>,
) {
    fun solution(): Map<Int, String> {
        val out = HashMap<Int, String>()
        words.forEach { w -> w.cells(size).forEachIndexed { k, c -> out[c] = w.letters[k] } }
        return out
    }
    fun wordAt(cell: Int, acrossDir: Boolean) = words.firstOrNull { it.across == acrossDir && cell in it.cells(size) }
    fun currentWord() = wordAt(selected, across) ?: wordAt(selected, !across)
}

fun dictionaryFile(lang: AppLanguage) = "crossword/" + when (lang) {
    AppLanguage.PORTUGUESE -> "pt"
    AppLanguage.SPANISH -> "es"
    AppLanguage.FRENCH -> "fr"
    AppLanguage.CHINESE -> "zh"
    else -> "en"
} + ".txt"

fun newCrossword(context: Context, language: AppLanguage, difficulty: Difficulty, random: Random = Random.Default): CrosswordState {
    val text = context.assets.open(dictionaryFile(language)).bufferedReader().use { it.readText() }
    val entries = Crossword.parse(text, language)
    val chinese = language == AppLanguage.CHINESE
    val size = Crossword.sizeFor(difficulty, chinese)
    val words = Crossword.generate(entries, size, Crossword.targetWords(difficulty, chinese), random)
    val alphabet = entries.flatMap { it.letters }.distinct()
    val first = words.first()
    return CrosswordState(size, language, words, List(size * size) { "" }, first.cell(0, size), first.across, alphabet)
}

@Composable
fun CrosswordGame(host: GameHost) {
    val context = LocalContext.current.applicationContext
    val language = LocalGameLanguage.current
    LoadGame(host, CrosswordState.serializer(), { newCrossword(context, language, host.difficulty) }) { state -> CrosswordScreen(state, host) }
}

private val QWERTY = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
private val QWERTY_ES = listOf("QWERTYUIOP", "ASDFGHJKLÑ", "ZXCVBNM")
private val AZERTY = listOf("AZERTYUIOP", "QSDFGHJKLM", "WXCVBN")

@Composable
private fun CrosswordScreen(state: MutableState<CrosswordState>, host: GameHost) {
    val s = state.value
    val solution = remember(s.words) { s.solution() }
    val showErrors = host.difficulty != Difficulty.HARD
    val chinese = s.language == AppLanguage.CHINESE
    val useBank = chinese || host.difficulty == Difficulty.EASY
    val word = s.currentWord()
    val solved = solution.all { (cell, letter) -> s.entries[cell] == letter }
    val scale = LocalSettings.current.pieceScale

    LaunchedEffect(solved) { if (solved) { delay(600); host.finish(GameOutcome(true, null)) } }

    fun select(cell: Int, acrossDir: Boolean) {
        state.value = s.copy(selected = cell, across = if (s.wordAt(cell, acrossDir) != null) acrossDir else !acrossDir)
    }

    fun moveToWord(delta: Int) {
        val idx = s.words.indexOf(word).let { if (it < 0) 0 else it }
        val next = s.words[(idx + delta + s.words.size) % s.words.size]
        val cells = next.cells(s.size)
        state.value = s.copy(selected = cells.firstOrNull { s.entries[it] != solution[it] } ?: cells.first(), across = next.across)
    }

    fun type(letter: String) {
        val w = word ?: return
        val entries = s.entries.toMutableList().also { it[s.selected] = letter }
        val cells = w.cells(s.size)
        val pos = cells.indexOf(s.selected)
        // Next empty cell in this word, else simply the next cell, else stay.
        val nextCell = (cells.drop(pos + 1) + cells.take(pos)).firstOrNull { entries[it].isEmpty() }
            ?: cells.getOrNull(pos + 1) ?: s.selected
        state.value = s.copy(entries = entries, selected = nextCell, across = w.across)
    }

    fun erase() {
        val w = word ?: return
        val cells = w.cells(s.size)
        val pos = cells.indexOf(s.selected)
        val target = if (s.entries[s.selected].isEmpty() && pos > 0) cells[pos - 1] else s.selected
        state.value = s.copy(entries = s.entries.toMutableList().also { it[target] = "" }, selected = target, across = w.across)
    }

    GameLayout(
        controls = {
            // Clue bar with previous / next word.
            val p = LocalPalette.current
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundIconButton(AppIcon.PREV, stringResource(R.string.crossword_prev), { moveToWord(-1) }, size = 52.dp)
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = p.highlight.copy(alpha = 0.45f)) {
                    val dir = if (word?.across == true) stringResource(R.string.crossword_across) else stringResource(R.string.crossword_down)
                    Text(
                        if (word == null) "" else "${word.number} $dir · ${word.clue} (${word.letters.size})",
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium, color = p.text, textAlign = TextAlign.Center,
                    )
                }
                RoundIconButton(AppIcon.NEXT, stringResource(R.string.crossword_next), { moveToWord(1) }, size = 52.dp, filled = true)
            }
            val keyHeight = (54 * scale).dp
            if (useBank && word != null) {
                val distractors = when {
                    host.difficulty == Difficulty.EASY -> 2
                    host.difficulty == Difficulty.MEDIUM -> 5
                    else -> 8
                }
                val bank = remember(word, s.alphabet) {
                    val rnd = Random(word.number * 31 + if (word.across) 1 else 2)
                    (word.letters.distinct() + s.alphabet.filter { it !in word.letters }.shuffled(rnd).take(distractors)).shuffled(rnd)
                }
                val perRow = if (bank.size <= 6) bank.size else (bank.size + 1) / 2
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    bank.chunked(perRow.coerceAtLeast(1)).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { l -> KeyButton(l, { type(l) }, Modifier.weight(1f).height(keyHeight)) }
                            if (row.size < perRow) repeat(perRow - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            } else {
                val rows = when (s.language) {
                    AppLanguage.FRENCH -> AZERTY
                    AppLanguage.SPANISH -> QWERTY_ES
                    else -> QWERTY
                }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    rows.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { ch -> KeyButton(ch.toString(), { type(ch.toString()) }, Modifier.weight(1f).height(keyHeight)) }
                        }
                    }
                }
            }
            ActionBar {
                BigButton("", ::erase, icon = AppIcon.ERASE, style = ButtonStyle.SECONDARY, compact = true)
                BigButton(stringResource(R.string.hint), {
                    val correct = solution[s.selected] ?: return@BigButton
                    type(correct)
                }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, compact = true)
            }
        },
    ) {
        val p = LocalPalette.current
        val measurer = rememberTextMeasurer()
        val wordCells = word?.cells(s.size)?.toSet() ?: emptySet()
        val correctWords = if (showErrors) s.words.filter { w -> w.cells(s.size).all { s.entries[it] == solution[it] } }.flatMap { it.cells(s.size) }.toSet() else emptySet()
        val starts = remember(s.words) { s.words.associate { it.cell(0, s.size) to it.number } }
        // Only the rows and columns that hold letters are shown, so the squares are as big as possible.
        val minR = solution.keys.minOf { it / s.size }
        val minC = solution.keys.minOf { it % s.size }
        val rows = solution.keys.maxOf { it / s.size } - minR + 1
        val cols = solution.keys.maxOf { it % s.size } - minC + 1
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            val cellDp = minOf(maxWidth / cols, maxHeight / rows)
            Canvas(
                Modifier
                    .size(cellDp * cols, cellDp * rows)
                    .pointerInput(s) {
                        detectTapGestures { pos ->
                            val cell = size.width / cols.toFloat()
                            val c = minC + (pos.x / cell).toInt().coerceIn(0, cols - 1)
                            val r = minR + (pos.y / cell).toInt().coerceIn(0, rows - 1)
                            val i = r * s.size + c
                            if (i !in solution) return@detectTapGestures
                            if (i == s.selected) select(i, !s.across) else select(i, s.across)
                        }
                    },
            ) {
                val cell = size.width / cols
                for ((i, letter) in solution) {
                    val tl = Offset((i % s.size - minC) * cell, (i / s.size - minR) * cell)
                    val bg = when {
                        i == s.selected -> p.highlight
                        i in wordCells -> p.highlight.copy(alpha = 0.4f)
                        i in correctWords -> p.success.copy(alpha = 0.18f)
                        else -> p.boardLight
                    }
                    drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.15f), tl + Offset(cell * 0.05f, cell * 0.07f), Size(cell, cell))
                    drawRect(bg, tl, Size(cell, cell))
                    drawRect(p.boardLine, tl, Size(cell, cell), style = Stroke(if (p.strongOutlines) 3f else 1.5f))
                    starts[i]?.let { n -> text(measurer, n.toString(), tl + Offset(cell * 0.2f, cell * 0.18f), p.textMuted, pxToSp(cell * 0.22f), bold = false) }
                    val typed = s.entries[i]
                    if (typed.isNotEmpty()) {
                        val wrong = showErrors && typed != letter
                        text(measurer, typed, tl + Offset(cell / 2, cell * 0.56f), if (wrong) p.danger else p.text, pxToSp(cell * (if (chinese) 0.56f else 0.6f)))
                    }
                }
            }
        }
    }
}
