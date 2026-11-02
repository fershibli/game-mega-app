package io.github.fershibli.megagames.games.words

import android.content.Context
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.crossword.Crossword
import io.github.fershibli.megagames.games.crossword.WordEntry
import io.github.fershibli.megagames.games.crossword.dictionaryFile
import java.util.Calendar

/**
 * Pieces shared by the word games (crossword, word search, anagrams, word of the day): the
 * per-language dictionaries in `assets/crossword`, already normalized the crossword way (upper
 * case without accents, katakana one kana per square, Arabic without harakat, one hanzi per
 * square), and the keyboards of the languages that are typed rather than picked from a bank.
 */
object WordKit {
    private val QWERTY = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
    private val QWERTY_ES = listOf("QWERTYUIOP", "ASDFGHJKLÑ", "ZXCVBNM")
    private val AZERTY = listOf("AZERTYUIOP", "QSDFGHJKLM", "WXCVBN")
    private val QWERTZ = listOf("QWERTZUIOP", "ASDFGHJKL", "YXCVBNM")
    private val JCUKEN = listOf("ЙЦУКЕНГШЩЗХ", "ФЫВАПРОЛДЖЭ", "ЯЧСМИТЬБЮЪ")

    // The usual Arabic keyboard (ض at the top right), with the letters that share a key on a
    // computer given keys of their own. Rows are listed from the right, as they are read.
    private val ARABIC = listOf("ضصثقفغعهخحج", "شسيبلاتنمكط", "ذئءؤرىةوزظد")

    /**
     * Keyboard rows for [language], one string per key. Japanese and Chinese have no keyboard
     * (their games offer a bank of kana or characters instead), so their list is empty.
     */
    fun keyboard(language: AppLanguage): List<List<String>> = when (language) {
        AppLanguage.FRENCH -> AZERTY
        AppLanguage.SPANISH -> QWERTY_ES
        AppLanguage.GERMAN -> QWERTZ
        AppLanguage.RUSSIAN -> JCUKEN
        AppLanguage.ARABIC -> ARABIC
        AppLanguage.JAPANESE, AppLanguage.CHINESE -> emptyList()
        else -> QWERTY
    }.map { row -> row.map { it.toString() } }

    /** The dictionary of [language], parsed and normalized. */
    fun dictionary(context: Context, language: AppLanguage): List<WordEntry> =
        Crossword.parse(context.assets.open(dictionaryFile(language)).bufferedReader().use { it.readText() }, language)

    /** How many of the last words of each game are kept out of its next games, so they don't keep coming back. */
    private const val RECENT_WORDS = 60

    private fun recentKey(game: String, language: AppLanguage) = "$game.${language.name}"

    /** The words [game] used lately in [language], oldest first. */
    fun recent(context: Context, game: String, language: AppLanguage): List<List<String>> =
        context.getSharedPreferences("recent_words", Context.MODE_PRIVATE).getString(recentKey(game, language), null)
            .orEmpty().lines().filter { it.isNotEmpty() }.map { it.split('\t') }

    /** Adds the [words] of a new game of [game] to its recent ones. */
    fun remember(context: Context, game: String, language: AppLanguage, words: List<List<String>>) {
        val all = (recent(context, game, language) + words).takeLast(RECENT_WORDS)
        context.getSharedPreferences("recent_words", Context.MODE_PRIVATE).edit()
            .putString(recentKey(game, language), all.joinToString("\n") { it.joinToString("\t") }).apply()
    }

    /**
     * The dictionary words for a new game of [game]: those that suit [difficulty] (when the
     * dictionary marks levels), leaving out the ones the game used lately while enough remain.
     */
    fun fresh(context: Context, game: String, language: AppLanguage, difficulty: Difficulty): List<WordEntry> =
        Crossword.poolFor(dictionary(context, language), difficulty, recent(context, game, language).toSet())

    /** The word as it is shown in lists and messages (Arabic letters join up again). */
    fun text(letters: List<String>) = letters.joinToString("")

    /** Number of the current day on the phone's own calendar (days since 1 January 1970). */
    fun today(calendar: Calendar = Calendar.getInstance()): Long {
        val offset = calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)
        return Math.floorDiv(calendar.timeInMillis + offset, 86_400_000L)
    }
}
