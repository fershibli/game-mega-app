package io.github.fershibli.megagames

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.parking.LOT_MARGIN
import io.github.fershibli.megagames.games.parking.Parking
import io.github.fershibli.megagames.games.parking.ParkingState
import io.github.fershibli.megagames.games.parking.Vehicle
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The parking puzzle with a finger: drag the truck away, then the red car out. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class ParkingUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun dragTheTruckDownAndTheRedCarOut() {
        var outcome: GameOutcome? = null
        // Red car at the left of row 2, a truck standing in column 4 across rows 1-3.
        val s = ParkingState(listOf(Vehicle(true, 2, 2), Vehicle(false, 4, 3)), listOf(0, 1), par = 2)
        val host = GameHost(GameId.PARKING, Difficulty.EASY, AppJson.encodeToJsonElement(ParkingState.serializer(), s), null) { outcome = it }
        rule.setContent { MegaGamesTheme(Settings()) { GameContent(GameId.PARKING, host) } }
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("Moves: 0", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        val board = rule.onNodeWithTag("parking-board")
        fun center(col: Float, row: Float, w: Float): Offset {
            val cell = w / (Parking.SIZE + LOT_MARGIN * 2)
            return Offset((LOT_MARGIN + col + 0.5f) * cell, (LOT_MARGIN + row + 0.5f) * cell)
        }
        board.performTouchInput { swipe(center(4f, 2f, width.toFloat()), center(4f, 4.2f, width.toFloat()), 400) }
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Moves: 1", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        board.performTouchInput { swipe(center(0.5f, 2f, width.toFloat()), center(4.6f, 2f, width.toFloat()), 400) }
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("The red car is out!", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitUntil(10_000) { outcome != null }
        assertTrue(outcome!!.won)
        assertTrue(outcome!!.detail!!.contains("Out with moves: 2"))
    }
}
