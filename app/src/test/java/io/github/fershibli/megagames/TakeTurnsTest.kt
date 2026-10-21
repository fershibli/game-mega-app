package io.github.fershibli.megagames

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.ChoiceStore
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.core.Motion
import io.github.fershibli.megagames.core.Pace
import io.github.fershibli.megagames.core.PlaySetup
import io.github.fershibli.megagames.core.SavedGame
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.core.SettingsStore
import io.github.fershibli.megagames.core.TableSize
import io.github.fershibli.megagames.games.reversi.ReversiGame
import io.github.fershibli.megagames.ui.screens.DifficultyScreen
import io.github.fershibli.megagames.ui.screens.InGameActions
import io.github.fershibli.megagames.ui.screens.SettingsScreen
import io.github.fershibli.megagames.ui.screens.TableScreen
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import io.github.fershibli.megagames.ui.theme.wait
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** People taking turns on one phone, the table size, and the pace and motion settings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class TakeTurnsTest {
    @get:Rule val rule = createComposeRule()
    private val english = Settings(language = AppLanguage.ENGLISH)

    @Test
    fun theWheelOffersTakingTurnsAndExplainsIt() {
        var together = false
        rule.setContent {
            MegaGamesTheme(english) {
                DifficultyScreen(GameId.REVERSI, Difficulty.EASY, {}, {}, {}, onTogether = { together = true }, initialTogether = true)
            }
        }
        rule.onNodeWithText("How do you want to play?").assertIsDisplayed()
        rule.onNodeWithText("Nobody plays against the computer", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Confirm").performClick()
        assertTrue(together)
    }

    @Test
    fun withoutTakingTurnsTheTitleStaysTheDifficulty() {
        rule.setContent { MegaGamesTheme(english) { DifficultyScreen(GameId.SUDOKU, Difficulty.EASY, {}, {}, {}) } }
        rule.onNodeWithText("Choose the difficulty").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Take turns on this phone").fetchSemanticsNodes().size)
    }

    @Test
    fun reversiTakingTurnsPassesThePhoneAndTheComputerNeverPlays() {
        var outcome: GameOutcome? = null
        val host = GameHost(GameId.REVERSI, Difficulty.EASY, null, null, null, PlaySetup(together = true)) { outcome = it }
        rule.setContent { MegaGamesTheme(english) { ReversiGame(host) } }
        rule.waitForIdle()
        fun tap(row: Int, col: Int) = rule.onNodeWithTag("reversi-board").performTouchInput {
            val frame = width * 0.04f
            val cell = (width - frame * 2) / 8f
            click(Offset(frame + (col + 0.5f) * cell, frame + (row + 0.5f) * cell))
        }
        rule.onNodeWithText("Dark pieces to play", substring = true).assertIsDisplayed()
        tap(2, 3) // d3, a legal first move for dark
        rule.waitForIdle()
        rule.onNodeWithText("Pass to the next player").assertIsDisplayed()
        rule.onNodeWithText("Light pieces").assertIsDisplayed()
        rule.onNodeWithContentDescription("Ready, it is my turn").performClick()
        // The scene dims, the view switches, and after a short pause the next person can play.
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("handoff").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Light pieces to play", substring = true).assertIsDisplayed()
        // Light is a person too: the computer did not answer for it.
        tap(2, 2) // c3, a legal reply for light after d3
        rule.waitForIdle()
        rule.onNodeWithText("Dark pieces").assertIsDisplayed()
        assertEquals(null, outcome)
    }

    @Test
    fun tableScreenPreselectsAndConfirms() {
        val tables = listOf(TableSize(2), TableSize(4, R.string.reversi_dark))
        var picked: TableSize? = null
        rule.setContent { MegaGamesTheme(english) { TableScreen(GameId.SUDOKU, false, tables[1], { picked = it }, {}, {}, tables) } }
        rule.onNodeWithText("How many at the table?").assertIsDisplayed()
        rule.onNodeWithText("You and 3 computer players").assertIsDisplayed()
        rule.onNodeWithText("Confirm").performClick()
        assertEquals(tables[1], picked)
        rule.onNodeWithText("You and 1 computer player").performClick()
        rule.onNodeWithText("Confirm").performClick()
        assertEquals(tables[0], picked)
    }

    @Test
    fun pastimesHaveNoNewGameAndLeaveWithoutAsking() {
        var left = false
        rule.setContent {
            MegaGamesTheme(english) { SettingsScreen(english, {}, {}, InGameActions({}, null) { left = true }) }
        }
        assertEquals(0, rule.onAllNodesWithText("New game").fetchSemanticsNodes().size)
        rule.onNodeWithText("Main menu").performClick()
        assertTrue(left)
    }

    @Test
    fun setupIsRememberedAndSaved() {
        val store = ChoiceStore(RuntimeEnvironment.getApplication())
        assertEquals(false, store.lastTogether(GameId.REVERSI))
        store.rememberSetup(GameId.REVERSI, PlaySetup(together = true, seats = 4))
        assertEquals(true, store.lastTogether(GameId.REVERSI))
        assertEquals(4, store.lastSeats(GameId.REVERSI))
        val saved = SavedGame("reversi", Difficulty.EASY, JsonObject(emptyMap()), setup = PlaySetup(true, 4))
        val back = AppJson.decodeFromString(SavedGame.serializer(), AppJson.encodeToString(SavedGame.serializer(), saved))
        assertEquals(PlaySetup(true, 4), back.setup)
        // Saves written before this version have no setup and still load.
        assertEquals(PlaySetup(), AppJson.decodeFromString(SavedGame.serializer(), """{"game":"sudoku","difficulty":"EASY","state":{}}""").setup)
    }

    @Test
    fun paceAndMotionAreRememberedAndScaleTheWaits() = runTest {
        val app = RuntimeEnvironment.getApplication()
        SettingsStore(app).save(Settings(pace = Pace.CALM, motion = Motion.NONE))
        assertEquals(Pace.CALM, SettingsStore(app).load().pace)
        assertEquals(Motion.NONE, SettingsStore(app).load().motion)
        val start = currentTime
        Pace.CALM.wait(500)
        assertEquals(1000, currentTime - start)
        Pace.QUICK.wait(500)
        assertEquals(1300, currentTime - start)
    }
}
