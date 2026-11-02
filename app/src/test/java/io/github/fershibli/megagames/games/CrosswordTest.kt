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
    fun normalizationFollowsGermanRussianAndJapaneseCrosswords() {
        assertEquals("KAESE", Crossword.normalize("Käse", AppLanguage.GERMAN).joinToString(""))
        assertEquals("STRASSE", Crossword.normalize("Straße", AppLanguage.GERMAN).joinToString(""))
        assertEquals(listOf("Е", "Л", "К", "А"), Crossword.normalize("ёлка", AppLanguage.RUSSIAN))
        assertEquals("ЧАЙ", Crossword.normalize("чай", AppLanguage.RUSSIAN).joinToString(""))
        assertEquals("CITTA", Crossword.normalize("città", AppLanguage.ITALIAN).joinToString(""))
        // Hiragana becomes katakana, small kana are written full size and ー keeps its square.
        assertEquals(listOf("キ", "ツ", "テ"), Crossword.normalize("きって", AppLanguage.JAPANESE))
        assertEquals(listOf("シ", "ヤ", "ツ"), Crossword.normalize("シャツ", AppLanguage.JAPANESE))
        assertEquals(listOf("ケ", "ー", "キ"), Crossword.normalize("ケーキ", AppLanguage.JAPANESE))
    }

    @Test
    fun arabicKeepsOneLetterPerSquareWithoutHarakat() {
        assertEquals(listOf("ك", "ت", "ا", "ب"), Crossword.normalize("كِتَاب", AppLanguage.ARABIC))
        assertEquals(listOf("ا", "س", "د"), Crossword.normalize("أسد", AppLanguage.ARABIC))
        assertEquals(listOf("م", "د", "ر", "س", "ة"), Crossword.normalize("مدرســة", AppLanguage.ARABIC))
        assertEquals(listOf("س", "ؤ", "ا", "ل"), Crossword.normalize("سؤال", AppLanguage.ARABIC))
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
    fun portugueseHasFiveHundredWordsEachWithALevel() {
        val words = dictionary(AppLanguage.PORTUGUESE)
        assertTrue("${words.size} words", words.size >= 500)
        assertTrue(words.all { it.level in 1..3 })
        for (level in 1..3) assertTrue("level $level: ${words.count { it.level == level }}", words.count { it.level == level } >= 80)
    }

    @Test
    fun levelsAndRecentWordsShapeThePool() {
        val words = dictionary(AppLanguage.PORTUGUESE)
        assertTrue(Crossword.poolFor(words, Difficulty.EASY).none { it.level == 3 })
        assertTrue(Crossword.poolFor(words, Difficulty.HARD).none { it.level == 1 })
        assertEquals(words.size, Crossword.poolFor(words, Difficulty.MEDIUM).size)
        // Words of the last games are left out of the next one.
        val first = Crossword.generate(words, 11, 11, Random(5), Difficulty.MEDIUM)
        val recent = first.map { it.letters }.toSet()
        val next = Crossword.generate(words, 11, 11, Random(6), Difficulty.MEDIUM, recent)
        assertTrue(next.none { it.letters in recent })
        // Unmarked dictionaries (the other languages, for now) keep every word at every level.
        val unmarked = dictionary(AppLanguage.ENGLISH)
        assertEquals(unmarked.size, Crossword.poolFor(unmarked, Difficulty.HARD).size)
    }

    @Test
    fun gamesVaryFromOneToTheNext() {
        val words = dictionary(AppLanguage.PORTUGUESE)
        val firsts = (1..8).map { seed -> Crossword.generate(words, 11, 11, Random(seed), Difficulty.MEDIUM).map { it.letters }.toSet() }
        // Two games in a row share only a few words, even without the memory of recent words.
        for (k in 1 until firsts.size) assertTrue((firsts[k] intersect firsts[k - 1]).size <= 4)
    }

    @Test
    fun generatedGridsAreConsistent() {
        for (lang in AppLanguage.entries.filter { it != AppLanguage.SYSTEM }) for (d in Difficulty.entries) for (seed in 1..3) {
            val size = Crossword.sizeFor(d, lang.syllabic)
            val words = Crossword.generate(dictionary(lang), size, Crossword.targetWords(d, lang.syllabic), Random(seed))
            assertTrue("$lang $d seed $seed: only ${words.size} words", words.size >= Crossword.targetWords(d, lang.syllabic) - 2)
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
