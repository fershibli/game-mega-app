package io.github.fershibli.megagames.games

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.snake.Snake
import io.github.fershibli.megagames.games.snake.SnakeState
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Steers the snake with the arrow cross and pauses it, stepping the test clock. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class SnakeUiTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    private fun saved(store: SaveStore, until: (SnakeState) -> Boolean): SnakeState? {
        var s: SnakeState? = null
        for (attempt in 0 until 100) {
            Thread.sleep(20)
            s = store.load()?.let { AppJson.decodeFromJsonElement(SnakeState.serializer(), it.state) }
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
    fun arrowsStartAndSteerTheSnakeAndPauseStopsIt() {
        val store = SaveStore(app).apply { clear() }
        // Snake on an 11x11 board, head at row 5 column 5 going right; the fruit two cells below the head.
        val start = SnakeState(Difficulty.EASY, 11, listOf(60, 59, 58), dir = Snake.RIGHT, fruit = 82, seed = 4)
        val host = GameHost(GameId.SNAKE, Difficulty.EASY, AppJson.encodeToJsonElement(SnakeState.serializer(), start), store) {}
        rule.mainClock.autoAdvance = false
        rule.setContent { MegaGamesTheme(Settings()) { GameContent(GameId.SNAKE, host) } }
        advance(100)

        // The down arrow both turns the snake and starts the game.
        tap(rule.onNodeWithContentDescription("Down"))
        val step = Snake.stepMillis(Difficulty.EASY)
        advance(step * 2 + 50)
        val ate = saved(store) { it.steps >= 2 }!!
        assertEquals(2, ate.steps)
        assertEquals(82, ate.body.first())
        assertEquals(4, ate.length) // it ate the fruit
        assertTrue(ate.fruit != 82)

        tap(rule.onNodeWithText("Pause"))
        advance(100)
        val paused = saved(store) { true }!!
        advance(step * 6)
        assertEquals(paused, saved(store) { true })
        store.clear()
    }
}
