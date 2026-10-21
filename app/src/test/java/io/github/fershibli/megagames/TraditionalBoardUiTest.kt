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
import io.github.fershibli.megagames.games.ludo.Ludo
import io.github.fershibli.megagames.games.ludo.LudoBoard
import io.github.fershibli.megagames.games.ludo.LudoState
import io.github.fershibli.megagames.games.mancala.MancalaGeometry
import io.github.fershibli.megagames.games.mancala.MancalaState
import io.github.fershibli.megagames.games.morris.Morris
import io.github.fershibli.megagames.games.morris.MorrisGeometry
import io.github.fershibli.megagames.games.morris.MorrisRules
import io.github.fershibli.megagames.games.morris.MorrisState
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Plays Ludo, Nine Men's Morris and Mancala by tapping the boards like a person would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class TraditionalBoardUiTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    private fun <T> waitForSave(store: SaveStore, serializer: KSerializer<T>, done: (T) -> Boolean): T? {
        var saved: T? = null
        for (attempt in 0 until 150) {
            Thread.sleep(20)
            rule.waitForIdle()
            saved = store.load()?.let { AppJson.decodeFromJsonElement(serializer, it.state) }
            if (saved != null && done(saved)) break
        }
        return saved
    }

    private fun <T> open(game: GameId, variant: String, serializer: KSerializer<T>, start: T?): SaveStore {
        val store = SaveStore(app).apply { clear() }
        val restored: JsonElement? = start?.let { AppJson.encodeToJsonElement(serializer, it) }
        val host = GameHost(game, Difficulty.EASY, restored, store, game.variant(variant)) {}
        rule.setContent { MegaGamesTheme(Settings(language = AppLanguage.ENGLISH)) { GameContent(game, host) } }
        rule.waitForIdle()
        return store
    }

    /** Taps the middle of Ludo cell (row, column) of the 15×15 board. */
    private fun tapLudo(cell: Int) = rule.onNodeWithTag("ludo-board").performTouchInput {
        val c = width / 15f
        click(LudoBoard.cellCenter(cell) * c)
    }

    @Test
    fun ludoChooseTheTableThenTapTheDie() {
        val store = open(GameId.LUDO, "classic", LudoState.serializer(), null)
        rule.onNodeWithText("2").performClick()
        val table = waitForSave(store, LudoState.serializer()) { it.colors.isNotEmpty() }
        assertEquals(3, table?.colors?.size)
        // The die sits in the middle of the board.
        tapLudo(7 * 15 + 7)
        val rolled = waitForSave(store, LudoState.serializer()) { it.die > 0 || it.turn != 0 }
        assertNotNull(rolled)
        assertTrue(rolled!!.die > 0 || rolled.turn != 0)
        store.clear()
    }

    @Test
    fun ludoTapAPieceToMoveItAndUndo() {
        // Blue rolled a 6: it may bring a piece out or move the piece at 10.
        val start = LudoState(Ludo.colorsFor(2), listOf(listOf(10, Ludo.YARD, Ludo.YARD, Ludo.YARD), List(4) { Ludo.YARD }), die = 6, sixes = 1)
        val store = open(GameId.LUDO, "classic", LudoState.serializer(), start)
        tapLudo(LudoBoard.cell(Ludo.BLUE, 10))
        val moved = waitForSave(store, LudoState.serializer()) { it.pieces[0][0] == 16 }
        assertEquals(16, moved?.pieces?.get(0)?.get(0))
        assertEquals("a 6 plays again", 0, moved?.turn)
        rule.onNodeWithText("Undo").performClick()
        val undone = waitForSave(store, LudoState.serializer()) { it.pieces[0][0] == 10 }
        assertEquals(10, undone?.pieces?.get(0)?.get(0))
        assertEquals(6, undone?.die)
        store.clear()
    }

    private fun tapMorris(point: Int, rules: MorrisRules) = rule.onNodeWithTag("morris-board").performTouchInput {
        click(MorrisGeometry(width.toFloat(), rules).point(rules.x(point), rules.y(point)))
    }

    @Test
    fun morrisPlaceAPieceByTappingAPoint() {
        val rules = MorrisRules.NINE
        val store = open(GameId.MORRIS, "nine", MorrisState.serializer(), null)
        tapMorris(0, rules)
        val saved = waitForSave(store, MorrisState.serializer()) { it.board[0] == Morris.WHITE }
        assertEquals(Morris.WHITE, saved?.board?.get(0))
        assertEquals(8, saved?.hand?.get(0))
    }

    @Test
    fun morrisMillThenTapTheComputerPieceToRemove() {
        val rules = MorrisRules.NINE
        val board = List(rules.points) { when (it) { 0, 1 -> Morris.WHITE; 9, 20 -> Morris.BLACK; else -> 0 } }
        val start = MorrisState(board, listOf(7, 7))
        val store = open(GameId.MORRIS, "nine", MorrisState.serializer(), start)
        tapMorris(2, rules)
        val mill = waitForSave(store, MorrisState.serializer()) { it.removing }
        assertTrue(mill!!.removing)
        tapMorris(20, rules)
        // Black had 2 on the board and 7 to place; one is gone (the computer may already have placed another).
        val saved = waitForSave(store, MorrisState.serializer()) { !it.removing && it.board.count { v -> v == Morris.BLACK } + it.hand[1] == 8 }
        assertEquals(8, saved!!.board.count { it == Morris.BLACK } + saved.hand[1])
        assertEquals(Morris.WHITE, saved.board[2])
        store.clear()
    }

    private fun tapMancala(hole: Int) = rule.onNodeWithTag("mancala-board").performTouchInput {
        click(MancalaGeometry(width.toFloat()).rect(hole).center)
    }

    @Test
    fun mancalaTapAPitToSowItAndPlayAgainFromTheStore() {
        val store = open(GameId.MANCALA, "kalah", MancalaState.serializer(), null)
        // Pit 2 of the bottom row has 4 seeds: the last one falls in your store.
        tapMancala(2)
        val saved = waitForSave(store, MancalaState.serializer()) { it.last == 2 }
        assertEquals(1, saved?.pits?.get(6))
        assertEquals(listOf(4, 4, 0, 5, 5, 5), saved?.pits?.take(6))
        assertEquals(0, saved?.turn)
        store.clear()
    }

    @Test
    fun owareTapAPitToSowIt() {
        val store = open(GameId.MANCALA, "oware", MancalaState.serializer(), null)
        tapMancala(5)
        // The four seeds of your last pit went into the computer's row (it may already have answered,
        // but no move of its first turn can reach your last pit).
        val saved = waitForSave(store, MancalaState.serializer()) { it.pits[5] == 0 }
        assertEquals(0, saved?.pits?.get(5))
        assertEquals(48, saved?.pits?.sum())
        store.clear()
    }
}
