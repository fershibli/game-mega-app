package io.github.fershibli.megagames

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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
import io.github.fershibli.megagames.ui.screens.SettingsScreen
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** "Open the last game on start": off by default; saving games works the same either way. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class OpenLastGameTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    private fun savedReversi(): SaveStore {
        val saves = SaveStore(app).apply { clear() }
        val state = ReversiState(Reversi.initial().toList())
        saves.save(SavedGame(GameId.REVERSI.key, Difficulty.HARD, AppJson.encodeToJsonElement(ReversiState.serializer(), state)))
        saves.flush()
        assertNotNull(saves.load(GameId.REVERSI))
        return saves
    }

    @Test
    fun theOptionStartsOff() {
        assertFalse(Settings().openLastGame)
        assertFalse(SettingsStore(app).load().openLastGame)
    }

    @Test
    fun settingsSavedBeforeTheOptionExistedLoadWithItOff() {
        // What an older version wrote: the other keys, but no "openLastGame".
        app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit()
            .putString("language", AppLanguage.ENGLISH.name)
            .putBoolean("recentFirst", false)
            .putBoolean("showCategories", false)
            .commit()
        val loaded = SettingsStore(app).load()
        assertFalse(loaded.openLastGame)
        assertFalse(loaded.recentFirst)
    }

    @Test
    fun theOptionIsRemembered() {
        SettingsStore(app).save(Settings(openLastGame = true))
        assertTrue(SettingsStore(app).load().openLastGame)
        SettingsStore(app).save(Settings(openLastGame = false))
        assertFalse(SettingsStore(app).load().openLastGame)
    }

    @Test
    fun theSettingsScreenTurnsItOnAndOff() {
        var settings by mutableStateOf(Settings(language = AppLanguage.ENGLISH))
        rule.setContent { MegaGamesTheme(settings) { SettingsScreen(settings, { settings = it }, {}, null) } }
        rule.onNodeWithText("Open the last game on start").performScrollTo().assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertTrue(settings.openLastGame)
        rule.onNodeWithText("Open the last game on start").performClick()
        rule.waitForIdle()
        assertFalse(settings.openLastGame)
    }

    @Test
    fun withTheOptionOffAColdStartShowsTheMenuAndKeepsTheSave() {
        val saves = savedReversi()
        SettingsStore(app).save(Settings(language = AppLanguage.ENGLISH))
        rule.setContent { MegaGamesApp(SettingsStore(app), saves, offerResume = true) }
        rule.onNodeWithText("Choose your game").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Welcome back!").fetchSemanticsNodes().size)
        assertNotNull(saves.load(GameId.REVERSI))
        // The game is still marked and opening it from the menu continues the saved match.
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasContentDescription("Reversi, game in progress"))
        rule.onAllNodesWithContentDescription("Reversi, game in progress")[0].performClick()
        rule.onNodeWithText("You have a game of Reversi in progress", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Continue playing").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Your turn", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(Difficulty.HARD, saves.load(GameId.REVERSI)?.difficulty)
        saves.clear()
    }

    @Test
    fun withTheOptionOnAColdStartAsksToContinueAsBefore() {
        val saves = savedReversi()
        SettingsStore(app).save(Settings(language = AppLanguage.ENGLISH, openLastGame = true))
        rule.setContent { MegaGamesApp(SettingsStore(app), saves, offerResume = true) }
        rule.onNodeWithText("Welcome back!").assertIsDisplayed()
        rule.onNodeWithText("Continue playing").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Your turn", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(Difficulty.HARD, saves.load(GameId.REVERSI)?.difficulty)
        saves.clear()
    }

    @Test
    fun withTheOptionOnTheMainMenuAnswerKeepsTheSave() {
        val saves = savedReversi()
        SettingsStore(app).save(Settings(language = AppLanguage.ENGLISH, openLastGame = true))
        rule.setContent { MegaGamesApp(SettingsStore(app), saves, offerResume = true) }
        rule.onNodeWithText("Welcome back!").assertIsDisplayed()
        rule.onNodeWithText("Main menu").performClick()
        rule.onNodeWithText("Choose your game").assertIsDisplayed()
        assertNotNull(saves.load(GameId.REVERSI))
        saves.clear()
    }

    @Test
    fun aStartThatIsNotColdNeverReopens() {
        val saves = savedReversi()
        SettingsStore(app).save(Settings(language = AppLanguage.ENGLISH, openLastGame = true))
        rule.setContent { MegaGamesApp(SettingsStore(app), saves, offerResume = false) }
        rule.onNodeWithText("Choose your game").assertIsDisplayed()
        saves.clear()
    }
}
