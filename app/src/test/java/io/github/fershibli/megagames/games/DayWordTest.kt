package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.crossword.Crossword
import io.github.fershibli.megagames.games.crossword.dictionaryFile
import io.github.fershibli.megagames.games.dayword.DayWord
import io.github.fershibli.megagames.games.dayword.DayWordState
import io.github.fershibli.megagames.games.dayword.Mark
import io.github.fershibli.megagames.games.dayword.Mark.ABSENT
import io.github.fershibli.megagames.games.dayword.Mark.ELSEWHERE
import io.github.fershibli.megagames.games.dayword.Mark.RIGHT
import io.github.fershibli.megagames.games.dayword.hint
import io.github.fershibli.megagames.games.words.WordKit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar
import java.util.TimeZone
import kotlin.random.Random

class DayWordTest {
    private val languages = DayWord.supportedLanguages

    @Test
    fun worksInAllTenLanguages() {
        assertEquals(AppLanguage.entries.toSet() - AppLanguage.SYSTEM, languages)
    }
    private fun dictionary(lang: AppLanguage) = Crossword.parse(File("src/main/assets/" + dictionaryFile(lang)).readText(), lang)
    private fun split(s: String) = s.map { it.toString() }
    private fun marks(guess: String, answer: String): List<Mark> = DayWord.feedback(split(guess), split(answer))

    @Test
    fun feedbackMarksEachLetter() {
        assertEquals(listOf(RIGHT, RIGHT, RIGHT, RIGHT, RIGHT), marks("APPLE", "APPLE"))
        assertEquals(listOf(ABSENT, ABSENT, ABSENT, ABSENT, ABSENT), marks("DOING", "APPLE"))
        assertEquals(listOf(ELSEWHERE, ELSEWHERE, ABSENT, ABSENT, ABSENT), marks("LAUGH", "APPLE"))
    }

    @Test
    fun repeatedLettersAreMarkedOnlyAsOftenAsTheWordHasThem() {
        // One E in the word, already in place: the other E's of the guess are not marked.
        assertEquals(listOf(ABSENT, ABSENT, ABSENT, ABSENT, RIGHT), marks("EEEEE", "STONE"))
        // One E in the word, elsewhere: only the first E of the guess gets the mark.
        assertEquals(listOf(ELSEWHERE, ABSENT, ABSENT, ABSENT, ABSENT), marks("EEXXX", "STONE"))
        // Two P's in the word: one in place and one elsewhere.
        assertEquals(listOf(ELSEWHERE, ABSENT, RIGHT, ABSENT, ABSENT), marks("PXPXX", "APPLE"))
        // Both P's in place: a third P in the guess gets nothing, even though it comes first.
        assertEquals(listOf(ABSENT, RIGHT, RIGHT, ABSENT, ABSENT), marks("PPPXX", "APPLE"))
        // Three P's guessed in wrong places, two in the word: two marks, from the left.
        assertEquals(listOf(ELSEWHERE, ABSENT, ABSENT, ELSEWHERE, ABSENT), marks("PXXPP", "APPLE"))
    }

    @Test
    fun keyboardShowsTheBestKnownMark() {
        val keys = DayWord.keyMarks(listOf(split("PXXPP"), split("PPPXX")), split("APPLE"))
        assertEquals(RIGHT, keys["P"])
        assertEquals(ABSENT, keys["X"])
        assertEquals(listOf(null, "P", "P", null, "E"), DayWord.known(listOf(split("PPPXX")), split("APPLE"), listOf(4)))
    }

    @Test
    fun everyLanguageAndLevelHasEnoughWordsOfTheRightLength() {
        for (lang in languages) {
            val words = dictionary(lang)
            for (d in Difficulty.entries) {
                val pool = DayWord.pool(words, d, lang)
                assertTrue("$lang $d has ${pool.size}", pool.size >= DayWord.MIN_POOL)
                assertTrue(pool.all { it.letters.size <= DayWord.lengths(d, lang).last && it.letters.size >= 2 })
                val w = DayWord.wordOfDay(pool, 20_000, d, lang)
                assertTrue(w in pool)
                if (DayWord.usesBank(lang)) {
                    val bank = DayWord.bank(w.letters, words.flatMap { it.letters }, d, Random(1))
                    assertTrue(bank.containsAll(w.letters))
                    assertEquals(w.letters.distinct().size + DayWord.distractors(d), bank.size)
                } else {
                    // Every letter of every word can be typed on the language's keyboard.
                    val keys = WordKit.keyboard(lang).flatten().toSet()
                    assertTrue("$lang: ${w.letters}", pool.all { keys.containsAll(it.letters) })
                }
            }
        }
    }

    @Test
    fun theWordOfTheDayIsFixedForADayAndChangesNextDay() {
        val pool = DayWord.pool(dictionary(AppLanguage.ENGLISH), Difficulty.MEDIUM, AppLanguage.ENGLISH)
        val today = DayWord.wordOfDay(pool, 20_500, Difficulty.MEDIUM, AppLanguage.ENGLISH)
        assertEquals(today, DayWord.wordOfDay(pool, 20_500, Difficulty.MEDIUM, AppLanguage.ENGLISH))
        assertNotEquals(today, DayWord.wordOfDay(pool, 20_501, Difficulty.MEDIUM, AppLanguage.ENGLISH))
        // A whole cycle shows every word once.
        assertEquals(pool.toSet(), (0L until pool.size).map { DayWord.wordOfDay(pool, 20_000 + it, Difficulty.MEDIUM, AppLanguage.ENGLISH) }.toSet())
        // Levels have their own words.
        val hard = DayWord.pool(dictionary(AppLanguage.ENGLISH), Difficulty.HARD, AppLanguage.ENGLISH)
        assertTrue(hard.all { it.letters.size == 6 })
    }

    @Test
    fun dayNumberFollowsTheLocalCalendar() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo")).apply {
            clear()
            set(2026, Calendar.OCTOBER, 20, 23, 30)
        }
        val late = WordKit.today(cal)
        cal.set(2026, Calendar.OCTOBER, 21, 0, 30)
        assertEquals(late + 1, WordKit.today(cal))
        cal.set(2026, Calendar.OCTOBER, 20, 0, 5)
        assertEquals(late, WordKit.today(cal))
    }

    @Test
    fun hintShowsTheClueThenLetters() {
        var s = DayWordState(AppLanguage.ENGLISH, split("APPLE"), "fruit", -1, 6, guesses = listOf(split("APXXX")))
        s = hint(s)
        assertTrue(s.clueShown)
        s = hint(s)
        assertEquals(listOf(2), s.hinted)
        assertEquals(2, s.hints)
    }
}
