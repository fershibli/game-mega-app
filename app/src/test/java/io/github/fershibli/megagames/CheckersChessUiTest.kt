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
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.checkers.Checkers
import io.github.fershibli.megagames.games.checkers.CheckersMove
import io.github.fershibli.megagames.games.checkers.CheckersState
import io.github.fershibli.megagames.games.chess.ChessState
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Plays checkers and chess by tapping the boards like a person would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class CheckersChessUiTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    private fun <T> waitForSave(store: SaveStore, read: (kotlinx.serialization.json.JsonElement) -> T, done: (T) -> Boolean): T? {
        var saved: T? = null
        for (attempt in 0 until 100) {
            Thread.sleep(20)
            rule.waitForIdle()
            saved = store.load()?.let { read(it.state) }
            if (saved != null && done(saved)) break
        }
        return saved
    }

    /** Taps square [sq] of a checkers board with [n] squares per side (3% frame around it). */
    private fun tapCheckers(sq: Int, n: Int) = rule.onNodeWithTag("checkers-board").performTouchInput {
        val frame = width * 0.03f
        val cell = (width - frame * 2) / n
        click(Offset(frame + (sq % n + 0.5f) * cell, frame + (sq / n + 0.5f) * cell))
    }

    /** Taps a chess square by name ("e2"); White is at the bottom, 5% frame with the coordinates. */
    private fun tapChess(square: String) = rule.onNodeWithTag("chess-board").performTouchInput {
        val frame = width * 0.05f
        val cell = (width - frame * 2) / 8
        val file = square[0] - 'a'
        val rank = square[1] - '1'
        click(Offset(frame + (file + 0.5f) * cell, frame + (7 - rank + 0.5f) * cell))
    }

    @Test
    fun checkersMoveByTappingAPieceAndItsDestination() {
        val store = SaveStore(app).apply { clear() }
        val rules = GameId.CHECKERS.variant("brazilian")
        val host = GameHost(GameId.CHECKERS, Difficulty.EASY, null, store, rules) {}
        rule.setContent { MegaGamesTheme(Settings()) { GameContent(GameId.CHECKERS, host) } }
        rule.waitForIdle()
        // Row 5, column 0 (bottom left of the front row) to row 4, column 1.
        tapCheckers(5 * 8 + 0, 8)
        rule.waitForIdle()
        tapCheckers(4 * 8 + 1, 8)
        val saved = waitForSave(store, { AppJson.decodeFromJsonElement(CheckersState.serializer(), it) }) { it.last != null }
        // The piece moved (the computer may already have answered, but cannot take it yet).
        assertEquals(Checkers.MAN, saved!!.board[33])
        assertEquals(0, saved.board[40])
        store.clear()
    }

    @Test
    fun checkersMultipleCaptureWithOneTapOnTheFinalSquare() {
        val store = SaveStore(app).apply { clear() }
        val board = IntArray(64).apply {
            this[6 * 8 + 5] = Checkers.MAN
            this[5 * 8 + 6] = -Checkers.MAN
            this[3 * 8 + 6] = -Checkers.MAN
            this[0 * 8 + 1] = -Checkers.MAN
        }
        val start = CheckersState(board.toList())
        val host = GameHost(GameId.CHECKERS, Difficulty.EASY, AppJson.encodeToJsonElement(CheckersState.serializer(), start), store, GameId.CHECKERS.variant("english")) {}
        rule.setContent { MegaGamesTheme(Settings()) { GameContent(GameId.CHECKERS, host) } }
        rule.waitForIdle()
        tapCheckers(6 * 8 + 5, 8)
        rule.waitForIdle()
        tapCheckers(2 * 8 + 5, 8)
        val saved = waitForSave(store, { AppJson.decodeFromJsonElement(CheckersState.serializer(), it) }) { it.last != null }
        val first = saved!!.last!!
        val expected = CheckersMove(listOf(53, 39, 21), listOf(46, 30))
        // Either the saved move is ours, or the computer already answered after it.
        if (first.from == 53) assertEquals(expected, first)
        assertEquals(0, saved.board[46])
        assertEquals(0, saved.board[30])
        store.clear()
    }

    @Test
    fun chessPawnMovesByTappingItAndItsDestination() {
        val store = SaveStore(app).apply { clear() }
        val host = GameHost(GameId.CHESS, Difficulty.EASY, null, store, GameId.CHESS.variant("standard")) {}
        rule.setContent { MegaGamesTheme(Settings()) { GameContent(GameId.CHESS, host) } }
        rule.waitForIdle()
        tapChess("e2")
        rule.waitForIdle()
        tapChess("e4")
        val saved = waitForSave(store, { AppJson.decodeFromJsonElement(ChessState.serializer(), it) }) { it.moves.isNotEmpty() }
        assertEquals("e2e4", saved?.moves?.first())
        store.clear()
    }

    @Test
    fun famousGamesAreChosenFromAListAndReplayedWithNext() {
        val store = SaveStore(app).apply { clear() }
        val host = GameHost(GameId.CHESS, Difficulty.EASY, null, store, GameId.CHESS.variant("famous")) {}
        rule.setContent { MegaGamesTheme(Settings(language = AppLanguage.ENGLISH)) { GameContent(GameId.CHESS, host) } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTextCount("The Opera Game") > 0 }
        rule.onNodeWithText("The Opera Game").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Next").performClick()
        rule.waitForIdle()
        val saved = waitForSave(store, { AppJson.decodeFromJsonElement(ChessState.serializer(), it) }) { it.ply == 2 }
        assertEquals("opera", saved?.famous)
        assertEquals(2, saved?.ply)
        rule.onNodeWithText("End").performClick()
        rule.onNodeWithText("End of the game: White wins.", substring = true).assertExists()
        store.clear()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
