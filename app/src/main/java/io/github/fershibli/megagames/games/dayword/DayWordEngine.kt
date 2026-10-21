package io.github.fershibli.megagames.games.dayword

import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.crossword.WordEntry
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** What a guessed letter tells: in the right place, in the word but elsewhere, or not in the word. */
@Serializable
enum class Mark { RIGHT, ELSEWHERE, ABSENT }

object DayWord {
    /**
     * Languages this game works in: all ten. Japanese and Chinese pick kana or characters from a
     * small bank instead of typing, with words of 3–5 kana or 2–4 characters.
     */
    val supportedLanguages: Set<AppLanguage> = AppLanguage.entries.toSet() - AppLanguage.SYSTEM

    /** Lines the player can write on before the word is shown. */
    fun tries(d: Difficulty) = when (d) {
        Difficulty.EASY -> 8
        Difficulty.MEDIUM -> 7
        Difficulty.HARD -> 6
    }

    /**
     * Word lengths per level. Spelled languages: 4–5, 5 and 6 letters. Arabic writes no short
     * vowels, so its words are a letter shorter (3–4, 4, 5). Japanese counts kana (3, 4, 5) and
     * Chinese counts characters (2, 3, 4: four-character sayings on Hard).
     */
    fun lengths(d: Difficulty, language: AppLanguage): IntRange = when (language) {
        AppLanguage.CHINESE -> when (d) {
            Difficulty.EASY -> 2..2
            Difficulty.MEDIUM -> 3..3
            Difficulty.HARD -> 4..4
        }
        AppLanguage.JAPANESE -> when (d) {
            Difficulty.EASY -> 3..3
            Difficulty.MEDIUM -> 4..4
            Difficulty.HARD -> 5..5
        }
        AppLanguage.ARABIC -> when (d) {
            Difficulty.EASY -> 3..4
            Difficulty.MEDIUM -> 4..4
            Difficulty.HARD -> 5..5
        }
        else -> when (d) {
            Difficulty.EASY -> 4..5
            Difficulty.MEDIUM -> 5..5
            Difficulty.HARD -> 6..6
        }
    }

    /** Fewest words a level needs, so the word of the day does not come back too soon. */
    const val MIN_POOL = 10

    /** Japanese and Chinese pick from a bank of kana or characters; the others type on a keyboard. */
    fun usesBank(language: AppLanguage) = language.syllabic

    /** The words of [d]: the range is widened downwards when a language has too few such words. */
    fun pool(entries: List<WordEntry>, d: Difficulty, language: AppLanguage): List<WordEntry> {
        val range = lengths(d, language)
        var low = range.first
        while (true) {
            val fit = entries.filter { it.letters.size in low..range.last }
            if (fit.size >= MIN_POOL || low <= 2) return fit
            low--
        }
    }

    /**
     * The word of day number [day] (days since 1970 on the phone's calendar). Every level and
     * language has its own fixed shuffled order and walks through it one word a day, so all the
     * words come up before any comes back.
     */
    fun wordOfDay(pool: List<WordEntry>, day: Long, d: Difficulty, language: AppLanguage): WordEntry {
        val order = pool.shuffled(Random(language.ordinal * 1009 + d.ordinal * 101 + 7))
        return order[Math.floorMod(day, order.size.toLong()).toInt()]
    }

    /**
     * Marks of [guess] against [answer]. Letters in the right place are marked first; then each
     * other letter is marked "elsewhere" only while the answer still has an unmatched copy of it,
     * so a letter guessed twice that the word holds once gets one mark, not two.
     */
    fun feedback(guess: List<String>, answer: List<String>): List<Mark> {
        val marks = MutableList(guess.size) { Mark.ABSENT }
        val left = HashMap<String, Int>()
        for (i in answer.indices) {
            if (guess.getOrNull(i) == answer[i]) marks[i] = Mark.RIGHT else left[answer[i]] = (left[answer[i]] ?: 0) + 1
        }
        for (i in guess.indices) {
            if (marks[i] == Mark.RIGHT) continue
            val n = left[guess[i]] ?: 0
            if (n > 0) {
                marks[i] = Mark.ELSEWHERE
                left[guess[i]] = n - 1
            }
        }
        return marks
    }

    /** Best thing known about each letter from all the guesses (right beats elsewhere beats absent). */
    fun keyMarks(guesses: List<List<String>>, answer: List<String>): Map<String, Mark> {
        val out = HashMap<String, Mark>()
        for (g in guesses) {
            feedback(g, answer).forEachIndexed { i, m ->
                val old = out[g[i]]
                if (old == null || m.ordinal < old.ordinal) out[g[i]] = m
            }
        }
        return out
    }

    /** Letters known in place, from guesses marked right and from hints; null where still unknown. */
    fun known(guesses: List<List<String>>, answer: List<String>, hinted: List<Int>): List<String?> =
        answer.indices.map { i -> if (i in hinted || guesses.any { it.getOrNull(i) == answer[i] }) answer[i] else null }

    /** Distractors added to the bank of kana or characters on each level. */
    fun distractors(d: Difficulty) = when (d) {
        Difficulty.EASY -> 3
        Difficulty.MEDIUM -> 5
        Difficulty.HARD -> 7
    }

    /** The bank for Japanese and Chinese: the answer's units and a few others from the dictionary. */
    fun bank(answer: List<String>, alphabet: List<String>, d: Difficulty, random: Random): List<String> {
        val own = answer.distinct()
        val others = alphabet.distinct().filter { it !in own }.shuffled(random).take(distractors(d))
        return (own + others).shuffled(random)
    }
}
