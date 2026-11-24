package io.github.fershibli.megagames

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.core.PlaySetup
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.trail.Pending
import io.github.fershibli.megagames.games.trail.Story
import io.github.fershibli.megagames.games.trail.Trail
import io.github.fershibli.megagames.games.trail.TrailState
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The life trail like a person plays it: roll, read a story, decide at a crossroads. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class TrailUiTest {
    @get:Rule val rule = createComposeRule()

    private var outcome: GameOutcome? = null
    private var saved: TrailState? = null

    private fun show(state: TrailState?, setup: PlaySetup = PlaySetup(seats = 2)) {
        val host = GameHost(GameId.TRAIL, Difficulty.EASY, state?.let { AppJson.encodeToJsonElement(TrailState.serializer(), it) }, null, null, setup) { outcome = it }
        rule.setContent { MegaGamesTheme(Settings()) { GameContent(GameId.TRAIL, host) } }
        rule.waitForIdle()
    }

    @Test
    fun rollingMovesYourPawn() {
        show(null)
        rule.onNodeWithText("Your turn: roll the die.", substring = true).assertExists()
        rule.onNodeWithTag("trail-roll").assertIsEnabled().performClick()
        rule.waitForIdle()
        // The board reads out every pawn's square: yours left the start.
        val board = rule.onNodeWithTag("trail-board").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        val square = Regex("""You (\d+)""").find(board)!!.groupValues[1].toInt()
        assertTrue(board, square in 2..9)
    }

    @Test
    fun aStoryIsReadAndItsHeartsCount() {
        val story = Story.SONG
        val s = TrailState(2, listOf(2, 0), listOf(0, 0), die = 2, pending = Pending(0, story = story.ordinal))
        show(s)
        rule.onNodeWithText("Grandma taught you a song you still remember.").assertExists()
        rule.onNodeWithText("Hearts +3").assertExists()
        rule.onNodeWithTag("trail-ok").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("You ♥ 3", substring = true).assertExists()
    }

    @Test
    fun atACrossroadsYouChooseToStay() {
        val s = TrailState(2, listOf(4, 0), listOf(1, 0), die = 4, pending = Pending(0, choiceStage = 0))
        show(s)
        rule.onNodeWithText("The friends call you to play outside a little longer.").assertExists()
        rule.onNodeWithTag("trail-stay").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("You ♥ ${1 + Trail.STAY_HEARTS}", substring = true).assertExists()
    }

    @Test
    fun theGameEndsWithTheMostHearts() {
        val s = TrailState(2, listOf(Trail.FINISH, Trail.FINISH), listOf(9, 4), die = 1, arrived = listOf(0, 1))
        show(s)
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitUntil(5_000) { outcome != null }
        assertTrue(outcome!!.won)
        assertTrue(outcome!!.detail!!.contains("Most hearts: You."))
        assertEquals(null, saved)
    }
}
