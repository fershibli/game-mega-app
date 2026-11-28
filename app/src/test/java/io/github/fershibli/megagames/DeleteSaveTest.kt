package io.github.fershibli.megagames

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
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
import io.github.fershibli.megagames.games.sudoku.SudokuState
import io.github.fershibli.megagames.games.sudoku.newSudoku
import io.github.fershibli.megagames.ui.MegaGamesApp
import io.github.fershibli.megagames.ui.screens.DELETE_SAVE_MILLIS
import io.github.fershibli.megagames.ui.screens.GameScreen
import io.github.fershibli.megagames.ui.screens.InGameActions
import io.github.fershibli.megagames.ui.screens.SettingsScreen
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The red "Delete the saved game of ..." button in the settings of a game, and its three steps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class DeleteSaveTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val english = Settings(language = AppLanguage.ENGLISH)
    private val button = "Delete the saved game of Reversi"

    private var deleted = 0

    private fun showSettings(inGame: InGameActions?) {
        rule.setContent { MegaGamesTheme(english) { SettingsScreen(english, {}, {}, inGame) } }
    }

    private fun game(withSave: Boolean) = InGameActions(
        onResume = {}, onNewGame = {}, onMainMenu = {},
        onDeleteSave = if (withSave) ({ deleted++ }) else null, deleteSaveOf = "Reversi",
    )

    private fun openFlow() {
        rule.onNodeWithTag("delete-save").performScrollTo().performClick()
        rule.onNodeWithText("Delete the saved game?").assertIsDisplayed()
    }

    private fun understood() {
        rule.onNodeWithText("I understand").performClick()
        rule.onNodeWithText("Are you sure?").assertIsDisplayed()
    }

    /** Confirms and leaves the clock in our hands: the bar fills only as far as the test lets time pass. */
    private fun confirm() {
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Yes, delete it").performClick()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("Deleting…").assertIsDisplayed()
    }

    private fun stop() {
        rule.onNodeWithText("Stop").performClick()
        rule.mainClock.advanceTimeBy(100)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
    }

    @Test
    fun theButtonIsNotInTheSettingsOfTheMainMenu() {
        showSettings(null)
        rule.onAllNodesWithTag("delete-save").assertCountEquals(0)
    }

    @Test
    fun aGameWithNothingSavedHasNoButton() {
        showSettings(game(withSave = false))
        rule.onAllNodesWithTag("delete-save").assertCountEquals(0)
    }

    @Test
    fun aGameWithASaveShowsItAtTheEndOfTheSettings() {
        showSettings(game(withSave = true))
        rule.onNodeWithText(button).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cancellingAtAnyStepDeletesNothing() {
        showSettings(game(withSave = true))
        // Step 1: "Cancel".
        openFlow()
        rule.onNodeWithText("Cancel").performClick()
        rule.onAllNodesWithText("Delete the saved game?").assertCountEquals(0)
        // Step 2: "No, cancel".
        openFlow(); understood()
        rule.onNodeWithText("No, cancel").performClick()
        rule.onAllNodesWithText("Are you sure?").assertCountEquals(0)
        // Step 3: "Stop", at once and after a while.
        openFlow(); understood(); confirm()
        stop()
        rule.onAllNodesWithText("Deleting…").assertCountEquals(0)
        openFlow(); understood(); confirm()
        rule.mainClock.advanceTimeBy(DELETE_SAVE_MILLIS - 500)
        stop()
        rule.mainClock.advanceTimeBy(DELETE_SAVE_MILLIS * 2)
        rule.waitForIdle()
        rule.onAllNodesWithText("Deleting…").assertCountEquals(0)
        assertEquals(0, deleted)
    }

    @Test
    fun theBarFillsInFourSecondsAndAnnouncesItsProgress() {
        showSettings(game(withSave = true))
        openFlow(); understood(); confirm()
        rule.onNodeWithTag("delete-save-bar").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(DELETE_SAVE_MILLIS / 2)
        // A bit past half: the 100 ms that showed the dialog count too.
        rule.onNode(SemanticsMatcher("about half full") { (it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.current ?: 0f) in 0.45f..0.6f }, useUnmergedTree = true).assertExists()
        rule.mainClock.advanceTimeBy(DELETE_SAVE_MILLIS / 2 - 300)
        assertEquals(0, deleted)
        rule.mainClock.advanceTimeBy(600)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(1, deleted)
        rule.onAllNodesWithText("Deleting…").assertCountEquals(0)
    }

    private fun gameScreen(game: GameId, saves: SaveStore) {
        rule.setContent {
            MegaGamesTheme(english) {
                GameScreen(
                    game = game, difficulty = Difficulty.EASY, saved = null, saveStore = saves, settings = english,
                    onSettingsChange = {}, askResume = false, onResumeAnswered = {}, onExit = {},
                )
            }
        }
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription("Settings").onFirst().performClick()
        rule.waitForIdle()
    }

    @Test
    fun insideAGameTheButtonFollowsWhetherSomethingIsSaved() {
        // A game with a save shows it, named after the game...
        val saves = SaveStore(app).apply { clear() }
        val sudoku = newSudoku(Difficulty.EASY, Random(5))
        saves.save(SavedGame(GameId.SUDOKU.key, Difficulty.EASY, AppJson.encodeToJsonElement(SudokuState.serializer(), sudoku)))
        saves.flush()
        gameScreen(GameId.SUDOKU, saves)
        rule.onNodeWithTag("delete-save").performScrollTo().assertIsDisplayed()
        saves.clear()
    }

    @Test
    fun aPastimeKeepsItsWorldAndHasNoButton() {
        gameScreen(GameId.PET, SaveStore(app).apply { clear() })
        rule.onAllNodesWithTag("delete-save").assertCountEquals(0)
    }

    @Test
    fun finishingDeletesOnlyThatGamesSaveAndGoesToTheMenu() {
        val saves = SaveStore(app).apply { clear() }
        val reversi = ReversiState(Reversi.initial().toList())
        saves.save(SavedGame(GameId.REVERSI.key, Difficulty.HARD, AppJson.encodeToJsonElement(ReversiState.serializer(), reversi)))
        val sudoku = newSudoku(Difficulty.EASY, Random(5))
        saves.save(SavedGame(GameId.SUDOKU.key, Difficulty.EASY, AppJson.encodeToJsonElement(SudokuState.serializer(), sudoku)))
        saves.flush()
        val store = SettingsStore(app).apply { save(Settings(language = AppLanguage.ENGLISH, openLastGame = true, darkMode = true)) }
        val before = store.load()
        rule.setContent { MegaGamesApp(SettingsStore(app), saves, offerResume = true) }
        // The game saved last (sudoku) is the one reopened.
        rule.onNodeWithText("Welcome back!").assertIsDisplayed()
        rule.onNodeWithText("Continue playing").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("sudoku-board").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithTag("delete-save").performScrollTo().performClick()
        understood(); confirm()
        // Interrupting keeps everything.
        rule.mainClock.advanceTimeBy(1_500)
        stop()
        assertNotNull(saves.load(GameId.SUDOKU))
        assertNotNull(saves.load(GameId.REVERSI))
        // Doing it again, to the end.
        rule.onNodeWithTag("delete-save").performScrollTo().performClick()
        understood(); confirm()
        rule.mainClock.advanceTimeBy(DELETE_SAVE_MILLIS + 300)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.onNodeWithText("Choose your game").assertIsDisplayed()
        assertNull(saves.load(GameId.SUDOKU))
        assertNotNull(saves.load(GameId.REVERSI))
        assertEquals(before, SettingsStore(app).load())
        saves.clear()
        assertNull(saves.load())
    }
}
