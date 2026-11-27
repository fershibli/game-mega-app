package io.github.fershibli.megagames

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.Motion
import io.github.fershibli.megagames.core.Pace
import io.github.fershibli.megagames.core.PlaySetup
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.battleship.Battleship
import io.github.fershibli.megagames.games.battleship.BattleshipState
import io.github.fershibli.megagames.games.battleship.SeaGrid
import io.github.fershibli.megagames.games.battleship.Ship
import io.github.fershibli.megagames.games.battleship.Shot
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

/** Battleship like a person plays it: look at the fleet, start, fire, and the computer answers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class BattleshipUiTest {
    @get:Rule val rule = createComposeRule()

    private val store = SaveStore(RuntimeEnvironment.getApplication())

    /** Opens the game on [s] (arranging), against the computer or [together]. */
    private fun open(s: BattleshipState, together: Boolean = false) {
        val saved = AppJson.encodeToJsonElement(BattleshipState.serializer(), s)
        val host = GameHost(GameId.BATTLESHIP, Difficulty.MEDIUM, saved, store, setup = PlaySetup(together = together)) {}
        rule.setContent { MegaGamesTheme(Settings()) { GameContent(GameId.BATTLESHIP, host) } }
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("Your sea", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    /** The game as saved now. */
    private fun saved(): BattleshipState {
        rule.waitForIdle()
        store.flush()
        return AppJson.decodeFromJsonElement(BattleshipState.serializer(), store.load(GameId.BATTLESHIP)!!.state)
    }

    /** A new game against the computer, as [Battleship.newGame] makes it. */
    private fun solo() = Battleship.newGame(Difficulty.MEDIUM, Random(7), together = false)

    @Test
    fun arrangeAgainChangesYourOwnFleet() {
        val s = solo()
        open(s)
        rule.onNodeWithTag("battleship-rearrange").performClick()
        val after = saved()
        assertNotEquals("your fleet changed", s.fleets[0], after.fleets[0])
        assertEquals("the computer's fleet is left alone", s.fleets[1], after.fleets[1])
    }

    /**
     * A 10 × 10 sea being arranged: a ship of 3 at C3–E3, one of 2 at A8–B8, one of 4 along the
     * bottom at G10–J10 and one of 2 at G8–H8, right above it.
     */
    private val fleet = listOf(Ship(listOf(22, 23, 24)), Ship(listOf(70, 71)), Ship(listOf(96, 97, 98, 99)), Ship(listOf(76, 77)))
    private fun arranging(seat: Int = 0): BattleshipState {
        val other = listOf(Ship(listOf(0, 1)))
        return BattleshipState(10, if (seat == 0) listOf(fleet, other) else listOf(other, fleet), placing = 1)
    }

    private fun center(width: Int, cell: Int) = SeaGrid(width.toFloat(), 10).center(cell)

    @Test
    fun dragAShipToMoveIt() {
        open(arranging())
        // From the middle of D3 to the middle of E6: three rows down and one column right.
        rule.onNodeWithTag("battleship-own").performTouchInput { swipe(center(width, 23), center(width, 54), 600) }
        assertEquals(listOf(53, 54, 55), saved().fleets[0][0].cells)
        // Undo puts it back.
        rule.onNodeWithTag("battleship-undo").performClick()
        assertEquals(fleet, saved().fleets[0])
    }

    @Test
    fun tapAShipToTurnIt() {
        open(arranging())
        rule.onNodeWithTag("battleship-own").performTouchInput { click(center(width, 23)) }
        // Across C3–E3 becomes down D2–D4, around the square touched.
        assertEquals(listOf(13, 23, 33), saved().fleets[0][0].cells)
    }

    @Test
    fun aTurnWithoutRoomIsGentlyRefused() {
        open(arranging())
        // The ship along the bottom can only turn upwards, into the ship above it.
        rule.onNodeWithTag("battleship-own").performTouchInput { click(center(width, 97)) }
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("There is no room there", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(fleet, saved().fleets[0])
        // A move that works clears the message.
        rule.onNodeWithTag("battleship-own").performTouchInput { click(center(width, 23)) }
        rule.waitForIdle()
        rule.onNodeWithText("There is no room there", substring = true).assertDoesNotExist()
    }

    @Test
    fun takingTurnsPlayerTwoArrangesTheirOwnFleet() {
        open(arranging(seat = 1), together = true)
        rule.onNodeWithTag("battleship-own").performTouchInput { swipe(center(width, 71), center(width, 1), 600) }
        val after = saved()
        assertEquals(listOf(Ship(listOf(0, 1))), after.fleets[0])
        assertEquals(listOf(0, 1), after.fleets[1][1].cells)
        rule.onNodeWithTag("battleship-rearrange").performClick()
        assertNotEquals(after.fleets[1], saved().fleets[1])
        assertEquals(listOf(Ship(listOf(0, 1))), saved().fleets[0])
    }

    /** Under way against the computer, nobody has fired yet: its ships are at A1–B1 and A6–C6. */
    private fun playing() = BattleshipState(10, listOf(fleet, listOf(Ship(listOf(0, 1)), Ship(listOf(50, 51, 52)))), placing = 2)

    /** Taps square [cell] of the other sea, and lets the tap through (it takes a few frames). */
    private fun fireAt(cell: Int) {
        rule.onNodeWithTag("battleship-target").performTouchInput { click(center(width, cell)) }
        pass(150)
    }
    private fun shows(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    /** What the status line says now. */
    private fun status() = rule.onNodeWithTag("battleship-status", useUnmergedTree = true).fetchSemanticsNode().children
        .joinToString { it.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString() }

    @Test
    fun afterYourShotTheComputerWaitsAndNoShotIsTakenMeanwhile() {
        open(playing())
        rule.mainClock.autoAdvance = false
        // J10 is water.
        fireAt(99)
        assertEquals(listOf(99), saved().shots[0])
        assertTrue(status(), shows("You: water."))
        // The splash, then a moment to see it: the computer has not fired, nor even started aiming.
        pass(1_500)
        assertEquals(emptyList<Int>(), saved().shots[1])
        assertFalse(shows("aiming"))
        // A second shot meanwhile is not taken.
        fireAt(50)
        assertEquals(listOf(99), saved().shots[0])
        // Then it shows where it aims on your sea, and waits before firing.
        pass(800)
        assertTrue(shows("The computer is aiming"))
        assertEquals(emptyList<Int>(), saved().shots[1])
        pass(1_100)
        assertEquals(1, saved().shots[1].size)
        assertTrue(shows("The computer:"))
        // Its shot lands and stays to be read: it is not your turn yet, and a shot is not taken.
        assertFalse(shows("Your turn"))
        fireAt(50)
        assertEquals(listOf(99), saved().shots[0])
        pass(2_000)
        assertTrue(shows("Your turn"))
        // Now A6 is a hit, marked as one.
        fireAt(50)
        val after = saved()
        assertEquals(listOf(99, 50), after.shots[0])
        assertEquals(Shot.HIT, after.lastResult)
        assertEquals(50, after.lastCell)
        assertTrue(status(), shows("You: a hit!"))
    }

    @Test
    fun withoutAnimationsAndQuickTheTurnComesBackSooner() {
        val saved = AppJson.encodeToJsonElement(BattleshipState.serializer(), playing())
        val host = GameHost(GameId.BATTLESHIP, Difficulty.MEDIUM, saved, store) {}
        rule.setContent { MegaGamesTheme(Settings(motion = Motion.NONE, pace = Pace.QUICK)) { GameContent(GameId.BATTLESHIP, host) } }
        rule.waitUntil(10_000) { shows("Your sea") }
        rule.mainClock.autoAdvance = false
        fireAt(99)
        // No splash to wait for: just a short look at the result, at the computer's aim and at its shot.
        pass(1_300)
        assertEquals(1, saved().shots[1].size)
        pass(600)
        assertTrue(shows("Your turn"))
    }

    /** Lets [millis] of the test clock pass, a frame at a time, as it would on the phone. */
    private fun pass(millis: Long) {
        var left = millis
        while (left > 0) {
            rule.waitForIdle()
            rule.mainClock.advanceTimeByFrame()
            left -= 16
        }
        rule.waitForIdle()
    }

    @Test
    fun takingTurnsTheShotIsSeenBeforeThePhoneIsPassed() {
        open(playing(), together = true)
        rule.mainClock.autoAdvance = false
        fireAt(99)
        pass(1_500)
        // Player 1 still sees their shot land, with no cover yet.
        assertTrue(status(), shows("Player 1: water."))
        assertTrue(rule.onAllNodes(hasTestTag("handoff")).fetchSemanticsNodes().isEmpty())
        pass(1_500)
        assertTrue(rule.onAllNodes(hasTestTag("handoff")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun arrangeStartFireAndTheComputerFiresBack() {
        rule.setContent { MegaGamesTheme(Settings()) { GameContent(GameId.BATTLESHIP, GameHost(GameId.BATTLESHIP, Difficulty.MEDIUM, null, null) {}) } }
        // The game is set up off the main thread: wait for it.
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("Your sea")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("battleship-rearrange").performClick()
        rule.onNodeWithTag("battleship-ready").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Your turn", substring = true).assertExists()
        rule.onNodeWithTag("battleship-target").performTouchInput { click(topLeft + Offset(5f, 5f)) }
        rule.waitForIdle()
        rule.onNodeWithText("You:", substring = true).assertExists()
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("The computer:", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }
}
