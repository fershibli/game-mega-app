package io.github.fershibli.megagames.games.wordsearch

import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.wait
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.games.words.WordKit
import io.github.fershibli.megagames.ui.art.pxToSp
import io.github.fershibli.megagames.ui.art.text
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalGameLanguage
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.LocalSettings
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** A word found by the player: its index in the list and the squares at both ends of its band. */
@Serializable
data class Band(val word: Int, val from: Int, val to: Int)

@Serializable
data class WordSearchState(
    val size: Int,
    val language: AppLanguage,
    val grid: List<String>,
    val words: List<HiddenWord>,
    /** Found words, in the order they were found (the order gives each band its color). */
    val bands: List<Band> = emptyList(),
    /** Word whose first letter is circled by Hint (-1: none), and whether its last letter is too. */
    val hintWord: Int = -1,
    val hintBothEnds: Boolean = false,
    val hints: Int = 0,
) {
    val found: Set<Int> get() = bands.mapTo(HashSet()) { it.word }
    val done: Boolean get() = bands.size == words.size
}

/** Pause before the end-of-game message, so the last band can be seen. */
private const val FINISH_PAUSE_MS = 900L

fun newWordSearch(context: Context, language: AppLanguage, difficulty: Difficulty, random: Random = Random.Default): WordSearchState {
    val size = WordSearch.sizeFor(difficulty)
    val puzzle = WordSearch.generate(
        WordKit.dictionary(context, language), size, WordSearch.wordsFor(difficulty),
        WordSearch.directions(difficulty), random, WordSearch.minLength(language),
    )
    return WordSearchState(size, language, puzzle.grid, puzzle.words)
}

@Composable
fun WordSearchGame(host: GameHost) {
    val context = LocalContext.current.applicationContext
    val language = LocalGameLanguage.current
    LoadGame(host, WordSearchState.serializer(), { newWordSearch(context, language, host.difficulty) }) { state -> WordSearchScreen(state, host) }
}

@Composable
private fun WordSearchScreen(state: MutableState<WordSearchState>, host: GameHost) {
    val pace = currentPace
    val s = state.value
    val p = LocalPalette.current
    // Arabic reads right to left: the grid is shown mirrored, so words read from the right.
    val rtl = s.language.rightToLeft
    var pending by remember { mutableIntStateOf(-1) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragTo by remember { mutableIntStateOf(-1) }
    // Status message of the last action (a clue, "not a word"...), or null for the count.
    var message by remember { mutableStateOf<String?>(null) }
    val notWord = stringResource(R.string.wordsearch_not_word)
    val tapLast = stringResource(R.string.wordsearch_tap_last)
    val hintFirst = stringResource(R.string.wordsearch_hint_first)
    val hintLast = stringResource(R.string.wordsearch_hint_last)
    val doneText = stringResource(R.string.wordsearch_done)
    val hintsText = stringResource(R.string.wordsearch_hints_used, s.hints)

    LaunchedEffect(s.done) {
        if (s.done) {
            pace.wait(FINISH_PAUSE_MS)
            host.finish(GameOutcome(true, if (s.hints > 0) "$doneText $hintsText" else doneText))
        }
    }

    fun select(from: Int, to: Int) {
        val cells = WordSearch.line(from, to, s.size) ?: return
        val cur = state.value
        val k = WordSearch.match(cur.grid, cur.words, cur.found, cells)
        if (k < 0) {
            message = notWord
            return
        }
        val w = cur.words[k]
        message = WordKit.text(w.letters) + " · " + w.clue
        state.value = cur.copy(
            bands = cur.bands + Band(k, from, to),
            hintWord = if (cur.hintWord == k) -1 else cur.hintWord,
            hintBothEnds = cur.hintBothEnds && cur.hintWord != k,
        )
    }

    fun tap(cell: Int) {
        when {
            pending < 0 -> { pending = cell; message = tapLast }
            pending == cell -> { pending = -1; message = null }
            WordSearch.line(pending, cell, s.size) == null -> { pending = cell; message = tapLast }
            else -> { val from = pending; pending = -1; message = null; select(from, cell) }
        }
    }

    GameLayout(
        status = {
            StatusPill(message ?: stringResource(R.string.wordsearch_found, s.bands.size, s.words.size), emphasized = s.done)
        },
        controls = {
            Text(stringResource(R.string.wordsearch_words), style = MaterialTheme.typography.titleSmall, color = p.textMuted)
            val order = s.bands.map { it.word }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.words.forEachIndexed { i, w ->
                    val k = order.indexOf(i)
                    WordChip(WordKit.text(w.letters), k, Modifier.testTag("wordsearch-word-$i")) {
                        message = WordKit.text(w.letters) + " · " + w.clue
                    }
                }
            }
            ActionBar {
                BigButton(stringResource(R.string.hint), {
                    val cur = state.value
                    val keep = cur.hintWord >= 0 && cur.hintWord !in cur.found
                    if (keep && cur.hintBothEnds) return@BigButton
                    val word = if (keep) cur.hintWord else cur.words.indices.firstOrNull { it !in cur.found } ?: return@BigButton
                    state.value = cur.copy(hintWord = word, hintBothEnds = keep, hints = cur.hints + 1)
                    message = if (keep) hintLast else hintFirst
                }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = !s.done)
            }
        },
    ) {
        val measurer = rememberTextMeasurer()
        val n = s.size
        fun shownCol(c: Int) = if (rtl) n - 1 - c else c
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            Canvas(
                Modifier
                    .size(side)
                    .testTag("wordsearch-grid")
                    .pointerInput(n, rtl) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            if (state.value.done) return@awaitEachGesture
                            val cellPx = size.width / n.toFloat()
                            fun rc(o: Offset): Pair<Int, Int> {
                                val r = (o.y / cellPx).toInt().coerceIn(0, n - 1)
                                val k = (o.x / cellPx).toInt().coerceIn(0, n - 1)
                                return r to (if (rtl) n - 1 - k else k)
                            }
                            val (r0, c0) = rc(down.position)
                            val start = r0 * n + c0
                            var end = start
                            var dragged = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.isConsumed) { dragFrom = -1; return@awaitEachGesture } // a zoom gesture took over
                                if (!change.pressed) break
                                if (!dragged && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    dragged = true
                                    pending = -1
                                }
                                if (dragged) {
                                    val (r, c) = rc(change.position)
                                    end = WordSearch.snap(start, r, c, n)
                                    dragFrom = start
                                    dragTo = end
                                    change.consume()
                                }
                            }
                            dragFrom = -1
                            if (dragged) {
                                if (end != start) select(start, end)
                            } else {
                                tap(start)
                            }
                        }
                    },
            ) {
                val cell = size.width / n
                fun center(i: Int) = Offset((shownCol(i % n) + 0.5f) * cell, (i / n + 0.5f) * cell)
                drawRect(p.boardLight)
                s.bands.forEachIndexed { k, b ->
                    drawBand(center(b.from), center(b.to), cell * 0.8f, bandColor(p, k), k, p.strongOutlines)
                }
                if (dragFrom >= 0) drawBand(center(dragFrom), center(dragTo), cell * 0.8f, p.highlight, 0, p.strongOutlines)
                if (pending >= 0) drawCircle(p.highlight, cell * 0.44f, center(pending))
                if (s.hintWord >= 0 && s.hintWord !in s.found) {
                    val w = s.words[s.hintWord]
                    val ends = if (s.hintBothEnds) listOf(w.cell(0, n), w.cell(w.letters.size - 1, n)) else listOf(w.cell(0, n))
                    ends.forEach { drawCircle(p.hint, cell * 0.44f, center(it), style = Stroke(cell * 0.09f)) }
                }
                val fontSize = pxToSp(cell * if (s.language.syllabic) 0.5f else 0.56f)
                for (i in 0 until n * n) text(measurer, s.grid[i], center(i), p.text, fontSize)
                drawRect(p.boardLine, Offset.Zero, Size(size.width, size.height), style = Stroke(if (p.strongOutlines) 4f else 2f))
            }
        }
    }
}

/** A word of the list; once found it is crossed out and shows a sample of its band. */
@Composable
private fun WordChip(word: String, foundOrder: Int, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val scale = LocalSettings.current.pieceScale
    val found = foundOrder >= 0
    Surface(
        modifier.clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (found) p.surfaceAlt else p.surface,
        border = BorderStroke(if (p.strongOutlines) 2.dp else 1.dp, p.outline),
    ) {
        Row(Modifier.padding(horizontal = (10 * scale).dp, vertical = (6 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
            if (found) {
                Canvas(Modifier.size((26 * scale).dp, (16 * scale).dp)) {
                    val h = size.height
                    drawBand(Offset(h / 2, h / 2), Offset(size.width - h / 2, h / 2), h, bandColor(p, foundOrder), foundOrder, p.strongOutlines)
                }
                Spacer(Modifier.size(6.dp))
            }
            Text(
                word,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * scale),
                color = if (found) p.textMuted else p.text,
                textDecoration = if (found) TextDecoration.LineThrough else null,
            )
        }
    }
}
