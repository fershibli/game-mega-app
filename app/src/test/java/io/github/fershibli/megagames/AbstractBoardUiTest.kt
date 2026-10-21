package io.github.fershibli.megagames

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.fourinrow.FourInRow
import io.github.fershibli.megagames.games.fourinrow.FourInRowState
import io.github.fershibli.megagames.games.go.Go
import io.github.fershibli.megagames.games.go.GoState
import io.github.fershibli.megagames.games.gomoku.Gomoku
import io.github.fershibli.megagames.games.gomoku.GomokuState
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Plays Go, five in a row and four in a row by tapping the boards like a person would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class AbstractBoardUiTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    private fun <T> waitForSave(store: SaveStore, read: (JsonElement) -> T, done: (T) -> Boolean): T? {
        var saved: T? = null
        for (attempt in 0 until 150) {
            Thread.sleep(20)
            // The computer waits a moment before answering: let the screen's clock run.
            rule.mainClock.advanceTimeBy(100)
            rule.waitForIdle()
            saved = store.load()?.let { read(it.state) }
            if (saved != null && done(saved)) break
        }
        return saved
    }

    /** Taps crossing [i] of an [n]×[n] board drawn as n×n square cells. */
    private fun tapCrossing(tag: String, i: Int, n: Int) = rule.onNodeWithTag(tag).performTouchInput {
        val cell = width / n.toFloat()
        click(Offset((i % n + 0.5f) * cell, (i / n + 0.5f) * cell))
    }

    private fun open(game: GameId, store: SaveStore, variant: String? = null, restored: JsonElement? = null, onFinish: (GameOutcome) -> Unit = {}) {
        val host = GameHost(game, Difficulty.EASY, restored, store, game.variant(variant), onFinish = onFinish)
        rule.setContent { MegaGamesTheme(Settings(language = AppLanguage.ENGLISH)) { GameContent(game, host) } }
        rule.waitForIdle()
    }

    @Test
    fun goStoneIsPlacedWithOneTapOnTheSmallBoard() {
        val store = SaveStore(app).apply { clear() }
        open(GameId.GO, store, "9")
        tapCrossing("go-board", 4 * 9 + 4, 9)
        val saved = waitForSave(store, { AppJson.decodeFromJsonElement(GoState.serializer(), it) }) { it.moves.size >= 2 }
        assertEquals(9, saved!!.size)
        assertEquals(40, saved.moves.first())
        // The computer answered with a stone or a pass.
        assertTrue(saved.moves.size >= 2)
        store.clear()
    }

    @Test
    fun goOnTheBigBoardAsksForASecondTap() {
        val store = SaveStore(app).apply { clear() }
        open(GameId.GO, store, "19")
        val point = 3 * 19 + 15
        tapCrossing("go-board", point, 19)
        rule.onNodeWithText("Tap the same crossing again", substring = true).assertExists()
        tapCrossing("go-board", point, 19)
        val saved = waitForSave(store, { AppJson.decodeFromJsonElement(GoState.serializer(), it) }) { it.moves.isNotEmpty() }
        assertEquals(19, saved!!.size)
        assertEquals(point, saved.moves.first())
        store.clear()
    }

    @Test
    fun goPassesLeadToCountingAndTheCount() {
        val store = SaveStore(app).apply { clear() }
        // Black's wall on column 4 touches White's on column 5: nothing is left to play. Black to move.
        val moves = (0 until 9).flatMap { r -> listOf(r * 9 + 4, r * 9 + 5) }
        val start = GoState(9, moves)
        var outcome: GameOutcome? = null
        open(GameId.GO, store, "9", AppJson.encodeToJsonElement(GoState.serializer(), start)) { outcome = it }
        rule.onNodeWithText("Pass").performClick()
        // With nothing left to play for, the computer passes too.
        val saved = waitForSave(store, { AppJson.decodeFromJsonElement(GoState.serializer(), it) }) { it.scoring }
        assertTrue(saved!!.scoring)
        assertEquals(Go.PASS, saved.moves.last())
        rule.waitForIdle()
        rule.onNodeWithText("You: 45 · Computer: 42.5", substring = true).assertExists()
        rule.onNodeWithText("Count").performClick()
        rule.waitForIdle()
        assertEquals(true, outcome?.won)
    }

    @Test
    fun gomokuStoneNeedsASecondTapToBePlaced() {
        val store = SaveStore(app).apply { clear() }
        open(GameId.GOMOKU, store)
        tapCrossing("gomoku-board", Gomoku.CENTER, Gomoku.N)
        rule.waitForIdle()
        tapCrossing("gomoku-board", Gomoku.CENTER, Gomoku.N)
        val saved = waitForSave(store, { AppJson.decodeFromJsonElement(GomokuState.serializer(), it) }) { it.moves.size >= 2 }
        assertEquals(Gomoku.CENTER, saved!!.moves.first())
        assertEquals(2, saved.moves.size)
        store.clear()
    }

    @Test
    fun fourInRowDiscDropsIntoTheTappedColumn() {
        val store = SaveStore(app).apply { clear() }
        open(GameId.FOURINROW, store)
        // Tap near the top of the board area, above column 2: the whole column height counts.
        rule.onNodeWithTag("fourinrow-board").performTouchInput {
            val cell = minOf(width / (FourInRow.COLS + 0.6f), height / (FourInRow.ROWS + 1.6f))
            val left = (width - cell * FourInRow.COLS) / 2
            click(Offset(left + 2.5f * cell, 10f))
        }
        val saved = waitForSave(store, { AppJson.decodeFromJsonElement(FourInRowState.serializer(), it) }) { it.moves.size >= 2 }
        assertEquals(2, saved!!.moves.first())
        assertEquals(2, saved.moves.size)
        store.clear()
    }
}
