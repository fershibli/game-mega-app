package io.github.fershibli.megagames

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.ChoiceStore
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.SavedGame
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.core.SettingsStore
import io.github.fershibli.megagames.games.crossword.CrosswordState
import io.github.fershibli.megagames.games.crossword.newCrossword
import io.github.fershibli.megagames.ui.MegaGamesApp
import io.github.fershibli.megagames.ui.theme.localized
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A game that is not available in the app language asks which language to play in, then plays in
 * it. No shipped game is limited yet, so the crossword (whose words follow the game language)
 * pretends to exist only in English and Portuguese.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class LanguageFallbackTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val onlyLatin = setOf(AppLanguage.ENGLISH, AppLanguage.PORTUGUESE)
    private val limited: (GameId) -> Set<AppLanguage>? = { if (it == GameId.CROSSWORD) onlyLatin else it.languages }

    private fun text(language: AppLanguage, id: Int) = app.localized(language).getString(id)

    /** Opens the app in [language] with the crossword first on the home screen. */
    private fun openApp(language: AppLanguage, saves: SaveStore = SaveStore(app).apply { clear() }, offerResume: Boolean = false) {
        SettingsStore(app).save(Settings(language = language))
        ChoiceStore(app).recordPlayed(GameId.CROSSWORD)
        rule.setContent { MegaGamesApp(SettingsStore(app), saves, offerResume = offerResume, languagesOf = limited) }
    }

    /** Waits for the crossword clue bar, which reads "1 Across · clue" in the game language. */
    private fun waitForClueIn(language: AppLanguage) {
        val directions = listOf(text(language, R.string.crossword_across), text(language, R.string.crossword_down))
        rule.waitUntil(30_000) {
            directions.any { rule.onAllNodesWithText(" $it · ", substring = true).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test
    fun anUnsupportedLanguageAsksFirstAndTheGameIsPlayedInTheChosenOne() {
        val ja = AppLanguage.JAPANESE
        val pt = AppLanguage.PORTUGUESE
        val saves = SaveStore(app).apply { clear() }
        openApp(ja, saves)
        rule.onAllNodesWithContentDescription(text(ja, R.string.game_crossword))[0].performClick()
        // The question is asked in the app language, with each language under its own name.
        rule.onNodeWithText(text(ja, R.string.language_fallback_message)).assertIsDisplayed()
        rule.onNodeWithText("English").assertIsDisplayed()
        rule.onNodeWithText("日本語").assertDoesNotExist()
        rule.onNodeWithText("Português").performClick()
        // The app around the game stays in Japanese; the rules of the game are in Portuguese.
        rule.onNodeWithText(text(ja, R.string.choose_difficulty)).assertIsDisplayed()
        rule.onNodeWithText(text(pt, R.string.howto_crossword)).assertIsDisplayed()
        rule.onNodeWithText(text(ja, R.string.confirm)).performClick()
        // The crossword itself: Portuguese words and texts.
        waitForClueIn(pt)
        var saved: SavedGame? = null
        repeat(100) { if (saved == null) { Thread.sleep(20); saved = saves.load() } }
        assertEquals(pt.name, saved?.language)
        assertEquals(pt, saved?.let { AppJson.decodeFromJsonElement(CrosswordState.serializer(), it.state).language })
        // Remembered for next time (it then comes first).
        assertEquals(pt, ChoiceStore(app).lastLanguage(GameId.CROSSWORD))
        saves.clear()
    }

    @Test
    fun cancelGoesBackToTheHomeScreen() {
        val ja = AppLanguage.JAPANESE
        openApp(ja)
        rule.onAllNodesWithContentDescription(text(ja, R.string.game_crossword))[0].performClick()
        rule.onNodeWithText(text(ja, R.string.cancel)).performClick()
        rule.onNodeWithText(text(ja, R.string.language_fallback_message)).assertDoesNotExist()
        rule.onNodeWithText(text(ja, R.string.choose_difficulty)).assertDoesNotExist()
        rule.onNodeWithText(text(ja, R.string.choose_game)).assertIsDisplayed()
    }

    @Test
    fun aSupportedLanguageOpensTheGameDirectly() {
        val pt = AppLanguage.PORTUGUESE
        openApp(pt)
        rule.onAllNodesWithContentDescription(text(pt, R.string.game_crossword))[0].performClick()
        rule.onNodeWithText(text(pt, R.string.language_fallback_message)).assertDoesNotExist()
        rule.onNodeWithText(text(pt, R.string.choose_difficulty)).assertIsDisplayed()
    }

    @Test
    fun aRestoredGameKeepsTheLanguageItWasStartedIn() {
        val ja = AppLanguage.JAPANESE
        val pt = AppLanguage.PORTUGUESE
        val saves = SaveStore(app).apply { clear() }
        val state = newCrossword(app, pt, Difficulty.EASY, Random(3))
        saves.save(SavedGame(GameId.CROSSWORD.key, Difficulty.EASY, AppJson.encodeToJsonElement(CrosswordState.serializer(), state), language = pt.name))
        repeat(50) { if (saves.load() == null) Thread.sleep(20) }
        openApp(ja, saves, offerResume = true)
        rule.onNodeWithText(text(ja, R.string.resume_continue)).performClick()
        waitForClueIn(pt)
        saves.clear()
    }
}
