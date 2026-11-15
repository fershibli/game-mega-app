package io.github.fershibli.megagames

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.SavedGame
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.core.SettingsStore
import io.github.fershibli.megagames.games.reversi.Reversi
import io.github.fershibli.megagames.games.reversi.ReversiState
import io.github.fershibli.megagames.ui.MegaGamesApp
import io.github.fershibli.megagames.ui.screens.SetupScreen
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The questions asked the first time the app opens. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class SetupTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    private fun choose(answer: String) {
        rule.onNodeWithText(answer).performScrollTo().performClick()
        rule.onNodeWithText("Continue").performClick()
    }

    @Test
    fun everyAnswerChangesItsSettingAtOnce() {
        var settings by mutableStateOf(Settings(language = AppLanguage.ENGLISH))
        var done = false
        rule.setContent { MegaGamesTheme(settings) { SetupScreen(settings, { settings = it }, { done = true }) } }
        rule.onNodeWithText("Welcome to Mili Games!").assertIsDisplayed()
        rule.onNodeWithText("Start").performClick()
        rule.onNodeWithText("1 / 6").assertIsDisplayed()
        choose("Very big")
        assertEquals(1.4f, settings.fontScale, 0.001f)
        choose("Left hand")
        assertTrue(settings.leftHanded)
        choose("One single list")
        assertFalse(settings.showCategories)
        choose("No, always the same order")
        assertFalse(settings.recentFirst)
        choose("Dark")
        assertTrue(settings.darkMode)
        rule.onNodeWithText("6 / 6").assertIsDisplayed()
        choose("Hard")
        assertEquals(Difficulty.HARD, settings.defaultDifficulty)
        rule.onNodeWithText("All set!").assertIsDisplayed()
        assertFalse(done)
        rule.onNodeWithText("Choose a game").performClick()
        assertTrue(done)
    }

    @Test
    fun backGoesToTheQuestionBeforeAndKeepsTheAnswers() {
        var settings by mutableStateOf(Settings(language = AppLanguage.ENGLISH))
        rule.setContent { MegaGamesTheme(settings) { SetupScreen(settings, { settings = it }, {}) } }
        rule.onNodeWithText("Start").performClick()
        choose("Big")
        rule.onNodeWithText("Which hand do you touch the screen with?").assertIsDisplayed()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Which text size is easiest to read?").assertIsDisplayed()
        assertEquals(1.2f, settings.fontScale, 0.001f)
    }

    @Test
    fun theFirstStartAsksAndTheNextOneGoesStraightToTheGames() {
        SaveStore(app).clear()
        val store = SettingsStore(app).apply { save(Settings(language = AppLanguage.ENGLISH)); setupDone = false }
        rule.setContent { MegaGamesApp(store, SaveStore(app), offerResume = true, firstRun = true) }
        rule.onNodeWithText("Welcome to Mili Games!").assertIsDisplayed()
        // Skipping answers nothing and keeps the settings as they were.
        rule.onNodeWithText("Skip").performClick()
        rule.onNodeWithText("Choose your game").assertIsDisplayed()
        assertTrue(SettingsStore(app).setupDone)
        assertEquals(Settings(language = AppLanguage.ENGLISH), SettingsStore(app).load())
        // The settings open the questions again.
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Initial setup").performScrollTo().performClick()
        rule.onNodeWithText("Welcome to Mili Games!").assertIsDisplayed()
    }

    @Test
    fun onceAnsweredTheAppOpensOnTheGames() {
        SaveStore(app).clear()
        val store = SettingsStore(app).apply { save(Settings(language = AppLanguage.ENGLISH)); setupDone = true }
        rule.setContent { MegaGamesApp(store, SaveStore(app), offerResume = true, firstRun = true) }
        rule.onNodeWithText("Choose your game").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Welcome to Mili Games!").fetchSemanticsNodes().size)
    }

    @Test
    fun aGameWaitingToBeContinuedComesBeforeTheQuestions() {
        val saves = SaveStore(app)
        val store = SettingsStore(app).apply { save(Settings(language = AppLanguage.ENGLISH)); setupDone = false }
        saves.save(SavedGame(GameId.REVERSI.key, Difficulty.EASY, AppJson.encodeToJsonElement(ReversiState.serializer(), ReversiState(Reversi.initial().toList()))))
        repeat(50) { if (saves.load() == null) Thread.sleep(20) }
        rule.setContent { MegaGamesApp(store, saves, offerResume = true, firstRun = true) }
        rule.onNodeWithText("Welcome back!").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Welcome to Mili Games!").fetchSemanticsNodes().size)
        assertFalse(SettingsStore(app).setupDone)
        saves.clear()
    }
}
