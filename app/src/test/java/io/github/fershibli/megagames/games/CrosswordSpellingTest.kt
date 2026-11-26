package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.games.crossword.Crossword
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Italian, German and Russian dictionaries write their answers with their real spelling
 * (accents, ß and Ё, spaces between words); these are the rules that fold them into squares.
 */
class CrosswordSpellingTest {
    private fun squares(word: String, lang: AppLanguage) = Crossword.normalize(word, lang).joinToString("")

    @Test
    fun capitalSharpSIsTwoSquaresInGerman() {
        assertEquals("FUSS", squares("FUß", AppLanguage.GERMAN))
        assertEquals("STRASSENBAHN", squares("STRAßENBAHN", AppLanguage.GERMAN))
        assertEquals("SUESS", squares("SÜß", AppLanguage.GERMAN))
    }

    @Test
    fun capitalYoCountsAsYeInRussian() {
        assertEquals("ЕЖ", squares("ЁЖ", AppLanguage.RUSSIAN))
        assertEquals("ЧЕРНЫЙ", squares("ЧЁРНЫЙ", AppLanguage.RUSSIAN))
    }

    @Test
    fun italianAccentsAndSpacesAreDropped() {
        assertEquals("NOCEMOSCATA", squares("NOCE MOSCATA", AppLanguage.ITALIAN))
        assertEquals("COMO", squares("COMÒ", AppLanguage.ITALIAN))
        assertEquals("TIRAMISU", squares("TIRAMISÙ", AppLanguage.ITALIAN))
    }
}
