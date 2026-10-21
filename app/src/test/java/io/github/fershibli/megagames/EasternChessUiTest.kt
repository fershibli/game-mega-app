package io.github.fershibli.megagames

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
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.eastern.BoardGeometry
import io.github.fershibli.megagames.games.eastern.EasternState
import io.github.fershibli.megagames.games.eastern.Mv
import io.github.fershibli.megagames.games.shogi.Shogi
import io.github.fershibli.megagames.games.shogi.ShogiRules
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Plays xiangqi, janggi and shogi by tapping the board like a person would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class EasternChessUiTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    private fun open(game: GameId, moves: List<Int>? = null, variant: String? = null): SaveStore {
        val store = SaveStore(app).apply { clear() }
        val restored = moves?.let { AppJson.encodeToJsonElement(EasternState.serializer(), EasternState(it, variant)) }
        val host = GameHost(game, Difficulty.EASY, restored, store, game.variant(variant)) {}
        rule.setContent { MegaGamesTheme(Settings()) { GameContent(game, host) } }
        rule.waitForIdle()
        return store
    }

    /** Taps the point or square [sq] of a board drawn with these proportions. */
    private fun tap(key: String, sq: Int, cols: Int, rows: Int, points: Boolean, strip: Float) {
        rule.onNodeWithTag("$key-board").performTouchInput {
            click(BoardGeometry(width.toFloat(), height.toFloat(), cols, rows, points, strip).center(sq))
        }
        rule.waitForIdle()
    }

    /** The saved moves once there are at least [count] of them. */
    private fun savedMoves(store: SaveStore, count: Int): List<Int> {
        var moves = emptyList<Int>()
        for (attempt in 0 until 200) {
            rule.mainClock.advanceTimeBy(100) // the computer waits a moment before it plays
            rule.waitForIdle()
            moves = store.load()?.let { AppJson.decodeFromJsonElement(EasternState.serializer(), it.state).moves }.orEmpty()
            if (moves.size >= count) break
            Thread.sleep(20)
        }
        return moves
    }

    @Test
    fun xiangqiCannonGoesToTheMiddleAndUndoTakesItBack() {
        val store = open(GameId.XIANGQI)
        tap("xiangqi", 7 * 9 + 1, 9, 10, true, 0.8f) // the left cannon
        tap("xiangqi", 7 * 9 + 4, 9, 10, true, 0.8f) // to the middle file
        val moves = savedMoves(store, 2)
        assertEquals(Mv.of(7 * 9 + 1, 7 * 9 + 4), moves.first())
        assertEquals(2, moves.size) // the computer answered
        rule.onNodeWithText("Undo").performClick()
        assertEquals(0, savedMovesAfterUndo(store))
        store.clear()
    }

    private fun savedMovesAfterUndo(store: SaveStore): Int {
        for (attempt in 0 until 100) {
            rule.waitForIdle()
            val n = store.load()?.let { AppJson.decodeFromJsonElement(EasternState.serializer(), it.state).moves.size } ?: 0
            if (n == 0) return 0
            Thread.sleep(20)
        }
        return -1
    }

    @Test
    fun janggiSoldierStepsForwardThenThePlayerPasses() {
        val store = open(GameId.JANGGI)
        tap("janggi", 6 * 9 + 0, 9, 10, true, 0.8f)
        tap("janggi", 5 * 9 + 0, 9, 10, true, 0.8f)
        assertEquals(Mv.of(54, 45), savedMoves(store, 2).first())
        rule.onNodeWithText("Pass").performClick()
        val moves = savedMoves(store, 3)
        assertEquals(Mv.PASS, moves[2])
        store.clear()
    }

    @Test
    fun shogiBishopCapturesAndIsPromotedWithTheBigButton() {
        val opening = listOf(Mv.of(56, 47), Mv.of(24, 33)) // both bishops' diagonals are open
        val store = open(GameId.SHOGI, opening, ShogiRules.STANDARD)
        tap("shogi", 64, 9, 9, false, 1.05f) // the bishop
        tap("shogi", 16, 9, 9, false, 1.05f) // takes the other bishop, in the promotion zone
        rule.onNodeWithText("Promote").performClick()
        rule.waitForIdle()
        assertEquals(Mv.of(64, 16, true), savedMoves(store, 3)[2])
        store.clear()
    }

    @Test
    fun shogiCapturedBishopIsDroppedFromTheHand() {
        val moves = listOf(Mv.of(56, 47), Mv.of(24, 33), Mv.of(64, 16, true), Mv.of(6, 16))
        val position = Shogi.fromMoves(false, moves)
        assertEquals(1, position.hand(0, Shogi.BISHOP))
        val store = open(GameId.SHOGI, moves, ShogiRules.STANDARD)
        rule.onNodeWithTag("shogi-board").performTouchInput {
            // The hand is the strip under the board; the bishop is the only piece in it.
            click(BoardGeometry(width.toFloat(), height.toFloat(), 9, 9, false, 1.05f).slotCenter(0, 0, 7))
        }
        rule.waitForIdle()
        tap("shogi", 40, 9, 9, false, 1.05f)
        assertEquals(Mv.drop(Shogi.BISHOP, 40), savedMoves(store, 5)[4])
        store.clear()
    }

    @Test
    fun miniShogiOpensOnItsSmallBoardAndPlays() {
        val store = open(GameId.SHOGI, emptyList(), ShogiRules.MINI)
        tap("shogi", 15, 5, 5, false, 1.05f) // the pawn in front of the king
        tap("shogi", 10, 5, 5, false, 1.05f)
        assertEquals(Mv.of(15, 10), savedMoves(store, 1).first())
        store.clear()
    }
}
