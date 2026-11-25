package io.github.fershibli.megagames

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** The pinball table opens waiting for a serve, and a served ball rolls until it is lost. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class PinballUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun serveABallAndLoseItWithoutTouchingTheFlippers() {
        rule.setContent { MegaGamesTheme(Settings()) { GameContent(GameId.PINBALL, GameHost(GameId.PINBALL, Difficulty.HARD, null, null) {}) } }
        // The game is set up off the main thread: wait for it.
        rule.waitUntil(10_000) { rule.onAllNodesWithTextCount("Balls left: 3") > 0 }
        rule.onNodeWithTag("pinball-serve").performClick()
        rule.waitForIdle()
        // The ball waits on the plunger until it is launched.
        rule.onNodeWithTag("pinball-launch").performClick()
        rule.waitForIdle()
        // Without flippers the ball always goes down within two minutes of play.
        rule.mainClock.autoAdvance = false
        var ms = 0L
        while (ms < 180_000L) {
            rule.mainClock.advanceTimeBy(1_000)
            ms += 1_000
            if (rule.onAllNodesWithTextCount("Balls left: 2") > 0) break
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.onNodeWithText("The ball went down", substring = true).assertExists()
        rule.onNodeWithTag("pinball-serve").assertExists()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text, substring = true)).fetchSemanticsNodes().size
}
