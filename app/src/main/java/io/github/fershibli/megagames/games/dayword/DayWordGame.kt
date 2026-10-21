package io.github.fershibli.megagames.games.dayword

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.core.WorldStore
import io.github.fershibli.megagames.core.rememberWorldStore
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
import io.github.fershibli.megagames.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class DayWordState(
    val language: AppLanguage,
    val answer: List<String>,
    val clue: String,
    /** Day of this word of the day (days since 1970), or -1 for a practice word. */
    val day: Long,
    val tries: Int,
    val guesses: List<List<String>> = emptyList(),
    /** Letters written on the current line, not checked yet. */
    val current: List<String> = emptyList(),
    /** Places revealed by Hint. */
    val hinted: List<Int> = emptyList(),
    val clueShown: Boolean = false,
    val hints: Int = 0,
    /** Kana or characters to pick from (Japanese, Chinese); empty when a keyboard is used. */
    val bank: List<String> = emptyList(),
    /** Today's word was already played, so this practice word was offered instead. */
    val todayDone: Boolean = false,
) {
    val won: Boolean get() = guesses.lastOrNull() == answer
    val lost: Boolean get() = !won && guesses.size >= tries
    val over: Boolean get() = won || lost
}

/** Days on which each language and level last finished its word of the day (kept between games). */
@Serializable
data class DayWordDays(val finished: Map<String, Long> = emptyMap())

private const val WORLD_KEY = "dayword"
private const val FINISH_PAUSE_MS = 1400L

private fun recordKey(language: AppLanguage, d: Difficulty) = "${language.name}-${d.name}"

/**
 * Today's word for [language] and [difficulty], or a practice word when today's has already been
 * played (or when [practice] asks for one).
 */
fun newDayWord(
    context: Context,
    world: WorldStore?,
    language: AppLanguage,
    difficulty: Difficulty,
    practice: Boolean = false,
    today: Long = WordKit.today(),
    random: Random = Random.Default,
): DayWordState {
    val entries = WordKit.dictionary(context, language)
    val pool = DayWord.pool(entries, difficulty, language)
    val daily = DayWord.wordOfDay(pool, today, difficulty, language)
    val key = recordKey(language, difficulty)
    val finished = world?.load(WORLD_KEY, DayWordDays.serializer())?.finished.orEmpty()
    val done = finished.entries.any { it.key == key && it.value == today }
    val isDay = !practice && !done
    val entry = if (isDay) daily else pool.filter { it != daily }.ifEmpty { pool }.random(random)
    val bank = if (DayWord.usesBank(language)) DayWord.bank(entry.letters, entries.flatMap { it.letters }, difficulty, random) else emptyList()
    return DayWordState(language, entry.letters, entry.clue, if (isDay) today else -1L, DayWord.tries(difficulty), bank = bank, todayDone = !practice && done)
}

/** Next step of Hint: the clue first, then one letter in its place. */
fun hint(s: DayWordState): DayWordState {
    if (s.over) return s
    if (!s.clueShown) return s.copy(clueShown = true, hints = s.hints + 1)
    val known = DayWord.known(s.guesses, s.answer, s.hinted)
    val i = known.indexOfFirst { it == null }
    if (i < 0) return s
    return s.copy(hinted = s.hinted + i, hints = s.hints + 1)
}

@Composable
fun DayWordGame(host: GameHost) {
    val context = LocalContext.current.applicationContext
    val language = LocalGameLanguage.current
    val world = rememberWorldStore()
    LoadGame(host, DayWordState.serializer(), { newDayWord(context, world, language, host.difficulty) }) { state -> DayWordScreen(state, host, world) }
}

@Composable
private fun DayWordScreen(state: MutableState<DayWordState>, host: GameHost, world: WorldStore) {
    val s = state.value
    val p = LocalPalette.current
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val scale = LocalSettings.current.pieceScale
    val n = s.answer.size
    val word = WordKit.text(s.answer)
    val wonText = stringResource(R.string.dayword_won, word, s.guesses.size)
    val lostText = stringResource(R.string.dayword_lost, word)
    val clueText = stringResource(R.string.dayword_clue, s.clue)

    LaunchedEffect(s.over) {
        if (!s.over) return@LaunchedEffect
        if (s.day >= 0) {
            val key = recordKey(s.language, host.difficulty)
            val old = world.load(WORLD_KEY, DayWordDays.serializer()) ?: DayWordDays()
            world.save(WORLD_KEY, DayWordDays.serializer(), old.copy(finished = old.finished + (key to s.day)))
        }
        delay(FINISH_PAUSE_MS)
        host.finish(GameOutcome(s.won, (if (s.won) wonText else lostText) + "\n" + clueText))
    }

    fun type(letter: String) {
        val cur = state.value
        if (!cur.over && cur.current.size < cur.answer.size) state.value = cur.copy(current = cur.current + letter)
    }

    GameLayout(
        status = {
            val line = (s.guesses.size + if (s.over) 0 else 1).coerceAtMost(s.tries)
            val msg = when {
                s.won -> stringResource(R.string.dayword_won, word, s.guesses.size)
                s.lost -> stringResource(R.string.dayword_lost, word)
                s.todayDone && s.guesses.isEmpty() && s.current.isEmpty() -> stringResource(R.string.dayword_today_done)
                s.day >= 0 -> stringResource(R.string.dayword_today, line, s.tries)
                else -> stringResource(R.string.dayword_practice, line, s.tries)
            }
            StatusPill(msg, emphasized = s.won)
        },
        controls = {
            val marks = DayWord.keyMarks(s.guesses, s.answer)
            val keyHeight = (50 * scale).dp
            val rows = if (s.bank.isNotEmpty()) s.bank.chunked((s.bank.size + 1) / 2) else WordKit.keyboard(s.language)
            val perRow = rows.maxOf { it.size }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { key -> MarkedKey(key, marks[key], { type(key) }, Modifier.weight(1f).height(keyHeight)) }
                        // Bank rows keep equal keys even when the last row is shorter.
                        if (s.bank.isNotEmpty() && row.size < perRow) repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            ActionBar {
                BigButton("", {
                    val cur = state.value
                    if (cur.current.isNotEmpty() && !cur.over) state.value = cur.copy(current = cur.current.dropLast(1))
                }, icon = AppIcon.ERASE, style = ButtonStyle.SECONDARY, compact = true, enabled = s.current.isNotEmpty() && !s.over)
                BigButton(stringResource(R.string.dayword_check), {
                    val cur = state.value
                    if (cur.current.size == cur.answer.size && !cur.over) state.value = cur.copy(guesses = cur.guesses + listOf(cur.current), current = emptyList())
                }, icon = AppIcon.CHECK, compact = true, enabled = s.current.size == n && !s.over)
                BigButton(stringResource(R.string.hint), { state.value = hint(state.value) }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, compact = true, enabled = !s.over)
                if (s.day < 0) {
                    BigButton(stringResource(R.string.dayword_another), {
                        scope.launch {
                            val next = withContext(Dispatchers.Default) { newDayWord(context, null, s.language, host.difficulty, practice = true) }
                            state.value = next
                        }
                    }, style = ButtonStyle.SECONDARY, compact = true, enabled = !s.over)
                }
            }
        },
    ) {
        // The board area is left to right; the lines follow the language of the word.
        val direction = if (s.language.rightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
                val headerLines = if (s.clueShown) 2.4f else 1.4f
                val lineHeight = minOf((70 * scale).dp, maxHeight / (s.tries + headerLines + 0.6f))
                val margin = 28.dp
                val cellWidth = minOf(lineHeight * 0.95f, (maxWidth - margin - 12.dp) / n)
                val rtl = direction == LayoutDirection.Rtl
                Column(
                    Modifier
                        .fillMaxSize()
                        .shadow(3.dp, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .background(p.boardLight)
                        .drawBehind { paper(p, lineHeight.toPx(), margin.toPx(), rtl) },
                ) {
                    val known = DayWord.known(s.guesses, s.answer, s.hinted)
                    Row(Modifier.height(lineHeight).padding(start = margin + 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.dayword_known), style = MaterialTheme.typography.titleSmall, color = p.textMuted)
                        Spacer(Modifier.width(10.dp))
                        known.forEach { l -> LetterCell(l ?: "_", null, cellWidth * 0.75f, lineHeight * 0.8f, hinted = l != null) }
                    }
                    if (s.clueShown) {
                        Text(
                            stringResource(R.string.dayword_clue, s.clue),
                            Modifier.height(lineHeight).fillMaxWidth().padding(start = margin + 6.dp, end = 8.dp),
                            style = MaterialTheme.typography.titleMedium, color = p.text, maxLines = 2,
                        )
                    }
                    for (line in 0 until s.tries) {
                        val guess = s.guesses.getOrNull(line)
                        val writing = guess == null && line == s.guesses.size && !s.over
                        Row(
                            Modifier
                                .height(lineHeight)
                                .fillMaxWidth()
                                .then(if (writing) Modifier.background(p.highlight.copy(alpha = 0.28f)) else Modifier)
                                .padding(start = margin + 6.dp)
                                .testTag("dayword-line-$line"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            when {
                                guess != null -> DayWord.feedback(guess, s.answer).forEachIndexed { i, m -> LetterCell(guess[i], m, cellWidth, lineHeight) }
                                writing -> for (i in 0 until n) LetterCell(s.current.getOrNull(i) ?: "_", null, cellWidth, lineHeight, faint = i >= s.current.size)
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.dayword_legend),
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium, color = p.textMuted, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Notebook paper: ruled lines under every line of writing and a margin line on the starting side. */
private fun DrawScope.paper(p: Palette, line: Float, margin: Float, rtl: Boolean) {
    var y = line
    while (y < size.height) {
        drawLine(p.primary.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), 2f)
        y += line
    }
    val x = if (rtl) size.width - margin else margin
    drawLine(p.danger.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), 3f)
}

/**
 * One handwritten letter with its mark below: ● in the right place (letter on a soft band),
 * ○ in the word elsewhere (letter circled), — not in the word (letter faded). The symbols carry
 * the meaning, so nothing depends on telling colors apart.
 */
@Composable
private fun LetterCell(letter: String, mark: Mark?, width: Dp, height: Dp, faint: Boolean = false, hinted: Boolean = false) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val letterSize = with(density) { (minOf(width, height) * 0.5f).toSp() }
    val symbolSize = with(density) { (height * 0.26f).toSp() }
    val symbol = when (mark) {
        Mark.RIGHT -> "●"
        Mark.ELSEWHERE -> "○"
        Mark.ABSENT -> "—"
        null -> ""
    }
    val description = when (mark) {
        Mark.RIGHT -> stringResource(R.string.dayword_mark_right)
        Mark.ELSEWHERE -> stringResource(R.string.dayword_mark_elsewhere)
        Mark.ABSENT -> stringResource(R.string.dayword_mark_absent)
        null -> ""
    }
    Column(
        Modifier.width(width).semantics { if (description.isNotEmpty()) contentDescription = "$letter: $description" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val box = width * 0.86f
        val shape = if (mark == Mark.ELSEWHERE) CircleShape else RoundedCornerShape(8.dp)
        Box(
            Modifier
                .size(box, height * 0.58f)
                .then(
                    when (mark) {
                        Mark.RIGHT -> Modifier.clip(shape).background(p.primary.copy(alpha = 0.28f))
                        Mark.ELSEWHERE -> Modifier.border(if (p.strongOutlines) 3.dp else 2.dp, p.primary, shape)
                        else -> if (hinted) Modifier.clip(shape).background(p.hint.copy(alpha = 0.35f)) else Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                letter,
                color = when {
                    mark == Mark.ABSENT -> p.textMuted.copy(alpha = 0.75f)
                    faint -> p.textMuted.copy(alpha = 0.6f)
                    else -> p.text
                },
                fontSize = letterSize, fontWeight = if (mark == Mark.RIGHT) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false,
            )
        }
        if (symbol.isNotEmpty()) Text(symbol, color = if (mark == Mark.ABSENT) p.textMuted else p.primary, fontSize = symbolSize, lineHeight = symbolSize, maxLines = 1)
    }
}

/** A key that also shows what is known about its letter, with the same symbols as the lines. */
@Composable
private fun MarkedKey(label: String, mark: Mark?, onClick: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(10.dp)
    val bg = when (mark) {
        Mark.RIGHT -> p.primary
        Mark.ABSENT -> p.surfaceAlt.copy(alpha = 0.45f)
        else -> p.surfaceAlt
    }
    val fg = when (mark) {
        Mark.RIGHT -> p.onPrimary
        Mark.ABSENT -> p.textMuted.copy(alpha = 0.7f)
        else -> p.text
    }
    Box(
        modifier
            .shadow(if (mark == Mark.ABSENT) 0.dp else 2.dp, shape)
            .clip(shape)
            .background(bg)
            .border(
                when {
                    mark == Mark.ELSEWHERE -> 3.dp
                    p.strongOutlines -> 2.dp
                    else -> 1.dp
                },
                if (mark == Mark.ELSEWHERE) p.primary else p.outline, shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("dayword-key-$label"),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
        val symbol = when (mark) {
            Mark.RIGHT -> "●"
            Mark.ELSEWHERE -> "○"
            Mark.ABSENT -> "—"
            null -> null
        }
        if (symbol != null) {
            Text(symbol, Modifier.align(Alignment.BottomCenter), color = fg, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/** Home-screen picture: a notebook page with two written guesses and their marks. */
fun dayWordThumbnail(scope: DrawScope, p: Palette, m: TextMeasurer) = with(scope) {
    drawRect(p.boardLight)
    val line = size.height / 4.2f
    for (k in 1..4) drawLine(p.primary.copy(alpha = 0.3f), Offset(0f, k * line), Offset(size.width, k * line), size.width * 0.012f)
    drawLine(p.danger.copy(alpha = 0.45f), Offset(size.width * 0.12f, 0f), Offset(size.width * 0.12f, size.height), size.width * 0.015f)
    val cell = size.width * 0.16f
    val x0 = size.width * 0.2f
    // The hidden word is SALT.
    val rows = listOf("PLAN" to listOf(Mark.ABSENT, Mark.ELSEWHERE, Mark.ELSEWHERE, Mark.ABSENT), "SALE" to listOf(Mark.RIGHT, Mark.RIGHT, Mark.RIGHT, Mark.ABSENT))
    rows.forEachIndexed { r, (w, marks) ->
        val yMid = (r + 1) * line - line * 0.55f
        w.forEachIndexed { i, ch ->
            val c = Offset(x0 + (i + 0.5f) * cell, yMid)
            when (marks[i]) {
                Mark.RIGHT -> drawRoundRect(p.primary.copy(alpha = 0.3f), c - Offset(cell * 0.42f, cell * 0.42f), Size(cell * 0.84f, cell * 0.84f), CornerRadius(cell * 0.15f))
                Mark.ELSEWHERE -> drawCircle(p.primary, cell * 0.42f, c, style = Stroke(cell * 0.07f))
                Mark.ABSENT -> {}
            }
            text(m, ch.toString(), c, if (marks[i] == Mark.ABSENT) p.textMuted else p.text, pxToSp(cell * 0.6f))
            val sym = when (marks[i]) {
                Mark.RIGHT -> "●"
                Mark.ELSEWHERE -> "○"
                Mark.ABSENT -> "—"
            }
            text(m, sym, c + Offset(0f, cell * 0.62f), p.primary, pxToSp(cell * 0.3f))
        }
    }
    // The line being written.
    for (i in 0 until 4) {
        val c = Offset(x0 + (i + 0.5f) * cell, 3 * line - line * 0.4f)
        drawLine(p.text.copy(alpha = 0.5f), c + Offset(-cell * 0.3f, 0f), c + Offset(cell * 0.3f, 0f), cell * 0.06f)
    }
}
