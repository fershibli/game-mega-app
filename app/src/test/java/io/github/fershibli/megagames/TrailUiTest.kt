package io.github.fershibli.megagames

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getBoundsInRoot
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

/**
 * The life trail like a person plays it: roll, read a story, decide at a crossroads. Every card
 * opens over the board and waits for a tap, the computer's too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class TrailUiTest {
    @get:Rule val rule = createComposeRule()

    private var outcome: GameOutcome? = null
    private var saved: TrailState? = null

    private fun show(state: TrailState?, setup: PlaySetup = PlaySetup(seats = 2), settings: Settings = Settings()) {
        val host = GameHost(GameId.TRAIL, Difficulty.EASY, state?.let { AppJson.encodeToJsonElement(TrailState.serializer(), it) }, null, null, setup) { outcome = it }
        rule.setContent { MegaGamesTheme(settings) { GameContent(GameId.TRAIL, host) } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("trail-board").fetchSemanticsNodes().isNotEmpty() }
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
        // The two choices are the answer: no Go on.
        rule.onNodeWithTag("trail-ok").assertDoesNotExist()
        rule.onNodeWithTag("trail-go").assertIsDisplayed()
        rule.onNodeWithTag("trail-stay").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("You ♥ ${1 + Trail.STAY_HEARTS}", substring = true).assertExists()
    }

    @Test
    fun aHeartCardWaitsForGoOn() {
        val s = TrailState(2, listOf(1, 0), listOf(1, 0), die = 1, lastSeat = 0, lastHearts = 1)
        show(s)
        rule.onNodeWithTag("trail-card").assertIsDisplayed()
        rule.onNodeWithText("Hearts +1").assertExists()
        rule.mainClock.advanceTimeBy(20_000)
        rule.waitForIdle()
        // Nothing goes on by itself: the card is still there and the turn has not passed.
        rule.onNodeWithText("You rolled 1.", substring = true).assertExists()
        rule.onNodeWithTag("trail-ok").assertIsDisplayed().performClick()
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForIdle()
        rule.onNodeWithText("You rolled 1.", substring = true).assertDoesNotExist()
    }

    @Test
    fun theComputersCrossroadsShowsItsChoiceAndWaitsForGoOn() {
        val s = TrailState(2, listOf(0, 4), listOf(0, 0), turn = 1, die = 4, lastSeat = 1, pending = Pending(1, choiceStage = 0))
        show(s)
        rule.onNodeWithText("The friends call you to play outside a little longer.").assertExists()
        // You cannot choose for the computer; you see what it chose.
        rule.onNodeWithTag("trail-stay").assertDoesNotExist()
        rule.onNodeWithTag("trail-chose", useUnmergedTree = true).assertIsDisplayed()
        val chose = rule.onNode(hasText("Blue chose: ", substring = true), useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        val stayed = chose.contains("Stay")
        rule.mainClock.advanceTimeBy(20_000)
        rule.waitForIdle()
        rule.onNodeWithTag("trail-chose", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Blue ♥ 0", substring = true).assertExists()
        rule.onNodeWithTag("trail-ok").assertIsDisplayed().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("trail-chose", useUnmergedTree = true).assertDoesNotExist()
        if (stayed) {
            rule.onNodeWithText("Blue ♥ ${Trail.STAY_HEARTS}", substring = true).assertExists()
        } else {
            val board = rule.onNodeWithTag("trail-board").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
            assertTrue(board, board.contains("Blue ${4 + Trail.GO_SQUARES + 1}"))
        }
    }

    @Test
    fun theComputersStoryWaitsForGoOn() {
        val story = Story.FRIEND
        val s = TrailState(2, listOf(0, 10), listOf(0, 0), turn = 1, die = 3, lastSeat = 1, pending = Pending(1, story = story.ordinal))
        show(s)
        rule.onNodeWithText("You made a friend for life.").assertExists()
        rule.mainClock.advanceTimeBy(20_000)
        rule.waitForIdle()
        rule.onNodeWithText("Blue ♥ 0", substring = true).assertExists()
        rule.onNodeWithTag("trail-ok").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Blue ♥ ${story.hearts}", substring = true).assertExists()
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

    private fun cardIsInTopHalf(): Boolean {
        val card = rule.onNodeWithTag("trail-card").getBoundsInRoot()
        val board = rule.onNodeWithTag("trail-board").getBoundsInRoot()
        return card.top.value < (board.top.value + board.bottom.value) / 2
    }

    @Test
    fun theCardMovesToTheTopWhenItWouldCoverAPawnInTheLowerBoard() {
        // Square 2 is in the lowest landscape: the crossroads card at the bottom would hide the pawn.
        val s = TrailState(2, listOf(2, 0), listOf(1, 0), die = 2, lastSeat = 0, pending = Pending(0, choiceStage = 0))
        show(s)
        rule.onNodeWithTag("trail-card").assertIsDisplayed()
        assertTrue(cardIsInTopHalf())
    }

    @Test
    fun theCardStaysAtTheBottomWhenThePawnIsHigherUp() {
        val s = TrailState(2, listOf(40, 0), listOf(1, 0), die = 3, lastSeat = 0, pending = Pending(0, choiceStage = 0))
        show(s)
        rule.onNodeWithTag("trail-card").assertIsDisplayed()
        assertTrue(!cardIsInTopHalf())
    }

    @Test
    fun noScrollbarShowsWhenTheCardsTextFits() {
        val s = TrailState(2, listOf(40, 0), listOf(1, 0), die = 3, lastSeat = 0, pending = Pending(0, story = Story.SONG.ordinal))
        show(s)
        rule.onNodeWithTag("trail-card").assertIsDisplayed()
        rule.onNodeWithTag("trail-scrollbar").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h300dp-xhdpi")
    fun aPixelScrollbarShowsWhenTheCardsTextScrolls() {
        val s = TrailState(2, listOf(2, 0), listOf(1, 0), die = 2, lastSeat = 0, pending = Pending(0, choiceStage = 0))
        show(s, settings = Settings(fontScale = Settings.SCALE_MAX))
        rule.onNodeWithTag("trail-card").assertIsDisplayed()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("trail-scrollbar").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("trail-scrollbar").assertIsDisplayed()
        // The buttons stay in sight while the words scroll.
        rule.onNodeWithTag("trail-stay").assertIsDisplayed()
    }
}
