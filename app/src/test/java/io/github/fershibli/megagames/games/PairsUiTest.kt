package io.github.fershibli.megagames.games

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.pairs.Pairs
import io.github.fershibli.megagames.games.pairs.PairsState
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Starts, steers and pauses the color drops with the big buttons, stepping the test clock. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class PairsUiTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    private fun saved(store: SaveStore, until: (PairsState) -> Boolean): PairsState? {
        var s: PairsState? = null
        for (attempt in 0 until 100) {
            Thread.sleep(20)
            s = store.load()?.let { AppJson.decodeFromJsonElement(PairsState.serializer(), it.state) }
            if (s != null && until(s)) break
        }
        return s
    }

    /** Taps like a finger: the test clock runs during the tap, then stops again. */
    private fun tap(node: SemanticsNodeInteraction) {
        rule.mainClock.autoAdvance = true
        node.performClick()
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    private fun advance(ms: Long) {
        rule.mainClock.advanceTimeBy(ms)
        rule.waitForIdle()
    }

    @Test
    fun waitsForStartFallsSlowlyMovesAndPauses() {
        val store = SaveStore(app).apply { clear() }
        val start = Pairs.newGame(Difficulty.MEDIUM, 21)
        val host = GameHost(GameId.PAIRS, Difficulty.MEDIUM, AppJson.encodeToJsonElement(PairsState.serializer(), start), store) {}
        rule.mainClock.autoAdvance = false
        rule.setContent { MegaGamesTheme(Settings()) { GameContent(GameId.PAIRS, host) } }
        advance(100)

        // Nothing moves before Start.
        advance(5_000)
        tap(rule.onNodeWithContentDescription("Move left"))
        advance(100)
        assertEquals(0, saved(store) { true }?.steps ?: 0)

        tap(rule.onAllNodesWithText("Start").onFirst())
        advance(100)
        rule.onNodeWithText("Pause").assertExists()
        val fall = Pairs.fallMillis(Difficulty.MEDIUM)
        advance(fall * 3)
        val fallen = saved(store) { it.steps >= 3 }!!
        assertEquals(3, fallen.steps)
        assertEquals(Pairs.SPAWN_ROW + 3, fallen.row)

        tap(rule.onNodeWithContentDescription("Move left"))
        advance(50)
        assertEquals(Pairs.SPAWN_COL - 1, saved(store) { it.col == Pairs.SPAWN_COL - 1 }!!.col)

        // Pause freezes everything.
        tap(rule.onNodeWithText("Pause"))
        advance(100)
        val paused = saved(store) { true }!!
        advance(fall * 5)
        tap(rule.onNodeWithContentDescription("Move right"))
        advance(100)
        assertEquals(paused, saved(store) { true })
        tap(rule.onAllNodesWithText("Continue").onFirst())
        advance(fall + 50)
        assertEquals(paused.steps + 1, saved(store) { it.steps > paused.steps }!!.steps)
        store.clear()
    }
}
