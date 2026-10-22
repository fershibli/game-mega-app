package io.github.fershibli.megagames.games.guessword

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
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
import io.github.fershibli.megagames.ui.theme.currentMotion
import io.github.fershibli.megagames.ui.theme.spec
import io.github.fershibli.megagames.ui.theme.swing
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Serializable
data class GuessWordState(
    val language: AppLanguage,
    val words: List<FlowerWord>,
    /** Petals on the flower at the start of each word. */
    val petals: Int,
    val index: Int = 0,
    /** Letters tried on the current word, in order (right and wrong, including given ones). */
    val tried: List<String> = emptyList(),
    /** Letters given by the level or by Hint: shown on a soft band, never a fallen petal. */
    val given: List<String> = emptyList(),
    val clueShown: Boolean = true,
    /** One result per finished word; the current word's result is added as soon as it ends. */
    val results: List<WordResult> = emptyList(),
    val hints: Int = 0,
) {
    val word: FlowerWord get() = words[index]
    val wrong: List<String> get() = GuessWord.wrong(word.letters, tried)
    val petalsLeft: Int get() = (petals - wrong.size).coerceAtLeast(0)
    val found: Boolean get() = GuessWord.found(word.letters, tried)
    val lost: Boolean get() = !found && petalsLeft == 0
    val wordOver: Boolean get() = found || lost
    val lastWord: Boolean get() = index == words.size - 1
}

/** Sets up word [index] with a fresh flower: Easy gives one letter, Hard hides the clue. */
fun startWord(s: GuessWordState, index: Int, difficulty: Difficulty, random: Random = Random.Default): GuessWordState {
    val w = s.words[index]
    val given = w.letters.distinct().shuffled(random).take(GuessWord.givenLetters(difficulty))
    return s.copy(index = index, tried = given, given = given, clueShown = GuessWord.clueShown(difficulty))
}

fun newGuessWord(context: Context, language: AppLanguage, difficulty: Difficulty, random: Random = Random.Default): GuessWordState {
    val words = GuessWord.round(WordKit.dictionary(context, language), difficulty, language, random)
    return startWord(GuessWordState(language, words, GuessWord.petals(difficulty)), 0, difficulty, random)
}

/** Adds the current word's result once it is found or its last petal has fallen. */
private fun settle(s: GuessWordState): GuessWordState =
    if (s.wordOver && s.results.size == s.index) s.copy(results = s.results + WordResult(s.found, s.petalsLeft)) else s

/** Tries [letter]: every place it fills is shown at once; a letter not in the word drops a petal. */
fun guess(s: GuessWordState, letter: String): GuessWordState {
    if (s.wordOver || letter in s.tried) return s
    return settle(s.copy(tried = s.tried + letter))
}

/** Shows the clue when it is hidden, otherwise the letter of the first hidden place (no petal falls). */
fun hint(s: GuessWordState): GuessWordState {
    if (s.wordOver) return s
    if (!s.clueShown) return s.copy(clueShown = true, hints = s.hints + 1)
    val letter = s.word.letters.firstOrNull { it !in s.tried } ?: return s
    return settle(s.copy(tried = s.tried + letter, given = s.given + letter, hints = s.hints + 1))
}

@Composable
fun GuessWordGame(host: GameHost) {
    val context = LocalContext.current.applicationContext
    val language = LocalGameLanguage.current
    LoadGame(host, GuessWordState.serializer(), { newGuessWord(context, language, host.difficulty) }) { state -> GuessWordScreen(state, host) }
}

@Composable
private fun GuessWordScreen(state: MutableState<GuessWordState>, host: GameHost) {
    val s = state.value
    val p = LocalPalette.current
    val scale = LocalSettings.current.pieceScale
    val word = WordKit.text(s.word.letters)
    val resultText = stringResource(R.string.guessword_result, GuessWord.foundCount(s.results), s.words.size)
    val keptText = stringResource(R.string.guessword_petals_kept, GuessWord.petalsKept(s.results))
    val hintsText = stringResource(R.string.guessword_hints_used, s.hints)

    GameLayout(
        status = {
            val msg = when {
                s.found -> stringResource(R.string.guessword_found, word)
                s.lost -> stringResource(R.string.guessword_word_was, word)
                else -> stringResource(R.string.guessword_progress, s.index + 1, s.words.size, s.petalsLeft)
            }
            StatusPill(msg, emphasized = s.found)
        },
        controls = {
            if (s.wordOver) {
                ActionBar {
                    if (s.lastWord) {
                        BigButton(stringResource(R.string.guessword_see_result), {
                            val cur = state.value
                            val detail = buildString {
                                append(resultText)
                                if (GuessWord.foundCount(cur.results) > 0) append(" ").append(keptText)
                                if (cur.hints > 0) append(" ").append(hintsText)
                            }
                            host.finish(GameOutcome(GuessWord.won(cur.results), detail))
                        }, icon = AppIcon.CHECK, modifier = Modifier.testTag("guessword-next"))
                    } else {
                        BigButton(stringResource(R.string.guessword_next), {
                            val cur = state.value
                            if (cur.wordOver && !cur.lastWord) state.value = startWord(cur, cur.index + 1, host.difficulty)
                        }, icon = AppIcon.NEXT, modifier = Modifier.testTag("guessword-next"))
                    }
                }
            } else {
                LetterKeys(s) { state.value = guess(state.value, it) }
                ActionBar {
                    BigButton(stringResource(R.string.hint), { state.value = hint(state.value) }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, compact = true)
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Flower(s, Modifier.weight(1f).fillMaxWidth())
            // The word and the texts follow the language of the word (Arabic from the right).
            val direction = if (s.language.rightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                WordSlots(s, scale)
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = p.highlight.copy(alpha = 0.4f)) {
                    Text(
                        if (s.clueShown || s.wordOver) stringResource(R.string.guessword_clue, s.word.clue) else stringResource(R.string.guessword_clue_hidden),
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp).testTag("guessword-clue"),
                        style = MaterialTheme.typography.titleMedium, color = p.text, textAlign = TextAlign.Center,
                    )
                }
                val note = when {
                    s.lost && !s.lastWord -> stringResource(R.string.guessword_replant)
                    s.wrong.isNotEmpty() -> stringResource(R.string.guessword_not_in_word, s.wrong.joinToString("  "))
                    else -> null
                }
                if (note != null) Text(note, style = MaterialTheme.typography.titleSmall, color = p.textMuted, textAlign = TextAlign.Center)
            }
        }
    }
}

/** The hidden word: one place per letter; letters appear as they are found, all of them at the end. */
@Composable
private fun WordSlots(s: GuessWordState, scale: Float) {
    val p = LocalPalette.current
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val n = s.word.letters.size
        val gap = 6.dp
        val cell = minOf((54 * scale).dp, (maxWidth - gap * (n - 1)) / n)
        val letterSize = with(LocalDensity.current) { (cell * 0.58f).toSp() }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            s.word.letters.forEachIndexed { i, letter ->
                val known = letter in s.tried
                val shape = RoundedCornerShape(cell * 0.16f)
                Box(
                    Modifier
                        .size(cell)
                        .clip(shape)
                        .background(
                            when {
                                s.found -> p.success.copy(alpha = 0.25f)
                                known && letter in s.given -> p.hint.copy(alpha = 0.4f)
                                else -> p.boardLight
                            },
                        )
                        .border(if (p.strongOutlines) 3.dp else 2.dp, if (known || s.wordOver) p.boardLine else p.outline, shape)
                        .testTag("guessword-slot-$i"),
                    contentAlignment = Alignment.Center,
                ) {
                    // When the last petal falls the word is shown kindly, the missing letters softer.
                    if (known || s.lost) {
                        Text(
                            letter, color = if (known) p.text else p.textMuted, fontSize = letterSize,
                            fontWeight = if (known) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

/** The keyboard (or the bank of kana or characters): keys already tried show ✓ or — and rest. */
@Composable
private fun LetterKeys(s: GuessWordState, onKey: (String) -> Unit) {
    val scale = LocalSettings.current.pieceScale
    val keyHeight = (50 * scale).dp
    val bank = s.word.bank
    val rows = if (bank.isNotEmpty()) bank.chunked((bank.size + 1) / 2) else WordKit.keyboard(s.language)
    val perRow = rows.maxOf { it.size }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { key ->
                    val tried = key in s.tried
                    LetterKey(key, if (tried) key in s.word.letters else null, { onKey(key) }, Modifier.weight(1f).height(keyHeight))
                }
                // Bank rows keep equal keys even when the last row is shorter.
                if (bank.isNotEmpty() && row.size < perRow) repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A key; [inWord] is null until the letter is tried, then true (✓, filled) or false (—, faded). */
@Composable
private fun LetterKey(label: String, inWord: Boolean?, onClick: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(10.dp)
    val bg = when (inWord) {
        true -> p.primary
        false -> p.surfaceAlt.copy(alpha = 0.4f)
        null -> p.surfaceAlt
    }
    val fg = when (inWord) {
        true -> p.onPrimary
        false -> p.textMuted.copy(alpha = 0.6f)
        null -> p.text
    }
    Box(
        modifier
            .shadow(if (inWord == null) 2.dp else 0.dp, shape)
            .clip(shape)
            .background(bg)
            .border(if (p.strongOutlines) 2.dp else 1.dp, p.outline, shape)
            .clickable(enabled = inWord == null, role = Role.Button, onClick = onClick)
            .testTag("guessword-key-$label"),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
        val symbol = when (inWord) {
            true -> "✓"
            false -> "—"
            null -> null
        }
        if (symbol != null) Text(symbol, Modifier.align(Alignment.BottomCenter), color = fg, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/**
 * The flower: a petal falls gently for every letter that is not in the word, a sun comes out and
 * the flower opens wide when the word is found, and a new flower grows for every new word.
 */
@Composable
private fun Flower(s: GuessWordState, modifier: Modifier) {
    val p = LocalPalette.current
    val motion = currentMotion
    val fallen = s.wrong.size.coerceAtMost(s.petals)
    // The petal that fell last drifts down; the earlier ones already lie on the grass. A restored
    // game starts with everything in place.
    val fall = remember { Animatable(1f) }
    var seen by remember { mutableIntStateOf(fallen) }
    LaunchedEffect(fallen) {
        if (fallen > seen) {
            seen = fallen
            fall.snapTo(0f)
            fall.animateTo(1f, motion.spec(1300))
        } else {
            seen = fallen
        }
    }
    val grow = remember { Animatable(1f) }
    var shownIndex by remember { mutableIntStateOf(s.index) }
    LaunchedEffect(s.index) {
        if (s.index != shownIndex) {
            shownIndex = s.index
            grow.snapTo(0f)
            grow.animateTo(1f, motion.spec(900))
        }
    }
    val bloom by animateFloatAsState(if (s.found) 1f else 0f, motion.spec(900), label = "bloom")
    val sway = motion.swing(1f)
    val description = stringResource(R.string.guessword_flower, s.petalsLeft, s.petals)
    Canvas(modifier.semantics { contentDescription = description }.testTag("guessword-flower")) {
        drawFlowerScene(p, s.petals, fallen, fall.value, bloom, grow.value, sway)
    }
}

/** Petal color: the pink of the palette (every palette keeps it apart from the green leaves). */
private fun petalColor(p: Palette) = p.pieces[7 % p.pieces.size]
private fun leafColor(p: Palette) = p.pieces[2 % p.pieces.size]
private fun heartColor(p: Palette) = p.pieces[3 % p.pieces.size]

/**
 * Draws the whole scene: sky, grass, stem, leaves, petals still on the flower ([fallen] already
 * down, the last one [fall] of the way), the flower's heart, and a sun as [bloom] grows.
 */
fun DrawScope.drawFlowerScene(p: Palette, petals: Int, fallen: Int, fall: Float, bloom: Float, grow: Float, sway: Float) {
    val w = size.width
    val h = size.height
    val groundY = h * 0.86f
    // Grass and soil.
    drawRect(leafColor(p).copy(alpha = 0.28f), Offset(0f, groundY), Size(w, h - groundY))
    drawLine(leafColor(p), Offset(0f, groundY), Offset(w, groundY), h * 0.012f)
    val unit = minOf(w, h)
    val cx = w / 2
    val headR = unit * 0.075f * (1f + 0.15f * bloom) * (0.4f + 0.6f * grow)
    val head = Offset(cx, groundY - h * 0.56f * (0.55f + 0.45f * grow))
    // Sun, when the word is found.
    if (bloom > 0f) {
        val sun = Offset(w - unit * 0.16f, unit * 0.16f)
        val sr = unit * 0.07f * bloom
        val sunColor = heartColor(p)
        for (k in 0 until 8) {
            val a = (k * PI / 4).toFloat()
            val dir = Offset(cos(a), sin(a))
            drawLine(sunColor, sun + dir * (sr * 1.35f), sun + dir * (sr * 1.9f), unit * 0.014f, StrokeCap.Round)
        }
        drawCircle(sunColor, sr, sun)
        drawCircle(p.boardLine.copy(alpha = 0.5f), sr, sun, style = Stroke(unit * 0.006f))
    }
    // Stem and two leaves.
    val stemWidth = unit * 0.022f
    drawLine(leafColor(p), Offset(cx, groundY), head, stemWidth, StrokeCap.Round)
    val leafLen = unit * 0.13f * grow
    val leafY = groundY - (groundY - head.y) * 0.35f
    rotate(-35f, Offset(cx, leafY)) { drawOval(leafColor(p), Offset(cx, leafY - leafLen * 0.22f), Size(leafLen, leafLen * 0.44f)) }
    rotate(35f, Offset(cx, leafY + unit * 0.05f)) { drawOval(leafColor(p), Offset(cx - leafLen, leafY + unit * 0.05f - leafLen * 0.22f), Size(leafLen, leafLen * 0.44f)) }
    // Petals: the ones still on the flower around the head, the fallen ones on the grass.
    val petalLen = headR * 1.9f
    val petalWide = headR * 1.05f
    val color = petalColor(p)
    val edge = p.boardLine.copy(alpha = if (p.strongOutlines) 0.9f else 0.45f)
    val edgeWidth = unit * if (p.strongOutlines) 0.008f else 0.005f
    for (i in 0 until petals) {
        val angle = -90f + i * 360f / petals
        val rad = angle * PI.toFloat() / 180f
        val onFlower = head + Offset(cos(rad), sin(rad)) * (headR + petalLen * 0.4f)
        // Each fallen petal has its own place on the grass, spread out on both sides of the stem.
        val side = if (i % 2 == 0) 1 else -1
        val ground = Offset(cx + side * (unit * 0.09f + (i / 2) * unit * 0.075f), groundY + unit * 0.035f)
        val isDown = i < fallen
        val t = when {
            !isDown -> 0f
            i == fallen - 1 -> fall
            else -> 1f
        }
        val eased = t * t * (3 - 2 * t)
        val drift = sin(t * PI.toFloat() * 3f) * unit * 0.06f * sway * (1f - t)
        val pos = Offset(onFlower.x + (ground.x - onFlower.x) * eased + drift, onFlower.y + (ground.y - onFlower.y) * eased)
        val rot = angle + 90f + (side * 80f - angle - 90f) * eased + drift * 0.6f
        val len = petalLen * (if (isDown) 1f else (0.3f + 0.7f * grow))
        val alpha = if (isDown) 1f - 0.35f * eased else 1f
        rotate(rot, pos) {
            val tl = Offset(pos.x - petalWide / 2, pos.y - len / 2)
            drawOval(color.copy(alpha = alpha), tl, Size(petalWide, len))
            drawOval(edge.copy(alpha = edge.alpha * alpha), tl, Size(petalWide, len), style = Stroke(edgeWidth))
        }
    }
    // The heart of the flower, with a few seeds.
    drawCircle(heartColor(p), headR, head)
    drawCircle(p.boardLine.copy(alpha = 0.55f), headR, head, style = Stroke(edgeWidth * 1.4f))
    for (k in 0 until 5) {
        val a = (k * 2 * PI / 5).toFloat()
        drawCircle(p.boardLine.copy(alpha = 0.35f), headR * 0.1f, head + Offset(cos(a), sin(a)) * (headR * 0.45f))
    }
}

/** Home-screen picture: a flower that has lost two petals, and a word with one letter found. */
fun guessWordThumbnail(scope: DrawScope, p: Palette, m: TextMeasurer) = with(scope) {
    drawRect(p.boardLight)
    val w = size.width
    val h = size.height
    translate(0f, -h * 0.12f) { drawFlowerScene(p, 7, 2, 1f, 0f, 1f, 0f) }
    val cell = w * 0.17f
    val y = h * 0.86f
    listOf(null, "A", null, null).forEachIndexed { i, l ->
        val c = Offset(w * 0.5f + (i - 1.5f) * cell * 1.1f, y)
        drawLine(p.text, c + Offset(-cell * 0.4f, cell * 0.45f), c + Offset(cell * 0.4f, cell * 0.45f), w * 0.015f)
        if (l != null) text(m, l, c, p.text, pxToSp(cell * 0.75f))
    }
    Unit
}
