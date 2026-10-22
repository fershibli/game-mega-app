package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.crossword.Crossword
import io.github.fershibli.megagames.games.crossword.dictionaryFile
import io.github.fershibli.megagames.games.guessword.FlowerWord
import io.github.fershibli.megagames.games.guessword.GuessWord
import io.github.fershibli.megagames.games.guessword.GuessWordState
import io.github.fershibli.megagames.games.guessword.WordResult
import io.github.fershibli.megagames.games.guessword.guess
import io.github.fershibli.megagames.games.guessword.hint
import io.github.fershibli.megagames.games.guessword.startWord
import io.github.fershibli.megagames.games.words.WordKit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class GuessWordTest {
    private fun dictionary(lang: AppLanguage) = Crossword.parse(File("src/main/assets/" + dictionaryFile(lang)).readText(), lang)
    private fun split(s: String) = s.map { it.toString() }
    private fun state(word: String, petals: Int = 5, clue: String = "a fruit") =
        GuessWordState(AppLanguage.ENGLISH, listOf(FlowerWord(split(word), clue)), petals)

    @Test
    fun levelsHaveFewerPetalsAndLessHelp() {
        assertEquals(listOf(8, 6, 5), Difficulty.entries.map { GuessWord.petals(it) })
        assertEquals(listOf(true, true, false), Difficulty.entries.map { GuessWord.clueShown(it) })
        assertEquals(listOf(1, 0, 0), Difficulty.entries.map { GuessWord.givenLetters(it) })
    }

    @Test
    fun aRepeatedLetterFillsEveryPlaceAtOnce() {
        var s = state("APPLE")
        s = guess(s, "P")
        assertEquals(listOf(null, "P", "P", null, null), GuessWord.shown(s.word.letters, s.tried))
        assertEquals(2, GuessWord.places(s.word.letters, "P"))
        assertEquals(5, s.petalsLeft)
        // Trying the same letter again changes nothing.
        assertEquals(s, guess(s, "P"))
        s = guess(guess(guess(s, "A"), "L"), "E")
        assertTrue(s.found)
        assertEquals(listOf(WordResult(true, 5)), s.results)
    }

    @Test
    fun eachWrongLetterDropsOnePetalUntilTheWordIsShown() {
        var s = state("SALT", petals = 3)
        s = guess(s, "X")
        assertEquals(listOf("X"), s.wrong)
        assertEquals(2, s.petalsLeft)
        s = guess(guess(s, "A"), "Q")
        assertEquals(1, s.petalsLeft)
        assertFalse(s.wordOver)
        s = guess(s, "Z")
        assertTrue(s.lost)
        assertEquals(0, s.petalsLeft)
        assertEquals(listOf(WordResult(false, 0)), s.results)
        // Once the flower has no petals, keys do nothing.
        assertEquals(s, guess(s, "S"))
    }

    @Test
    fun scoringCountsWordsFoundAndPetalsKept() {
        val results = listOf(WordResult(true, 5), WordResult(false, 0), WordResult(true, 2), WordResult(true, 8), WordResult(false, 0))
        assertEquals(3, GuessWord.foundCount(results))
        assertEquals(15, GuessWord.petalsKept(results))
        assertTrue(GuessWord.won(results))
        assertFalse(GuessWord.won(results.take(2) + WordResult(false, 0)))
        assertFalse(GuessWord.won(emptyList()))
    }

    @Test
    fun hintShowsTheHiddenClueFirstThenALetterWithoutLosingAPetal() {
        val hard = startWord(state("PEAR"), 0, Difficulty.HARD)
        assertFalse(hard.clueShown)
        val clue = hint(hard)
        assertTrue(clue.clueShown)
        assertEquals(1, clue.hints)
        val letter = hint(clue)
        assertEquals(listOf("P"), letter.tried)
        assertEquals(listOf("P"), letter.given)
        assertEquals(5, letter.petalsLeft)
        // Hints can finish the word: it counts as found, with all the petals still on.
        val done = hint(hint(hint(letter)))
        assertTrue(done.found)
        assertEquals(WordResult(true, 5), done.results.single())
    }

    @Test
    fun easyGivesOneLetterButNeverTheWholeWord() {
        repeat(30) { seed ->
            val s = startWord(state("TOOT"), 0, Difficulty.EASY, Random(seed))
            assertEquals(1, s.given.size)
            assertTrue(s.clueShown)
            assertFalse(s.found)
            assertEquals(s.given, s.tried)
        }
    }

    @Test
    fun nextWordGetsANewFlower() {
        val round = GuessWordState(AppLanguage.ENGLISH, listOf(FlowerWord(split("AB"), "x"), FlowerWord(split("CD"), "y")), 5)
        var s = guess(guess(round, "X"), "Y")
        s = guess(guess(guess(s, "Z"), "Q"), "W")
        assertTrue(s.lost)
        val next = startWord(s, 1, Difficulty.MEDIUM)
        assertEquals(5, next.petalsLeft)
        assertEquals(emptyList<String>(), next.tried)
        assertEquals(1, next.results.size)
    }

    @Test
    fun everyLanguageMakesARoundThatCanBeTypedOrPicked() {
        for (lang in GuessWord.supportedLanguages) {
            val entries = dictionary(lang)
            for (d in Difficulty.entries) {
                val round = GuessWord.round(entries, d, lang, Random(d.ordinal + 11 * lang.ordinal))
                assertEquals("$lang $d", GuessWord.ROUND, round.size)
                assertEquals("$lang $d distinct", GuessWord.ROUND, round.map { it.letters }.distinct().size)
                val keys = WordKit.keyboard(lang).flatten().toSet()
                for (w in round) {
                    assertTrue("$lang $d ${w.letters}", w.letters.distinct().size >= 2)
                    if (GuessWord.usesBank(lang)) {
                        assertTrue("$lang bank ${w.letters}", w.bank.containsAll(w.letters))
                        assertEquals("$lang bank size", w.letters.distinct().size + GuessWord.BANK_DISTRACTORS, w.bank.size)
                        // There are always enough wrong keys to lose every petal.
                        assertTrue(w.bank.count { it !in w.letters } >= GuessWord.petals(Difficulty.EASY))
                    } else {
                        assertTrue("$lang ${w.letters} not on the keyboard", keys.containsAll(w.letters))
                    }
                }
            }
        }
    }

    @Test
    fun everyDictionaryWordOfEveryLevelCanBeTyped() {
        for (lang in GuessWord.supportedLanguages.filter { !GuessWord.usesBank(it) }) {
            val keys = WordKit.keyboard(lang).flatten().toSet()
            for (d in Difficulty.entries) for (e in GuessWord.pool(dictionary(lang), d, lang)) {
                assertTrue("$lang ${e.letters}", keys.containsAll(e.letters))
            }
        }
    }
}
