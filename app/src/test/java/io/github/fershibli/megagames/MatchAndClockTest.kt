package io.github.fershibli.megagames

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.PlaySetup
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.core.offersMatches
import io.github.fershibli.megagames.core.offersTimeLimit
import io.github.fershibli.megagames.ui.screens.DifficultyScreen
import io.github.fershibli.megagames.ui.screens.GameScreen
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Matches of 3 or 5 against the computer, and games against the clock. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class MatchAndClockTest {
    @get:Rule val rule = createComposeRule()
    private val english = Settings(language = AppLanguage.ENGLISH)

    @Test
    fun matchesAreForOpponentsAndTheClockForGamesPlayedAlone() {
        assertTrue(GameId.BACKGAMMON.offersMatches)
        assertFalse(GameId.BACKGAMMON.offersTimeLimit)
        assertTrue(GameId.SUDOKU.offersTimeLimit)
        assertFalse(GameId.SUDOKU.offersMatches)
        assertFalse(GameId.PET.offersTimeLimit)
    }

    @Test
    fun aMatchEndsWhenOneSideHasWonMostOfItsGames() {
        val three = PlaySetup(bestOf = 3)
        assertEquals(2, three.toWin)
        assertFalse(three.copy(wins = 1, losses = 1).matchOver)
        assertTrue(three.copy(wins = 2, losses = 1).matchOver)
        assertTrue(PlaySetup(bestOf = 5, losses = 3).matchOver)
        assertTrue(PlaySetup().copy(wins = 1).matchOver)
    }

    @Test
    fun theLevelScreenOffersTheMatchLengthAndHearsIt() {
        var heard = 0 to 0
        rule.setContent {
            MegaGamesTheme(english) {
                DifficultyScreen(GameId.BACKGAMMON, Difficulty.EASY, {}, {}, {}, initialBestOf = 1, onOptions = { b, m -> heard = b to m })
            }
        }
        rule.onNodeWithText("Single game").assertExists()
        rule.onNodeWithText("Best of 3").performClick()
        rule.onNodeWithText("Confirm").performClick()
        assertEquals(3 to 0, heard)
    }

    @Test
    fun theLevelScreenOffersATimeLimitForGamesPlayedAlone() {
        var heard = 0 to 0
        rule.setContent {
            MegaGamesTheme(english) {
                DifficultyScreen(GameId.SUDOKU, Difficulty.EASY, {}, {}, {}, initialMinutes = 0, onOptions = { b, m -> heard = b to m })
            }
        }
        rule.onNodeWithText("No limit").assertExists()
        rule.onNodeWithText("10 min").performClick()
        rule.onNodeWithText("Confirm").performClick()
        assertEquals(1 to 10, heard)
    }

    @Test
    fun whenTheTimeRunsOutTheGameIsLost() {
        val store = SaveStore(ApplicationProvider.getApplicationContext()).apply { clear() }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MegaGamesTheme(english) {
                GameScreen(
                    GameId.SUDOKU, Difficulty.EASY, null, store, english, {}, askResume = false, onResumeAnswered = {}, onExit = {},
                    setup = PlaySetup(minutes = 5),
                )
            }
        }
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("⏱", substring = true).assertExists()
        repeat(320) { rule.mainClock.advanceTimeBy(1_000) }
        rule.onNodeWithText("Time is up!", substring = true).assertExists()
        store.clear()
    }
}
