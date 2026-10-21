package io.github.fershibli.megagames

import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.AppLanguage.ENGLISH
import io.github.fershibli.megagames.core.AppLanguage.JAPANESE
import io.github.fershibli.megagames.core.AppLanguage.PORTUGUESE
import io.github.fershibli.megagames.core.AppLanguage.SPANISH
import io.github.fershibli.megagames.core.AppLanguage.SYSTEM
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.GameLanguages
import io.github.fershibli.megagames.core.SavedGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** Games that cannot be played in every language: when to ask, what to offer, what to restore. */
class GameLanguagesTest {
    private val latin = setOf(SPANISH, ENGLISH, PORTUGUESE)

    @Test
    fun everyShippedGameSupportsEveryLanguageUnlessItSaysOtherwise() {
        for (game in GameId.entries.filter { it.languages == null }) {
            for (language in AppLanguage.entries) assertTrue("$game in $language", game.supports(language))
        }
    }

    @Test
    fun aSupportedLanguageOpensDirectly() {
        assertNull(GameLanguages.fallbackChoices(null, JAPANESE, null))
        assertNull(GameLanguages.fallbackChoices(latin, PORTUGUESE, ENGLISH))
        // The phone language counts, never "follow the phone" itself.
        assertTrue(GameLanguages.supports(latin, SYSTEM.resolve(Locale.forLanguageTag("es-MX"))))
    }

    @Test
    fun anUnsupportedLanguageOffersTheOthersInTheSettingsOrder() {
        assertEquals(listOf(ENGLISH, PORTUGUESE, SPANISH), GameLanguages.fallbackChoices(latin, JAPANESE, null))
        assertFalse(GameLanguages.supports(latin, JAPANESE))
    }

    @Test
    fun theLanguagePickedLastTimeComesFirst() {
        assertEquals(listOf(SPANISH, ENGLISH, PORTUGUESE), GameLanguages.fallbackChoices(latin, JAPANESE, SPANISH))
        // A remembered language the game no longer has is simply ignored.
        assertEquals(listOf(ENGLISH, PORTUGUESE, SPANISH), GameLanguages.fallbackChoices(latin, JAPANESE, JAPANESE))
    }

    @Test
    fun limitedGamesArePinnedToTheLanguageTheyStartIn() {
        assertNull(GameLanguages.opening(null, PORTUGUESE))
        assertEquals(PORTUGUESE, GameLanguages.opening(latin, PORTUGUESE))
    }

    @Test
    fun aRestoredGameKeepsItsLanguage() {
        // Started in Spanish, the app is now in Japanese: still Spanish.
        assertEquals(SPANISH, GameLanguages.restored(latin, JAPANESE, SPANISH))
        // Old saves without a language: the app language when possible, else the first one.
        assertEquals(PORTUGUESE, GameLanguages.restored(latin, PORTUGUESE, null))
        assertEquals(ENGLISH, GameLanguages.restored(latin, JAPANESE, null))
        // Games with every language keep following the app, as before.
        assertNull(GameLanguages.restored(null, JAPANESE, null))
    }

    @Test
    fun oldSavesWithoutALanguageStillLoad() {
        val old = """{"game":"sudoku","difficulty":"EASY","state":1,"formatVersion":1}"""
        assertNull(AppJson.decodeFromString(SavedGame.serializer(), old).language)
        assertNull(GameLanguages.fromName(null))
        assertNull(GameLanguages.fromName("KLINGON"))
        assertEquals(SPANISH, GameLanguages.fromName(SPANISH.name))
    }
}
