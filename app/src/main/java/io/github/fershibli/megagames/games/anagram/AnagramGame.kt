package io.github.fershibli.megagames.games.anagram

import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class AnagramState(
    val language: AppLanguage,
    val words: List<AnagramWord>,
    val index: Int = 0,
    /** Tiles (indices into the current word's tiles) placed in the answer, in order. */
    val picked: List<Int> = emptyList(),
    /** Leading answer tiles given by a hint (or by the Easy level); they stay in place. */
    val locked: Int = 0,
    /** Order in which the tiles are shown below the answer (Shuffle changes it). */
    val order: List<Int> = emptyList(),
    val clueShown: Boolean = true,
    /** The current word is spelled right; the next one comes after a short pause. */
    val solved: Boolean = false,
    val hints: Int = 0,
) {
    val word: AnagramWord get() = words[index]
    val built: List<String> get() = picked.map { word.tiles[it] }
    val full: Boolean get() = picked.size == word.letters.size
}

/** How long a solved word stays on screen before the next one. */
private const val SOLVED_PAUSE_MS = 1300L

/** Sets up word [index]: Easy shows the clue and the first letter, Medium the clue, Hard nothing. */
fun startWord(s: AnagramState, index: Int, difficulty: Difficulty): AnagramState {
    val w = s.words[index]
    val base = s.copy(index = index, picked = emptyList(), locked = 0, order = w.tiles.indices.toList(), clueShown = difficulty != Difficulty.HARD, solved = false)
    if (difficulty != Difficulty.EASY) return base
    return base.copy(picked = listOf(w.tiles.indexOf(w.letters[0])), locked = 1)
}

fun newAnagram(context: Context, language: AppLanguage, difficulty: Difficulty, random: Random = Random.Default): AnagramState {
    val words = Anagram.round(WordKit.dictionary(context, language), difficulty, language, random)
    return startWord(AnagramState(language, words), 0, difficulty)
}

/** Places the next right letter, after taking back any wrong ones (a hidden clue comes first). */
fun hint(s: AnagramState): AnagramState {
    if (s.solved) return s
    if (!s.clueShown) return s.copy(clueShown = true, hints = s.hints + 1)
    val keep = Anagram.correctPrefix(s.word, s.built)
    if (keep >= s.word.letters.size) return s
    val picked = s.picked.take(keep)
    val tile = s.word.tiles.indices.first { it !in picked && s.word.tiles[it] == s.word.letters[keep] }
    return place(s.copy(picked = picked, locked = keep + 1, hints = s.hints + 1), tile, force = true)
}

/** Puts tile [tile] at the end of the answer; a full and right answer solves the word. */
fun place(s: AnagramState, tile: Int, force: Boolean = false): AnagramState {
    if (s.solved || tile in s.picked || (s.full && !force)) return s
    val next = s.copy(picked = s.picked + tile)
    return if (next.full && next.word.accepts(next.built)) next.copy(solved = true) else next
}

@Composable
fun AnagramGame(host: GameHost) {
    val context = LocalContext.current.applicationContext
    val language = LocalGameLanguage.current
    LoadGame(host, AnagramState.serializer(), { newAnagram(context, language, host.difficulty) }) { state -> AnagramScreen(state, host) }
}

@Composable
private fun AnagramScreen(state: MutableState<AnagramState>, host: GameHost) {
    val s = state.value
    val p = LocalPalette.current
    val scale = LocalSettings.current.pieceScale
    val doneText = stringResource(R.string.anagram_done)
    val hintsText = stringResource(R.string.anagram_hints_used, s.hints)

    LaunchedEffect(s.index, s.solved) {
        if (!s.solved) return@LaunchedEffect
        delay(SOLVED_PAUSE_MS)
        val cur = state.value
        if (cur.index + 1 < cur.words.size) {
            state.value = startWord(cur, cur.index + 1, host.difficulty)
        } else {
            host.finish(GameOutcome(true, if (cur.hints > 0) "$doneText $hintsText" else doneText))
        }
    }

    GameLayout(
        status = {
            val msg = when {
                s.solved -> stringResource(R.string.anagram_right)
                s.full -> stringResource(R.string.anagram_wrong)
                else -> stringResource(R.string.anagram_progress, s.index + 1, s.words.size)
            }
            StatusPill(msg, emphasized = s.solved)
        },
        controls = {
            ActionBar {
                BigButton(stringResource(R.string.undo), {
                    val cur = state.value
                    if (cur.picked.size > cur.locked && !cur.solved) state.value = cur.copy(picked = cur.picked.dropLast(1))
                }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = s.picked.size > s.locked && !s.solved, compact = true)
                BigButton(stringResource(R.string.anagram_shuffle), {
                    val cur = state.value
                    state.value = cur.copy(order = cur.order.shuffled())
                }, icon = AppIcon.SHUFFLE, style = ButtonStyle.SECONDARY, enabled = !s.solved, compact = true)
                BigButton(stringResource(R.string.hint), { state.value = hint(state.value) }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = !s.solved, compact = true)
            }
        },
    ) {
        // The board area is left to right; the word itself follows its own language.
        val direction = if (s.language.rightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                val n = s.word.letters.size
                val gap = 6.dp
                val tile = minOf((64 * scale).dp, (maxWidth - gap * (n - 1)) / n)
                val poolTile = minOf((64 * scale).dp, maxWidth / 4.5f)
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = p.highlight.copy(alpha = 0.4f)) {
                        Text(
                            if (s.clueShown) s.word.clue else stringResource(R.string.anagram_clue_hidden),
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.titleMedium, color = p.text, textAlign = TextAlign.Center,
                        )
                    }
                    // The answer: one slot per letter.
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (k in 0 until n) {
                            val tileIndex = s.picked.getOrNull(k)
                            val color = when {
                                s.solved -> p.success.copy(alpha = 0.3f)
                                tileIndex != null && k < s.locked -> p.hint.copy(alpha = 0.45f)
                                else -> null
                            }
                            if (tileIndex == null) {
                                EmptySlot(tile, Modifier.testTag("anagram-slot-$k"))
                            } else {
                                LetterTile(s.word.tiles[tileIndex], tile, color, Modifier.testTag("anagram-slot-$k")) {
                                    val cur = state.value
                                    if (k >= cur.locked && !cur.solved) state.value = cur.copy(picked = cur.picked.filterIndexed { i, _ -> i != k })
                                }
                            }
                        }
                    }
                    // The scrambled tiles; used ones leave an empty place so nothing jumps around.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        s.order.forEach { i ->
                            if (i in s.picked) {
                                EmptySlot(poolTile, Modifier, faint = true)
                            } else {
                                LetterTile(s.word.tiles[i], poolTile, null, Modifier.testTag("anagram-tile-$i")) { state.value = place(state.value, i) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A big letter tile, like a wooden game piece. */
@Composable
private fun LetterTile(letter: String, size: Dp, tint: Color?, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(size * 0.18f)
    Box(
        modifier
            .size(size)
            .shadow(3.dp, shape)
            .clip(shape)
            .background(p.tileFace)
            .then(if (tint != null) Modifier.background(tint) else Modifier)
            .border(if (p.strongOutlines) 3.dp else 1.5.dp, p.boardLine.copy(alpha = if (p.strongOutlines) 1f else 0.6f), shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Sized from the tile itself, so the letter fits whatever the text size setting is.
        val fontSize = with(LocalDensity.current) { (size * 0.55f).toSp() }
        Text(letter, color = Color.Black.copy(alpha = 0.85f), fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun EmptySlot(size: Dp, modifier: Modifier, faint: Boolean = false) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(size * 0.18f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(p.surfaceAlt.copy(alpha = if (faint) 0.35f else 0.8f))
            .border(BorderStroke(if (p.strongOutlines) 2.dp else 1.dp, p.outline.copy(alpha = if (faint) 0.35f else 0.8f)), shape),
    )
}

/** Home-screen picture: scrambled tiles above, the word being built from them below. */
fun anagramThumbnail(scope: DrawScope, p: Palette, m: TextMeasurer) = with(scope) {
    drawRect(p.felt)
    val t = size.width * 0.2f
    val r = CornerRadius(t * 0.18f)
    fun tile(cx: Float, cy: Float, letter: String?, angle: Float) = rotate(angle, Offset(cx, cy)) {
        val tl = Offset(cx - t / 2, cy - t / 2)
        val sz = Size(t, t)
        if (letter == null) {
            drawRoundRect(p.feltDark, tl, sz, r)
            drawRoundRect(p.tileFace.copy(alpha = 0.5f), tl, sz, r, style = Stroke(t * 0.04f))
            return@rotate
        }
        drawRoundRect(Color.Black.copy(alpha = 0.25f), tl + Offset(t * 0.05f, t * 0.07f), sz, r)
        drawRoundRect(p.tileFace, tl, sz, r)
        drawRoundRect(p.boardLine.copy(alpha = 0.6f), tl, sz, r, style = Stroke(t * 0.04f))
        text(m, letter, Offset(cx, cy), Color.Black.copy(alpha = 0.85f), pxToSp(t * 0.55f))
    }
    val w = size.width
    tile(w * 0.17f, size.height * 0.3f, "D", -12f)
    tile(w * 0.39f, size.height * 0.22f, "R", 8f)
    tile(w * 0.61f, size.height * 0.3f, null, 0f)
    tile(w * 0.83f, size.height * 0.24f, null, 0f)
    listOf("W", "O", null, null).forEachIndexed { i, l -> tile(w * (0.17f + i * 0.22f), size.height * 0.7f, l, 0f) }
}
