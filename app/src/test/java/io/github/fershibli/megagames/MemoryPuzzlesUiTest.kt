package io.github.fershibli.megagames

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.core.WorldStore
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.memory.Memory
import io.github.fershibli.megagames.games.memory.MemoryRecords
import io.github.fershibli.megagames.games.memory.MemoryState
import io.github.fershibli.megagames.games.pegs.Pegs
import io.github.fershibli.megagames.games.pegs.PegsGeometry
import io.github.fershibli.megagames.games.pegs.PegsState
import io.github.fershibli.megagames.games.sequence.SequencePhase
import io.github.fershibli.megagames.games.sequence.SequenceRecords
import io.github.fershibli.megagames.games.sequence.SequenceState
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import kotlinx.serialization.KSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Peg solitaire, memory and "follow the colors", played by tapping like a person would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class MemoryPuzzlesUiTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    private fun <T> open(game: GameId, difficulty: Difficulty, serializer: KSerializer<T>, state: T, store: SaveStore, onFinish: (GameOutcome) -> Unit = {}) {
        val host = GameHost(game, difficulty, AppJson.encodeToJsonElement(serializer, state), store, game.variant(null), onFinish)
        rule.setContent { MegaGamesTheme(Settings()) { GameContent(game, host) } }
        settle()
    }

    /** Lets every pause and animation run to the end. */
    private fun settle() {
        // Small steps: each pause resumes on the main thread before the next one is scheduled.
        repeat(60) {
            rule.mainClock.advanceTimeBy(250)
            rule.waitForIdle()
        }
    }

    /** The autosave, once it shows what [done] expects. */
    private fun <T> saved(store: SaveStore, serializer: KSerializer<T>, done: (T) -> Boolean): T {
        var last: T? = null
        repeat(100) {
            last = store.load()?.let { AppJson.decodeFromJsonElement(serializer, it.state) }
            if (last != null && done(last!!)) return last!!
            Thread.sleep(20)
        }
        error("autosave never reached the expected state, last: $last")
    }

    @Test
    fun pegsJumpByTappingAMarbleThenAHoleAndUndo() {
        val store = SaveStore(app).apply { clear() }
        val board = Pegs.fullBoard(false, Pegs.CENTER)
        open(GameId.PEGS, Difficulty.MEDIUM, PegsState.serializer(), PegsState(false, board, board), store)
        fun tapCell(i: Int) {
            rule.onNodeWithTag("pegs-board").performTouchInput { click(PegsGeometry(width.toFloat()).center(i)) }
            rule.waitForIdle()
        }
        // A hole that is not a legal landing keeps the marble selected; then the real jump.
        tapCell(10)
        tapCell(0)
        tapCell(10)
        tapCell(24)
        val jumped = saved(store, PegsState.serializer()) { it.history.size == 1 }
        assertTrue(jumped.pegs[24])
        assertFalse(jumped.pegs[17])
        assertFalse(jumped.pegs[10])
        assertEquals(31, jumped.count)
        rule.onNodeWithText("Undo").performClick()
        rule.waitForIdle()
        val back = saved(store, PegsState.serializer()) { it.history.isEmpty() }
        assertEquals(board, back.pegs)
        store.clear()
    }

    @Test
    fun memoryKeepsPairsAndTurnsAMissBackOnTheNextTap() {
        val store = SaveStore(app).apply { clear() }
        // Pairs sit side by side: 0-1, 2-3, 4-5...
        val faces = (0 until 12).toList()
        var outcome: GameOutcome? = null
        open(GameId.MEMORY, Difficulty.EASY, MemoryState.serializer(), MemoryState(Memory.FRUITS, 4, 3, faces, List(12) { false }), store) { outcome = it }
        fun tapCard(i: Int) {
            rule.onNodeWithTag("memory-card-$i").performClick()
            settle()
        }
        tapCard(0)
        tapCard(2) // a miss: both stay up
        val miss = saved(store, MemoryState.serializer()) { it.moves == 1 }
        assertEquals(listOf(0, 2), miss.open)
        tapCard(4) // turns the miss back and shows card 4
        tapCard(5) // its pair
        val pair = saved(store, MemoryState.serializer()) { it.moves == 2 }
        assertTrue(pair.matched[4] && pair.matched[5])
        assertTrue(pair.open.isEmpty())
        for (k in listOf(0, 2, 6, 8, 10)) { tapCard(k); tapCard(k + 1) }
        assertEquals(true, outcome?.won)
        val best = WorldStore(app).apply { flush() }.load(Memory.STORE_KEY, MemoryRecords.serializer())
        assertEquals(7, best?.of(Memory.FRUITS, Difficulty.EASY))
        store.clear()
    }

    @Test
    fun followTheColorsShowsThenWaitsForTheSameOrder() {
        val store = SaveStore(app).apply { clear() }
        open(GameId.SEQUENCE, Difficulty.EASY, SequenceState.serializer(), SequenceState(listOf(2, 0, 1, 3, 0)), store)
        rule.onNodeWithText("Start").performClick()
        settle()
        assertEquals(SequencePhase.INPUT, saved(store, SequenceState.serializer()) { it.phase == SequencePhase.INPUT }.phase)
        fun tapPad(i: Int) {
            rule.onNodeWithTag("sequence-pad-$i").performClick()
            rule.waitForIdle()
        }
        tapPad(2)
        settle()
        tapPad(2)
        tapPad(1) // wrong: on Easy the same two colors are shown again
        val retry = saved(store, SequenceState.serializer()) { it.retry }
        assertEquals(2, retry.round)
        settle()
        tapPad(2)
        tapPad(0)
        val next = saved(store, SequenceState.serializer()) { it.round == 3 }
        assertEquals(SequencePhase.WATCH, next.phase)
        settle()
        val best = WorldStore(app).apply { flush() }.load("sequence", SequenceRecords.serializer())
        assertEquals(2, best?.of(Difficulty.EASY))
        store.clear()
    }
}
