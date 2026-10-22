package io.github.fershibli.megagames.games.cryptogram

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
import io.github.fershibli.megagames.ui.theme.Palette
import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.wait
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class CryptogramState(
    val language: AppLanguage,
    /** The saying as it is written, shown once it is decoded. */
    val saying: String,
    /** The saying cut into cells: alphabet letters, spaces and punctuation. */
    val cells: List<String>,
    val alphabet: List<String>,
    /** Symbol hiding each letter of [alphabet] (a letter, or a number on Easy). */
    val cipher: List<String>,
    /** Letter the player put on each symbol. */
    val guesses: Map<String, String> = emptyMap(),
    /** Symbols revealed by the level or by Hint: their letter is right and stays. */
    val locked: List<String> = emptyList(),
    /** Symbol being filled in (every cell with it is highlighted), or null. */
    val selected: String? = null,
    val hints: Int = 0,
) {
    val symbols: List<String> get() = Cryptogram.encode(cells, alphabet, cipher)

    /** The symbols of the saying, each once, in reading order. */
    val inPlay: List<String> get() = cells.filter { it in alphabet }.distinct().map { cipher[alphabet.indexOf(it)] }

    fun truth(symbol: String): String = alphabet[cipher.indexOf(symbol)]
    fun isSymbol(cell: String) = cell in cipher
    val solved: Boolean get() = inPlay.all { guesses[it] == truth(it) }
    val filled: Boolean get() = inPlay.all { it in guesses }
}

/** Puts the right letter on [symbol] for good, taking it off any other symbol that had it. */
fun reveal(s: CryptogramState, symbol: String): CryptogramState {
    val letter = s.truth(symbol)
    val others = s.guesses.filter { (k, v) -> k == symbol || v != letter }
    return s.copy(guesses = others + (symbol to letter), locked = (s.locked + symbol).distinct())
}

/** The next symbol without a letter after [from] in reading order (wrapping), or null when all have one. */
fun nextEmpty(s: CryptogramState, from: String?): String? {
    val order = s.inPlay
    val start = order.indexOf(from)
    for (k in 1..order.size) {
        val sym = order[(start + k).floorMod(order.size)]
        if (sym !in s.guesses) return sym
    }
    return null
}

private fun Int.floorMod(n: Int) = Math.floorMod(this, n)

/**
 * Writes [letter] on the selected symbol, everywhere it appears. A letter already on another
 * symbol moves here; a letter revealed by a hint stays where it is and the key does nothing.
 */
fun assign(s: CryptogramState, letter: String): CryptogramState {
    val sym = s.selected ?: return s
    if (sym in s.locked || s.locked.any { s.guesses[it] == letter }) return s
    val guesses = s.guesses.filter { (k, v) -> k == sym || v != letter } + (sym to letter)
    val next = s.copy(guesses = guesses)
    return next.copy(selected = nextEmpty(next, sym) ?: sym)
}

/** Takes the letter off the selected symbol (revealed letters stay). */
fun erase(s: CryptogramState): CryptogramState {
    val sym = s.selected ?: return s
    if (sym in s.locked) return s
    return s.copy(guesses = s.guesses - sym)
}

/** Reveals the selected symbol when it is not right yet, otherwise the first one that is not. */
fun hint(s: CryptogramState): CryptogramState {
    val wrong = { sym: String -> s.guesses[sym] != s.truth(sym) }
    val target = s.selected?.takeIf { it !in s.locked && wrong(it) } ?: s.inPlay.firstOrNull(wrong) ?: return s
    val next = reveal(s, target).copy(hints = s.hints + 1)
    return next.copy(selected = nextEmpty(next, target))
}

fun newCryptogram(context: Context, language: AppLanguage, difficulty: Difficulty, random: Random = Random.Default): CryptogramState {
    val lang = language.takeIf { it in Cryptogram.LANGUAGES } ?: AppLanguage.ENGLISH
    val sayings = Cryptogram.parse(context.assets.open(Cryptogram.sayingsFile(lang)).bufferedReader().use { it.readText() })
    return cryptogramFor(sayings.random(random), lang, difficulty, random)
}

/** The puzzle of [saying]: a fresh key, and the level's letters revealed. */
fun cryptogramFor(saying: String, language: AppLanguage, difficulty: Difficulty, random: Random = Random.Default): CryptogramState {
    val alphabet = Cryptogram.alphabet(language)
    val cipher = Cryptogram.cipher(alphabet, Cryptogram.usesNumbers(difficulty), random)
    var s = CryptogramState(language, saying, Cryptogram.cells(saying, language), alphabet, cipher)
    s.inPlay.shuffled(random).take(Cryptogram.revealed(difficulty)).forEach { s = reveal(s, it) }
    return s.copy(selected = nextEmpty(s, null))
}

private const val SOLVED_PAUSE_MS = 1800L

@Composable
fun CryptogramGame(host: GameHost) {
    val context = LocalContext.current.applicationContext
    val language = LocalGameLanguage.current
    LoadGame(host, CryptogramState.serializer(), { newCryptogram(context, language, host.difficulty) }) { state -> CryptogramScreen(state, host) }
}

@Composable
private fun CryptogramScreen(state: MutableState<CryptogramState>, host: GameHost) {
    val s = state.value
    val p = LocalPalette.current
    val pace = currentPace
    val scale = LocalSettings.current.pieceScale
    var message by remember { mutableStateOf<String?>(null) }
    val doneText = stringResource(R.string.cryptogram_done, s.saying)
    val hintsText = stringResource(R.string.cryptogram_hints_used, s.hints)
    val lockedText = stringResource(R.string.cryptogram_letter_locked)

    LaunchedEffect(s.solved) {
        if (!s.solved) return@LaunchedEffect
        pace.wait(SOLVED_PAUSE_MS)
        host.finish(GameOutcome(true, if (s.hints > 0) "$doneText $hintsText" else doneText))
    }

    GameLayout(
        status = {
            val msg = when {
                s.solved -> stringResource(R.string.cryptogram_solved)
                message != null -> message!!
                s.filled -> stringResource(R.string.cryptogram_not_yet)
                s.selected != null -> stringResource(R.string.cryptogram_which, s.selected)
                else -> stringResource(R.string.cryptogram_pick)
            }
            StatusPill(msg, emphasized = s.solved)
        },
        controls = {
            CipherKeys(s) { letter ->
                val cur = state.value
                val next = assign(cur, letter)
                message = if (next == cur && cur.selected != null && cur.selected !in cur.locked) lockedText else null
                state.value = next
            }
            ActionBar {
                BigButton("", { message = null; state.value = erase(state.value) }, icon = AppIcon.ERASE, style = ButtonStyle.SECONDARY, compact = true, enabled = !s.solved && s.selected != null && s.selected in s.guesses && s.selected !in s.locked)
                BigButton(stringResource(R.string.hint), { message = null; state.value = hint(state.value) }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, compact = true, enabled = !s.solved)
            }
        },
    ) {
        // The board area is left to right; the saying reads in its own direction (Arabic from the right).
        val direction = if (s.language.rightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp)) {
                val words = remember(s.cells) { splitWords(s.cells) }
                val gap = 3.dp
                // The biggest squares with which the whole saying fits on the screen.
                val cell = remember(words, maxWidth, maxHeight, scale) {
                    fitCell(words.map { w -> w.count { s.isSymbol(s.symbols[it]) } }, maxWidth.value - 20f, maxHeight.value - 20f, 44f * scale, gap.value).dp
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .shadow(3.dp, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .background(p.boardLight)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                ) {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(cell * 0.55f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        words.forEach { word ->
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                word.forEach { i ->
                                    val c = s.symbols[i]
                                    if (s.isSymbol(c)) {
                                        SymbolCell(s, i, c, cell) {
                                            message = null
                                            val cur = state.value
                                            if (!cur.solved) state.value = cur.copy(selected = c)
                                        }
                                    } else {
                                        Text(c, Modifier.height(cell * 1.6f).padding(top = cell * 0.1f), color = p.text, style = MaterialTheme.typography.headlineSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Size of a square (in dp) for words of [letters] letters each, laid out in lines [width] wide:
 * the biggest one, up to [biggest], with which every line fits in [height]. On very long sayings
 * it stops at a size that is still easy to touch, and the saying scrolls.
 */
internal fun fitCell(letters: List<Int>, width: Float, height: Float, biggest: Float, gap: Float): Float {
    val longest = (letters.maxOrNull() ?: 1).coerceAtLeast(1)
    var cell = minOf(biggest, width / longest - gap - 1f)
    val smallest = minOf(cell, 30f)
    while (cell > smallest) {
        var lines = 1
        var x = 0f
        for (n in letters) {
            val w = n * (cell + gap) + cell * 0.4f
            if (x > 0f && x + cell * 0.55f + w > width) {
                lines++
                x = w
            } else {
                x += (if (x > 0f) cell * 0.55f else 0f) + w
            }
        }
        if (lines * (cell * 1.75f + 4f) + (lines - 1) * 12f <= height) return cell
        cell -= 1f
    }
    return cell
}

/** The cells of [cells] grouped into words (indices), so that a word never breaks across lines. */
private fun splitWords(cells: List<String>): List<List<Int>> {
    val out = ArrayList<List<Int>>()
    var cur = ArrayList<Int>()
    cells.forEachIndexed { i, c ->
        if (c.isBlank()) {
            if (cur.isNotEmpty()) out += cur
            cur = ArrayList()
        } else {
            cur += i
        }
    }
    if (cur.isNotEmpty()) out += cur
    return out
}

/**
 * One letter of the saying, newspaper style: the letter found by the player on a line, and the
 * symbol that hides it in small print below.
 */
@Composable
private fun SymbolCell(s: CryptogramState, index: Int, symbol: String, width: Dp, onClick: () -> Unit) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val guess = s.guesses[symbol]
    val selected = symbol == s.selected && !s.solved
    val locked = symbol in s.locked
    val shape = RoundedCornerShape(6.dp)
    val letterSize = with(density) { (width * 0.62f).toSp() }
    val symbolSize = with(density) { (width * if (symbol.length > 1) 0.38f else 0.42f).toSp() }
    Column(
        Modifier
            .width(width)
            .clip(shape)
            .background(
                when {
                    s.solved -> p.success.copy(alpha = 0.18f)
                    selected -> p.highlight
                    locked -> p.hint.copy(alpha = 0.35f)
                    else -> p.surfaceAlt.copy(alpha = 0.35f)
                },
            )
            .then(if (selected) Modifier.border(if (p.strongOutlines) 3.dp else 2.dp, p.boardLine, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$symbol: ${guess ?: "?"}" }
            .testTag("cryptogram-cell-$index"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width, width * 1.05f), contentAlignment = Alignment.Center) {
            Text(guess ?: "", color = p.text, fontSize = letterSize, fontWeight = if (locked) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false)
        }
        Box(Modifier.width(width * 0.8f).height(if (p.strongOutlines) 3.dp else 2.dp).background(p.boardLine))
        Text(symbol, color = p.textMuted, fontSize = symbolSize, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

/** The keyboard of the saying's language; letters already placed are marked with a dot. */
@Composable
private fun CipherKeys(s: CryptogramState, onKey: (String) -> Unit) {
    val p = LocalPalette.current
    val scale = LocalSettings.current.pieceScale
    val used = s.guesses.values.toSet()
    val lockedLetters = s.locked.map { s.truth(it) }.toSet()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        WordKit.keyboard(s.language).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { key ->
                    val shape = RoundedCornerShape(10.dp)
                    val locked = key in lockedLetters
                    Box(
                        Modifier
                            .weight(1f)
                            .height((50 * scale).dp)
                            .shadow(if (locked) 0.dp else 2.dp, shape)
                            .clip(shape)
                            .background(if (locked) p.surfaceAlt.copy(alpha = 0.4f) else p.surfaceAlt)
                            .border(if (p.strongOutlines) 2.dp else 1.dp, p.outline, shape)
                            .clickable(enabled = !locked && !s.solved, role = Role.Button) { onKey(key) }
                            .testTag("cryptogram-key-$key"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(key, color = if (locked) p.textMuted.copy(alpha = 0.6f) else p.text, style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
                        if (key in used && !locked) Text("•", Modifier.align(Alignment.BottomCenter), color = p.primary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Home-screen picture: a word in newspaper style, letters above and numbers below. */
fun cryptogramThumbnail(scope: DrawScope, p: Palette, m: TextMeasurer) = with(scope) {
    drawRect(p.boardLight)
    val w = size.width
    val cell = w * 0.2f
    val rows = listOf(listOf("S" to "7", "U" to "21", "N" to "4"), listOf("" to "12", "A" to "3", "" to "9", "" to "16"))
    rows.forEachIndexed { r, row ->
        val y = size.height * (0.28f + r * 0.42f)
        val x0 = (w - row.size * cell) / 2
        row.forEachIndexed { k, (letter, number) ->
            val cx = x0 + (k + 0.5f) * cell
            if (r == 1 && k == 1) drawRect(p.highlight, Offset(cx - cell * 0.45f, y - cell * 0.5f), Size(cell * 0.9f, cell * 1.45f))
            if (letter.isNotEmpty()) text(m, letter, Offset(cx, y), p.text, pxToSp(cell * 0.7f))
            drawLine(p.boardLine, Offset(cx - cell * 0.38f, y + cell * 0.45f), Offset(cx + cell * 0.38f, y + cell * 0.45f), w * 0.012f)
            text(m, number, Offset(cx, y + cell * 0.72f), p.textMuted, pxToSp(cell * 0.34f))
        }
    }
}
