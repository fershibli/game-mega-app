package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.anagram.Anagram
import io.github.fershibli.megagames.games.anagram.AnagramState
import io.github.fershibli.megagames.games.anagram.hint
import io.github.fershibli.megagames.games.anagram.place
import io.github.fershibli.megagames.games.anagram.startWord
import io.github.fershibli.megagames.games.crossword.Crossword
import io.github.fershibli.megagames.games.crossword.dictionaryFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class AnagramTest {
    private val languages = Anagram.supportedLanguages

    @Test
    fun worksInAllTenLanguages() {
        assertEquals(AppLanguage.entries.toSet() - AppLanguage.SYSTEM, languages)
    }
    private fun dictionary(lang: AppLanguage) = Crossword.parse(File("src/main/assets/" + dictionaryFile(lang)).readText(), lang)

    @Test
    fun everyLanguageAndLevelMakesARoundOfScrambledWords() {
        for (lang in languages) for (d in Difficulty.entries) for (seed in 1..3) {
            val round = Anagram.round(dictionary(lang), d, lang, Random(seed))
            assertEquals("$lang $d", Anagram.ROUND, round.size)
            assertEquals(Anagram.ROUND, round.map { it.letters }.toSet().size)
            for (w in round) {
                // The tiles are exactly the letters of the word, in another order.
                assertEquals(w.letters.sorted(), w.tiles.sorted())
                assertNotEquals("$lang ${w.letters}", w.letters, w.tiles)
                assertTrue("$lang $d ${w.letters}", w.letters.size >= Anagram.MIN_LENGTH)
                assertTrue(w.letters.size <= Anagram.lengths(d, lang).last)
                assertTrue(w.clue.isNotBlank())
            }
        }
    }

    @Test
    fun chineseAndJapaneseWordsHaveAtLeastThreeUnits() {
        for (lang in listOf(AppLanguage.CHINESE, AppLanguage.JAPANESE)) for (d in Difficulty.entries) {
            assertTrue(Anagram.pool(dictionary(lang), d, lang).all { it.letters.size >= 3 })
            assertTrue("$lang $d", Anagram.pool(dictionary(lang), d, lang).size >= Anagram.ROUND)
        }
    }

    @Test
    fun levelsAskLongerWords() {
        val en = AppLanguage.ENGLISH
        assertTrue(Anagram.lengths(Difficulty.EASY, en).last < Anagram.lengths(Difficulty.MEDIUM, en).first)
        assertTrue(Anagram.lengths(Difficulty.MEDIUM, en).last < Anagram.lengths(Difficulty.HARD, en).first)
    }

    @Test
    fun tappingTheTilesInOrderSolvesTheWordAndHintsLeadThere() {
        val round = Anagram.round(dictionary(AppLanguage.ENGLISH), Difficulty.MEDIUM, AppLanguage.ENGLISH, Random(1))
        var s = startWord(AnagramState(AppLanguage.ENGLISH, round), 0, Difficulty.MEDIUM)
        val w = s.word
        val used = HashSet<Int>()
        for (letter in w.letters) {
            val tile = w.tiles.indices.first { it !in used && w.tiles[it] == letter }
            used += tile
            s = place(s, tile)
        }
        assertTrue(s.solved)
        // Hard hides the clue: the first hint shows it, every later hint places one right letter.
        var h = startWord(AnagramState(AppLanguage.ENGLISH, round), 1, Difficulty.HARD)
        assertFalse(h.clueShown)
        h = hint(h)
        assertTrue(h.clueShown)
        repeat(h.word.letters.size) { h = hint(h) }
        assertTrue(h.solved)
        assertEquals(h.word.letters, h.built)
        // Easy gives the first letter, locked in place.
        val e = startWord(AnagramState(AppLanguage.ENGLISH, round), 2, Difficulty.EASY)
        assertEquals(listOf(e.word.letters[0]), e.built)
        assertEquals(1, e.locked)
    }

    @Test
    fun hintTakesBackWrongLettersFirst() {
        val round = Anagram.round(dictionary(AppLanguage.GERMAN), Difficulty.EASY, AppLanguage.GERMAN, Random(2))
        var s = startWord(AnagramState(AppLanguage.GERMAN, round), 0, Difficulty.MEDIUM)
        val wrongFirst = s.word.tiles.indices.first { s.word.tiles[it] != s.word.letters[0] }
        s = place(s, wrongFirst)
        s = hint(s)
        assertEquals(listOf(s.word.letters[0]), s.built)
    }
}
