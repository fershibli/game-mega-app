package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.GameLanguages
import io.github.fershibli.megagames.games.cryptogram.Cryptogram
import io.github.fershibli.megagames.games.cryptogram.CryptogramState
import io.github.fershibli.megagames.games.cryptogram.assign
import io.github.fershibli.megagames.games.cryptogram.cryptogramFor
import io.github.fershibli.megagames.games.cryptogram.erase
import io.github.fershibli.megagames.games.cryptogram.hint
import io.github.fershibli.megagames.games.words.WordKit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class CryptogramTest {
    private val languages = Cryptogram.LANGUAGES
    private fun sayings(lang: AppLanguage) = Cryptogram.parse(File("src/main/assets/" + Cryptogram.sayingsFile(lang)).readText())

    @Test
    fun alphabeticLanguagesOnlyAndTheGameSaysSo() {
        assertEquals(AppLanguage.entries.toSet() - AppLanguage.SYSTEM - AppLanguage.JAPANESE - AppLanguage.CHINESE, languages)
        assertEquals(languages, GameId.CRYPTOGRAM.languages)
        // Japanese and Chinese players are asked to pick one of the other languages.
        assertFalse(GameId.CRYPTOGRAM.supports(AppLanguage.JAPANESE))
        assertFalse(GameId.CRYPTOGRAM.supports(AppLanguage.CHINESE))
        assertEquals(languages.size, GameLanguages.fallbackChoices(GameId.CRYPTOGRAM.languages, AppLanguage.JAPANESE, null)!!.size)
        assertNull(GameLanguages.fallbackChoices(GameId.CRYPTOGRAM.languages, AppLanguage.ARABIC, null))
    }

    @Test
    fun theKeyboardHasEveryLetterOfTheAlphabet() {
        for (lang in languages) {
            val alphabet = Cryptogram.alphabet(lang)
            assertEquals("$lang alphabet has repeats", alphabet.size, alphabet.toSet().size)
            assertEquals("$lang keyboard", alphabet.toSet(), WordKit.keyboard(lang).flatten().toSet())
        }
        assertEquals(26, Cryptogram.alphabet(AppLanguage.ENGLISH).size)
        assertEquals(27, Cryptogram.alphabet(AppLanguage.SPANISH).size)
        assertEquals(32, Cryptogram.alphabet(AppLanguage.RUSSIAN).size)
        assertEquals(33, Cryptogram.alphabet(AppLanguage.ARABIC).size)
    }

    @Test
    fun theSwapIsADerangementInEveryLanguageAndMode() {
        for (lang in languages) {
            val alphabet = Cryptogram.alphabet(lang)
            repeat(200) { seed ->
                for (numbers in listOf(false, true)) {
                    val cipher = Cryptogram.cipher(alphabet, numbers, Random(seed))
                    assertEquals("$lang every symbol used once", alphabet.size, cipher.toSet().size)
                    alphabet.indices.forEach { k ->
                        if (numbers) assertNotEquals("$lang ${alphabet[k]}", (k + 1).toString(), cipher[k]) else assertNotEquals("$lang", alphabet[k], cipher[k])
                    }
                    if (numbers) assertEquals((1..alphabet.size).map { it.toString() }.toSet(), cipher.toSet())
                }
            }
        }
    }

    @Test
    fun cellsFollowTheCrosswordSpelling() {
        assertEquals("UEBUNG MACHT DEN MEISTER.".map { it.toString() }, Cryptogram.cells("Übung macht den Meister.", AppLanguage.GERMAN))
        assertEquals(listOf("M", "A", "Ñ", "A", "N", "A"), Cryptogram.cells("mañana", AppLanguage.SPANISH))
        assertEquals(listOf("C", "O", "R", "A", "C", "A", "O"), Cryptogram.cells("coração", AppLanguage.PORTUGUESE))
        assertEquals(listOf("M", "O", "E", "U", "R", "S"), Cryptogram.cells("mœurs", AppLanguage.FRENCH))
        assertEquals(listOf("Е", "Л", "К", "А"), Cryptogram.cells("ёлка", AppLanguage.RUSSIAN))
        // Arabic: harakat dropped, alef with hamza written as a plain alef.
        assertEquals(listOf("ا", "ل", "ص", "ب", "ر"), Cryptogram.cells("الصَّبْر", AppLanguage.ARABIC))
        assertEquals(listOf("ا", "م"), Cryptogram.cells("أُم", AppLanguage.ARABIC))
    }

    @Test
    fun everySayingInEveryLanguageIsMadeOfItsAlphabetAndDecodesBack() {
        for (lang in languages) {
            val alphabet = Cryptogram.alphabet(lang)
            val list = sayings(lang)
            assertTrue("$lang has ${list.size} sayings", list.size >= 55)
            assertEquals("$lang repeats a saying", list.size, list.toSet().size)
            list.forEachIndexed { n, saying ->
                val cells = Cryptogram.cells(saying, lang)
                val letters = cells.filter { it in alphabet }
                assertTrue("$lang: $saying is too short or too long", letters.size in 8..70)
                assertTrue("$lang: $saying needs several letters", letters.distinct().size >= 5)
                for (c in cells) {
                    if (c in alphabet) continue
                    // Anything else is a space or punctuation, never a stray letter or digit.
                    assertFalse("$lang: '$c' in $saying", c.any { Character.isLetterOrDigit(it) })
                }
                // Every letter written in the saying became at least one alphabet letter.
                saying.filter { Character.isLetter(it) && it != 'ـ' }.forEach { ch ->
                    assertTrue("$lang: '$ch' in $saying", Cryptogram.cells(ch.toString(), lang).isNotEmpty())
                }
                for (numbers in listOf(false, true)) {
                    val cipher = Cryptogram.cipher(alphabet, numbers, Random(n))
                    val coded = Cryptogram.encode(cells, alphabet, cipher)
                    assertEquals("$lang: $saying", cells, Cryptogram.decode(coded, alphabet, cipher))
                    letters.forEach { l -> assertNotEquals(l, cipher[alphabet.indexOf(l)]) }
                }
            }
        }
    }

    @Test
    fun levelsRevealThreeOneOrNoLettersAndEasyUsesNumbers() {
        for (lang in languages) for (d in Difficulty.entries) {
            val s = cryptogramFor(sayings(lang).first(), lang, d, Random(5))
            assertEquals("$lang $d", Cryptogram.revealed(d), s.locked.size)
            assertEquals(Cryptogram.usesNumbers(d), s.cipher.first().first().isDigit())
            s.locked.forEach { assertEquals(s.truth(it), s.guesses[it]) }
            assertFalse(s.solved)
            assertTrue(s.selected != null && s.selected !in s.locked)
        }
    }

    private fun solveAll(start: CryptogramState): CryptogramState {
        var s = start
        for (sym in s.inPlay) if (sym !in s.locked) s = assign(s.copy(selected = sym), s.truth(sym))
        return s
    }

    @Test
    fun aLetterGoesOnEverySameSymbolAndMovesWhenUsedElsewhere() {
        val s0 = cryptogramFor("Practice makes perfect.", AppLanguage.ENGLISH, Difficulty.HARD, Random(1))
        val c = s0.cipher[s0.alphabet.indexOf("C")]
        val e = s0.cipher[s0.alphabet.indexOf("E")]
        var s = assign(s0.copy(selected = c), "C")
        // Every C of the saying shows it, because they share one symbol.
        assertEquals(3, s.symbols.count { it == c })
        assertEquals("C", s.guesses[c])
        // Putting C on another symbol takes it off the first one.
        s = assign(s.copy(selected = e), "C")
        assertNull(s.guesses[c])
        assertEquals("C", s.guesses[e])
        s = erase(s.copy(selected = e))
        assertNull(s.guesses[e])
        assertTrue(solveAll(s0).solved)
    }

    @Test
    fun revealedLettersStayAndHintFixesTheSelectedSymbol() {
        val s0 = cryptogramFor("Knowledge is power.", AppLanguage.ENGLISH, Difficulty.MEDIUM, Random(3))
        val locked = s0.locked.single()
        val letter = s0.truth(locked)
        val other = s0.inPlay.first { it != locked }
        // A revealed letter cannot be put elsewhere, nor taken off.
        assertEquals(s0.copy(selected = other), assign(s0.copy(selected = other), letter))
        assertEquals(s0.copy(selected = locked), erase(s0.copy(selected = locked)))
        val wrongLetter = s0.alphabet.first { it != s0.truth(other) && it != letter }
        val wrong = assign(s0.copy(selected = other), wrongLetter).copy(selected = other)
        val fixed = hint(wrong)
        assertEquals(s0.truth(other), fixed.guesses[other])
        assertTrue(other in fixed.locked)
        assertEquals(1, fixed.hints)
        // Hints alone solve the puzzle.
        var s = s0
        repeat(s0.inPlay.size) { s = hint(s) }
        assertTrue(s.solved)
    }

    @Test
    fun arabicSayingsSolveToo() {
        val s = cryptogramFor(sayings(AppLanguage.ARABIC).first(), AppLanguage.ARABIC, Difficulty.EASY, Random(9))
        assertTrue(solveAll(s).solved)
    }
}
