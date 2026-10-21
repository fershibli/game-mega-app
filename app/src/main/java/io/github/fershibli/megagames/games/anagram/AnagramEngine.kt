package io.github.fershibli.megagames.games.anagram

import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.crossword.WordEntry
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * One word of a round. [tiles] holds its letters in scrambled order; [alternatives] are other
 * dictionary words made of exactly the same letters, which are accepted too.
 */
@Serializable
data class AnagramWord(
    val letters: List<String>,
    val clue: String,
    val tiles: List<String>,
    val alternatives: List<List<String>> = emptyList(),
) {
    fun accepts(built: List<String>) = built == letters || built in alternatives
}

object Anagram {
    /**
     * Languages this game works in: all ten (Japanese kana and Chinese characters are the tiles;
     * only words of three or more of them are asked).
     */
    val supportedLanguages: Set<AppLanguage> = AppLanguage.entries.toSet() - AppLanguage.SYSTEM

    /** Words in one round. */
    const val ROUND = 5

    /**
     * Word lengths per level. Spelled languages: 4–5, 6–7 and 8 letters or more. Arabic writes no
     * short vowels and Japanese counts kana, so their words are shorter: 3, 4 and 5–6. Chinese
     * words are counted in characters: 3, 3–4 and 4 (two-character words would be too easy).
     */
    fun lengths(d: Difficulty, language: AppLanguage): IntRange = when (language) {
        AppLanguage.CHINESE -> when (d) {
            Difficulty.EASY -> 3..3
            Difficulty.MEDIUM -> 3..4
            Difficulty.HARD -> 4..4
        }
        AppLanguage.JAPANESE, AppLanguage.ARABIC -> when (d) {
            Difficulty.EASY -> 3..3
            Difficulty.MEDIUM -> 4..4
            Difficulty.HARD -> 5..6
        }
        else -> when (d) {
            Difficulty.EASY -> 4..5
            Difficulty.MEDIUM -> 6..7
            Difficulty.HARD -> 8..12
        }
    }

    /** Shortest word ever asked: below three letters there is nothing to unscramble. */
    const val MIN_LENGTH = 3

    /** Words for a round of [d] in [language]; the range is widened downwards if too few words fit. */
    fun pool(entries: List<WordEntry>, d: Difficulty, language: AppLanguage): List<WordEntry> {
        val range = lengths(d, language)
        var low = range.first
        while (true) {
            val fit = entries.filter { it.letters.size in low..range.last && it.letters.distinct().size > 1 }
            if (fit.size >= ROUND || low <= MIN_LENGTH) return fit
            low--
        }
    }

    /** The letters of [letters] in an order different from the word (when such an order exists). */
    fun scramble(letters: List<String>, random: Random, avoid: List<List<String>> = emptyList()): List<String> {
        var best = letters
        repeat(30) {
            val t = letters.shuffled(random)
            if (t != letters && t !in avoid) return t
            best = t
        }
        return best
    }

    /** A round of [ROUND] different words, each with its tiles scrambled. */
    fun round(entries: List<WordEntry>, d: Difficulty, language: AppLanguage, random: Random): List<AnagramWord> {
        val byLetters = entries.groupBy { it.letters.sorted() }
        return pool(entries, d, language).shuffled(random).take(ROUND).map { e ->
            val others = byLetters[e.letters.sorted()].orEmpty().map { it.letters }.filter { it != e.letters }
            AnagramWord(e.letters, e.clue, scramble(e.letters, random, others), others)
        }
    }

    /** Number of leading letters of [built] that already match [word]. */
    fun correctPrefix(word: AnagramWord, built: List<String>): Int {
        var k = 0
        while (k < built.size && k < word.letters.size && built[k] == word.letters[k]) k++
        return k
    }
}
