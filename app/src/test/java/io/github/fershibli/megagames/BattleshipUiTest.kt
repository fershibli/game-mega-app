package io.github.fershibli.megagames

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Battleship like a person plays it: look at the fleet, start, fire, and the computer answers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class BattleshipUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun arrangeStartFireAndTheComputerFiresBack() {
        rule.setContent { MegaGamesTheme(Settings()) { GameContent(GameId.BATTLESHIP, GameHost(GameId.BATTLESHIP, Difficulty.MEDIUM, null, null) {}) } }
        rule.waitForIdle()
        rule.onNodeWithText("Here is your fleet", substring = true).assertExists()
        rule.onNodeWithTag("battleship-rearrange").performClick()
        rule.onNodeWithTag("battleship-ready").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Your turn", substring = true).assertExists()
        rule.onNodeWithTag("battleship-target").performTouchInput { click(topLeft + androidx.compose.ui.geometry.Offset(5f, 5f)) }
        rule.waitForIdle()
        rule.onNodeWithText("You:", substring = true).assertExists()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithText("The computer:", substring = true).assertExists()
    }
}
