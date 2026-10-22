package io.github.fershibli.megagames.games.cryptogram

import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.crossword.Crossword
import kotlin.random.Random

/**
 * A cryptogram: a well-known saying where every letter was swapped for another letter of the same
 * alphabet (or, on Easy, for a number). The player finds which letter hides behind each symbol.
 */
object Cryptogram {
    /**
     * Languages with an alphabet to swap letters in. Japanese and Chinese are not written with an
     * alphabet of a few dozen letters, so the game asks their players to pick one of these.
     */
    val LANGUAGES: Set<AppLanguage> = setOf(
        AppLanguage.ENGLISH, AppLanguage.PORTUGUESE, AppLanguage.SPANISH, AppLanguage.FRENCH,
        AppLanguage.ITALIAN, AppLanguage.GERMAN, AppLanguage.RUSSIAN, AppLanguage.ARABIC,
    )

    private val LATIN = ('A'..'Z').map { it.toString() }

    /**
     * The letters of [language], in alphabetical order, spelled the crossword way: accents folded,
     * Spanish Ñ kept as a letter, German umlauts written AE OE UE, Russian Ё read as Е, and Arabic
     * without harakat and with أ إ آ written as ا (ء ؤ ئ ى ة stay letters, as on the keyboard).
     */
    fun alphabet(language: AppLanguage): List<String> = when (language) {
        AppLanguage.SPANISH -> LATIN.take(14) + "Ñ" + LATIN.drop(14)
        AppLanguage.RUSSIAN -> ('А'..'Я').map { it.toString() }
        AppLanguage.ARABIC -> "ابتثجحخدذرزسشصضطظعغفقكلمنهوي".map { it.toString() } + listOf("ء", "ؤ", "ئ", "ى", "ة")
        else -> LATIN
    }

    /** File of sayings for [language] in the assets. */
    fun sayingsFile(language: AppLanguage) = "cryptogram/" + when (language) {
        AppLanguage.PORTUGUESE -> "pt"
        AppLanguage.SPANISH -> "es"
        AppLanguage.FRENCH -> "fr"
        AppLanguage.ITALIAN -> "it"
        AppLanguage.GERMAN -> "de"
        AppLanguage.RUSSIAN -> "ru"
        AppLanguage.ARABIC -> "ar"
        else -> "en"
    } + ".txt"

    /** Sayings of a file: one per line; blank lines and lines starting with # are ignored. */
    fun parse(text: String): List<String> = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()

    /**
     * The saying cut into cells: each letter becomes one or more alphabet letters (Ä is AE), and
     * every other character (space, comma, apostrophe...) is kept as it is. Arabic harakat and
     * the stretching stroke are dropped, as they are not letters of their own.
     */
    fun cells(saying: String, language: AppLanguage): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < saying.length) {
            val cp = saying.codePointAt(i)
            val ch = String(Character.toChars(cp))
            i += Character.charCount(cp)
            if (Character.isLetter(cp)) {
                out += Crossword.normalize(ch, language)
            } else if (Character.getType(cp) != Character.NON_SPACING_MARK.toInt()) {
                out += ch
            }
        }
        return out
    }

    /** True when [cell] is a letter of the puzzle (and not a space or a punctuation mark). */
    fun isLetter(cell: String, alphabet: List<String>) = cell in alphabet

    /**
     * A random swap of the alphabet in which no letter stays itself (a derangement): position k of
     * the result is the symbol that hides the k-th letter.
     */
    fun derangement(n: Int, random: Random): List<Int> {
        while (true) {
            val order = (0 until n).shuffled(random)
            if (order.indices.none { order[it] == it }) return order
        }
    }

    /**
     * The symbols that hide each letter of [alphabet]: other letters of the alphabet, or the
     * numbers 1 to its size on Easy (never a letter's own place in the alphabet either).
     */
    fun cipher(alphabet: List<String>, numbers: Boolean, random: Random): List<String> {
        val order = derangement(alphabet.size, random)
        return order.map { if (numbers) (it + 1).toString() else alphabet[it] }
    }

    /** Easy shows numbers, which many people find friendlier; the other levels show letters. */
    fun usesNumbers(d: Difficulty) = d == Difficulty.EASY

    /** Letters revealed at the start: three on Easy, one on Medium, none on Hard. */
    fun revealed(d: Difficulty) = when (d) {
        Difficulty.EASY -> 3
        Difficulty.MEDIUM -> 1
        Difficulty.HARD -> 0
    }

    /** The saying written in symbols: letters swapped through [cipher], other cells kept. */
    fun encode(cells: List<String>, alphabet: List<String>, cipher: List<String>): List<String> =
        cells.map { c -> alphabet.indexOf(c).let { k -> if (k >= 0) cipher[k] else c } }

    /** The symbols read back with the key: what the player sees once every letter is found. */
    fun decode(symbols: List<String>, alphabet: List<String>, cipher: List<String>): List<String> =
        symbols.map { c -> cipher.indexOf(c).let { k -> if (k >= 0) alphabet[k] else c } }
}
