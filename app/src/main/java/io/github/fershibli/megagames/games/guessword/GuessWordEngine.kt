package io.github.fershibli.megagames.games.guessword

import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.crossword.WordEntry
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * One word of a round: its letters (as the crossword spells them), its clue and, in Japanese and
 * Chinese, the bank of kana or characters to pick from (empty when a keyboard is used).
 */
@Serializable
data class FlowerWord(val letters: List<String>, val clue: String, val bank: List<String> = emptyList())

/** How a finished word went: found or not, and how many petals were still on the flower. */
@Serializable
data class WordResult(val found: Boolean, val petalsLeft: Int)

/**
 * Guess the word: a hidden word is guessed one letter at a time. Every letter that is not in the
 * word makes one petal fall from a flower; the word must be found before the last petal falls.
 */
object GuessWord {
    /** Languages this game works in: all ten (Japanese and Chinese pick from a bank). */
    val supportedLanguages: Set<AppLanguage> = AppLanguage.entries.toSet() - AppLanguage.SYSTEM

    /** Words in one round. */
    const val ROUND = 5

    /** Petals on the flower at the start of each word: the number of wrong letters allowed. */
    fun petals(d: Difficulty) = when (d) {
        Difficulty.EASY -> 8
        Difficulty.MEDIUM -> 6
        Difficulty.HARD -> 5
    }

    /** Easy and Medium show the clue from the start; Hard shows it only after a Hint. */
    fun clueShown(d: Difficulty) = d != Difficulty.HARD

    /** Letters given at the start of each word: one on Easy, none otherwise. */
    fun givenLetters(d: Difficulty) = if (d == Difficulty.EASY) 1 else 0

    /**
     * Word lengths per level. Spelled languages: 4–6, 5–7 and 6–9 letters. Arabic writes no short
     * vowels and Japanese counts kana, so their words are shorter; Chinese counts characters.
     */
    fun lengths(d: Difficulty, language: AppLanguage): IntRange = when (language) {
        AppLanguage.CHINESE -> when (d) {
            Difficulty.EASY -> 2..2
            Difficulty.MEDIUM -> 2..3
            Difficulty.HARD -> 3..4
        }
        AppLanguage.JAPANESE -> when (d) {
            Difficulty.EASY -> 3..4
            Difficulty.MEDIUM -> 3..5
            Difficulty.HARD -> 4..6
        }
        AppLanguage.ARABIC -> when (d) {
            Difficulty.EASY -> 3..4
            Difficulty.MEDIUM -> 4..5
            Difficulty.HARD -> 4..6
        }
        else -> when (d) {
            Difficulty.EASY -> 4..6
            Difficulty.MEDIUM -> 5..7
            Difficulty.HARD -> 6..9
        }
    }

    /** Japanese and Chinese pick kana or characters from a bank; the others use a keyboard. */
    fun usesBank(language: AppLanguage) = language.syllabic

    /** Kana or characters added to the bank that are not in the word (enough to lose every petal). */
    const val BANK_DISTRACTORS = 10

    /**
     * Words a round of [d] can use: two different letters at least (one may be given), within the
     * level's lengths; the range widens when a language has too few such words.
     */
    fun pool(entries: List<WordEntry>, d: Difficulty, language: AppLanguage): List<WordEntry> {
        val range = lengths(d, language)
        val usable = entries.filter { it.letters.distinct().size >= 2 }
        var low = range.first
        var high = range.last
        while (true) {
            val fit = usable.filter { it.letters.size in low..high }
            if (fit.size >= ROUND || (low <= 2 && high >= 12)) return fit
            if (low > 2) low-- else high++
        }
    }

    /** The bank for Japanese and Chinese: the word's own units and [BANK_DISTRACTORS] others. */
    fun bank(answer: List<String>, alphabet: List<String>, random: Random): List<String> {
        val own = answer.distinct()
        val others = alphabet.distinct().filter { it !in own }.shuffled(random).take(BANK_DISTRACTORS)
        return (own + others).shuffled(random)
    }

    /** A round of [ROUND] different words for [language], with banks where the language needs them. */
    fun round(entries: List<WordEntry>, d: Difficulty, language: AppLanguage, random: Random): List<FlowerWord> {
        val alphabet = if (usesBank(language)) entries.flatMap { it.letters } else emptyList()
        return pool(entries, d, language).shuffled(random).take(ROUND).map { e ->
            FlowerWord(e.letters, e.clue, if (usesBank(language)) bank(e.letters, alphabet, random) else emptyList())
        }
    }

    /** Letters tried that are not in [word]: each one is a fallen petal. */
    fun wrong(word: List<String>, tried: List<String>): List<String> = tried.filter { it !in word }

    /** True when every letter of [word] has been tried (or given): a repeated letter shows everywhere at once. */
    fun found(word: List<String>, tried: List<String>): Boolean = word.all { it in tried }

    /** The word as shown: its letters where tried, `null` in the places still hidden. */
    fun shown(word: List<String>, tried: List<String>): List<String?> = word.map { if (it in tried) it else null }

    /** Number of places of [word] that [letter] fills (a repeated letter fills all of them). */
    fun places(word: List<String>, letter: String): Int = word.count { it == letter }

    /** Words found in a round. */
    fun foundCount(results: List<WordResult>) = results.count { it.found }

    /** Petals kept on the flowers of the words found: the round's score. */
    fun petalsKept(results: List<WordResult>) = results.filter { it.found }.sumOf { it.petalsLeft }

    /** A round counts as won when most of its words were found (three of five). */
    fun won(results: List<WordResult>) = results.isNotEmpty() && foundCount(results) * 2 > results.size
}
