package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.crossword.Crossword
import io.github.fershibli.megagames.games.crossword.dictionaryFile
import io.github.fershibli.megagames.games.wordsearch.WordSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class WordSearchTest {
    private val languages = WordSearch.supportedLanguages

    @Test
    fun worksInAllTenLanguages() {
        assertEquals(AppLanguage.entries.toSet() - AppLanguage.SYSTEM, languages)
    }
    private fun dictionary(lang: AppLanguage) = Crossword.parse(File("src/main/assets/" + dictionaryFile(lang)).readText(), lang)

    @Test
    fun everyLanguageAndLevelHidesEveryWordInAnAllowedDirection() {
        for (lang in languages) {
            val words = dictionary(lang)
            for (d in Difficulty.entries) for (seed in 1..4) {
                val size = WordSearch.sizeFor(d)
                val dirs = WordSearch.directions(d)
                val p = WordSearch.generate(words, size, WordSearch.wordsFor(d), dirs, Random(seed), WordSearch.minLength(lang))
                assertEquals("$lang $d seed $seed", WordSearch.wordsFor(d), p.words.size)
                assertEquals(size * size, p.grid.size)
                assertTrue(p.grid.all { it.isNotEmpty() })
                assertEquals("words are different", p.words.size, p.words.map { it.letters }.toSet().size)
                for (w in p.words) {
                    assertTrue("$lang $d: ${w.dr},${w.dc} not allowed", (w.dr to w.dc) in dirs)
                    assertTrue(w.letters.size >= WordSearch.minLength(lang))
                    val endR = w.row + w.dr * (w.letters.size - 1)
                    val endC = w.col + w.dc * (w.letters.size - 1)
                    assertTrue(w.row in 0 until size && w.col in 0 until size && endR in 0 until size && endC in 0 until size)
                    assertEquals(w.letters, w.cells(size).map { p.grid[it] })
                    // The word is found by selecting it from its first letter to its last.
                    val cells = WordSearch.line(w.cell(0, size), w.cell(w.letters.size - 1, size), size)!!
                    assertEquals(w.cells(size), cells)
                }
            }
        }
    }

    @Test
    fun levelsAddDiagonalsThenBackwards() {
        assertEquals(setOf(0 to 1, 1 to 0), WordSearch.directions(Difficulty.EASY).toSet())
        assertEquals(setOf(0 to 1, 1 to 0, 1 to 1, -1 to 1), WordSearch.directions(Difficulty.MEDIUM).toSet())
        assertEquals(8, WordSearch.directions(Difficulty.HARD).toSet().size)
        assertEquals(listOf(8, 10, 12), Difficulty.entries.map { WordSearch.sizeFor(it) })
    }

    @Test
    fun noWordHidesInsideAnother() {
        val p = WordSearch.generate(dictionary(AppLanguage.ENGLISH), 12, 10, WordSearch.directions(Difficulty.HARD), Random(3))
        for (a in p.words) for (b in p.words) if (a !== b) {
            val text = a.letters.joinToString("")
            assertTrue(b.letters.joinToString("") !in text && b.letters.reversed().joinToString("") !in text)
        }
    }

    @Test
    fun linesSnapToTheEightDirections() {
        val n = 8
        assertEquals(listOf(0, 1, 2, 3), WordSearch.line(0, 3, n))
        assertEquals(listOf(0, 9, 18), WordSearch.line(0, 18, n))
        assertEquals(listOf(3, 2, 1), WordSearch.line(3, 1, n))
        assertNull(WordSearch.line(0, 10, n))
        // A finger a little off the diagonal still selects the diagonal.
        assertEquals(4 * n + 4, WordSearch.snap(0, 3, 4, n))
        assertEquals(4, WordSearch.snap(0, 0, 4, n))
        // Never runs off the grid.
        assertEquals(7 * n + 7, WordSearch.snap(0, 9, 9, n))
    }

    @Test
    fun selectionMatchesForwardsOrBackwardsOnce() {
        val p = WordSearch.generate(dictionary(AppLanguage.PORTUGUESE), 8, 6, WordSearch.directions(Difficulty.EASY), Random(5))
        val w = p.words[2]
        val cells = w.cells(8)
        assertEquals(2, WordSearch.match(p.grid, p.words, emptySet(), cells))
        assertEquals(2, WordSearch.match(p.grid, p.words, emptySet(), cells.reversed()))
        assertEquals(-1, WordSearch.match(p.grid, p.words, setOf(2), cells))
    }
}
