package io.github.fershibli.megagames

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import io.github.fershibli.megagames.core.PlaySetup
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.backgammon.AI
import io.github.fershibli.megagames.games.backgammon.BackgammonState
import io.github.fershibli.megagames.games.backgammon.BgPosition
import io.github.fershibli.megagames.games.backgammon.HUMAN
import io.github.fershibli.megagames.games.checkers.Checkers
import io.github.fershibli.megagames.games.checkers.CheckersRules
import io.github.fershibli.megagames.games.checkers.CheckersState
import io.github.fershibli.megagames.games.chess.Chess
import io.github.fershibli.megagames.games.chess.ChessState
import io.github.fershibli.megagames.games.eastern.BoardGeometry
import io.github.fershibli.megagames.games.eastern.EasternState
import io.github.fershibli.megagames.games.eastern.Mv
import io.github.fershibli.megagames.games.go.GoState
import io.github.fershibli.megagames.games.shogi.Shogi
import io.github.fershibli.megagames.games.ludo.Ludo
import io.github.fershibli.megagames.games.ludo.LudoBoard
import io.github.fershibli.megagames.games.ludo.LudoState
import io.github.fershibli.megagames.games.mancala.Mancala
import io.github.fershibli.megagames.games.mancala.MancalaGeometry
import io.github.fershibli.megagames.games.mancala.MancalaRules
import io.github.fershibli.megagames.games.mancala.MancalaState
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import kotlinx.serialization.KSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Board games played by people taking turns on one phone: the computer never plays, the phone is
 * passed between the sides (the board itself never turns) and the next side can move.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class BoardTakeTurnsTest {
    @get:Rule val rule = createComposeRule()
    private val store get() = SaveStore(RuntimeEnvironment.getApplication())
    private var outcome: GameOutcome? = null

    private fun <T> open(game: GameId, variant: String?, serializer: KSerializer<T>, start: T? = null, seats: Int = 0) {
        store.clear()
        val restored = start?.let { AppJson.encodeToJsonElement(serializer, it) }
        val host = GameHost(game, Difficulty.HARD, restored, store, game.variant(variant), PlaySetup(together = true, seats = seats), onFinish = { outcome = it })
        rule.setContent { MegaGamesTheme(Settings(language = AppLanguage.ENGLISH)) { GameContent(game, host) } }
        rule.waitForIdle()
    }

    private fun <T> saved(serializer: KSerializer<T>): T = AppJson.decodeFromJsonElement(serializer, store.load()!!.state)

    /** Lets time run (the computer would have answered by now), then reads the save. */
    private fun <T> settle(serializer: KSerializer<T>): T {
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForIdle()
        return saved(serializer)
    }

    /** The cover names [next]; they tap "Ready" and, after a short pause, the board is theirs. */
    private fun passPhoneTo(next: String) {
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Pass to the next player").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(next).assertIsDisplayed()
        rule.onNodeWithContentDescription("Ready, it is my turn").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("handoff").fetchSemanticsNodes().isEmpty() }
    }

    private fun status(text: String) = rule.onNodeWithText(text, substring = true).assertIsDisplayed()

    @Test
    fun checkersBothSidesArePeople() {
        val rules = CheckersRules.of("brazilian")
        open(GameId.CHECKERS, "brazilian", CheckersState.serializer())
        val first = if (rules.darkMovesFirst) "Dark pieces" else "Light pieces"
        val second = if (rules.darkMovesFirst) "Light pieces" else "Dark pieces"
        fun tap(sq: Int) = rule.onNodeWithTag("checkers-board").performTouchInput {
            val frame = width * 0.03f
            val cell = (width - frame * 2) / rules.size
            click(Offset(frame + (sq % rules.size + 0.5f) * cell, frame + (sq / rules.size + 0.5f) * cell))
        }
        status("$first to play")
        val opening = Checkers.legalMoves(Checkers.initial(rules), Checkers.PLAYER, rules).first()
        tap(opening.path.first())
        tap(opening.path.last())
        passPhoneTo(second)
        status("$second to play")
        val board = saved(CheckersState.serializer()).board.toIntArray()
        val reply = Checkers.legalMoves(board, Checkers.AI, rules).first()
        tap(reply.path.first())
        tap(reply.path.last())
        passPhoneTo(first)
        val s = settle(CheckersState.serializer())
        assertEquals(Checkers.PLAYER, s.turn)
        assertEquals(reply, s.last)
        // Undo takes back one move only, not a move and an answer.
        rule.onNodeWithText("Undo").performClick()
        rule.waitForIdle()
        assertEquals(Checkers.AI, saved(CheckersState.serializer()).turn)
        assertNull(outcome)
    }

    @Test
    fun chessWhiteAndBlackTakeTurnsAndARestoredGameWaitsForAPerson() {
        fun tap(sq: Int) = rule.onNodeWithTag("chess-board").performTouchInput {
            val frame = width * 0.05f
            val cell = (width - frame * 2) / 8
            click(Offset(frame + (Chess.file(sq) + 0.5f) * cell, frame + (7 - Chess.rank(sq) + 0.5f) * cell))
        }
        // A game saved after White's first move: Black is a person, so nothing happens by itself.
        open(GameId.CHESS, "standard", ChessState.serializer(), ChessState(listOf("e2e4")))
        status("Black to play")
        assertEquals(listOf("e2e4"), settle(ChessState.serializer()).moves)
        tap(52) // e7
        tap(36) // e5
        passPhoneTo("White")
        status("White to play")
        tap(6) // g1
        tap(21) // f3
        passPhoneTo("Black")
        assertEquals(listOf("e2e4", "e7e5", "g1f3"), settle(ChessState.serializer()).moves)
        rule.onNodeWithText("Undo").performClick()
        rule.waitForIdle()
        assertEquals(listOf("e2e4", "e7e5"), saved(ChessState.serializer()).moves)
        assertNull(outcome)
    }

    @Test
    fun shogiTopPlayerDropsAPieceFromItsOwnHand() {
        // Both bishops were taken, one by each side; the bottom player then moved a pawn.
        val moves = listOf(Mv.of(56, 47), Mv.of(24, 33), Mv.of(64, 16, true), Mv.of(6, 16), Mv.of(54, 45))
        assertEquals(1, Shogi.fromMoves(false, moves).hand(1, Shogi.BISHOP))
        open(GameId.SHOGI, "standard", EasternState.serializer(), EasternState(moves, "standard"))
        status("Top player to play")
        fun tap(at: BoardGeometry.() -> Offset) = rule.onNodeWithTag("shogi-board").performTouchInput {
            click(BoardGeometry(width.toFloat(), height.toFloat(), 9, 9, false, 1.05f).at())
        }
        // The top player's hand is the strip above the board.
        tap { slotCenter(1, 0, 7) }
        tap { center(40) }
        passPhoneTo("Bottom player")
        val s = settle(EasternState.serializer())
        assertEquals(moves + Mv.drop(Shogi.BISHOP, 40), s.moves)
        assertNull(outcome)
    }

    @Test
    fun backgammonEachSideRollsItsOwnDice() {
        // Light has a checker on the bar and every entry point is closed: no move, only Done.
        val points = MutableList(26) { 0 }
        for (p in 19..24) points[p] = -2
        points[1] = -3
        val position = BgPosition(points, barHuman = 1, offHuman = 14)
        open(GameId.BACKGAMMON, null, BackgammonState.serializer(), BackgammonState(position, HUMAN, listOf(3, 5), position))
        status("Light pieces to play")
        rule.onNodeWithText("Done").performClick()
        passPhoneTo("Dark pieces")
        status("Roll the dice")
        // The computer does not roll for the dark side.
        assertTrue(settle(BackgammonState.serializer()).roll.isEmpty())
        rule.onNodeWithText("Roll dice").performClick()
        val s = settle(BackgammonState.serializer())
        assertEquals(AI, s.turn)
        assertEquals(2, s.roll.size)
        assertTrue(s.aiMoves.isEmpty())
        assertEquals(position, s.turnStart)
        assertNull(outcome)
    }

    @Test
    fun goPassingHandsThePhoneOverAndTheOtherPlayerIsTold() {
        open(GameId.GO, "9", GoState.serializer())
        status("Black to play")
        rule.onNodeWithTag("go-board").performTouchInput {
            val cell = width / 9f
            click(Offset(4.5f * cell, 4.5f * cell))
        }
        passPhoneTo("White")
        status("White to play")
        rule.onNodeWithText("Pass").performClick()
        passPhoneTo("Black")
        status("The other player passed")
        val s = settle(GoState.serializer())
        assertEquals(2, s.moves.size)
        assertEquals(40, s.moves.first())
        assertNull(outcome)
    }

    @Test
    fun ludoThreePeopleAroundTheTable() {
        // Blue rolled a 3 with every piece in the yard: nothing can move, so the turn goes to Red.
        val colors = Ludo.colorsFor(3)
        open(GameId.LUDO, "classic", LudoState.serializer(), LudoState(colors, colors.map { List(Ludo.PIECES) { Ludo.YARD } }, die = 3), seats = 3)
        passPhoneTo("Red")
        status("Red: tap the die")
        // Red is a person: the computer does not roll for it.
        val waiting = settle(LudoState.serializer())
        assertEquals(3, waiting.colors.size)
        assertEquals(1, waiting.turn)
        assertEquals(0, waiting.die)
        rule.onNodeWithTag("ludo-board").performTouchInput { click(LudoBoard.cellCenter(7 * 15 + 7) * (width / 15f)) }
        rule.waitUntil(10_000) { rule.waitForIdle(); saved(LudoState.serializer()).let { it.die > 0 || it.turn != 1 } }
        assertNull(outcome)
    }

    @Test
    fun mancalaTopRowIsAPersonToo() {
        val rules = MancalaRules.of("kalah")
        val start = Mancala.initial()
        // A first move whose last seed does not fall in the store, so the turn goes to the top row.
        val hole = Mancala.legalMoves(start.pits.toIntArray(), 0, rules).first { Mancala.play(start, it, rules).turn == 1 }
        open(GameId.MANCALA, "kalah", MancalaState.serializer())
        status("Bottom player to play")
        rule.onNodeWithTag("mancala-board").performTouchInput { click(MancalaGeometry(width.toFloat()).rect(hole).center) }
        passPhoneTo("Top player")
        status("Top player to play")
        val s = settle(MancalaState.serializer())
        assertEquals(1, s.turn)
        val top = Mancala.legalMoves(s.pits.toIntArray(), 1, rules).first()
        rule.onNodeWithTag("mancala-board").performTouchInput { click(MancalaGeometry(width.toFloat()).rect(top).center) }
        rule.waitUntil(10_000) { rule.waitForIdle(); saved(MancalaState.serializer()).last == top }
        assertNull(outcome)
    }
}
