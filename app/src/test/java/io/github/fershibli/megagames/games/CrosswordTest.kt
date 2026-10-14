package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.crossword.Crossword
import io.github.fershibli.megagames.games.crossword.dictionaryFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class CrosswordTest {
    private fun dictionary(lang: AppLanguage) = Crossword.parse(File("src/main/assets/" + dictionaryFile(lang)).readText(), lang)

    @Test
    fun normalizationRemovesAccentsButKeepsSpanishEnye() {
        assertEquals(listOf("C", "O", "R", "A", "C", "A", "O"), Crossword.normalize("Coração", AppLanguage.PORTUGUESE))
        assertEquals(listOf("O", "T", "O", "Ñ", "O"), Crossword.normalize("otoño", AppLanguage.SPANISH))
        assertEquals("SOEUR", Crossword.normalize("sœur", AppLanguage.FRENCH).joinToString(""))
        assertEquals("GUARDACHUVA", Crossword.normalize("guarda-chuva", AppLanguage.PORTUGUESE).joinToString(""))
        assertEquals(listOf("苹", "果"), Crossword.normalize("苹果", AppLanguage.CHINESE))
    }

    @Test
    fun everyDictionaryIsLargeAndClean() {
        for (lang in AppLanguage.entries.filter { it != AppLanguage.SYSTEM }) {
            val words = dictionary(lang)
            assertTrue("$lang has ${words.size} words", words.size >= 120)
            assertTrue(words.all { it.clue.isNotBlank() && it.letters.size >= 2 })
        }
    }

    @Test
    fun generatedGridsAreConsistent() {
        for (lang in AppLanguage.entries.filter { it != AppLanguage.SYSTEM }) for (d in Difficulty.entries) for (seed in 1..3) {
            val chinese = lang == AppLanguage.CHINESE
            val size = Crossword.sizeFor(d, chinese)
            val words = Crossword.generate(dictionary(lang), size, Crossword.targetWords(d, chinese), Random(seed))
            assertTrue("$lang $d seed $seed: only ${words.size} words", words.size >= Crossword.targetWords(d, chinese) - 2)
            val grid = HashMap<Int, String>()
            for (w in words) {
                w.cells(size).forEachIndexed { k, c ->
                    assertTrue(c in 0 until size * size)
                    val prev = grid.put(c, w.letters[k])
                    assertTrue("letter clash in $lang", prev == null || prev == w.letters[k])
                }
            }
            // No two words share a start in the same direction, and numbering starts at 1.
            assertEquals(words.size, words.map { Triple(it.row, it.col, it.across) }.toSet().size)
            assertEquals(1, words.minOf { it.number })
        }
    }
}
